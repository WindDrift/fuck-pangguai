package io.github.winddrift.fuckpangguai.ticket

import io.github.winddrift.fuckpangguai.account.StoredAccount
import io.github.winddrift.fuckpangguai.net.dto.PayChannel
import io.github.winddrift.fuckpangguai.net.dto.PrePay
import io.github.winddrift.fuckpangguai.net.dto.TicketBalance
import io.github.winddrift.fuckpangguai.net.dto.TicketGoods
import io.github.winddrift.fuckpangguai.net.dto.TicketReward
import io.github.winddrift.fuckpangguai.net.dto.VipOrderDetail
import io.github.winddrift.fuckpangguai.net.dto.VirtualOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TicketRechargeControllerTest {

    @Test
    fun `create order uses official parameter names and selected reward`() = runTest {
        val deps = Deps()
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()

        assertEquals(RechargeStage.GoodsReady, controller.state.value.stage)
        controller.selectReward("r2")
        controller.createOrder()
        advanceUntilIdle()

        val call = deps.data.createCalls.single()
        assertEquals("shop1", call.shopId)
        assertEquals("goods-1", call.goodsId)
        assertEquals("type-1", call.goodsType)
        assertEquals("2000", call.num)
        assertEquals("r2", call.coinRewardId)
        assertEquals(RechargeStage.OrderReady, controller.state.value.stage)
    }

    @Test
    fun `repeated create taps only create one order`() = runTest {
        val deps = Deps()
        deps.data.createGate = CompletableDeferred()
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()

        controller.createOrder()
        controller.createOrder()
        advanceUntilIdle()

        assertEquals(1, deps.data.createCalls.size)
        deps.data.createGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(RechargeStage.OrderReady, controller.state.value.stage)
    }

    @Test
    fun `repeated pay taps only invoke gateway once`() = runTest {
        val deps = Deps()
        deps.data.payStatusResponses.add(true)
        deps.gateway.gate = CompletableDeferred()
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()
        controller.createOrder()
        advanceUntilIdle()

        val gateway = deps.gateway
        controller.pay(gateway)
        controller.pay(gateway)
        advanceUntilIdle()
        assertEquals(1, gateway.calls)

        gateway.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(RechargeStage.Paid, controller.state.value.stage)
    }

    @Test
    fun `shop without alipay cannot create order`() = runTest {
        val deps = Deps()
        deps.data.channels = listOf(PayChannel(payType = 2, payTypeName = "钱包"))
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()

        assertFalse(controller.state.value.alipayAvailable)
        assertFalse(controller.state.value.canConfirm)
        controller.createOrder()
        advanceUntilIdle()
        assertTrue(deps.data.createCalls.isEmpty())
        assertEquals(RechargeStage.GoodsReady, controller.state.value.stage)
    }

    @Test
    fun `empty or failed channel list falls back to alipay`() = runTest {
        val empty = Deps()
        empty.data.channels = emptyList()
        val emptyController = empty.controller(this)
        emptyController.start("shop1", "一号店")
        advanceUntilIdle()
        assertTrue(emptyController.state.value.alipayAvailable)

        val failed = Deps()
        failed.data.channelsError = IOException("offline")
        val failedController = failed.controller(this)
        failedController.start("shop1", "一号店")
        advanceUntilIdle()
        assertTrue(failedController.state.value.alipayAvailable)
    }

    @Test
    fun `9000 with confirmed status marks paid once and clears pending`() = runTest {
        val deps = Deps()
        deps.data.payStatusResponses.add(true)
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()
        controller.createOrder()
        advanceUntilIdle()

        controller.pay(deps.gateway)
        advanceUntilIdle()

        assertEquals(RechargeStage.Paid, controller.state.value.stage)
        assertEquals(1, deps.onPaidCalls)
        assertEquals(1, deps.gateway.calls)
        assertNull(deps.pending.pending("a1"))
        assertTrue(deps.pending.cleared.contains("a1"))
    }

    @Test
    fun `9000 with unpaid status falls back to paid detail`() = runTest {
        val deps = Deps()
        deps.data.detailResponses.addAll(listOf(detail(0), detail(0), detail(2)))
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()
        controller.createOrder()
        advanceUntilIdle()

        controller.pay(deps.gateway)
        advanceUntilIdle()

        assertEquals(RechargeStage.Paid, controller.state.value.stage)
        assertEquals(1, deps.onPaidCalls)
    }

    @Test
    fun `9000 with unresolved status stays unknown and keeps pending`() = runTest {
        val deps = Deps()
        deps.data.detailResponses.addAll(listOf(detail(0), detail(0), detail(0)))
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()
        controller.createOrder()
        advanceUntilIdle()

        controller.pay(deps.gateway)
        advanceUntilIdle()

        assertEquals(RechargeStage.Unknown, controller.state.value.stage)
        assertEquals(0, deps.onPaidCalls)
        assertNotNull(deps.pending.pending("a1"))
        assertTrue(deps.pending.cleared.isEmpty())
    }

    @Test
    fun `existing prepay checks server status before launching alipay`() = runTest {
        val deps = Deps()
        deps.data.prePay = PrePay(
            exist = true,
            prepayParam = "ORDER-INFO",
        )
        deps.data.payStatusResponses.add(true)
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()
        controller.createOrder()
        advanceUntilIdle()

        controller.pay(deps.gateway)
        advanceUntilIdle()

        assertEquals(RechargeStage.Paid, controller.state.value.stage)
        assertEquals(0, deps.gateway.calls)
        assertEquals(1, deps.onPaidCalls)
    }

    @Test
    fun `6001 with paid status marks paid`() = runTest {
        val deps = Deps()
        deps.gateway.outcome = AlipayOutcome("6001")
        deps.data.payStatusResponses.add(true)
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()
        controller.createOrder()
        advanceUntilIdle()

        controller.pay(deps.gateway)
        advanceUntilIdle()

        assertEquals(RechargeStage.Paid, controller.state.value.stage)
        assertEquals(1, deps.onPaidCalls)
    }

    @Test
    fun `6001 without paid status cancels and clears pending`() = runTest {
        val deps = Deps()
        deps.gateway.outcome = AlipayOutcome("6001")
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()
        controller.createOrder()
        advanceUntilIdle()

        controller.pay(deps.gateway)
        advanceUntilIdle()

        assertEquals(RechargeStage.Cancelled, controller.state.value.stage)
        assertEquals(0, deps.onPaidCalls)
        assertTrue(deps.pending.cleared.contains("a1"))
    }

    @Test
    fun `non success statuses never show paid`() = runTest {
        for (status in listOf("4000", "5000", "8000", "UNKNOWN")) {
            val deps = Deps()
            deps.gateway.outcome = AlipayOutcome(status)
            val controller = deps.controller(this)
            controller.start("shop1", "一号店")
            advanceUntilIdle()
            controller.createOrder()
            advanceUntilIdle()

            controller.pay(deps.gateway)
            advanceUntilIdle()

            assertEquals(status, RechargeStage.Unknown, controller.state.value.stage)
            assertEquals(0, deps.onPaidCalls)
        }
    }

    @Test
    fun `network failure before sdk becomes unknown without sdk call`() = runTest {
        val deps = Deps()
        deps.data.prePayError = IOException("offline")
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()
        controller.createOrder()
        advanceUntilIdle()

        controller.pay(deps.gateway)
        advanceUntilIdle()

        assertEquals(RechargeStage.Unknown, controller.state.value.stage)
        assertEquals(0, deps.gateway.calls)
        assertEquals(0, deps.onPaidCalls)
    }

    @Test
    fun `account switch discards in flight payment result`() = runTest {
        val deps = Deps()
        deps.data.payStatusResponses.add(true)
        deps.gateway.gate = CompletableDeferred()
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()
        controller.createOrder()
        advanceUntilIdle()

        controller.pay(deps.gateway)
        advanceUntilIdle()
        assertEquals(RechargeStage.WaitingSdk, controller.state.value.stage)

        deps.account.active.value = StoredAccount(localId = "a2")
        advanceUntilIdle()
        deps.gateway.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(RechargeStage.Idle, controller.state.value.stage)
        assertEquals(0, deps.onPaidCalls)
    }

    @Test
    fun `pending order recovery only queries and never prepays`() = runTest {
        val deps = Deps()
        deps.pending.records["a1"] = PendingRecharge(
            accountLocalId = "a1",
            shopId = "shop1",
            shopName = "一号店",
            orderId = "old-order",
            orderNo = "old-no",
            price = "10",
            points = 1000,
            createdAt = 1_000L,
        )
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()

        assertTrue(deps.data.statusCalls >= 1)
        assertEquals(0, deps.data.prePayCalls)
        assertEquals(0, deps.gateway.calls)
        assertEquals(RechargeStage.GoodsReady, controller.state.value.stage)
    }

    @Test
    fun `prepay param never enters state or pending serialization`() = runTest {
        val deps = Deps()
        deps.data.prePay = PrePay(prepayParam = "SECRET-ORDER-INFO")
        val controller = deps.controller(this)
        controller.start("shop1", "一号店")
        advanceUntilIdle()
        controller.createOrder()
        advanceUntilIdle()

        val pending = deps.pending.pending("a1") ?: error("pending missing")
        val encoded = Json.encodeToString(PendingRecharge.serializer(), pending)
        assertFalse(encoded.contains("SECRET-ORDER-INFO"))
        assertFalse(controller.state.value.toString().contains("SECRET-ORDER-INFO"))
    }

    private fun detail(status: Int): VipOrderDetail = VipOrderDetail(
        id = "order-1",
        orderNo = "order-1",
        orderStatus = status,
        payPrice = "10.00",
    )

    private class Deps {
        val data = FakeDataSource()
        val account = FakeAccountSource()
        val pending = FakePendingStore()
        val gateway = FakeGateway()
        var onPaidCalls = 0

        fun controller(test: TestScope) = TicketRechargeController(
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(test.testScheduler)),
            dataSource = data,
            pendingStore = pending,
            accountSource = account,
            onPaid = { onPaidCalls++ },
            now = { 1_000L },
            statusPollIntervalMs = 0L,
            slowPollIntervalMs = 0L,
        )
    }

    private class FakeAccountSource : TicketAccountSource {
        val active = MutableStateFlow<StoredAccount?>(StoredAccount(localId = "a1"))
        override val activeAccount = active
        override fun activeLocalId(): String? = active.value?.localId
    }

    private class FakePendingStore : PendingRechargeStore {
        val records = mutableMapOf<String, PendingRecharge>()
        val cleared = mutableListOf<String>()

        override suspend fun load(accountId: String): PendingRecharge? = records[accountId]
        override suspend fun save(pending: PendingRecharge) {
            records[pending.accountLocalId] = pending
        }
        override suspend fun clear(accountId: String) {
            records.remove(accountId)
            cleared += accountId
        }
        fun pending(accountId: String): PendingRecharge? = records[accountId]
    }

    private data class CreateCall(
        val shopId: String,
        val goodsId: String,
        val goodsType: String,
        val num: String,
        val coinRewardId: String,
    )

    private class FakeDataSource : TicketRechargeDataSource {
        var goods = TicketGoods(
            shopId = "shop1",
            shopName = "一号店",
            goodsId = "goods-1",
            goodsType = "type-1",
            refundRemark = "",
            isAllowRefund = 0,
            shopTokenCoinRewards = listOf(
                TicketReward(id = "r1", reach = 1000, reward = 20, cashValue = "10"),
                TicketReward(id = "r2", reach = 2000, reward = 50, cashValue = "20"),
            ),
            userTokenCoinDTO = TicketBalance(
                totalAmount = 500,
                principalAmount = "5.00",
                presentAmount = "0.00",
            ),
        )
        var goodsError: Throwable? = null
        var channels: List<PayChannel> = listOf(PayChannel(payType = 13, payTypeName = "支付宝"))
        var channelsError: Throwable? = null
        var createGate: CompletableDeferred<Unit>? = null
        var createError: Throwable? = null
        var detailResponses = ArrayDeque<VipOrderDetail>()
        var detailError: Throwable? = null
        var prePay = PrePay(prepayParam = "ORDER-INFO")
        var prePayError: Throwable? = null
        var payStatusResponses = ArrayDeque<Boolean>()
        var payStatusError: Throwable? = null

        val createCalls = mutableListOf<CreateCall>()
        var detailCalls = 0
        var prePayCalls = 0
        var statusCalls = 0

        override suspend fun loadGoods(shopId: String, deviceGoodsId: String?): TicketGoods {
            goodsError?.let { throw it }
            return goods
        }

        override suspend fun createOrder(
            shopId: String,
            goodsId: String,
            goodsType: String,
            num: String,
            coinRewardId: String,
        ): VirtualOrder {
            createCalls += CreateCall(shopId, goodsId, goodsType, num, coinRewardId)
            createGate?.await()
            createError?.let { throw it }
            return VirtualOrder(
                orderId = "order-1",
                orderNo = "order-1",
                price = "10.00",
                shopName = "一号店",
            )
        }

        override suspend fun loadOrderDetail(orderId: String): VipOrderDetail {
            detailCalls++
            detailError?.let { throw it }
            return detailResponses.removeFirstOrNull() ?: VipOrderDetail(
                id = orderId,
                orderNo = orderId,
                orderStatus = 0,
                payPrice = "10.00",
            )
        }

        override suspend fun loadPayChannels(shopId: String): List<PayChannel> {
            channelsError?.let { throw it }
            return channels
        }

        override suspend fun prePay(orderNo: String, payType: String): PrePay {
            prePayCalls++
            prePayError?.let { throw it }
            return prePay
        }

        override suspend fun queryPayStatus(orderNo: String, payType: String): Boolean {
            statusCalls++
            payStatusError?.let { throw it }
            return payStatusResponses.removeFirstOrNull() ?: false
        }
    }

    private class FakeGateway : AlipayGateway {
        var outcome = AlipayOutcome("9000")
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0

        override suspend fun pay(orderInfo: String): AlipayOutcome {
            calls++
            gate?.await()
            return outcome
        }
    }
}
