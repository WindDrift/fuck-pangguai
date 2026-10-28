package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.water.WaterConversion
import io.github.winddrift.fuckpangguai.water.formatCupCount
import io.github.winddrift.fuckpangguai.water.formatWaterVolume
import io.github.winddrift.fuckpangguai.water.parsePureUsage
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
    )
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
