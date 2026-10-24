package io.github.winddrift.fuckpangguai.history

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.EmptyState
import io.github.winddrift.fuckpangguai.ui.components.KeyValueRow
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.components.PhaseBadge
import io.github.winddrift.fuckpangguai.ui.components.SectionCard
import io.github.winddrift.fuckpangguai.ui.format.sessionPhaseLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    repository: HistoryRepository,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    var records by remember { mutableStateOf<List<HistoryRecord>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var selectedRecord by remember { mutableStateOf<HistoryRecord?>(null) }
    var pendingDelete by remember { mutableStateOf<HistoryRecord?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            refreshing = true
            runCatching { repository.list() }
                .onSuccess { records = it }
                .onFailure { snackbar.show("读取记录失败", isError = true) }
            refreshing = false
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
            snackbar.show(
                if (ok) "CSV 已导出" else "CSV 导出失败",
                isError = !ok,
            )
        }
    }

    BackHandler(enabled = selectedRecord != null) { selectedRecord = null }

    PullToRefresh(
        isRefreshing = refreshing,
        onRefresh = ::refresh,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "共 ${records.size} 条",
                        style = MiuixTheme.textStyles.body1,
                    )
                    Row {
                        TextButton(
                            text = "导出 CSV",
                            onClick = {
                                pendingCsv = records.toCsv()
                                exportLauncher.launch("pangguai-history.csv")
                            },
                            enabled = records.isNotEmpty(),
                        )
                        TextButton(
                            text = "清空全部",
                            onClick = { showClearConfirm = true },
                            enabled = records.isNotEmpty(),
                            colors = ButtonDefaults.textButtonColors(textColor = colors.error),
                        )
                    }
                }
            }

            if (records.isEmpty()) {
                item {
                    EmptyState(
                        icon = MiuixIcons.Recent,
                        title = "还没有接水记录",
                        summary = "扫码接水后，这里会显示记录；下拉可刷新。",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            items(records, key = { it.id }) { record ->
                HistoryCard(
                    record = record,
                    onClick = { selectedRecord = record },
                    onDelete = { pendingDelete = record },
                )
            }
        }
    }

    selectedRecord?.let { record ->
        OverlayBottomSheet(
            show = true,
            title = "记录详情",
            onDismissRequest = { selectedRecord = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionCard(title = record.deviceName.ifBlank { "饮水机" }) {
                    KeyValueRow("时间", formatTime(record.updatedAt))
                    KeyValueRow("状态", sessionPhaseLabel(record.phase))
                    if (record.amount.isNotBlank()) {
                        KeyValueRow("用量", record.amount)
                    }
                    if (record.orderNo.isNotBlank()) {
                        KeyValueRow("订单", record.orderNo)
                    }
                    if (record.payOrderNo.isNotBlank()) {
                        KeyValueRow("结算订单", record.payOrderNo)
                    }
                    KeyValueRow("goodsId", record.goodsId)
                    KeyValueRow("skuId", record.skuId)
                    if (record.error.isNotBlank()) {
                        Text(
                            text = record.error,
                            style = MiuixTheme.textStyles.body2,
                            color = colors.error,
                        )
                    }
                }
                Button(
                    onClick = {
                        pendingDelete = record
                        selectedRecord = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(
                        color = colors.error,
                        contentColor = colors.onError,
                    ),
                ) {
                    Text("删除记录")
                }
            }
        }
    }

    ConfirmDialog(
        show = pendingDelete != null,
        title = "删除这条记录？",
        summary = pendingDelete?.let { "${it.deviceName.ifBlank { "饮水机" }} · ${formatTime(it.updatedAt)}" },
        confirmText = "删除",
        destructive = true,
        onConfirm = {
            val record = pendingDelete ?: return@ConfirmDialog
            scope.launch {
                runCatching { repository.delete(record.id) }
                    .onSuccess {
                        refresh()
                        snackbar.show("记录已删除")
                    }
                    .onFailure { snackbar.show("删除失败", isError = true) }
            }
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )

    ConfirmDialog(
        show = showClearConfirm,
        title = "清空全部记录？",
        summary = "本机保存的 ${records.size} 条接水记录将被删除，此操作无法撤销。",
        confirmText = "清空",
        destructive = true,
        onConfirm = {
            scope.launch {
                runCatching { repository.clear() }
                    .onSuccess {
                        refresh()
                        snackbar.show("记录已清空")
                    }
                    .onFailure { snackbar.show("清空失败", isError = true) }
            }
            showClearConfirm = false
        },
        onDismiss = { showClearConfirm = false },
    )
}

@Composable
private fun HistoryCard(
    record: HistoryRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = record.deviceName.ifBlank { "饮水机" },
                    style = MiuixTheme.textStyles.body1,
                )
                Text(
                    text = formatTime(record.updatedAt),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PhaseBadge(record.phase)
                    if (record.amount.isNotBlank()) {
                        Text(
                            text = record.amount,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
                if (record.orderNo.isNotBlank()) {
                    Text(
                        text = "订单 ${record.orderNo}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
                if (record.error.isNotBlank()) {
                    Text(
                        text = record.error,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = "删除记录",
                )
            }
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
                sessionPhaseLabel(record.phase),
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
