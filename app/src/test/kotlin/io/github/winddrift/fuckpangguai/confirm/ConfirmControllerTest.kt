package io.github.winddrift.fuckpangguai.confirm

import io.github.winddrift.fuckpangguai.device.DeviceDataSource
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
    fun `missing location permission asks for permission`() = runTest {
        val deps = Deps(locationGranted = false)
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()
        assertTrue(controller.state.value is ConfirmState.NeedsLocationPermission)
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
    }

    @Test
    fun `one tap auto unlocks a previously confirmed device`() = runTest {
        val deps = Deps(oneTap = true)
        deps.configSource.existing = ConfirmedDevice(
            goodsId = "goods-1",
            skuId = "sku-1",
            channelMethod = 15,
            deviceName = "水机",
            updatedAt = 1L,
        )
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()
        assertTrue(controller.state.value is ConfirmState.Unlocked)
        assertEquals(1, deps.waterSource.unlockCalls)
    }

    private fun device(bluetooth: Int = 0) = ResolvedDevice(
        scan = ScanV2Bean(
            categoryCode = "04",
            categoryName = "饮水机",
            id = "goods-1",
        ),
        detail = GoodsDetail(
            goodsId = "goods-1",
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

    private class Deps(
        locationGranted: Boolean = true,
        dryRun: Boolean = false,
        oneTap: Boolean = false,
        msgReady: Boolean = true,
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
        val deviceSource = FakeDeviceSource()
        val waterSource = FakeWaterSource(msgReady)
        val locationSource = FakeLocationSource(locationGranted)
        val settingsSource = FakeSettingsSource(oneTap, dryRun)
        val configSource = FakeConfigSource()

        fun controller(scope: kotlinx.coroutines.CoroutineScope) =
            ConfirmController(
                scope = scope,
                deviceRepository = deviceSource,
                waterRepository = waterSource,
                locationProvider = locationSource,
                settingsStore = settingsSource,
                configStore = configSource,
            )
    }

    private class FakeDeviceSource : DeviceDataSource {
        var bindCalls = 0
        override suspend fun loadSkus(goodsId: String) = listOf(
            Sku(skuId = "sku-1", name = "1 元", price = "0.2", unit = "L"),
        )
        override suspend fun loadChannels(detail: GoodsDetail) = listOf(
            PayChannelRoute(method = 15, methodName = "支付宝免密", checked = true),
        )
        override suspend fun bindChannel(method: Int) {
            bindCalls += 1
        }
    }

    private class FakeWaterSource(private val msgReady: Boolean) : WaterDataSource {
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

    private class FakeLocationSource(private val granted: Boolean) : LocationSource {
        override fun hasPermission() = granted
        override suspend fun getCurrentLocation() = GeoPoint(30.123456, 120.123456)
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
