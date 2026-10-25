package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.water.SessionPhase
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class HistoryTimePreset {
    ALL,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_90_DAYS,
    CUSTOM,
}

enum class SessionResult(val phase: String) {
    PAID(SessionPhase.PAID),
    FINISHED_NO_CHARGE(SessionPhase.FINISHED_NO_CHARGE),
    PAY_UNKNOWN(SessionPhase.PAY_UNKNOWN),
    FAULT(SessionPhase.FAULT),
    ;

    companion object {
        fun fromPhase(phase: String): SessionResult? =
            entries.firstOrNull { it.phase == phase }
    }
}

enum class BillPaymentGroup(val statuses: Set<Int>) {
    NEED_PAY(setOf(0, 1)),
    PAID_OR_COMPLETED(setOf(2, 3)),
    REFUNDING(setOf(4)),
    REFUNDED(setOf(5)),
}

enum class TicketUsage {
    ALL,
    USED,
    UNUSED,
}

enum class TicketLogMatch {
    MATCHED,
    UNMATCHED_OR_FAILED,
}

enum class TicketComposition {
    ALL,
    PRINCIPAL_ONLY,
    PRESENT_ONLY,
    BOTH,
    DEDUCTION_WITHOUT_SPLIT,
}

data class AmountRange(
    val minText: String = "",
    val maxText: String = "",
) {
    val min: BigDecimal? get() = parseAmount(minText)
    val max: BigDecimal? get() = parseAmount(maxText)
    val isActive: Boolean get() = min != null || max != null
    val hasInvalidInput: Boolean
        get() = (minText.isNotBlank() && min == null) ||
            (maxText.isNotBlank() && max == null)
    val isInverted: Boolean
        get() = min != null && max != null && min!! > max!!

    fun matches(raw: String?): Boolean {
        if (!isActive) return true
        val value = parseAmount(raw.orEmpty()) ?: return false
        val lower = min ?: return value <= max!!
        val upper = max ?: return value >= lower
        return value >= lower && value <= upper
    }
}

data class HistoryFilter(
    val query: String = "",
    val timePreset: HistoryTimePreset = HistoryTimePreset.ALL,
    val customStartEpochDay: Long? = null,
    val customEndEpochDay: Long? = null,
    val devices: Set<String> = emptySet(),
    val sessionResults: Set<SessionResult> = emptySet(),
    val billStates: Set<BillState> = emptySet(),
    val paymentGroups: Set<BillPaymentGroup> = emptySet(),
    val exceptionsOnly: Boolean = false,
    val ticketUsage: TicketUsage = TicketUsage.ALL,
    val ticketLogMatches: Set<TicketLogMatch> = emptySet(),
    val ticketShops: Set<String> = emptySet(),
    val ticketComposition: TicketComposition = TicketComposition.ALL,
    val paidAmount: AmountRange = AmountRange(),
    val ticketDeduction: AmountRange = AmountRange(),
) {
    val timeIsActive: Boolean
        get() = timePreset != HistoryTimePreset.ALL &&
            (timePreset != HistoryTimePreset.CUSTOM ||
                customStartEpochDay != null ||
                customEndEpochDay != null)

    val hasActiveFilters: Boolean get() = activeCategoryCount > 0

    val hasActiveConstraints: Boolean
        get() = hasActiveFilters || query.isNotBlank()

    val activeCategoryCount: Int
        get() {
            var count = 0
            if (timeIsActive) count++
            if (devices.isNotEmpty()) count++
            if (sessionResults.isNotEmpty()) count++
            if (billStates.isNotEmpty()) count++
            if (paymentGroups.isNotEmpty()) count++
            if (exceptionsOnly) count++
            if (ticketUsage != TicketUsage.ALL) count++
            if (ticketLogMatches.isNotEmpty()) count++
            if (ticketShops.isNotEmpty()) count++
            if (ticketComposition != TicketComposition.ALL) count++
            if (paidAmount.isActive) count++
            if (ticketDeduction.isActive) count++
            return count
        }

    fun chips(): List<ActiveFilterChip> = buildList {
        if (timeIsActive) {
            add(
                ActiveFilterChip.Time(
                    preset = timePreset,
                    startEpochDay = customStartEpochDay,
                    endEpochDay = customEndEpochDay,
                ),
            )
        }
        devices.sorted().forEach { add(ActiveFilterChip.Device(it)) }
        sessionResults.sortedBy { it.ordinal }.forEach {
            add(ActiveFilterChip.SessionResultChip(it))
        }
        billStates.sortedBy { billStateFilterOrder.indexOf(it) }.forEach {
            add(ActiveFilterChip.BillStateChip(it))
        }
        paymentGroups.sortedBy { it.ordinal }.forEach {
            add(ActiveFilterChip.PaymentGroupChip(it))
        }
        if (exceptionsOnly) add(ActiveFilterChip.Exceptions)
        if (ticketUsage != TicketUsage.ALL) {
            add(ActiveFilterChip.TicketUsageChip(ticketUsage))
        }
        ticketLogMatches.sortedBy { it.ordinal }.forEach {
            add(ActiveFilterChip.TicketFlowChip(it))
        }
        ticketShops.sorted().forEach { add(ActiveFilterChip.TicketShop(it)) }
        if (ticketComposition != TicketComposition.ALL) {
            add(ActiveFilterChip.TicketCompositionChip(ticketComposition))
        }
        if (paidAmount.isActive) add(ActiveFilterChip.PaidRange)
        if (ticketDeduction.isActive) add(ActiveFilterChip.TicketDeductionRange)
    }

    fun removeChip(chip: ActiveFilterChip): HistoryFilter = when (chip) {
        is ActiveFilterChip.Time -> copy(
            timePreset = HistoryTimePreset.ALL,
            customStartEpochDay = null,
            customEndEpochDay = null,
        )
        is ActiveFilterChip.Device -> copy(devices = devices - chip.value)
        is ActiveFilterChip.SessionResultChip -> copy(
            sessionResults = sessionResults - chip.value,
        )
        is ActiveFilterChip.BillStateChip -> copy(billStates = billStates - chip.value)
        is ActiveFilterChip.PaymentGroupChip -> copy(
            paymentGroups = paymentGroups - chip.value,
        )
        ActiveFilterChip.Exceptions -> copy(exceptionsOnly = false)
        is ActiveFilterChip.TicketUsageChip -> copy(ticketUsage = TicketUsage.ALL)
        is ActiveFilterChip.TicketFlowChip -> copy(
            ticketLogMatches = ticketLogMatches - chip.value,
        )
        is ActiveFilterChip.TicketShop -> copy(ticketShops = ticketShops - chip.value)
        is ActiveFilterChip.TicketCompositionChip -> copy(
            ticketComposition = TicketComposition.ALL,
        )
        ActiveFilterChip.PaidRange -> copy(paidAmount = AmountRange())
        ActiveFilterChip.TicketDeductionRange -> copy(ticketDeduction = AmountRange())
    }
}

sealed interface ActiveFilterChip {
    data class Time(
        val preset: HistoryTimePreset,
        val startEpochDay: Long?,
        val endEpochDay: Long?,
    ) : ActiveFilterChip

    data class Device(val value: String) : ActiveFilterChip
    data class SessionResultChip(val value: SessionResult) : ActiveFilterChip
    data class BillStateChip(val value: BillState) : ActiveFilterChip
    data class PaymentGroupChip(val value: BillPaymentGroup) : ActiveFilterChip
    data object Exceptions : ActiveFilterChip
    data class TicketUsageChip(val value: TicketUsage) : ActiveFilterChip
    data class TicketFlowChip(val value: TicketLogMatch) : ActiveFilterChip
    data class TicketShop(val value: String) : ActiveFilterChip
    data class TicketCompositionChip(val value: TicketComposition) : ActiveFilterChip
    data object PaidRange : ActiveFilterChip
    data object TicketDeductionRange : ActiveFilterChip
}

internal fun List<HistoryRecord>.applyHistoryFilter(
    filter: HistoryFilter,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<HistoryRecord> {
    if (!filter.hasActiveConstraints) return this
    val query = filter.query.trim().lowercase(Locale.ROOT)
    val timeWindow = filter.timeWindow(nowMillis, zoneId)
    return filter { record ->
        (query.isEmpty() || record.filterHaystack().contains(query)) &&
            timeWindow.contains(record.updatedAt) &&
            record.matchesDevice(filter) &&
            record.matchesSession(filter) &&
            record.matchesBillState(filter) &&
            record.matchesPayment(filter) &&
            record.matchesException(filter) &&
            record.matchesTicketUsage(filter) &&
            record.matchesTicketFlow(filter) &&
            record.matchesTicketShop(filter) &&
            record.matchesTicketComposition(filter) &&
            filter.paidAmount.matches(record.bill?.paidAmount) &&
            filter.ticketDeduction.matches(record.bill?.ticket?.deductionYuan)
    }
}

internal fun HistoryRecord.displayOrderNo(): String =
    orderNo.takeUnless { it.startsWith("local-") }.orEmpty()

internal fun parseAmount(raw: String): BigDecimal? {
    val cleaned = raw.trim()
        .replace("¥", "")
        .replace("￥", "")
        .replace(",", "")
        .replace("，", "")
        .replace(" ", "")
        .replace("\u3000", "")
    if (cleaned.isBlank()) return null
    return cleaned.toBigDecimalOrNull()
}

private data class TimeWindow(
    val startInclusive: Long?,
    val endExclusive: Long?,
) {
    fun contains(value: Long): Boolean {
        if (startInclusive != null && value < startInclusive) return false
        if (endExclusive != null && value >= endExclusive) return false
        return true
    }
}

private fun HistoryFilter.timeWindow(nowMillis: Long, zone: ZoneId): TimeWindow {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when (timePreset) {
        HistoryTimePreset.ALL -> TimeWindow(null, null)
        HistoryTimePreset.TODAY -> window(today, today, zone)
        HistoryTimePreset.LAST_7_DAYS -> window(today.minusDays(6), today, zone)
        HistoryTimePreset.LAST_30_DAYS -> window(today.minusDays(29), today, zone)
        HistoryTimePreset.LAST_90_DAYS -> window(today.minusDays(89), today, zone)
        HistoryTimePreset.CUSTOM -> {
            val start = customStartEpochDay?.let { LocalDate.ofEpochDay(it) }
            val end = customEndEpochDay?.let { LocalDate.ofEpochDay(it) }
            if (start == null && end == null) {
                TimeWindow(null, null)
            } else {
                TimeWindow(
                    startInclusive = start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                    endExclusive = end?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                )
            }
        }
    }
}

private fun window(start: LocalDate, end: LocalDate, zone: ZoneId): TimeWindow =
    TimeWindow(
        startInclusive = start.atStartOfDay(zone).toInstant().toEpochMilli(),
        endExclusive = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )

private fun HistoryRecord.matchesDevice(filter: HistoryFilter): Boolean =
    filter.devices.isEmpty() || deviceName in filter.devices

private fun HistoryRecord.matchesSession(filter: HistoryFilter): Boolean {
    if (filter.sessionResults.isEmpty()) return true
    val result = SessionResult.fromPhase(phase) ?: return false
    return result in filter.sessionResults
}

private fun HistoryRecord.matchesBillState(filter: HistoryFilter): Boolean =
    filter.billStates.isEmpty() || billState in filter.billStates

private fun HistoryRecord.matchesPayment(filter: HistoryFilter): Boolean {
    if (filter.paymentGroups.isEmpty()) return true
    val status = bill?.orderStatus ?: return false
    return filter.paymentGroups.any { status in it.statuses }
}

private fun HistoryRecord.matchesException(filter: HistoryFilter): Boolean =
    !filter.exceptionsOnly ||
        error.isNotBlank() ||
        billState == BillState.ERROR ||
        bill?.ticketLogError?.isNotBlank() == true

private fun HistoryRecord.matchesTicketUsage(filter: HistoryFilter): Boolean =
    when (filter.ticketUsage) {
        TicketUsage.ALL -> true
        TicketUsage.USED -> bill?.ticket != null
        TicketUsage.UNUSED -> billState == BillState.MATCHED &&
            bill != null &&
            bill.ticket == null
    }

private fun HistoryRecord.matchesTicketFlow(filter: HistoryFilter): Boolean {
    if (filter.ticketLogMatches.isEmpty()) return true
    val ticket = bill?.ticket ?: return false
    return filter.ticketLogMatches.any { match ->
        when (match) {
            TicketLogMatch.MATCHED -> ticket.matchedLog
            TicketLogMatch.UNMATCHED_OR_FAILED -> !ticket.matchedLog
        }
    }
}

private fun HistoryRecord.matchesTicketShop(filter: HistoryFilter): Boolean {
    if (filter.ticketShops.isEmpty()) return true
    val ticket = bill?.ticket ?: return false
    return ticket.shopName in filter.ticketShops
}

private fun HistoryRecord.matchesTicketComposition(filter: HistoryFilter): Boolean {
    val composition = filter.ticketComposition
    if (composition == TicketComposition.ALL) return true
    val ticket = bill?.ticket ?: return false
    return when (composition) {
        TicketComposition.ALL -> true
        TicketComposition.PRINCIPAL_ONLY -> ticket.matchedLog &&
            ticket.principalPoints > 0 &&
            ticket.presentPoints == 0
        TicketComposition.PRESENT_ONLY -> ticket.matchedLog &&
            ticket.presentPoints > 0 &&
            ticket.principalPoints == 0
        TicketComposition.BOTH -> ticket.matchedLog &&
            ticket.principalPoints > 0 &&
            ticket.presentPoints > 0
        TicketComposition.DEDUCTION_WITHOUT_SPLIT -> !ticket.matchedLog ||
            (ticket.principalPoints <= 0 && ticket.presentPoints <= 0)
    }
}

private fun HistoryRecord.filterHaystack(): String {
    val bill = bill
    val ticket = bill?.ticket
    val values = buildList {
        add(deviceName)
        add(displayOrderNo())
        add(payOrderNo)
        add(payOrderId)
        add(identify)
        add(amount)
        add(error)
        add(billError)
        if (bill != null) {
            add(bill.orderNo.takeUnless { it.startsWith("local-") }.orEmpty())
            add(bill.shopName)
            add(bill.machineName)
            add(bill.payTypeName)
            add(bill.originalAmount)
            add(bill.payAmount)
            add(bill.paidAmount)
            add(bill.paymentDiscountAmount)
            add(bill.ticketLogError)
            bill.promotions.forEach {
                add(it.label)
                add(it.amount)
                add("¥${it.amount}")
                add("-¥${it.amount}")
            }
            addMoneyVariants(bill.originalAmount)
            addMoneyVariants(bill.paidAmount)
            addMoneyVariants(bill.paymentDiscountAmount)
        }
        if (ticket != null) {
            add(ticket.shopName)
            add(ticket.remark)
            add(ticket.deductionYuan)
            addMoneyVariants(ticket.deductionYuan)
            listOf(
                ticket.points,
                ticket.principalPoints,
                ticket.presentPoints,
                ticket.logPoints,
                ticket.billPoints,
            ).distinct().forEach {
                add("$it")
                add("$it 点")
                add("${it}点")
            }
        }
    }
    return values
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .lowercase(Locale.ROOT)
}

private fun MutableList<String>.addMoneyVariants(value: String) {
    if (value.isBlank()) return
    add("¥$value")
    add("-¥$value")
}
