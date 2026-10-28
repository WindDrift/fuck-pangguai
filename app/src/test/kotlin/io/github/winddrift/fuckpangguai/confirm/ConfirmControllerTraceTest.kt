package io.github.winddrift.fuckpangguai.confirm

import io.github.winddrift.fuckpangguai.device.DeviceDataSource
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.diagnostics.FakeDiagSink
import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmControllerTraceTest {

    private val traceId = "scan-trace-1"

    @Test
    fun `confirm ready and unlock join the scan trace`() = runTest {
        val deps = Deps()
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        var trace = deps.sink.trace(traceId)
        assertEquals(StageOutcome.Success, trace.stage(TraceStage.ConfirmReady)!!.outcome)
        assertEquals(2, trace.stage(TraceStage.ConfirmReady)!!.attempts)
        assertEquals(TraceOutcome.Running, trace.outcome)

        controller.unlock()
        advanceUntilIdle()

        trace = deps.sink.trace(traceId)
        val unlock = trace.stage(TraceStage.Unlock)!!
        assertEquals(StageOutcome.Success, unlock.outcome)
        assertTrue(unlock.attempts >= 1)
        assertEquals(TraceOutcome.Running, trace.outcome)
        val session = (controller.state.value as ConfirmState.Unlocked).session
        assertEquals(traceId, session.traceId)
    }

    @Test
    fun `blocked device fails the trace`() = runTest {
        val deps = Deps()
        val controller = deps.controller(this)
        controller.start(deps.device.copy(detail = deps.device.detail.copy(isBluetooth = 1)))
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.Blocked)
        val trace = deps.sink.trace(traceId)
        assertEquals(StageOutcome.Failed, trace.stage(TraceStage.ConfirmReady)!!.outcome)
        assertEquals(TraceOutcome.Failed, trace.outcome)
    }

    @Test
    fun `leaving while waiting for permission abandons the trace`() = runTest {
        val deps = Deps(permission = false)
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.NeedsLocationPermission)
        controller.abandon("离开确认页")

        val trace = deps.sink.trace(traceId)
        assertEquals(StageOutcome.Abandoned, trace.stage(TraceStage.ConfirmReady)!!.outcome)
        assertEquals(TraceOutcome.Abandoned, trace.outcome)
    }

    @Test
    fun `dry run abandons the unlock stage`() = runTest {
        val deps = Deps(dryRun = true)
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()
        controller.unlock()
        advanceUntilIdle()

        val trace = deps.sink.trace(traceId)
        assertEquals(StageOutcome.Abandoned, trace.stage(TraceStage.Unlock)!!.outcome)
        assertEquals(TraceOutcome.Abandoned, trace.outcome)
        assertEquals(0, deps.water.unlockCalls)
    }

    @Test
    fun `leaving while unlocking waits for the result and marks it abandoned`() = runTest {
        val deps = Deps()
        val unlockGate = CompletableDeferred<Unit>()
        deps.water.unlockGate = unlockGate
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.unlock()
        advanceUntilIdle()
        assertTrue(controller.state.value is ConfirmState.Unlocking)

        controller.abandon("离开确认页")
        unlockGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.Unlocked)
        val trace = deps.sink.trace(traceId)
        assertEquals(StageOutcome.Abandoned, trace.stage(TraceStage.Unlock)!!.outcome)
        assertEquals(TraceOutcome.Abandoned, trace.outcome)

        controller.abandon("再次离开")
        assertEquals(TraceOutcome.Abandoned, deps.sink.trace(traceId).outcome)
    }

    @Test
    fun `unlock failure fails the trace`() = runTest {
        val deps = Deps()
        deps.water.unlockFails = true
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()
        controller.unlock()
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.Error)
        val trace = deps.sink.trace(traceId)
        assertEquals(StageOutcome.Failed, trace.stage(TraceStage.Unlock)!!.outcome)
        assertEquals(TraceOutcome.Failed, trace.outcome)
    }

    private class Deps(
        permission: Boolean = true,
        dryRun: Boolean = false,
    ) {
        val sink = FakeDiagSink()
        val device = ResolvedDevice(
            scan = ScanV2Bean(categoryCode = "04", categoryName = "饮水机", id = "goods-1"),
            detail = GoodsDetail(
                goodsId = "goods-1",
                imei = "860",
                categoryCode = "04",
                orgId = "org-1",
                shopId = "shop-1",
                name = "水机",
                supportAppUserFinish = true,
            ),
            sourceKey = "SN",
            sourceValue = "860",
            traceId = "scan-trace-1",
        )
        val deviceSource = FakeDeviceSource()
        val water = FakeWaterSource()
        val location = FakeLocation(permission)
        val settings = FakeSettings(dryRun)
        val config = FakeConfig()

        fun controller(scope: CoroutineScope) = ConfirmController(
            scope = scope,
            deviceRepository = deviceSource,
            waterRepository = water,
            locationProvider = location,
            settingsStore = settings,
            configStore = config,
            diagnostics = sink,
        )
    }

    private class FakeDeviceSource : DeviceDataSource {
        override suspend fun loadSkus(goodsId: String) =
            listOf(Sku(skuId = "sku-1", name = "1 元", price = "1"))

        override suspend fun loadChannels(detail: GoodsDetail) =
            listOf(PayChannelRoute(method = 15, methodName = "支付宝免密", checked = true))

        override suspend fun bindChannel(method: Int) = Unit
    }

    private class FakeWaterSource : WaterDataSource {
        var unlockGate: CompletableDeferred<Unit>? = null
        var unlockFails = false
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
            unlockGate?.await()
            if (unlockFails) error("unlock failed")
            return Unlock(msgId = "msg-1", orderNo = "order-1")
        }

        override suspend fun msgSync(msgId: String) = MsgSyncBean(state = "OK", code = "05")

        override fun isDeviceReady(bean: MsgSyncBean): Boolean =
            bean.state.equals("OK", ignoreCase = true) || bean.code == "05"

        override suspend fun waterSync(skuId: String) = WaterSync()

        override suspend fun endWater(goodsId: String) = Unit

        override suspend fun afterPayCreate(identify: String) =
            AfterPay(orderId = "1", orderNo = "2")

        override suspend fun orderPayStatus(orderNo: String, payType: String) = true
    }

    private class FakeLocation(private val permission: Boolean) : LocationSource {
        override fun hasPermission(): Boolean = permission
        override suspend fun getCurrentLocation(): GeoPoint = GeoPoint(30.0, 120.0)
    }

    private class FakeSettings(private val dryRun: Boolean) : SettingsSource {
        override suspend fun isOneTapEnabled(): Boolean = false
        override suspend fun isDryRun(): Boolean = dryRun
    }

    private class FakeConfig : DeviceConfigSource {
        override suspend fun find(goodsId: String): ConfirmedDevice? = null
        override suspend fun save(device: ConfirmedDevice) = Unit
    }
}
