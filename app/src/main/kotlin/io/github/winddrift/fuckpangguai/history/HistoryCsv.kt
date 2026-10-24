package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.ui.format.sessionPhaseLabel
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

internal fun formatHistoryTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun String.csvEscape(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }
