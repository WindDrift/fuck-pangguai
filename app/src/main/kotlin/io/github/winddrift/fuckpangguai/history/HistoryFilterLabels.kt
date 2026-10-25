package io.github.winddrift.fuckpangguai.history

internal fun HistoryTimePreset.label(): String = when (this) {
    HistoryTimePreset.ALL -> "全部"
    HistoryTimePreset.TODAY -> "今天"
    HistoryTimePreset.LAST_7_DAYS -> "近 7 天"
    HistoryTimePreset.LAST_30_DAYS -> "近 30 天"
    HistoryTimePreset.LAST_90_DAYS -> "近 90 天"
    HistoryTimePreset.CUSTOM -> "自定义"
}

internal fun SessionResult.label(): String = when (this) {
    SessionResult.PAID -> "已支付"
    SessionResult.FINISHED_NO_CHARGE -> "未扣费"
    SessionResult.PAY_UNKNOWN -> "支付未知"
    SessionResult.FAULT -> "设备异常"
}

internal fun BillState.label(): String = when (this) {
    BillState.MATCHED -> "账单已匹配"
    BillState.UNMATCHED -> "账单未匹配"
    BillState.ERROR -> "账单同步失败"
    BillState.NONE -> "账单未同步"
}

internal fun BillPaymentGroup.label(): String = when (this) {
    BillPaymentGroup.NEED_PAY -> "需付"
    BillPaymentGroup.PAID_OR_COMPLETED -> "已支付/完成"
    BillPaymentGroup.REFUNDING -> "退款中"
    BillPaymentGroup.REFUNDED -> "已退款"
}

internal fun TicketUsage.label(): String = when (this) {
    TicketUsage.ALL -> "全部"
    TicketUsage.USED -> "用了小票"
    TicketUsage.UNUSED -> "未用小票"
}

internal fun TicketLogMatch.label(): String = when (this) {
    TicketLogMatch.MATCHED -> "小票流水已匹配"
    TicketLogMatch.UNMATCHED_OR_FAILED -> "小票流水未匹配或失败"
}

internal fun TicketComposition.label(): String = when (this) {
    TicketComposition.ALL -> "全部"
    TicketComposition.PRINCIPAL_ONLY -> "仅消耗本金"
    TicketComposition.PRESENT_ONLY -> "仅消耗赠送"
    TicketComposition.BOTH -> "本金+赠送"
    TicketComposition.DEDUCTION_WITHOUT_SPLIT -> "有抵扣但无流水拆分"
}

internal val billStateFilterOrder: List<BillState> = listOf(
    BillState.MATCHED,
    BillState.UNMATCHED,
    BillState.ERROR,
    BillState.NONE,
)

internal fun TicketComposition.summary(): String = when (this) {
    TicketComposition.ALL -> "不限制本金/赠送构成"
    TicketComposition.PRINCIPAL_ONLY -> "流水拆分中只有本金"
    TicketComposition.PRESENT_ONLY -> "流水拆分中只有赠送"
    TicketComposition.BOTH -> "流水拆分中本金和赠送都有"
    TicketComposition.DEDUCTION_WITHOUT_SPLIT -> "账单有抵扣，但没有可用的流水拆分"
}
