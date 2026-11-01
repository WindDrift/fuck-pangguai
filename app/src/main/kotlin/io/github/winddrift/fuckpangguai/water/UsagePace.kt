package io.github.winddrift.fuckpangguai.water

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 单次接水的用时与均速。
 *
 * 时长从检测到设备解锁（[WaterSession.startedAt]）算到检测到结算完成
 * （[WaterSession.endedAt]），毫秒精度，展示时截断到 0.1 秒。
 */
data class UsagePace(
    val durationMs: Long,
    val usagePerSecond: BigDecimal?,
    val mlPerSecond: BigDecimal?,
)

/** 低于该时长时，设备用量还没同步，均速没有参考价值。 */
internal const val MIN_USAGE_PACE_DURATION_MS = 100L

/**
 * 结算用时太短或时间戳缺失时返回 null；金额无法解析时仍保留时长，
 * 只把对应的均速置空。
 */
fun computeUsagePace(
    startedAt: Long,
    endedAt: Long,
    amount: String,
    conversion: WaterConversion?,
): UsagePace? {
    val durationMs = usagePaceDurationMs(startedAt, endedAt) ?: return null
    val seconds = BigDecimal(durationMs)
        .divide(BigDecimal(1000), RATE_SCALE, RoundingMode.HALF_UP)
    val usagePerSecond = parsePureUsage(amount)
        ?.divide(seconds, RATE_SCALE, RoundingMode.HALF_UP)
    val mlPerSecond = conversion?.convert(amount)
        ?.volumeMl
        ?.divide(seconds, RATE_SCALE, RoundingMode.HALF_UP)
    return UsagePace(
        durationMs = durationMs,
        usagePerSecond = usagePerSecond,
        mlPerSecond = mlPerSecond,
    )
}

/** 只有结算完成且时间戳可信时才返回时长。 */
internal fun usagePaceDurationMs(startedAt: Long, endedAt: Long): Long? {
    if (startedAt <= 0L || endedAt <= startedAt) return null
    val durationMs = endedAt - startedAt
    return durationMs.takeIf { it >= MIN_USAGE_PACE_DURATION_MS }
}

/** 截断到 0.1 秒，避免计时器在边界反复进位；超过一分钟显示分秒。 */
fun formatUsageDuration(durationMs: Long): String {
    val totalTenths = durationMs.coerceAtLeast(0L) / 100L
    val minutes = totalTenths / 600L
    val seconds = (totalTenths % 600L) / 10L
    val tenths = totalTenths % 10L
    return if (minutes > 0L) {
        "$minutes 分 ${seconds.toString().padStart(2, '0')}.$tenths 秒"
    } else {
        "$seconds.$tenths 秒"
    }
}

/** 最多保留 1 位小数并去掉尾零；正数四舍五入到 0 时用“不足 0.1”避免误导。 */
fun formatUsageRate(value: BigDecimal): String {
    if (value <= BigDecimal.ZERO) return "0"
    val rounded = value.setScale(1, RoundingMode.HALF_UP)
    if (rounded.compareTo(BigDecimal.ZERO) == 0) return "不足 0.1"
    return rounded.stripTrailingZeros().toPlainString()
}

/** 例如“0.1 用量/秒 · 0.4 mL/秒”；两种均速都不可用时返回 null。 */
fun formatUsagePace(pace: UsagePace): String? =
    formatFlowRates(pace.usagePerSecond, pace.mlPerSecond)

internal fun formatFlowRates(
    usagePerSecond: BigDecimal?,
    mlPerSecond: BigDecimal?,
): String? {
    val parts = buildList {
        usagePerSecond?.let { add("${formatUsageRate(it)} 用量/秒") }
        mlPerSecond?.let { add("${formatUsageRate(it)} mL/秒") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private const val RATE_SCALE = 4
