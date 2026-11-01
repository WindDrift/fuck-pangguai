package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.water.MIN_USAGE_PACE_DURATION_MS
import io.github.winddrift.fuckpangguai.water.WaterConversion
import io.github.winddrift.fuckpangguai.water.formatCupCount
import io.github.winddrift.fuckpangguai.water.formatFlowRates
import io.github.winddrift.fuckpangguai.water.formatWaterVolume
import io.github.winddrift.fuckpangguai.water.parsePureUsage
import io.github.winddrift.fuckpangguai.water.usagePaceDurationMs
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
    val waterCoverage: Int = 0,
    val waterAverageCoverage: Int = 0,
    val totalWaterMl: BigDecimal? = null,
    val waterPerYuanMl: BigDecimal? = null,
    /** 有可信结算用时的记录数。 */
    val paceCoverage: Int = 0,
    val flowUsagePerSecond: BigDecimal? = null,
    val flowMlPerSecond: BigDecimal? = null,
)

internal fun List<HistoryRecord>.summarizeHistory(
    conversion: WaterConversion? = null,
): HistorySummary {
    var spendCoverage = 0
    var usageCoverage = 0
    var averageCoverage = 0
    var waterCoverage = 0
    var waterAverageCoverage = 0
    var totalSpend = BigDecimal.ZERO
    var totalUsage = BigDecimal.ZERO
    var totalWaterMl = BigDecimal.ZERO
    var sameSpend = BigDecimal.ZERO
    var sameUsage = BigDecimal.ZERO
    var sameWaterSpend = BigDecimal.ZERO
    var sameWaterMl = BigDecimal.ZERO
    var paceCoverage = 0
    var usagePaceDurationMs = 0L
    var usagePaceTotal = BigDecimal.ZERO
    var waterPaceDurationMs = 0L
    var waterPaceTotal = BigDecimal.ZERO

    forEach { record ->
        val original = record.countedOriginalAmount()
        val usage = parsePureUsage(record.amount)
        val volume = conversion?.convert(record.amount)?.volumeMl
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
        if (volume != null) {
            waterCoverage++
            totalWaterMl = totalWaterMl.add(volume)
        }
        if (original != null && volume != null) {
            waterAverageCoverage++
            sameWaterSpend = sameWaterSpend.add(original)
            sameWaterMl = sameWaterMl.add(volume)
        }
        val paceDurationMs = usagePaceDurationMs(record.startedAt, record.endedAt)
        if (paceDurationMs != null) {
            paceCoverage++
            if (usage != null) {
                usagePaceDurationMs += paceDurationMs
                usagePaceTotal = usagePaceTotal.add(usage)
            }
            if (volume != null) {
                waterPaceDurationMs += paceDurationMs
                waterPaceTotal = waterPaceTotal.add(volume)
            }
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
    val waterAverage = if (waterAverageCoverage > 0 &&
        sameWaterSpend > BigDecimal.ZERO &&
        sameWaterMl > BigDecimal.ZERO
    ) {
        sameWaterMl.divide(sameWaterSpend, 2, RoundingMode.HALF_UP)
    } else {
        null
    }

    return HistorySummary(
        visibleCount = size,
        spendCoverage = spendCoverage,
        usageCoverage = usageCoverage,
        averageCoverage = averageCoverage,
        waterCoverage = waterCoverage,
        waterAverageCoverage = waterAverageCoverage,
        totalSpend = totalSpend.takeIf { spendCoverage > 0 },
        totalUsage = totalUsage.takeIf { usageCoverage > 0 },
        totalWaterMl = totalWaterMl.takeIf { waterCoverage > 0 },
        usagePerYuan = average,
        waterPerYuanMl = waterAverage,
        paceCoverage = paceCoverage,
        flowUsagePerSecond = flowRate(usagePaceTotal, usagePaceDurationMs),
        flowMlPerSecond = flowRate(waterPaceTotal, waterPaceDurationMs),
    )
}

/** 合计口径：总用量/总水量 ÷ 各自可计时记录的用时合计；旧记录不参与。 */
private fun flowRate(total: BigDecimal, durationMs: Long): BigDecimal? {
    if (total <= BigDecimal.ZERO || durationMs < MIN_USAGE_PACE_DURATION_MS) return null
    val seconds = BigDecimal(durationMs)
        .divide(THOUSAND, FLOW_SCALE, RoundingMode.HALF_UP)
    return total.divide(seconds, FLOW_SCALE, RoundingMode.HALF_UP)
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

internal fun formatSummaryFlow(summary: HistorySummary): String =
    formatFlowRates(summary.flowUsagePerSecond, summary.flowMlPerSecond) ?: "—"

internal fun formatSummaryAverage(
    usagePerYuan: BigDecimal?,
    waterPerYuanMl: BigDecimal? = null,
    cupMl: BigDecimal? = null,
): String {
    val parts = mutableListOf<String>()
    usagePerYuan?.let {
        parts += "${it.setScale(2, RoundingMode.HALF_UP).toPlainString()} 用量"
    }
    waterPerYuanMl?.let {
        parts += formatWaterVolume(it)
        cupMl?.let { cup -> formatCupCount(it, cup)?.let(parts::add) }
    }
    if (parts.isEmpty()) return "—"
    return "1 元 ≈ ${parts.joinToString(" · ")}"
}

private fun HistoryRecord.countedOriginalAmount(): BigDecimal? {
    if (billState != BillState.MATCHED) return null
    val bill = bill ?: return null
    if (bill.orderStatus != 2 && bill.orderStatus != 3) return null
    return parseAmount(bill.originalAmount)?.takeIf { it >= BigDecimal.ZERO }
}

private val THOUSAND = BigDecimal(1000)
private const val FLOW_SCALE = 4
