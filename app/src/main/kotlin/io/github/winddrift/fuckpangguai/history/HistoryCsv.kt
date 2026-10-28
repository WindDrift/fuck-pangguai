package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.ui.format.sessionPhaseLabel
import io.github.winddrift.fuckpangguai.water.SessionPhase
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun List<HistoryRecord>.toCsv(): String {
    val header = listOf(
        "时间",
        "设备",
        "goodsId",
        "skuId",
        "用量",
        "订单号",
        "结算订单号",
        "状态",
        "错误",
        "开始时间",
        "更新时间",
    )
    val builder = StringBuilder()
    builder.append(header.joinToString(",") { it.csvEscape() }).append('\n')
    forEach { record ->
        builder.append(
            listOf(
                formatHistoryTime(record.updatedAt),
                record.deviceName,
                record.goodsId,
                record.skuId,
                record.amount,
                record.orderNo,
                record.payOrderNo,
                sessionPhaseLabel(record.phase),
                record.error,
                record.startedAt.toString(),
                record.updatedAt.toString(),
            ).joinToString(",") { it.csvEscape() },
        ).append('\n')
    }
    return builder.toString()
}

internal data class ImportHistoryRow(
    val deviceName: String,
    val goodsId: String,
    val skuId: String,
    val amount: String,
    val orderNo: String,
    val payOrderNo: String,
    val phase: String,
    val error: String,
    val startedAt: Long,
    val updatedAt: Long,
)

internal sealed interface HistoryCsvParseResult {
    data class Success(
        val rows: List<ImportHistoryRow>,
        val invalidRows: Int,
    ) : HistoryCsvParseResult

    data object InvalidFormat : HistoryCsvParseResult
}

data class HistoryImportResult(
    val inserted: Int = 0,
    val duplicates: Int = 0,
    val invalid: Int = 0,
    val formatError: Boolean = false,
)

internal fun parseHistoryCsv(content: String): HistoryCsvParseResult {
    val rows = parseCsvRows(content)
    if (rows.isEmpty()) return HistoryCsvParseResult.InvalidFormat

    val header = rows.first().map { it.trim() }
    val columnIndex = header.withIndex()
        .filter { it.value.isNotBlank() }
        .associate { (index, name) -> name to index }
    if (!columnIndex.keys.containsAll(REQUIRED_HEADERS)) {
        return HistoryCsvParseResult.InvalidFormat
    }

    var invalidRows = 0
    val imported = rows.drop(1).mapNotNull { cells ->
        fun cell(name: String): String =
            columnIndex[name]?.let { cells.getOrNull(it) }?.trim().orEmpty()

        val orderNo = cell("订单号")
        val updatedAt = cell("更新时间").toEpochMillisOrNull()
            ?: cell("开始时间").toEpochMillisOrNull()
            ?: parseHistoryTime(cell("时间"))
        val startedAt = cell("开始时间").toEpochMillisOrNull() ?: updatedAt
        if (orderNo.isBlank() || updatedAt == null || startedAt == null) {
            invalidRows++
            return@mapNotNull null
        }
        val amount = cell("用量")
        ImportHistoryRow(
            deviceName = cell("设备"),
            goodsId = cell("goodsId"),
            skuId = cell("skuId"),
            amount = amount,
            orderNo = orderNo,
            payOrderNo = cell("结算订单号"),
            phase = resolvePhase(cell("状态"), amount),
            error = cell("错误"),
            startedAt = startedAt,
            updatedAt = updatedAt,
        )
    }
    return HistoryCsvParseResult.Success(rows = imported, invalidRows = invalidRows)
}

internal fun formatHistoryTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private val PHASE_BY_LABEL = mapOf(
    "接水中" to SessionPhase.USING,
    "正在结束" to SessionPhase.ENDING,
    "正在结算" to SessionPhase.SETTLING,
    "已支付" to SessionPhase.PAID,
    "已结束（无费用）" to SessionPhase.FINISHED_NO_CHARGE,
    "支付状态待确认" to SessionPhase.PAY_UNKNOWN,
    "设备异常" to SessionPhase.FAULT,
)

private val KNOWN_PHASES = PHASE_BY_LABEL.values.toSet()

private val REQUIRED_HEADERS = setOf(
    "时间",
    "设备",
    "用量",
    "订单号",
    "状态",
    "开始时间",
    "更新时间",
)

private fun resolvePhase(label: String, amount: String): String {
    val value = label.trim()
    PHASE_BY_LABEL[value]?.let { return it }
    if (value in KNOWN_PHASES) return value
    val paid = amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true
    return if (paid) SessionPhase.PAID else SessionPhase.FINISHED_NO_CHARGE
}

private fun String.toEpochMillisOrNull(): Long? =
    trim().takeIf { it.isNotEmpty() }
        ?.toLongOrNull()
        ?.takeIf { it > 0 }

private fun parseHistoryTime(value: String): Long? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(trimmed)?.time
    }.getOrNull()
}

private fun parseCsvRows(content: String): List<List<String>> {
    val normalized = content.removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    if (normalized.isBlank()) return emptyList()

    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var index = 0
    while (index < normalized.length) {
        val char = normalized[index]
        when {
            inQuotes -> when {
                char == '"' && index + 1 < normalized.length && normalized[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }

                char == '"' -> inQuotes = false
                else -> field.append(char)
            }

            char == '"' -> inQuotes = true
            char == ',' -> {
                row += field.toString()
                field.clear()
            }

            char == '\n' -> {
                row += field.toString()
                field.clear()
                rows += row
                row = mutableListOf()
            }

            else -> field.append(char)
        }
        index++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) {
        row += field.toString()
        rows += row
    }
    return rows.filter { cells -> cells.any { it.isNotBlank() } }
}

private fun String.csvEscape(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }
