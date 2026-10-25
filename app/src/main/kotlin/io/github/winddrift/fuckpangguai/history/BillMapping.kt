package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.net.dto.BillOrderDto
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

internal const val TICKET_PROMOTION_TYPE = 4

internal fun BillOrderDto.toBillInfo(fetchedAt: Long): BillInfo {
    val original = formatBillMoney(markPrice).ifBlank {
        val sum = tradeOrderItem
            .mapNotNull { it.originPrice.trim().toBigDecimalOrNull() }
            .fold(BigDecimal.ZERO, BigDecimal::add)
        if (sum > BigDecimal.ZERO) formatBillMoney(sum) else ""
    }
    val paymentDiscount = formatBillMoney(paymentDiscountPrice).ifBlank { "0.00" }
    val ticketPromotions = promotionList.mapNotNull { promotion ->
        if (promotion.promotionType != TICKET_PROMOTION_TYPE) return@mapNotNull null
        promotion.discountAmount.trim().toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO }
    }
    val ticketAmount = ticketPromotions.fold(BigDecimal.ZERO, BigDecimal::add)
    val ticket = if (ticketAmount > BigDecimal.ZERO) {
        val points = ticketAmount
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
        BillTicket(
            billPoints = points,
            points = points,
            deductionYuan = formatBillMoney(ticketAmount),
            shopId = shopId,
            shopName = shopName,
        )
    } else {
        null
    }

    return BillInfo(
        orderId = id,
        orderNo = orderNo,
        orderStatus = orderStatus,
        orderType = orderType,
        originalAmount = original,
        payAmount = formatBillMoney(payPrice),
        paymentDiscountAmount = paymentDiscount.takeIf { it != "0.00" }.orEmpty(),
        paidAmount = officialPaidAmount(payPrice, paymentDiscountPrice),
        promotions = promotionList
            .filter { it.promotionType != TICKET_PROMOTION_TYPE }
            .mapNotNull { promotion ->
                val amount = promotion.discountAmount.trim().toBigDecimalOrNull()
                    ?.takeIf { it > BigDecimal.ZERO }
                    ?: return@mapNotNull null
                BillDiscount(
                    type = promotion.promotionType,
                    label = promotionLabel(promotion.promotionType),
                    amount = formatBillMoney(amount),
                )
            },
        ticket = ticket,
        shopId = shopId,
        shopName = shopName,
        machineName = machineName,
        payTypeName = payTypeName,
        createTime = createTime,
        payTime = payTime,
        fetchedAt = fetchedAt,
    )
}

internal fun BillInfo.withTicketLogs(
    logs: List<TicketLog>,
    logError: String = "",
): BillInfo {
    val current = ticket ?: return this
    val deductions = logs.filter { it.type != 100 }
    if (deductions.isEmpty()) {
        return copy(
            ticket = current.copy(matchedLog = false, logPoints = 0),
            ticketLogError = logError,
        )
    }

    val logPoints = deductions.sumOf { abs(it.amount) }
    val principal = deductions.sumOf { abs(it.principalAmount) }
    val present = deductions.sumOf { abs(it.presentAmount) }
    val first = deductions.first()

    return copy(
        ticket = current.copy(
            points = logPoints.takeIf { it > 0 } ?: current.billPoints,
            principalPoints = principal,
            presentPoints = present,
            shopId = first.shopId.ifBlank { current.shopId },
            shopName = first.shopName.ifBlank { current.shopName },
            remark = first.remark,
            createdAt = first.createdAt,
            matchedLog = true,
            logPoints = logPoints,
        ),
        ticketLogError = logError,
    )
}

internal fun officialPaidAmount(payPrice: String, paymentDiscountPrice: String): String {
    val pay = payPrice.trim().toBigDecimalOrNull() ?: return ""
    val discount = paymentDiscountPrice.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
    return formatBillMoney(pay.subtract(discount))
}

internal fun formatBillMoney(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ""
    val parsed = trimmed.toBigDecimalOrNull() ?: return trimmed
    return formatBillMoney(parsed)
}

internal fun formatBillMoney(value: BigDecimal): String =
    value.setScale(2, RoundingMode.HALF_UP).toPlainString()

internal fun promotionLabel(type: Int): String = when (type) {
    1 -> "VIP 优惠"
    2 -> "限时优惠"
    3 -> "优惠券"
    4 -> "通用小票"
    6 -> "商家优惠券"
    7 -> "胖乖优惠券"
    8 -> "积分抵扣"
    9 -> "支付优惠"
    10 -> "免费额度"
    15 -> "饮水省钱卡"
    21 -> "用水额度"
    44 -> "购买小票"
    45 -> "购买 VIP"
    1000 -> "视频活动"
    3000 -> "会员活动"
    3001 -> "支付宝会员活动"
    3002 -> "新会员活动"
    4000, 4001 -> "校园活动"
    5000 -> "云码优惠"
    6000, 6001 -> "内容平台活动"
    7000 -> "保险活动"
    8000 -> "钱包购买"
    9000 -> "秒杀活动"
    10000 -> "券包活动"
    else -> "其他优惠（$type）"
}

internal fun billOrderStatusLabel(status: Int): String = when (status) {
    0 -> "未支付"
    1 -> "支付超时"
    2 -> "已支付"
    3 -> "已完成"
    4 -> "退款中"
    5 -> "已退款"
    else -> "状态 $status"
}
