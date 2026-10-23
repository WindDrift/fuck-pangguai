package io.github.winddrift.fuckpangguai.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.water.SessionPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    repository: HistoryRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<HistoryRecord>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            records = repository.list()
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri == null || csv == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(("\uFEFF" + csv).toByteArray(Charsets.UTF_8))
                    } ?: error("无法打开输出文件")
                }.isSuccess
            }
            message = if (ok) "CSV 已导出" else "CSV 导出失败"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = "接水记录") },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text("共 ${records.size} 条", style = MiuixTheme.textStyles.body1)
            message?.let { Text(it, style = MiuixTheme.textStyles.footnote1) }

            Button(
                onClick = {
                    pendingCsv = records.toCsv()
                    exportLauncher.launch("pangguai-history.csv")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = records.isNotEmpty(),
            ) {
                Text("导出 CSV")
            }
            Button(
                onClick = {
                    scope.launch {
                        repository.clear()
                        refresh()
                        message = "记录已清空"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = records.isNotEmpty(),
            ) {
                Text("清空全部记录")
            }

            records.forEach { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "${record.deviceName.ifBlank { "饮水机" }}  ·  ${formatTime(record.updatedAt)}",
                            style = MiuixTheme.textStyles.title3,
                        )
                        Text("状态：${phaseLabel(record.phase)}", style = MiuixTheme.textStyles.body2)
                        if (record.amount.isNotBlank()) {
                            Text("用量：${record.amount}", style = MiuixTheme.textStyles.body2)
                        }
                        if (record.orderNo.isNotBlank()) {
                            Text("订单：${record.orderNo}", style = MiuixTheme.textStyles.footnote1)
                        }
                        if (record.error.isNotBlank()) {
                            Text(record.error, style = MiuixTheme.textStyles.footnote1)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    repository.delete(record.id)
                                    refresh()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("删除")
                        }
                    }
                }
            }

            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun List<HistoryRecord>.toCsv(): String {
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
                formatTime(record.updatedAt),
                record.deviceName,
                record.goodsId,
                record.skuId,
                record.amount,
                record.orderNo,
                record.payOrderNo,
                phaseLabel(record.phase),
                record.error,
                record.startedAt.toString(),
                record.updatedAt.toString(),
            ).joinToString(",") { it.csvEscape() },
        ).append('\n')
    }
    return builder.toString()
}

private fun String.csvEscape(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun phaseLabel(phase: String): String = when (phase) {
    SessionPhase.USING -> "接水中"
    SessionPhase.ENDING -> "正在结束"
    SessionPhase.SETTLING -> "正在结算"
    SessionPhase.PAID -> "已支付"
    SessionPhase.FINISHED_NO_CHARGE -> "已结束（无费用）"
    SessionPhase.PAY_UNKNOWN -> "支付待确认"
    SessionPhase.FAULT -> "设备异常"
    else -> phase
}
