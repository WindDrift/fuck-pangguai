package io.github.winddrift.fuckpangguai.confirm

import io.github.winddrift.fuckpangguai.device.DeviceDataSource
import io.github.winddrift.fuckpangguai.device.DevicePlans
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.location.GeoPoint
import io.github.winddrift.fuckpangguai.location.LocationSource
import io.github.winddrift.fuckpangguai.net.dto.AfterPay
import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.net.dto.MsgSyncBean
import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.ScanV2Bean
import io.github.winddrift.fuckpangguai.net.dto.Sku
import io.github.winddrift.fuckpangguai.net.dto.Unlock
import io.github.winddrift.fuckpangguai.net.dto.WaterSync
import io.github.winddrift.fuckpangguai.storage.ConfirmedDevice
import io.github.winddrift.fuckpangguai.storage.DeviceConfigSource
import io.github.winddrift.fuckpangguai.storage.SettingsSource
import io.github.winddrift.fuckpangguai.water.WaterDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmControllerTest {

    @Test
    fun `BLE device is blocked without binding or unlocking`() = runTest {
        val deps = Deps()
        val controller = deps.controller(this)
        controller.start(device(bluetooth = 1))
        advanceUntilIdle()
        assertTrue(controller.state.value is ConfirmState.Blocked)
        assertEquals(0, deps.deviceSource.bindCalls)
        assertEquals(0, deps.waterSource.unlockCalls)
    }

    @Test
    fun `missing location permission asks for permission without loading plans`() = runTest {
        val deps = Deps(locationGranted = false)
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()
        assertTrue(controller.state.value is ConfirmState.NeedsLocationPermission)
        assertEquals(0, deps.deviceSource.loadCalls)
        assertEquals(0, deps.deviceSource.refreshCalls)
        assertEquals(0, deps.locationSource.calls)
    }

    @Test
    fun `denied permission keeps the permission state`() = runTest {
        val deps = Deps(locationGranted = false)
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.retryLocation()
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.NeedsLocationPermission)
        assertEquals(0, deps.deviceSource.loadCalls)
    }

    @Test
    fun `granting permission reloads plans`() = runTest {
        val deps = Deps(locationGranted = false)
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        deps.locationSource.grant()
        controller.retryLocation()
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.Ready)
        assertEquals(1, deps.deviceSource.loadCalls)
    }

    @Test
    fun `plans are shown before location resolves`() = runTest {
        val deps = Deps()
        val gate = deps.locationSource.enqueueGate()
        val controller = deps.controller(this)

        controller.start(deps.device)
        advanceUntilIdle()

        val ready = controller.state.value as ConfirmState.Ready
        assertEquals(LocationPhase.Fetching, ready.value.locationPhase)
        assertNull(ready.value.location)
        assertEquals(1, deps.deviceSource.loadCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        val updated = controller.state.value as ConfirmState.Ready
        assertEquals(LocationPhase.Ready, updated.value.locationPhase)
        assertEquals("30.123456", updated.value.location?.lat)
    }

    @Test
    fun `location failure keeps plans browsable`() = runTest {
        val deps = Deps()
        deps.locationSource.setResult(null)
        val controller = deps.controller(this)

        controller.start(deps.device)
        advanceUntilIdle()

        val ready = controller.state.value as ConfirmState.Ready
        assertEquals(LocationPhase.Failed, ready.value.locationPhase)
        assertEquals("1 元", ready.value.sku.name)

        controller.unlock()
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.Ready)
        assertEquals(0, deps.waterSource.unlockCalls)
    }

    @Test
    fun `retry after location failure only refreshes location`() = runTest {
        val deps = Deps()
        deps.locationSource.setResult(null)
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()
        assertEquals(
            LocationPhase.Failed,
            (controller.state.value as ConfirmState.Ready).value.locationPhase,
        )
        assertEquals(1, deps.deviceSource.loadCalls)

        deps.locationSource.setResult(GeoPoint(30.0, 120.0))
        controller.retryLocation()
        advanceUntilIdle()

        val ready = controller.state.value as ConfirmState.Ready
        assertEquals(LocationPhase.Ready, ready.value.locationPhase)
        assertEquals(1, deps.deviceSource.loadCalls)
        assertEquals(0, deps.deviceSource.refreshCalls)
        assertEquals(2, deps.locationSource.calls)
    }

    @Test
    fun `retry while location is fetching is ignored`() = runTest {
        val deps = Deps()
        val gate = deps.locationSource.enqueueGate()
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.retryLocation()
        controller.retryLocation()
        advanceUntilIdle()

        assertEquals(1, deps.locationSource.calls)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `dry run never binds or unlocks`() = runTest {
        val deps = Deps(dryRun = true)
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()
        assertTrue(controller.state.value is ConfirmState.Ready)
        controller.unlock()
        advanceUntilIdle()
        assertTrue(controller.state.value is ConfirmState.DryRun)
        assertEquals(0, deps.deviceSource.bindCalls)
        assertEquals(0, deps.waterSource.unlockCalls)
    }

    @Test
    fun `dry run works without location`() = runTest {
        val deps = Deps(dryRun = true)
        val gate = deps.locationSource.enqueueGate()
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.unlock()
        advanceUntilIdle()

        val dryRun = controller.state.value as ConfirmState.DryRun
        assertEquals("", dryRun.info.lat)
        assertEquals("", dryRun.info.lng)
        assertEquals(0, deps.waterSource.unlockCalls)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `real unlock binds channel then unlocks`() = runTest {
        val deps = Deps()
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()
        controller.unlock()
        advanceUntilIdle()
        assertTrue(controller.state.value is ConfirmState.Unlocked)
        assertEquals(1, deps.deviceSource.bindCalls)
        assertEquals(1, deps.waterSource.unlockCalls)
        assertNotNull(deps.configSource.saved)
    }

    @Test
    fun `msg sync timeout produces error and does not mark device confirmed`() = runTest {
        val deps = Deps(msgReady = false)
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()
        controller.unlock()
        advanceTimeBy(16_000)
        advanceUntilIdle()
        assertTrue(controller.state.value is ConfirmState.Error)
        assertNull(deps.configSource.saved)
        assertEquals(1, deps.deviceSource.clearCalls)
    }

    @Test
    fun `one tap auto unlocks a previously confirmed device`() = runTest {
        val deps = Deps(oneTap = true)
        deps.configSource.existing = confirmedDevice()
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()
        assertTrue(controller.state.value is ConfirmState.Unlocked)
        assertEquals(1, deps.waterSource.unlockCalls)
    }

    @Test
    fun `one tap waits for location before unlocking`() = runTest {
        val deps = Deps(oneTap = true)
        deps.configSource.existing = confirmedDevice()
        val gate = deps.locationSource.enqueueGate()
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        val ready = controller.state.value as ConfirmState.Ready
        assertEquals(LocationPhase.Fetching, ready.value.locationPhase)
        assertEquals(0, deps.waterSource.unlockCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.Unlocked)
        assertEquals(1, deps.waterSource.unlockCalls)
    }

    @Test
    fun `one tap auto unlocks after a successful location retry`() = runTest {
        val deps = Deps(oneTap = true)
        deps.configSource.existing = confirmedDevice()
        deps.locationSource.setResult(null)
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()
        assertEquals(
            LocationPhase.Failed,
            (controller.state.value as ConfirmState.Ready).value.locationPhase,
        )
        assertEquals(0, deps.waterSource.unlockCalls)

        deps.locationSource.setResult(GeoPoint(30.0, 120.0))
        controller.retryLocation()
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.Unlocked)
        assertEquals(1, deps.waterSource.unlockCalls)
    }

    @Test
    fun `cache hit shows cached plans and refresh does not replace them`() = runTest {
        val deps = Deps(cachedPlans = cachedPlans(skuName = "cached"))
        deps.deviceSource.plans = plans(skuName = "fresh")
        val controller = deps.controller(this)

        controller.start(deps.device)
        advanceUntilIdle()

        val ready = controller.state.value as ConfirmState.Ready
        assertEquals("cached", ready.value.sku.name)
        assertEquals(0, deps.deviceSource.loadCalls)
        assertEquals(1, deps.deviceSource.refreshCalls)
        assertEquals(
            "cached",
            (controller.state.value as ConfirmState.Ready).value.sku.name,
        )
    }

    @Test
    fun `refresh failure keeps ready state`() = runTest {
        val deps = Deps(cachedPlans = cachedPlans(), refreshFails = true)
        val controller = deps.controller(this)

        controller.start(deps.device)
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.Ready)
        assertEquals(1, deps.deviceSource.refreshCalls)
    }

    @Test
    fun `unlock failure clears cached plans and keeps the error`() = runTest {
        val deps = Deps(unlockFails = true, cachedPlans = cachedPlans())
        val controller = deps.controller(this)

        controller.start(deps.device)
        advanceUntilIdle()
        controller.unlock()
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.Error)
        assertEquals(1, deps.deviceSource.clearCalls)
        assertNull(deps.configSource.saved)
    }

    @Test
    fun `starting a new device ignores the old location result`() = runTest {
        val deps = Deps()
        val gate = deps.locationSource.enqueueGate()
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.start(device(goodsId = "goods-2"))
        advanceUntilIdle()

        val ready = controller.state.value as ConfirmState.Ready
        assertEquals("goods-2", ready.value.device.scan.id)
        assertEquals(LocationPhase.Ready, ready.value.locationPhase)

        gate.complete(Unit)
        advanceUntilIdle()

        val updated = controller.state.value as ConfirmState.Ready
        assertEquals("goods-2", updated.value.device.scan.id)
        assertEquals(LocationPhase.Ready, updated.value.locationPhase)
    }

    private fun device(
        bluetooth: Int = 0,
        goodsId: String = "goods-1",
    ) = ResolvedDevice(
        scan = ScanV2Bean(
            categoryCode = "04",
            categoryName = "饮水机",
            id = goodsId,
        ),
        detail = GoodsDetail(
            goodsId = goodsId,
            imei = "860456070165314",
            categoryCode = "04",
            orgId = "org-1",
            shopId = "shop-1",
            name = "水机",
            isBluetooth = bluetooth,
            supportAppUserFinish = true,
        ),
        sourceKey = "SN",
        sourceValue = "860456070165314",
    )

    private fun confirmedDevice() = ConfirmedDevice(
        goodsId = "goods-1",
        skuId = "sku-1",
        channelMethod = 15,
        deviceName = "水机",
        updatedAt = 1L,
    )

    private fun cachedPlans(skuName: String = "cached") = DevicePlans(
        skus = listOf(Sku(skuId = "sku-1", name = skuName, price = "0.2", unit = "L")),
        channels = listOf(PayChannelRoute(method = 15, methodName = "支付宝免密", checked = true)),
        fetchedAt = 1_000L,
        fromCache = true,
    )

    private fun plans(skuName: String = "fresh") = DevicePlans(
        skus = listOf(Sku(skuId = "sku-1", name = skuName, price = "0.2", unit = "L")),
        channels = listOf(PayChannelRoute(method = 15, methodName = "支付宝免密", checked = true)),
        fetchedAt = 2_000L,
        fromCache = false,
    )

    private class Deps(
        locationGranted: Boolean = true,
        dryRun: Boolean = false,
        oneTap: Boolean = false,
        msgReady: Boolean = true,
        unlockFails: Boolean = false,
        cachedPlans: DevicePlans? = null,
        refreshFails: Boolean = false,
    ) {
        val device = ResolvedDevice(
            scan = ScanV2Bean(categoryCode = "04", categoryName = "饮水机", id = "goods-1"),
            detail = GoodsDetail(
                goodsId = "goods-1",
                imei = "860456070165314",
                categoryCode = "04",
                orgId = "org-1",
                shopId = "shop-1",
                name = "水机",
                supportAppUserFinish = true,
            ),
            sourceKey = "SN",
            sourceValue = "860456070165314",
        )
        val deviceSource = FakeDeviceSource(cachedPlans).also {
            it.refreshFails = refreshFails
        }
        val waterSource = FakeWaterSource(msgReady, unlockFails)
        val locationSource = FakeLocationSource(locationGranted)
        val settingsSource = FakeSettingsSource(oneTap, dryRun)
        val configSource = FakeConfigSource()

        fun controller(scope: CoroutineScope) =
            ConfirmController(
                scope = scope,
                deviceRepository = deviceSource,
                waterRepository = waterSource,
                locationProvider = locationSource,
                settingsStore = settingsSource,
                configStore = configSource,
            )
    }

    private class FakeDeviceSource(
        var cachedPlans: DevicePlans?,
    ) : DeviceDataSource {
        var bindCalls = 0
        var loadCalls = 0
        var refreshCalls = 0
        var clearCalls = 0
        var refreshFails = false
        var plans = DevicePlans(
            skus = listOf(Sku(skuId = "sku-1", name = "1 元", price = "0.2", unit = "L")),
            channels = listOf(
                PayChannelRoute(method = 15, methodName = "支付宝免密", checked = true),
            ),
            fetchedAt = 0L,
            fromCache = false,
        )

        override suspend fun loadSkus(goodsId: String) = plans.skus

        override suspend fun loadChannels(detail: GoodsDetail) = plans.channels

        override suspend fun bindChannel(method: Int) {
            bindCalls += 1
        }

        override suspend fun readCachedPlans(device: ResolvedDevice) = cachedPlans

        override suspend fun loadPlans(device: ResolvedDevice): DevicePlans {
            loadCalls += 1
            return plans
        }

        override suspend fun refreshPlans(device: ResolvedDevice) {
            refreshCalls += 1
            if (refreshFails) error("refresh failed")
        }

        override suspend fun clearCachedPlans(device: ResolvedDevice) {
            clearCalls += 1
            cachedPlans = null
        }
    }

    private class FakeWaterSource(
        private val msgReady: Boolean,
        private val unlockFails: Boolean,
    ) : WaterDataSource {
        var unlockCalls = 0

        override suspend fun unlock(
            detail: GoodsDetail,
            skuId: String,
            lat: String,
            lng: String,
            payType: String,
            iotBOrderNo: String,
        ): Unlock {
            unlockCalls += 1
            if (unlockFails) error("unlock failed")
            return Unlock(orderNo = "order-1", msgId = "msg-1")
        }

        override suspend fun msgSync(msgId: String) = MsgSyncBean(
            state = if (msgReady) "OK" else "WAIT",
            code = if (msgReady) "05" else "",
        )

        override fun isDeviceReady(bean: MsgSyncBean) =
            bean.state.equals("OK", ignoreCase = true) || bean.code == "05"

        override suspend fun waterSync(skuId: String) = WaterSync()

        override suspend fun endWater(goodsId: String) = Unit

        override suspend fun afterPayCreate(identify: String) = AfterPay()

        override suspend fun orderPayStatus(orderNo: String, payType: String) = false
    }

    private class FakeLocationSource(
        private var granted: Boolean,
        private var result: GeoPoint? = GeoPoint(30.123456, 120.123456),
    ) : LocationSource {
        var calls = 0
        private val gates = ArrayDeque<CompletableDeferred<Unit>>()

        fun enqueueGate(): CompletableDeferred<Unit> =
            CompletableDeferred<Unit>().also { gates.addLast(it) }

        fun grant() {
            granted = true
        }

        fun setResult(point: GeoPoint?) {
            result = point
        }

        override fun hasPermission() = granted

        override suspend fun getCurrentLocation(): GeoPoint? {
            calls += 1
            gates.removeFirstOrNull()?.await()
            return result
        }
    }

    private class FakeSettingsSource(
        private val oneTap: Boolean,
        private val dryRun: Boolean,
    ) : SettingsSource {
        override suspend fun isOneTapEnabled() = oneTap

        override suspend fun isDryRun() = dryRun
    }

    private class FakeConfigSource : DeviceConfigSource {
        var existing: ConfirmedDevice? = null
        var saved: ConfirmedDevice? = null

        override suspend fun find(goodsId: String) = existing

        override suspend fun save(device: ConfirmedDevice) {
            saved = device
        }
    }
}
