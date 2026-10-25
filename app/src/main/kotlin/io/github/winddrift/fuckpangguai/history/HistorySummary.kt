package io.github.winddrift.fuckpangguai.history

import java.math.BigDecimal
import java.math.RoundingMode

internal data class HistorySummary(
    val visibleCount: Int,
    val spendCoverage: Int,
    val usageCoverage: Int,
    val averageCoverage: Int,
    val totalSpend: BigDecimal?,
    val totalUsage: BigDecimal?,
    val usagePerYuan: BigDecimal?,
)

internal fun List<HistoryRecord>.summarizeHistory(): HistorySummary {
    var spendCoverage = 0
    var usageCoverage = 0
    var averageCoverage = 0
    var totalSpend = BigDecimal.ZERO
    var totalUsage = BigDecimal.ZERO
    var sameSpend = BigDecimal.ZERO
    var sameUsage = BigDecimal.ZERO

    forEach { record ->
        val original = record.countedOriginalAmount()
        val usage = parsePureUsage(record.amount)
        if (original != null) {
            spendCoverage++
            totalSpend = totalSpend.add(original)
        }
        if (usage != null) {
            usageCoverage++
            totalUsage = totalUsage.add(usage)
        }
        if (original != null && usage != null) {
            averageCoverage++
            sameSpend = sameSpend.add(original)
            sameUsage = sameUsage.add(usage)
        }
    }

    val average = if (averageCoverage > 0 &&
        sameSpend > BigDecimal.ZERO &&
        sameUsage > BigDecimal.ZERO
    ) {
        sameUsage.divide(sameSpend, 2, RoundingMode.HALF_UP)
    } else {
        null
    }

    return HistorySummary(
        visibleCount = size,
        spendCoverage = spendCoverage,
        usageCoverage = usageCoverage,
        averageCoverage = averageCoverage,
        totalSpend = totalSpend.takeIf { spendCoverage > 0 },
        totalUsage = totalUsage.takeIf { usageCoverage > 0 },
        usagePerYuan = average,
    )
}

/**
 * 统计只接受纯数字用量，避免把 1.2L 与 30 分钟相加。
 * 前后空格允许，单位后缀、逗号、负数和科学计数法都不算有效用量。
 */
internal fun parsePureUsage(raw: String): BigDecimal? {
    val value = raw.trim()
    if (!pureUsagePattern.matches(value)) return null
    return value.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO }
}

internal fun formatSummaryMoney(value: BigDecimal?): String =
    value?.let { "¥${formatBillMoney(it)}" } ?: "—"

internal fun formatSummaryUsage(value: BigDecimal?): String {
    if (value == null) return "—"
    val rounded = if (value.scale() > 3) {
        value.setScale(3, RoundingMode.HALF_UP)
    } else {
        value
    }
    return rounded.stripTrailingZeros().toPlainString()
}

internal fun formatSummaryAverage(value: BigDecimal?): String =
    value?.let { "1 元 ≈ ${it.setScale(2, RoundingMode.HALF_UP).toPlainString()} 用量" } ?: "—"

private fun HistoryRecord.countedOriginalAmount(): BigDecimal? {
    if (billState != BillState.MATCHED) return null
    val bill = bill ?: return null
    if (bill.orderStatus != 2 && bill.orderStatus != 3) return null
    return parseAmount(bill.originalAmount)?.takeIf { it >= BigDecimal.ZERO }
}

private val pureUsagePattern = Regex("""^\s*(?:\d+(?:\.\d+)?|\.\d+)\s*$""")
