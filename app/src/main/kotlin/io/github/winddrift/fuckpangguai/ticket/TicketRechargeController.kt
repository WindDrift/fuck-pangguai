package io.github.winddrift.fuckpangguai.ticket

import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.net.dto.PayChannel
import io.github.winddrift.fuckpangguai.net.dto.TicketGoods
import io.github.winddrift.fuckpangguai.net.dto.TicketReward
import io.github.winddrift.fuckpangguai.net.dto.VipOrderDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RechargeStage {
    Idle,
    LoadingGoods,
    GoodsReady,
    CreatingOrder,
    OrderReady,
    PreparingPay,
    WaitingSdk,
    Verifying,
    Paid,
    Cancelled,
    Unknown,
    Expired,
    Error,
}

data class TicketRechargeState(
    val stage: RechargeStage = RechargeStage.Idle,
    val shopId: String = "",
    val shopName: String = "",
    val goods: TicketGoods? = null,
    val selectedRewardId: String = "",
    val payChannels: List<PayChannel> = emptyList(),
    val alipayAvailable: Boolean = false,
    val pending: PendingRecharge? = null,
    val orderId: String = "",
    val orderNo: String = "",
    val price: String = "",
    val points: Int = 0,
    val orderStatus: Int? = null,
    val message: String? = null,
) {
    val selectedReward: TicketReward?
        get() = goods?.shopTokenCoinRewards?.firstOrNull { it.id == selectedRewardId }

    val busy: Boolean
        get() = stage == RechargeStage.CreatingOrder ||
            stage == RechargeStage.PreparingPay ||
            stage == RechargeStage.WaitingSdk ||
            stage == RechargeStage.Verifying

    val canConfirm: Boolean
        get() = stage == RechargeStage.GoodsReady && alipayAvailable && selectedReward != null

    val canPay: Boolean
        get() = stage == RechargeStage.OrderReady && orderId.isNotBlank() && orderNo.isNotBlank()
}

class TicketRechargeController(
    private val scope: CoroutineScope,
    private val dataSource: TicketRechargeDataSource,
    private val pendingStore: PendingRechargeStore,
    private val accountSource: TicketAccountSource,
    private val onPaid: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val statusPollIntervalMs: Long = 1_000L,
    private val slowPollIntervalMs: Long = 2_000L,
) {
    private val _state = MutableStateFlow(TicketRechargeState())
    val state = _state.asStateFlow()

    private var generation = 0
    private var observedLocalId: String? = accountSource.activeLocalId()
    private var loadJob: Job? = null
    private var createJob: Job? = null
    private var payJob: Job? = null
    private var queryJob: Job? = null
    private var creatingOrder = false
    private var sdkInFlight = false
    private var paidNotified = false
    private var pendingOrderInfo: String? = null

    init {
        scope.launch {
            accountSource.activeAccount
                .map { it?.localId }
                .distinctUntilChanged()
                .collect { localId ->
                    if (localId != observedLocalId) {
                        observedLocalId = localId
                        resetForAccountChange()
                    }
                }
        }
    }

    fun start(shopId: String, shopName: String) {
        val shop = shopId.trim()
        if (shop.isBlank()) {
            _state.value = TicketRechargeState(
                stage = RechargeStage.Error,
                message = "缺少店铺信息，无法充值",
            )
            return
        }
        val localId = accountSource.activeLocalId()
        if (localId == null) {
            _state.value = TicketRechargeState(
                stage = RechargeStage.Error,
                shopId = shop,
                shopName = shopName,
                message = "未登录，无法充值",
            )
            return
        }

        generation++
        cancelJobs()
        creatingOrder = false
        sdkInFlight = false
        paidNotified = false
        pendingOrderInfo = null
        val gen = generation
        _state.value = TicketRechargeState(
            stage = RechargeStage.LoadingGoods,
            shopId = shop,
            shopName = shopName,
        )

        loadJob = scope.launch {
            val goodsDeferred = async { runCatchingSuspend { dataSource.loadGoods(shop, null) } }
            val channelsDeferred = async { runCatchingSuspend { dataSource.loadPayChannels(shop) } }
            val goodsResult = goodsDeferred.await()
            val channelsResult = channelsDeferred.await()
            if (!isCurrent(gen, localId)) return@launch

            val goods = goodsResult.getOrElse { throwable ->
                _state.update {
                    it.copy(stage = RechargeStage.Error, message = throwable.userMessage())
                }
                return@launch
            }
            if (goods.shopTokenCoinRewards.isEmpty()) {
                _state.update {
                    it.copy(
                        stage = RechargeStage.Error,
                        goods = goods,
                        message = "该店铺暂无可充值面额",
                    )
                }
                return@launch
            }

            val channels = channelsResult.getOrDefault(emptyList())
            val alipayAvailable = channelsResult.isFailure ||
                channels.isEmpty() ||
                channels.any { it.payType == ALIPAY_PAY_TYPE }
            val reward = goods.shopTokenCoinRewards.first()
            _state.update {
                it.copy(
                    stage = RechargeStage.GoodsReady,
                    shopName = goods.shopName.ifBlank { it.shopName },
                    goods = goods,
                    selectedRewardId = reward.id,
                    payChannels = channels,
                    alipayAvailable = alipayAvailable,
                    price = reward.cashValue,
                    points = reward.reach,
                    message = when {
                        !alipayAvailable -> "该店铺暂不支持支付宝充值"
                        channelsResult.isFailure -> "支付渠道加载失败，已回退支付宝"
                        else -> null
                    },
                )
            }

            val pending = runCatchingSuspend { pendingStore.load(localId) }.getOrNull()
            if (!isCurrent(gen, localId) || pending == null || pending.shopId != shop) return@launch
            _state.update {
                if (it.stage == RechargeStage.GoodsReady) it.copy(pending = pending) else it
            }
            queryPending(pending, gen, localId)
        }
    }

    fun selectReward(rewardId: String) {
        val current = _state.value
        if (current.stage != RechargeStage.GoodsReady) return
        val reward = current.goods?.shopTokenCoinRewards?.firstOrNull { it.id == rewardId } ?: return
        _state.update {
            it.copy(selectedRewardId = reward.id, price = reward.cashValue, points = reward.reach)
        }
    }

    fun confirmCreateOrder() = createOrder()

    fun createOrder() {
        if (creatingOrder || sdkInFlight) return
        if (queryJob?.isActive == true) {
            _state.update { it.copy(message = "正在查询上一笔订单，请稍候") }
            return
        }
        val current = _state.value
        if (current.stage != RechargeStage.GoodsReady) return
        if (!current.alipayAvailable) {
            _state.update { it.copy(message = "该店铺暂不支持支付宝充值") }
            return
        }
        val goods = current.goods ?: return
        val reward = current.selectedReward ?: return
        val localId = accountSource.activeLocalId() ?: return
        val gen = generation

        creatingOrder = true
        _state.update { it.copy(stage = RechargeStage.CreatingOrder, message = null) }
        createJob = scope.launch {
            try {
                val order = dataSource.createOrder(
                    shopId = current.shopId,
                    goodsId = goods.goodsId,
                    goodsType = goods.goodsType,
                    num = reward.reach.toString(),
                    coinRewardId = reward.id,
                )
                if (!isCurrent(gen, localId)) return@launch
                if (order.orderId.isBlank() || order.orderNo.isBlank()) {
                    _state.update {
                        it.copy(stage = RechargeStage.GoodsReady, message = "下单响应缺少订单信息")
                    }
                    return@launch
                }

                var pending = PendingRecharge(
                    accountLocalId = localId,
                    shopId = current.shopId,
                    shopName = goods.shopName.ifBlank { current.shopName },
                    orderId = order.orderId,
                    orderNo = order.orderNo,
                    price = order.price.ifBlank { reward.cashValue },
                    points = reward.reach,
                    createdAt = now(),
                )
                runCatchingSuspend { pendingStore.save(pending) }
                if (!isCurrent(gen, localId)) return@launch
                _state.update {
                    it.copy(
                        stage = RechargeStage.OrderReady,
                        orderId = pending.orderId,
                        orderNo = pending.orderNo,
                        price = pending.price,
                        points = pending.points,
                        pending = pending,
                        message = null,
                    )
                }

                val detailResult = runCatchingSuspend {
                    dataSource.loadOrderDetail(order.orderId)
                }
                if (!isCurrent(gen, localId)) return@launch
                val detail = detailResult.getOrElse { throwable ->
                    _state.update {
                        it.copy(message = "订单已创建，但详情加载失败：${throwable.userMessage()}")
                    }
                    return@launch
                }
                if (detail.orderNo.isNotBlank() && detail.orderNo != order.orderNo) {
                    _state.update {
                        it.copy(
                            stage = RechargeStage.Unknown,
                            message = "订单信息不一致，请勿支付，可重新查询状态",
                        )
                    }
                    return@launch
                }
                val price = detail.payPrice.ifBlank { pending.price }
                pending = pending.copy(price = price)
                runCatchingSuspend { pendingStore.save(pending) }
                if (!isCurrent(gen, localId)) return@launch
                _state.update {
                    it.copy(
                        price = price,
                        pending = pending,
                        orderStatus = detail.orderStatus,
                        message = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (isCurrent(gen, localId)) {
                    _state.update {
                        it.copy(
                            stage = RechargeStage.GoodsReady,
                            message = "创建订单失败：${throwable.userMessage()}",
                        )
                    }
                }
            } finally {
                creatingOrder = false
            }
        }
    }

    fun pay(gateway: AlipayGateway) {
        if (creatingOrder || sdkInFlight) return
        val current = _state.value
        if (!current.canPay) return
        val localId = accountSource.activeLocalId() ?: return
        val gen = generation
        sdkInFlight = true

        payJob = scope.launch {
            try {
                _state.update { it.copy(stage = RechargeStage.PreparingPay, message = null) }
                val detailResult = runCatchingSuspend {
                    dataSource.loadOrderDetail(current.orderId)
                }
                if (!isCurrent(gen, localId)) return@launch
                val detail = detailResult.getOrNull()
                if (detail == null) {
                    _state.update {
                        it.copy(
                            stage = RechargeStage.Unknown,
                            message = "无法加载订单详情，未发起支付；可重新查询状态",
                        )
                    }
                    return@launch
                }
                if (detail.orderNo.isNotBlank() && detail.orderNo != current.orderNo) {
                    _state.update {
                        it.copy(
                            stage = RechargeStage.Unknown,
                            message = "订单信息不一致，请勿支付",
                        )
                    }
                    return@launch
                }
                applyDetailSnapshot(detail)
                when (detailOutcome(detail)) {
                    DetailOutcome.Paid -> {
                        markPaid(gen, localId, detail.orderStatus)
                        return@launch
                    }
                    DetailOutcome.Expired -> {
                        finishWith(gen, localId, RechargeStage.Expired, "订单已超时关闭，请重新下单")
                        return@launch
                    }
                    DetailOutcome.Cancelled -> {
                        finishWith(gen, localId, RechargeStage.Cancelled, refundMessage(detail))
                        return@launch
                    }
                    DetailOutcome.Pending -> Unit
                }

                val amount = detail.payPrice.ifBlank { current.price }.toDoubleOrNull()
                if (amount != null && amount <= 0.0) {
                    val paid = queryPayStatus(gen, localId, current.orderNo, attempts = 3)
                    if (!isCurrent(gen, localId)) return@launch
                    if (paid) markPaid(gen, localId, detail.orderStatus)
                    else resolveByDetail(gen, localId, current.orderId, current.orderNo)
                    return@launch
                }

                val prePay = runCatchingSuspend {
                    dataSource.prePay(current.orderNo, ALIPAY_PAY_TYPE.toString())
                }.getOrElse { throwable ->
                    if (isCurrent(gen, localId)) {
                        _state.update {
                            it.copy(
                                stage = RechargeStage.Unknown,
                                message = "无法获取支付参数：${throwable.userMessage()}；请勿重复支付",
                            )
                        }
                    }
                    return@launch
                }
                if (!isCurrent(gen, localId)) return@launch
                if (prePay.prepayParam.isBlank()) {
                    val paid = queryPayStatus(gen, localId, current.orderNo, attempts = 1)
                    if (!isCurrent(gen, localId)) return@launch
                    if (paid) markPaid(gen, localId, detail.orderStatus)
                    else {
                        _state.update {
                            it.copy(
                                stage = RechargeStage.Unknown,
                                message = "未获取到支付宝支付参数，请勿重复支付；可重新查询状态",
                            )
                        }
                    }
                    return@launch
                }

                pendingOrderInfo = prePay.prepayParam
                _state.update {
                    it.copy(stage = RechargeStage.WaitingSdk, message = "正在等待支付宝…")
                }
                val outcome = try {
                    gateway.pay(pendingOrderInfo.orEmpty())
                } finally {
                    pendingOrderInfo = null
                }
                if (!isCurrent(gen, localId)) return@launch
                _state.update {
                    it.copy(
                        stage = RechargeStage.Verifying,
                        message = "正在核验支付结果，请勿重复支付",
                    )
                }
                resolveAlipayOutcome(outcome, gen, localId, current.orderId, current.orderNo, detail)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (isCurrent(gen, localId)) {
                    _state.update {
                        it.copy(
                            stage = RechargeStage.Unknown,
                            message = "支付结果确认中：${throwable.userMessage()}",
                        )
                    }
                }
            } finally {
                pendingOrderInfo = null
                sdkInFlight = false
            }
        }
    }

    fun retryQuery() {
        if (creatingOrder || sdkInFlight) return
        val current = _state.value
        if (current.stage == RechargeStage.Paid ||
            current.stage == RechargeStage.CreatingOrder ||
            current.stage == RechargeStage.LoadingGoods
        ) {
            return
        }
        val localId = accountSource.activeLocalId() ?: return
        val pending = current.pending ?: current.orderNo.takeIf { it.isNotBlank() }?.let {
            PendingRecharge(
                accountLocalId = localId,
                shopId = current.shopId,
                shopName = current.shopName,
                orderId = current.orderId,
                orderNo = current.orderNo,
                price = current.price,
                points = current.points,
                createdAt = now(),
            )
        } ?: return
        val gen = generation
        val fromBanner = current.stage == RechargeStage.GoodsReady && current.orderNo.isBlank()
        queryJob?.cancel()
        queryJob = scope.launch {
            val paid = queryPayStatus(gen, localId, pending.orderNo, attempts = 1, showVerifying = true)
            if (!isCurrent(gen, localId)) return@launch
            if (paid) {
                markPaid(gen, localId, orderStatus = null)
                return@launch
            }
            val detail = runCatchingSuspend {
                dataSource.loadOrderDetail(pending.orderId)
            }.getOrNull()
            if (!isCurrent(gen, localId)) return@launch
            if (detail == null) {
                _state.update {
                    it.copy(message = "仍无法确认支付结果，请稍后重试")
                }
                return@launch
            }
            if (detail.orderNo.isNotBlank() && detail.orderNo != pending.orderNo) {
                _state.update {
                    it.copy(
                        stage = RechargeStage.Unknown,
                        message = "订单信息不一致，请勿重复支付",
                    )
                }
                return@launch
            }
            when (detailOutcome(detail)) {
                DetailOutcome.Paid -> markPaid(gen, localId, detail.orderStatus)
                DetailOutcome.Expired -> {
                    if (fromBanner) {
                        clearPendingForBanner(gen, localId, "上一笔待确认订单已超时关闭")
                    } else {
                        finishWith(
                            gen,
                            localId,
                            RechargeStage.Expired,
                            "订单已超时关闭，请重新下单",
                        )
                    }
                }
                DetailOutcome.Cancelled -> {
                    if (fromBanner) {
                        clearPendingForBanner(gen, localId, refundMessage(detail))
                    } else {
                        finishWith(gen, localId, RechargeStage.Cancelled, refundMessage(detail))
                    }
                }
                DetailOutcome.Pending -> {
                    _state.update {
                        it.copy(
                            stage = if (fromBanner) it.stage else RechargeStage.Unknown,
                            orderStatus = detail.orderStatus,
                            message = "支付结果确认中，请稍后重新查询；如已扣款请勿重复支付",
                        )
                    }
                }
            }
        }
    }

    fun startOver() {
        val current = _state.value
        if (current.stage != RechargeStage.Cancelled &&
            current.stage != RechargeStage.Expired &&
            current.stage != RechargeStage.Unknown
        ) {
            return
        }
        if (current.goods == null) {
            start(current.shopId, current.shopName)
            return
        }
        val reward = current.selectedReward
        _state.update {
            it.copy(
                stage = RechargeStage.GoodsReady,
                orderId = "",
                orderNo = "",
                orderStatus = null,
                price = reward?.cashValue.orEmpty(),
                points = reward?.reach ?: 0,
                pending = null,
                message = null,
            )
        }
    }

    fun retryLoadGoods() {
        val current = _state.value
        if (current.stage == RechargeStage.Error) start(current.shopId, current.shopName)
    }

    private suspend fun queryPending(pending: PendingRecharge, gen: Int, localId: String) {
        queryJob?.cancel()
        queryJob = scope.launch {
            val paid = queryPayStatus(gen, localId, pending.orderNo, attempts = 1)
            if (!isCurrent(gen, localId)) return@launch
            if (paid) {
                markPaid(gen, localId, orderStatus = null)
                return@launch
            }
            val detail = runCatchingSuspend {
                dataSource.loadOrderDetail(pending.orderId)
            }.getOrNull()
            if (!isCurrent(gen, localId)) return@launch
            if (detail == null) {
                _state.update {
                    it.copy(message = "有一笔待确认的充值订单，可查询状态；如已扣款请勿重复支付")
                }
                return@launch
            }
            when (detailOutcome(detail)) {
                DetailOutcome.Paid -> markPaid(gen, localId, detail.orderStatus)
                DetailOutcome.Expired -> clearPendingForBanner(
                    gen,
                    localId,
                    "上一笔待确认订单已超时关闭",
                )
                DetailOutcome.Cancelled -> clearPendingForBanner(
                    gen,
                    localId,
                    refundMessage(detail),
                )
                DetailOutcome.Pending -> _state.update {
                    it.copy(message = "有一笔待确认的充值订单，可查询状态；如已扣款请勿重复支付")
                }
            }
        }
    }

    private suspend fun resolveAlipayOutcome(
        outcome: AlipayOutcome,
        gen: Int,
        localId: String,
        orderId: String,
        orderNo: String,
        detail: VipOrderDetail,
    ) {
        when (outcome.resultStatus.trim()) {
            RESULT_SUCCESS -> {
                val paid = queryPayStatus(gen, localId, orderNo, attempts = STATUS_SUCCESS_ATTEMPTS)
                if (!isCurrent(gen, localId)) return
                if (paid) markPaid(gen, localId, detail.orderStatus)
                else resolveByDetail(gen, localId, orderId, orderNo)
            }

            RESULT_CANCELLED -> {
                val paid = queryPayStatus(gen, localId, orderNo, attempts = 1)
                if (!isCurrent(gen, localId)) return
                if (paid) {
                    markPaid(gen, localId, detail.orderStatus)
                } else {
                    finishWith(gen, localId, RechargeStage.Cancelled, "未完成支付")
                }
            }

            else -> {
                val paid = queryPayStatus(gen, localId, orderNo, attempts = STATUS_RETRY_ATTEMPTS)
                if (!isCurrent(gen, localId)) return
                if (paid) markPaid(gen, localId, detail.orderStatus)
                else resolveByDetail(gen, localId, orderId, orderNo)
            }
        }
    }

    private suspend fun resolveByDetail(
        gen: Int,
        localId: String,
        orderId: String,
        orderNo: String,
    ) {
        val detail = runCatchingSuspend {
            dataSource.loadOrderDetail(orderId)
        }.getOrNull()
        if (!isCurrent(gen, localId)) return
        if (detail == null) {
            _state.update {
                it.copy(
                    stage = RechargeStage.Unknown,
                    message = "无法确认支付结果，请勿重复支付；可重新查询状态",
                )
            }
            return
        }
        if (detail.orderNo.isNotBlank() && detail.orderNo != orderNo) {
            _state.update {
                it.copy(stage = RechargeStage.Unknown, message = "订单信息不一致，请勿重复支付")
            }
            return
        }
        when (detailOutcome(detail)) {
            DetailOutcome.Paid -> markPaid(gen, localId, detail.orderStatus)
            DetailOutcome.Expired -> finishWith(
                gen,
                localId,
                RechargeStage.Expired,
                "订单已超时关闭，请重新下单",
            )
            DetailOutcome.Cancelled -> finishWith(
                gen,
                localId,
                RechargeStage.Cancelled,
                refundMessage(detail),
            )
            DetailOutcome.Pending -> _state.update {
                it.copy(
                    stage = RechargeStage.Unknown,
                    orderStatus = detail.orderStatus,
                    message = "支付结果确认中，请稍后重新查询；如已扣款请勿重复支付",
                )
            }
        }
    }

    private suspend fun queryPayStatus(
        gen: Int,
        localId: String,
        orderNo: String,
        attempts: Int,
        showVerifying: Boolean = false,
    ): Boolean {
        repeat(attempts) { index ->
            val paid = runCatchingSuspend {
                dataSource.queryPayStatus(orderNo, ALIPAY_PAY_TYPE.toString())
            }.getOrNull() == true
            if (!isCurrent(gen, localId)) return false
            if (paid) return true
            if (index < attempts - 1) {
                if (showVerifying) {
                    _state.update {
                        it.copy(
                            stage = RechargeStage.Verifying,
                            message = "正在核验支付结果，请勿重复支付",
                        )
                    }
                }
                delay(statusPollIntervalMs)
            }
        }
        return false
    }

    private suspend fun markPaid(gen: Int, localId: String, orderStatus: Int?) {
        if (!isCurrent(gen, localId) || paidNotified) return
        paidNotified = true
        runCatchingSuspend { pendingStore.clear(localId) }
        if (!isCurrent(gen, localId)) return
        _state.update {
            it.copy(
                stage = RechargeStage.Paid,
                orderStatus = orderStatus,
                pending = null,
                message = null,
            )
        }
        onPaid()
    }

    private suspend fun finishWith(
        gen: Int,
        localId: String,
        stage: RechargeStage,
        message: String?,
    ) {
        if (!isCurrent(gen, localId)) return
        runCatchingSuspend { pendingStore.clear(localId) }
        if (!isCurrent(gen, localId)) return
        _state.update {
            it.copy(stage = stage, pending = null, message = message)
        }
    }

    private suspend fun clearPendingForBanner(gen: Int, localId: String, message: String) {
        if (!isCurrent(gen, localId)) return
        runCatchingSuspend { pendingStore.clear(localId) }
        if (!isCurrent(gen, localId)) return
        _state.update {
            if (it.stage == RechargeStage.GoodsReady) {
                it.copy(pending = null, message = message)
            } else {
                it
            }
        }
    }

    private fun applyDetailSnapshot(detail: VipOrderDetail) {
        _state.update {
            it.copy(
                price = detail.payPrice.ifBlank { it.price },
                orderStatus = detail.orderStatus,
            )
        }
    }

    private fun detailOutcome(detail: VipOrderDetail): DetailOutcome = when (detail.orderStatus) {
        ORDER_PAID, ORDER_COMPLETED -> DetailOutcome.Paid
        ORDER_EXPIRED -> DetailOutcome.Expired
        ORDER_REFUNDING, ORDER_REFUNDED -> DetailOutcome.Cancelled
        else -> DetailOutcome.Pending
    }

    private fun refundMessage(detail: VipOrderDetail): String = when (detail.orderStatus) {
        ORDER_REFUNDING -> "订单退款中，本次充值未完成"
        ORDER_REFUNDED -> "订单已退款，本次充值未完成"
        else -> "未完成支付"
    }

    private fun isCurrent(gen: Int, localId: String): Boolean =
        gen == generation && accountSource.activeLocalId() == localId

    private fun resetForAccountChange() {
        generation++
        cancelJobs()
        creatingOrder = false
        sdkInFlight = false
        paidNotified = false
        pendingOrderInfo = null
        _state.value = TicketRechargeState()
    }

    private fun cancelJobs() {
        loadJob?.cancel()
        createJob?.cancel()
        payJob?.cancel()
        queryJob?.cancel()
        loadJob = null
        createJob = null
        payJob = null
        queryJob = null
    }

    private enum class DetailOutcome {
        Paid,
        Expired,
        Cancelled,
        Pending,
    }

    private companion object {
        const val ALIPAY_PAY_TYPE = 13
        const val RESULT_SUCCESS = "9000"
        const val RESULT_CANCELLED = "6001"
        const val ORDER_PAID = 2
        const val ORDER_COMPLETED = 3
        const val ORDER_EXPIRED = 1
        const val ORDER_REFUNDING = 4
        const val ORDER_REFUNDED = 5
        const val STATUS_SUCCESS_ATTEMPTS = 8
        const val STATUS_RETRY_ATTEMPTS = 3
    }
}

private suspend inline fun <T> runCatchingSuspend(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Result.failure(throwable)
}
