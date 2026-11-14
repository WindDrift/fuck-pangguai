package io.github.winddrift.fuckpangguai.confirm

import io.github.winddrift.fuckpangguai.device.DeviceDataSource
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import io.github.winddrift.fuckpangguai.diagnostics.FakeDiagSink
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
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
import io.github.winddrift.fuckpangguai.water.PendingUnlock
import io.github.winddrift.fuckpangguai.water.PendingUnlockStore
import io.github.winddrift.fuckpangguai.water.WaterDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmControllerReconcileTest {

    @Test
    fun `timeout records pending unlock with order identifiers`() = runTest {
        val deps = Deps(msgReady = false)
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.unlock()
        advanceTimeBy(16_000)
        advanceUntilIdle()

        assertEquals(1, deps.water.unlockCalls)
        val saved = deps.pendingStore.saved
        assertTrue(saved != null)
        assertEquals("order-1", saved!!.orderNo)
        assertEquals("msg-1", saved.msgId)
        assertEquals("goods-1", saved.goodsId)
        assertEquals("sku-1", saved.skuId)
        // 订单仍待核对，界面给的是可放弃的待核对态而不是普通错误态。
        assertTrue(controller.state.value is ConfirmState.PendingReconcile)
    }

    @Test
    fun `second unlock is blocked while pending exists`() = runTest {
        val deps = Deps(msgReady = false)
        deps.keepReconcileRetrying()
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.unlock()
        advanceTimeBy(16_000)
        advanceUntilIdle()
        assertEquals(1, deps.water.unlockCalls)

        controller.unlock()
        advanceUntilIdle()

        assertEquals(1, deps.water.unlockCalls)
        val blocked = controller.state.value as ConfirmState.PendingReconcile
        assertTrue(blocked.message.contains("待核对"))
    }

    @Test
    fun `adopting pending creates a using session`() = runTest {
        val deps = Deps(msgReady = false)
        // 解锁请求成功后设备确实启动了，但 msgSync 一直没就绪。
        deps.water.started = true
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.unlock()
        advanceTimeBy(16_000)
        advanceUntilIdle()

        val unlocked = controller.state.value as ConfirmState.Unlocked
        assertEquals("order-1", unlocked.session.unlock.orderNo)
        assertEquals("msg-1", unlocked.session.unlock.msgId)
        assertEquals(1, deps.water.unlockCalls)
        assertNull(deps.pendingStore.saved)
    }

    @Test
    fun `pending on another device does not block unlocking`() = runTest {
        val deps = Deps()
        deps.pendingStore.stored = PendingUnlock(
            orderNo = "order-old",
            msgId = "msg-old",
            goodsId = "goods-other",
            skuId = "sku-1",
            createdAt = 1L,
        )
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.unlock()
        advanceUntilIdle()

        assertEquals(1, deps.water.unlockCalls)
        assertTrue(controller.state.value is ConfirmState.Unlocked)
    }

    @Test
    fun `pending on the same device blocks unlocking before any request`() = runTest {
        val deps = Deps()
        deps.keepReconcileRetrying()
        deps.pendingStore.stored = PendingUnlock(
            orderNo = "order-old",
            msgId = "msg-old",
            goodsId = "goods-1",
            skuId = "sku-1",
            createdAt = deps.now,
        )
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.unlock()
        advanceUntilIdle()

        assertEquals(0, deps.water.unlockCalls)
        assertTrue(controller.state.value is ConfirmState.PendingReconcile)
    }

    @Test
    fun `existing pending on the same device is reconciled on entry`() = runTest {
        val deps = Deps()
        deps.water.started = true
        deps.pendingStore.stored = PendingUnlock(
            orderNo = "order-old",
            msgId = "msg-old",
            goodsId = "goods-1",
            skuId = "sku-1",
            createdAt = deps.now,
        )
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        // 对账接管，不再下单。
        val unlocked = controller.state.value as ConfirmState.Unlocked
        assertEquals("order-old", unlocked.session.unlock.orderNo)
        assertEquals(0, deps.water.unlockCalls)
        assertTrue(deps.water.deviceStartCalls >= 1)
        assertNull(deps.pendingStore.saved)
    }

    @Test
    fun `unconfirmed pending stays visible instead of offering unlock`() = runTest {
        val deps = Deps()
        deps.keepReconcileRetrying()
        deps.pendingStore.stored = PendingUnlock(
            orderNo = "order-old",
            msgId = "msg-old",
            goodsId = "goods-1",
            skuId = "sku-1",
            createdAt = deps.now,
        )
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        assertTrue(controller.state.value is ConfirmState.PendingReconcile)
        assertEquals(0, deps.water.unlockCalls)
    }

    @Test
    fun `abandoning pending clears the store and records the event`() = runTest {
        val deps = Deps()
        deps.keepReconcileRetrying()
        deps.pendingStore.stored = PendingUnlock(
            orderNo = "order-old",
            msgId = "msg-old",
            goodsId = "goods-1",
            skuId = "sku-1",
            createdAt = deps.now,
        )
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.abandonPendingUnlock()
        advanceUntilIdle()

        assertNull(deps.pendingStore.stored)
        assertNull(deps.pendingStore.saved)
        val event = deps.sink.events.single { it.name == "unlock_reconcile_abandoned" }
        assertEquals("order-old", event.fields["orderNo"])
        assertEquals("0", event.fields["pendingAgeMs"])
        assertEquals(DiagLevel.Warn, event.level)
        assertEquals(DiagFlow.Confirm, event.flow)
    }

    @Test
    fun `abandoning after a generic abandon still clears pending`() = runTest {
        val deps = Deps()
        deps.keepReconcileRetrying()
        deps.pendingStore.stored = PendingUnlock(
            orderNo = "order-old",
            msgId = "msg-old",
            goodsId = "goods-1",
            skuId = "sku-1",
            createdAt = deps.now,
        )
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        // 通用 abandon 只收尾 trace，不得顺手清掉待对账记录。
        controller.abandon("切换设备")
        advanceUntilIdle()
        assertEquals("order-old", deps.pendingStore.stored?.orderNo)

        controller.abandonPendingUnlock()
        advanceUntilIdle()
        assertNull(deps.pendingStore.stored)
    }

    @Test
    fun `msg sync readiness adopts the pending order without another unlock`() = runTest {
        val deps = Deps(msgReady = false)
        // device_is_start 一直未知，但 msgSync 在探测期间就绪。
        deps.water.started = null
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.unlock()
        advanceTimeBy(16_000)
        deps.water.msgReady = true
        advanceUntilIdle()

        assertTrue(deps.water.msgSyncCalls > 0)
        val unlocked = controller.state.value as ConfirmState.Unlocked
        assertEquals("order-1", unlocked.session.unlock.orderNo)
        assertEquals(1, deps.water.unlockCalls)
    }

    @Test
    fun `adopting publishes the original order so the session keeps a real account id`() = runTest {
        val deps = Deps()
        deps.water.started = true
        deps.pendingStore.stored = PendingUnlock(
            orderNo = "order-old",
            msgId = "msg-old",
            goodsId = "goods-1",
            skuId = "sku-1",
            createdAt = deps.now,
        )
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        // 接管沿用原订单，真正落盘的会话由 WaterSessionManager 用实时账号重建。
        val unlocked = controller.state.value as ConfirmState.Unlocked
        assertEquals("order-old", unlocked.session.unlock.orderNo)
        assertEquals("msg-old", unlocked.session.unlock.msgId)
        assertTrue(deps.accountId!!.isNotBlank())
        assertEquals(0, deps.water.unlockCalls)
    }

    @Test
    fun `adopt is deferred when the page was left during reconciliation`() = runTest {
        val deps = Deps(msgReady = false)
        deps.keepReconcileRetrying()
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.unlock()
        advanceTimeBy(16_000)
        // 对账还在重试时用户离开确认页，随后设备被确认已启动。
        controller.abandon("离开确认页")
        deps.water.started = true
        advanceTimeBy(30_000)
        advanceUntilIdle()

        // 记录必须留着给下一次核对，且不能发布 Unlocked。
        assertTrue(deps.pendingStore.saved != null || deps.pendingStore.stored != null)
        assertTrue(controller.state.value !is ConfirmState.Unlocked)
        assertTrue(deps.sink.events.any { it.name == "unlock_reconcile_adopt_deferred" })
        assertEquals(1, deps.water.unlockCalls)
    }

    @Test
    fun `abandoning pending stops the loop and never adopts`() = runTest {
        val deps = Deps(msgReady = false)
        deps.keepReconcileRetrying()
        val controller = deps.controller(this)
        controller.start(deps.device)
        advanceUntilIdle()

        controller.unlock()
        advanceTimeBy(16_000)
        advanceUntilIdle()
        assertTrue(controller.state.value is ConfirmState.PendingReconcile)

        controller.abandonPendingUnlock()
        advanceUntilIdle()
        // 放弃后即使设备启动、预算耗尽，也不能复活记录或发布 Unlocked。
        deps.water.started = true
        advanceTimeBy(30_000)
        advanceUntilIdle()

        assertNull(deps.pendingStore.saved)
        assertNull(deps.pendingStore.stored)
        assertTrue(controller.state.value is ConfirmState.Error)
        assertTrue(controller.state.value !is ConfirmState.Unlocked)
    }

    private class Deps(msgReady: Boolean = true) {
        val now = 1_000_000L
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
        )
        val pendingStore = FakePendingStore()
        val water = FakeWater(msgReady)
        val sink = FakeDiagSink()
        var accountId: String? = "account-a"

        /** 让对账停在"未知"分支：两路探测都给不出结论。 */
        fun keepReconcileRetrying() {
            water.started = null
            water.msgReady = false
        }

        fun controller(scope: CoroutineScope) = ConfirmController(
            scope = scope,
            deviceRepository = FakeDeviceSource(),
            waterRepository = water,
            locationProvider = FakeLocation,
            settingsStore = FakeSettings,
            configStore = FakeConfig,
            diagnostics = sink,
            pendingStore = pendingStore,
            accountIdProvider = { accountId },
            clock = { now },
        )
    }

    private class FakePendingStore : PendingUnlockStore {
        var stored: PendingUnlock? = null
        var saved: PendingUnlock? = null

        override suspend fun load(): PendingUnlock? = stored

        override suspend fun save(pending: PendingUnlock) {
            saved = pending
            stored = pending
        }

        override suspend fun clear() {
            saved = null
            stored = null
        }
    }

    private class FakeWater(msgReady: Boolean) : WaterDataSource {
        var unlockCalls = 0
        // null 表示 device_is_start 查询未知，让对账继续重试。
        var started: Boolean? = false
        var deviceStartCalls = 0
        var msgSyncCalls = 0
        var msgReady = msgReady

        override suspend fun unlock(
            detail: GoodsDetail,
            skuId: String,
            lat: String,
            lng: String,
            payType: String,
            iotBOrderNo: String,
            usePoints: Boolean,
        ): Unlock {
            unlockCalls += 1
            return Unlock(orderNo = "order-1", msgId = "msg-1")
        }

        override suspend fun msgSync(msgId: String): MsgSyncBean {
            msgSyncCalls += 1
            return MsgSyncBean(
                state = if (msgReady) "OK" else "WAIT",
                code = if (msgReady) "05" else "",
            )
        }

        override fun isDeviceReady(bean: MsgSyncBean) =
            bean.state.equals("OK", ignoreCase = true) || bean.code == "05"

        override suspend fun queryDeviceStarted(orderNo: String): Boolean? {
            deviceStartCalls += 1
            return started
        }

        override suspend fun waterSync(skuId: String) = WaterSync()

        override suspend fun endWater(goodsId: String) = Unit

        override suspend fun afterPayCreate(identify: String) = AfterPay()

        override suspend fun orderPayStatus(orderNo: String, payType: String) = false
    }

    private class FakeDeviceSource : DeviceDataSource {
        override suspend fun loadSkus(goodsId: String) =
            listOf(Sku(skuId = "sku-1", name = "1 元", price = "1"))

        override suspend fun loadChannels(detail: GoodsDetail) =
            listOf(PayChannelRoute(method = 15, methodName = "支付宝免密", checked = true))

        override suspend fun bindChannel(method: Int) = Unit
    }

    private object FakeLocation : LocationSource {
        override fun hasPermission(): Boolean = true

        override suspend fun getCurrentLocation(): GeoPoint = GeoPoint(30.0, 120.0)
    }

    private object FakeSettings : SettingsSource {
        override suspend fun isOneTapEnabled(): Boolean = false

        override suspend fun isDryRun(): Boolean = false

        override suspend fun getUnlockAcceleration(): UnlockAcceleration =
            UnlockAcceleration.Conservative
    }

    private object FakeConfig : DeviceConfigSource {
        override suspend fun find(goodsId: String): ConfirmedDevice? = null

        override suspend fun save(device: ConfirmedDevice) = Unit
    }
}