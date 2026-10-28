package io.github.winddrift.fuckpangguai.water

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 全局的“用量 → 水量”换算规则。
 *
 * 用量是设备返回的原始数字，水量以 mL 为单位；杯容量可选，
 * 仅用于把水量换算成更直观的杯数。
 */
data class WaterConversion(
    val usageBase: BigDecimal,
    val waterMl: BigDecimal,
    val cupMl: BigDecimal? = null,
) {
    init {
        require(usageBase > BigDecimal.ZERO) { "usageBase must be positive" }
        require(waterMl > BigDecimal.ZERO) { "waterMl must be positive" }
        require(cupMl == null || cupMl > BigDecimal.ZERO) { "cupMl must be positive" }
    }

    fun convert(raw: String): ConvertedAmount? {
        parseWaterVolumeMl(raw)?.let { volume ->
            return ConvertedAmount(volumeMl = volume, usage = null, fromVolume = true)
        }
        parsePureUsage(raw)?.let { usage ->
            val volume = usage.multiply(waterMl)
                .divide(usageBase, VOLUME_SCALE, RoundingMode.HALF_UP)
            return ConvertedAmount(volumeMl = volume, usage = usage, fromVolume = false)
        }
        return null
    }

    private companion object {
        const val VOLUME_SCALE = 6
    }
}

data class ConvertedAmount(
    val volumeMl: BigDecimal,
    val usage: BigDecimal?,
    val fromVolume: Boolean,
)

sealed interface WaterConversionValidation {
    data object Empty : WaterConversionValidation

    data class Valid(val conversion: WaterConversion) : WaterConversionValidation

    data class Invalid(val message: String) : WaterConversionValidation
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

internal fun parseWaterVolumeMl(raw: String): BigDecimal? {
    val match = volumePattern.matchEntire(raw) ?: return null
    val value = match.groupValues[1].toBigDecimalOrNull() ?: return null
    val unit = match.groupValues[2]
    return if (unit == "L" || unit == "l" || unit == "升") {
        value.multiply(THOUSAND)
    } else {
        value
    }
}

internal fun validateWaterConversion(
    usageBaseText: String,
    waterMlText: String,
    cupMlText: String,
): WaterConversionValidation {
    val usageText = usageBaseText.trim()
    val waterText = waterMlText.trim()
    val cupText = cupMlText.trim()
    if (usageText.isEmpty() && waterText.isEmpty() && cupText.isEmpty()) {
        return WaterConversionValidation.Empty
    }
    if (usageText.isEmpty() || waterText.isEmpty()) {
        return WaterConversionValidation.Invalid("请同时填写用量和对应水量")
    }
    val usageBase = parsePositiveDecimal(usageText)
        ?: return WaterConversionValidation.Invalid("用量需为大于 0 的数字")
    val waterMl = parsePositiveDecimal(waterText)
        ?: return WaterConversionValidation.Invalid("对应水量需为大于 0 的数字")
    val cupMl = if (cupText.isEmpty()) {
        null
    } else {
        parsePositiveDecimal(cupText)
            ?: return WaterConversionValidation.Invalid("一杯容量需为大于 0 的数字，或留空")
    }
    return WaterConversionValidation.Valid(
        WaterConversion(usageBase = usageBase, waterMl = waterMl, cupMl = cupMl),
    )
}

/**
 * 只保留“用量 · 水量”的形式，供接水页等已经单独展示原始用量的位置使用。
 * 带单位的原始值不再重复显示 mL，只在配置了杯容量时补杯数。
 */
internal fun formatConvertedWater(raw: String, conversion: WaterConversion?): String? {
    if (conversion == null) return null
    val converted = conversion.convert(raw) ?: return null
    val parts = mutableListOf<String>()
    if (!converted.fromVolume) {
        parts += formatWaterVolume(converted.volumeMl)
    }
    conversion.cupMl?.let { cupMl ->
        formatCupCount(converted.volumeMl, cupMl)?.let(parts::add)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * 保留原始用量，并在后面追加换算水量与杯数。
 * 没有配置换算、或原始值无法解析时，原样返回。
 */
internal fun formatAmountWithWater(raw: String, conversion: WaterConversion?): String {
    val detail = formatConvertedWater(raw, conversion) ?: return raw
    return if (raw.isBlank()) detail else "$raw · $detail"
}

internal fun formatWaterVolume(ml: BigDecimal): String {
    val safe = ml.max(BigDecimal.ZERO)
    val roundedMl = safe.setScale(0, RoundingMode.HALF_UP)
    return if (roundedMl >= THOUSAND) {
        val liters = safe.divide(THOUSAND, 2, RoundingMode.HALF_UP)
        "${liters.toPlainText()} L"
    } else {
        "${roundedMl.toPlainText()} mL"
    }
}

internal fun formatCupCount(volumeMl: BigDecimal, cupMl: BigDecimal): String? {
    if (cupMl <= BigDecimal.ZERO) return null
    val cups = volumeMl.max(BigDecimal.ZERO)
        .divide(cupMl, 1, RoundingMode.HALF_UP)
    if (volumeMl > BigDecimal.ZERO && cups < BigDecimal("0.1")) {
        return "不足 0.1 杯"
    }
    return "约 ${cups.toPlainText()} 杯"
}

internal fun formatConversionRule(conversion: WaterConversion): String {
    val cup = conversion.cupMl
        ?.let { " · 一杯 ${it.toPlainText()} mL" }
        .orEmpty()
    return "${conversion.usageBase.toPlainText()} 用量 = " +
        "${conversion.waterMl.toPlainText()} mL$cup"
}

internal fun formatConversionPreview(conversion: WaterConversion): String {
    val water = formatWaterVolume(conversion.waterMl)
    val cup = conversion.cupMl
        ?.let { formatCupCount(conversion.waterMl, it) }
        ?.let { " · $it" }
        .orEmpty()
    return "${conversion.usageBase.toPlainText()} 用量 ≈ $water$cup"
}

private fun parsePositiveDecimal(raw: String): BigDecimal? {
    if (!positiveDecimalPattern.matches(raw)) return null
    return raw.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
}

private fun BigDecimal.toPlainText(): String {
    val normalized = if (compareTo(BigDecimal.ZERO) == 0) {
        BigDecimal.ZERO
    } else {
        stripTrailingZeros()
    }
    return normalized.toPlainString()
}

private val THOUSAND = BigDecimal("1000")

private val pureUsagePattern = Regex("""^\s*(?:\d+(?:\.\d+)?|\.\d+)\s*$""")

private val positiveDecimalPattern = Regex("""^\s*(?:\d+(?:\.\d+)?|\.\d+)\s*$""")

private val volumePattern = Regex(
    """^\s*(\d+(?:\.\d+)?|\.\d+)\s*(ml|毫升|l|升)\s*$""",
    RegexOption.IGNORE_CASE,
)
