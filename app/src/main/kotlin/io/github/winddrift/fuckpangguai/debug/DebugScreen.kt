package io.github.winddrift.fuckpangguai.debug

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.winddrift.fuckpangguai.BuildConfig
import io.github.winddrift.fuckpangguai.diagnostics.DiagnosticsController
import io.github.winddrift.fuckpangguai.diagnostics.model.CrashEntry
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagEvent
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagTimeRange
import io.github.winddrift.fuckpangguai.diagnostics.model.EventFilter
import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceFilter
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import io.github.winddrift.fuckpangguai.diagnostics.model.applyFilter
import io.github.winddrift.fuckpangguai.diagnostics.model.computeTraceStats
import io.github.winddrift.fuckpangguai.net.RequestLog
import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.scan.ScanController
import io.github.winddrift.fuckpangguai.scan.ScanState
import io.github.winddrift.fuckpangguai.storage.AppSettingsStore
import io.github.winddrift.fuckpangguai.ui.components.ChoiceChip
import io.github.winddrift.fuckpangguai.ui.components.ConfirmDialog
import io.github.winddrift.fuckpangguai.ui.components.EmptyState
import io.github.winddrift.fuckpangguai.ui.components.KeyValueRow
import io.github.winddrift.fuckpangguai.ui.components.LocalSnackbar
import io.github.winddrift.fuckpangguai.ui.components.PreferenceDivider
import io.github.winddrift.fuckpangguai.ui.components.PreferenceGroup
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DebugTab(val label: String) {
    Overview("概览"),
    Events("事件"),
    Traces("接水"),
    Settings("设置"),
}

private enum class ExportMode { Share, Save }

@Composable
fun DebugScreen(
    settingsStore: AppSettingsStore,
    scanController: ScanController,
    diagnosticsController: DiagnosticsController,
    onImportToken: (String) -> Unit,
    scrollBehavior: ScrollBehavior? = null,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val diagnostics = diagnosticsController.diagnostics

    val settings by diagnostics.settings.collectAsStateWithLifecycle()
    val events by diagnostics.events.collectAsStateWithLifecycle()
    val traces by diagnostics.traces.collectAsStateWithLifecycle()
    val crashes by diagnostics.recentCrashes.collectAsStateWithLifecycle()
    val eventFilter by diagnosticsController.eventFilter.collectAsStateWithLifecycle()
    val traceFilter by diagnosticsController.traceFilter.collectAsStateWithLifecycle()
    val storageStats by diagnosticsController.storageStats.collectAsStateWithLifecycle()
    val exporting by diagnosticsController.exporting.collectAsStateWithLifecycle()
    val scanState by scanController.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(DebugTab.Overview.ordinal) }
    var dryRun by remember { mutableStateOf(false) }
    var showExportWarning by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(ExportMode.Share) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        dryRun = settingsStore.isDryRun()
        diagnosticsController.refreshStats()
    }

    LaunchedEffect(tab) {
        if (scrollBehavior != null) {
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        if (tab == DebugTab.Overview.ordinal) diagnosticsController.refreshStats()
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val file = pendingSaveFile
        pendingSaveFile = null
        if (uri != null && file != null) {
            scope.launch {
                val ok = diagnosticsController.save(uri, file)
                snackbar.show(if (ok) "诊断包已保存" else "保存失败", isError = !ok)
            }
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        snackbar.show("$label 已复制")
    }

    fun runExport(mode: ExportMode) {
        exportMode = mode
        if (settings.enabled) {
            showExportWarning = true
        } else {
            snackbar.show("日志记录已关闭，只能导出已有日志")
            showExportWarning = true
        }
    }

    fun performExport() {
        scope.launch {
            val result = diagnosticsController.exportZip()
            result.onSuccess { file ->
                when (exportMode) {
                    ExportMode.Share -> {
                        runCatching { diagnosticsController.share(file) }
                            .onFailure { snackbar.show("分享失败：${it.message}", isError = true) }
                    }
                    ExportMode.Save -> {
                        pendingSaveFile = file
                        saveLauncher.launch(file.name)
                    }
                }
            }.onFailure {
                snackbar.show("导出失败：${it.message}", isError = true)
            }
        }
    }

    val nested = if (scrollBehavior != null) {
        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .then(nested),
    ) {
        TabRow(
            tabs = DebugTab.entries.map { it.label },
            selectedTabIndex = tab,
            onTabSelected = { tab = it },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        when (DebugTab.entries[tab]) {
            DebugTab.Overview -> OverviewTab(
                settingsEnabled = settings.enabled,
                stats = storageStats,
                traces = traces,
                crashes = crashes,
                scanState = scanState,
                dryRun = dryRun,
                exporting = exporting,
                onToggleLogging = { enabled ->
                    scope.launch { diagnosticsController.setLoggingEnabled(enabled) }
                },
                onRefreshStats = diagnosticsController::refreshStats,
                onExport = { mode -> runExport(mode) },
                onDryRunChange = { enabled ->
                    dryRun = enabled
                    scope.launch { settingsStore.setDryRun(enabled) }
                },
                onImportToken = onImportToken,
                onOpenTrace = { traceId ->
                    if (traceId.isNullOrBlank()) {
                        diagnosticsController.clearTraceFilters()
                    } else {
                        diagnosticsController.filterByTrace(traceId)
                    }
                    tab = DebugTab.Traces.ordinal
                },
                copyToClipboard = ::copyToClipboard,
            )

            DebugTab.Events -> EventsTab(
                events = events,
                filter = eventFilter,
                onFilterChange = diagnosticsController::updateEventFilter,
                onClearFilters = diagnosticsController::clearEventFilters,
                onLoadMore = diagnosticsController::loadMoreEvents,
                onOpenTrace = { traceId ->
                    diagnosticsController.filterByTrace(traceId)
                    tab = DebugTab.Traces.ordinal
                },
                copyToClipboard = ::copyToClipboard,
            )

            DebugTab.Traces -> TracesTab(
                traces = traces,
                filter = traceFilter,
                onFilterChange = diagnosticsController::updateTraceFilter,
                onClearFilters = diagnosticsController::clearTraceFilters,
            )

            DebugTab.Settings -> SettingsTab(
                settings = settings,
                dryRun = dryRun,
                exporting = exporting,
                onToggleLogging = { enabled ->
                    scope.launch { diagnosticsController.setLoggingEnabled(enabled) }
                },
                onRetentionChange = { days ->
                    scope.launch { diagnosticsController.setRetentionDays(days) }
                },
                onMaxMbChange = { mb ->
                    scope.launch { diagnosticsController.setMaxMb(mb) }
                },
                onExport = { mode -> runExport(mode) },
                onClear = { showClearConfirm = true },
                onDryRunChange = { enabled ->
                    dryRun = enabled
                    scope.launch { settingsStore.setDryRun(enabled) }
                },
                onImportToken = onImportToken,
            )
        }
    }

    ConfirmDialog(
        show = showExportWarning,
        title = "导出诊断日志？",
        summary = "诊断包可能包含设备名称、goodsId、订单号与脱敏响应片段。分享到公开渠道前请逐项检查并手动打码。",
        confirmText = "继续导出",
        onConfirm = ::performExport,
        onDismiss = { showExportWarning = false },
    )

    ConfirmDialog(
        show = showClearConfirm,
        title = "清空全部诊断日志？",
        summary = "将删除所有事件、接水链路与崩溃记录，无法恢复。",
        confirmText = "清空",
        destructive = true,
        onConfirm = {
            scope.launch {
                diagnosticsController.clearAll()
                snackbar.show("诊断日志已清空")
            }
        },
        onDismiss = { showClearConfirm = false },
    )
}

@Composable
private fun OverviewTab(
    settingsEnabled: Boolean,
    stats: io.github.winddrift.fuckpangguai.diagnostics.DiagStorageStats?,
    traces: List<WaterTrace>,
    crashes: List<CrashEntry>,
    scanState: ScanState,
    dryRun: Boolean,
    exporting: Boolean,
    onToggleLogging: (Boolean) -> Unit,
    onRefreshStats: () -> Unit,
    onExport: (ExportMode) -> Unit,
    onDryRunChange: (Boolean) -> Unit,
    onImportToken: (String) -> Unit,
    onOpenTrace: (String?) -> Unit,
    copyToClipboard: (String, String) -> Unit,
) {
    val snackbar = LocalSnackbar.current
    val requestEntries by RequestLog.entries.collectAsStateWithLifecycle()
    val requestCleared = { snackbar.show("请求日志已清空") }
    val currentTrace = traces.firstOrNull { it.outcome == TraceOutcome.Running }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "诊断日志仅保存在本机，不会自动上传；导出前请检查隐私内容。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }

        item {
            PreferenceGroup(title = "诊断日志") {
                SwitchPreference(
                    checked = settingsEnabled,
                    onCheckedChange = onToggleLogging,
                    title = "记录关键事件",
                    summary = "关闭后停止写入，已有日志仍会保留；重新开启后继续记录",
                )
                PreferenceDivider()
                BasicComponent(
                    title = "存储占用",
                    summary = stats?.let {
                        "${it.eventCount} 条事件 · ${it.traceCount} 个接水 · " +
                            "${it.crashCount} 次崩溃 · ${formatBytes(it.bytes)}"
                    } ?: "正在统计…",
                    onClick = onRefreshStats,
                )
                PreferenceDivider()
                BasicComponent(
                    title = "分享诊断包",
                    summary = if (exporting) "正在生成…" else "TXT + JSON 单 ZIP，包含保留期内全部日志",
                    enabled = !exporting,
                    onClick = { onExport(ExportMode.Share) },
                )
                PreferenceDivider()
                BasicComponent(
                    title = "保存诊断包",
                    summary = "选择本机目录保存 ZIP",
                    enabled = !exporting,
                    onClick = { onExport(ExportMode.Save) },
                )
            }
        }

        if (crashes.isNotEmpty()) {
            item {
                PreferenceGroup(title = "崩溃记录") {
                    crashes.take(3).forEachIndexed { index, crash ->
                        if (index > 0) PreferenceDivider()
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "${formatTraceTime(crash.at)} · ${crash.threadName}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                            Text(
                                text = crash.exception,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.error,
                            )
                            Text(
                                text = crash.stack.take(1200),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    text = "复制崩溃 JSON",
                                    onClick = {
                                        copyToClipboard(
                                            "崩溃 JSON",
                                            diagJson.encodeToString(CrashEntry.serializer(), crash),
                                        )
                                    },
                                )
                                if (crash.traceId.isNotBlank()) {
                                    TextButton(
                                        text = "查看接水",
                                        onClick = { onOpenTrace(crash.traceId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            PreferenceGroup(title = "当前接水") {
                if (currentTrace == null) {
                    BasicComponent(
                        title = "暂无进行中的接水",
                        summary = "完成一次扫码接水后，这里显示当前 trace 的阶段进度。",
                    )
                } else {
                    BasicComponent(
                        title = currentTrace.deviceName.ifBlank { "饮水机" },
                        summary = "已完成 ${currentTrace.stages.count { it.reached }} / 7 段 · " +
                            "已持续 ${formatDuration(currentTrace.totalDurationMs)} · 点此查看",
                        onClick = { onOpenTrace(currentTrace.traceId) },
                    )
                }
            }
        }

        item {
            PreferenceGroup(title = "调试") {
                SwitchPreference(
                    checked = dryRun,
                    onCheckedChange = onDryRunChange,
                    title = "演练模式",
                    summary = "只展示请求，不真实调用 water/unlock",
                )
                if (BuildConfig.DEBUG) {
                    PreferenceDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        DebugTokenImportSection(onToken = onImportToken)
                    }
                }
            }
        }

        item {
            val ready = scanState as? ScanState.DeviceReady
            PreferenceGroup(title = "设备探测") {
                if (ready == null) {
                    BasicComponent(
                        title = "暂无设备",
                        summary = "先在扫码页识别一台设备，再回到这里查看详情。",
                    )
                } else {
                    val detail = ready.device.detail
                    BasicComponent(
                        title = detail.name.ifBlank { "饮水机" },
                        summary = "类别 ${ready.device.scan.categoryCode} · goodsId ${ready.device.scan.id}",
                    )
                    PreferenceDivider()
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        KeyValueRow("goodsId", ready.device.scan.id)
                        KeyValueRow("isBluetooth", detail.isBluetooth.toString())
                        KeyValueRow("deviceErrorCode", detail.deviceErrorCode ?: "无")
                        KeyValueRow("payment", detail.payment)
                        KeyValueRow("payModel", detail.payModel.toString())
                        KeyValueRow("tradeMain", detail.tradeMain.toString())
                        KeyValueRow("orgId", detail.orgId)
                        KeyValueRow("shopId", detail.shopId)
                        KeyValueRow("payTypes", detail.payTypes.joinToString())
                        KeyValueRow("extAttr", detail.extAttr)
                    }
                    PreferenceDivider()
                    BasicComponent(
                        title = "复制设备 JSON",
                        summary = "包含当前设备的完整探测字段",
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.Copy,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        },
                        onClick = {
                            copyToClipboard(
                                "设备 JSON",
                                Json.encodeToString(GoodsDetail.serializer(), detail),
                            )
                        },
                    )
                }
            }
        }

        item {
            PreferenceGroup(title = "最近请求") {
                if (requestEntries.isEmpty()) {
                    BasicComponent(
                        title = "暂无请求",
                        summary = "Debug/诊断构建会保留最近 100 条请求与 cURL 视图。",
                    )
                } else {
                    requestEntries.take(10).forEachIndexed { index, entry ->
                        if (index > 0) PreferenceDivider()
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = "${entry.method} ${entry.statusCode ?: "-"} · ${entry.durationMs}ms",
                                style = MiuixTheme.textStyles.body2,
                            )
                            Text(
                                text = entry.url,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                            entry.error?.let { error ->
                                Text(
                                    text = "error: $error",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    PreferenceDivider()
                    val latest = requestEntries.firstOrNull()
                    BasicComponent(
                        title = "复制最近请求 cURL",
                        enabled = latest != null,
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.Copy,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        },
                        onClick = latest?.let { entry ->
                            { copyToClipboard("cURL", entry.toCurl()) }
                        },
                    )
                    PreferenceDivider()
                    BasicComponent(
                        title = "清空请求日志",
                        enabled = requestEntries.isNotEmpty(),
                        onClick = {
                            RequestLog.clear()
                            requestCleared()
                        },
                    )
                }
            }
        }

        if (BuildConfig.DIAGNOSTIC_LOGGING && !BuildConfig.DEBUG) {
            item {
                Text(
                    text = "当前为诊断构建：记录 Debug 级事件与更大的响应预览，使用正式签名与 R8。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}

@Composable
private fun EventsTab(
    events: List<DiagEvent>,
    filter: EventFilter,
    onFilterChange: ((EventFilter) -> EventFilter) -> Unit,
    onClearFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenTrace: (String) -> Unit,
    copyToClipboard: (String, String) -> Unit,
) {
    var showFilters by remember { mutableStateOf(false) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    val filtered = remember(events, filter, now / 30_000) {
        events.applyFilter(filter, System.currentTimeMillis())
    }
    val matchedTotal = remember(events, filter, now / 30_000) {
        events.applyFilter(filter.copy(limit = Int.MAX_VALUE), System.currentTimeMillis()).size
    }
    val chips = eventChips(filter, onFilterChange)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextField(
                    value = filter.query,
                    onValueChange = { query ->
                        onFilterChange { it.copy(query = query, limit = EventFilter.DEFAULT_LIMIT) }
                    },
                    modifier = Modifier.weight(1f),
                    label = "搜索事件",
                    singleLine = true,
                )
                if (filter.query.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onFilterChange { it.copy(query = "", limit = EventFilter.DEFAULT_LIMIT) }
                        },
                        backgroundColor = Color.Transparent,
                    ) {
                        Icon(imageVector = MiuixIcons.Close, contentDescription = "清除搜索")
                    }
                }
                IconButton(
                    onClick = { showFilters = true },
                    backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                ) {
                    Icon(imageVector = MiuixIcons.Filter, contentDescription = "筛选")
                }
            }
            if (chips.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chips, key = { it.first }) { (label, remove) ->
                        ChoiceChip(label = label, selected = true, onClick = remove)
                    }
                    item {
                        TextButton(text = "清除筛选", onClick = onClearFilters)
                    }
                }
            }
            Text(
                text = "已显示 ${filtered.size} / 匹配 $matchedTotal（共 ${events.size}）",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        icon = MiuixIcons.Info,
                        title = "没有匹配的事件",
                        summary = "调整级别、类别或时间范围后再试。",
                    )
                }
            } else {
                items(filtered, key = { it.id }) { event ->
                    DiagEventCard(
                        event = event,
                        expanded = expandedId == event.id,
                        onToggle = {
                            expandedId = if (expandedId == event.id) null else event.id
                        },
                        onCopy = {
                            copyToClipboard(
                                "事件 JSON",
                                diagJson.encodeToString(DiagEvent.serializer(), event),
                            )
                        },
                        onOpenTrace = event.traceId.takeIf { it.isNotBlank() }?.let { traceId ->
                            { onOpenTrace(traceId) }
                        },
                    )
                }
                if (filtered.size >= filter.limit && filtered.size < matchedTotal) {
                    item {
                        Button(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("加载更多")
                        }
                    }
                }
            }
        }
    }

    EventFilterSheet(
        show = showFilters,
        filter = filter,
        onFilterChange = onFilterChange,
        onDismiss = { showFilters = false },
    )
}

@Composable
private fun DiagEventCard(
    event: DiagEvent,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onOpenTrace: (() -> Unit)?,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        onClick = onToggle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = event.level.name,
                    style = MiuixTheme.textStyles.footnote1,
                    color = levelColor(event.level),
                )
                Text(
                    text = event.flow.label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                Text(
                    text = event.name,
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body2,
                )
                Text(
                    text = formatDiagTime(event.at),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
            }
            if (event.message.isNotBlank()) {
                Text(
                    text = event.message,
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (event.level == DiagLevel.Error) colors.error else colors.onSurface,
                )
            }
            if (event.traceId.isNotBlank()) {
                Text(
                    text = "trace ${shortId(event.traceId)}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.primary,
                )
            }
            if (expanded) {
                if (event.fields.isNotEmpty()) {
                    PreferenceDivider()
                    event.fields.forEach { (key, value) ->
                        KeyValueRow(key, value)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(text = "复制 JSON", onClick = onCopy)
                    if (onOpenTrace != null) {
                        TextButton(text = "查看接水", onClick = onOpenTrace)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventFilterSheet(
    show: Boolean,
    filter: EventFilter,
    onFilterChange: ((EventFilter) -> EventFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = "筛选事件",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("级别", style = MiuixTheme.textStyles.title3)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DiagLevel.entries) { level ->
                    ChoiceChip(
                        label = level.name,
                        selected = level in filter.levels,
                        onClick = {
                            onFilterChange {
                                it.copy(
                                    levels = if (level in it.levels) {
                                        it.levels - level
                                    } else {
                                        it.levels + level
                                    },
                                    limit = EventFilter.DEFAULT_LIMIT,
                                )
                            }
                        },
                    )
                }
            }
            Text("类别", style = MiuixTheme.textStyles.title3)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DiagFlow.entries) { flow ->
                    ChoiceChip(
                        label = flow.label,
                        selected = flow in filter.flows,
                        onClick = {
                            onFilterChange {
                                it.copy(
                                    flows = if (flow in it.flows) {
                                        it.flows - flow
                                    } else {
                                        it.flows + flow
                                    },
                                    limit = EventFilter.DEFAULT_LIMIT,
                                )
                            }
                        },
                    )
                }
            }
            Text("时间", style = MiuixTheme.textStyles.title3)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DiagTimeRange.entries) { range ->
                    ChoiceChip(
                        label = range.label,
                        selected = filter.range == range,
                        onClick = {
                            onFilterChange { it.copy(range = range, limit = EventFilter.DEFAULT_LIMIT) }
                        },
                    )
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("完成")
            }
        }
    }
}

@Composable
private fun TracesTab(
    traces: List<WaterTrace>,
    filter: TraceFilter,
    onFilterChange: ((TraceFilter) -> TraceFilter) -> Unit,
    onClearFilters: () -> Unit,
) {
    var showFilters by remember { mutableStateOf(false) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    val filtered = remember(traces, filter, now / 30_000) {
        traces.applyFilter(filter, System.currentTimeMillis())
    }
    val stats = remember(filtered) { computeTraceStats(filtered) }
    val chips = traceChips(filter, onFilterChange)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextField(
                    value = filter.deviceQuery,
                    onValueChange = { query -> onFilterChange { it.copy(deviceQuery = query) } },
                    modifier = Modifier.weight(1f),
                    label = "搜索设备 / goodsId / trace",
                    singleLine = true,
                )
                if (filter.deviceQuery.isNotBlank()) {
                    IconButton(
                        onClick = { onFilterChange { it.copy(deviceQuery = "") } },
                        backgroundColor = Color.Transparent,
                    ) {
                        Icon(imageVector = MiuixIcons.Close, contentDescription = "清除搜索")
                    }
                }
                IconButton(
                    onClick = { showFilters = true },
                    backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                ) {
                    Icon(imageVector = MiuixIcons.Filter, contentDescription = "筛选")
                }
            }
            if (chips.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chips, key = { it.first }) { (label, remove) ->
                        ChoiceChip(label = label, selected = true, onClick = remove)
                    }
                    item {
                        TextButton(text = "清除筛选", onClick = onClearFilters)
                    }
                }
            }
            Text(
                text = "共 ${filtered.size} 个 trace · 平均 ${formatDuration(stats.avgTotalDurationMs)}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                TraceStatsCard(stats)
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        icon = MiuixIcons.Info,
                        title = "没有匹配的接水",
                        summary = "完成一次接水后，这里会显示 7 段链路与耗时。",
                    )
                }
            } else {
                items(filtered, key = { it.traceId }) { trace ->
                    TraceCard(
                        trace = trace,
                        expanded = expandedId == trace.traceId,
                        onToggle = {
                            expandedId = if (expandedId == trace.traceId) null else trace.traceId
                        },
                    )
                }
            }
        }
    }

    OverlayBottomSheet(
        show = showFilters,
        title = "筛选接水",
        onDismissRequest = { showFilters = false },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("结果", style = MiuixTheme.textStyles.title3)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TraceOutcome.entries) { outcome ->
                    ChoiceChip(
                        label = outcome.label,
                        selected = outcome in filter.outcomes,
                        onClick = {
                            onFilterChange {
                                it.copy(
                                    outcomes = if (outcome in it.outcomes) {
                                        it.outcomes - outcome
                                    } else {
                                        it.outcomes + outcome
                                    },
                                )
                            }
                        },
                    )
                }
            }
            Text("时间", style = MiuixTheme.textStyles.title3)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DiagTimeRange.entries) { range ->
                    ChoiceChip(
                        label = range.label,
                        selected = filter.range == range,
                        onClick = { onFilterChange { it.copy(range = range) } },
                    )
                }
            }
            Button(
                onClick = { showFilters = false },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("完成")
            }
        }
    }
}

@Composable
private fun TraceStatsCard(stats: io.github.winddrift.fuckpangguai.diagnostics.model.TraceStats) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("全链路统计", style = MiuixTheme.textStyles.title3)
            Text(
                text = "总计 ${stats.total} · 成功 ${stats.success} · 失败 ${stats.failed} · " +
                    "放弃 ${stats.abandoned} · 进行中 ${stats.running} · 中断 ${stats.interrupted}",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
            Text(
                text = "总耗时平均 ${formatDuration(stats.avgTotalDurationMs)} · " +
                    "最大 ${formatDuration(stats.maxTotalDurationMs)}",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
            PreferenceDivider()
            TraceStage.entries.forEach { stage ->
                val stageStats = stats.stage(stage)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stage.label, style = MiuixTheme.textStyles.body2)
                        Text(
                            "成功 ${stageStats.success} · 失败 ${stageStats.failed} · " +
                                "放弃 ${stageStats.abandoned} · 进行中 ${stageStats.running}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.onSurfaceSecondary,
                        )
                    }
                    Text(
                        text = "平均 ${formatDuration(stageStats.avgDurationMs)} · " +
                            "最大 ${formatDuration(stageStats.maxDurationMs)} · " +
                            "参与 ${stageStats.participants} · " +
                            "平均尝试 ${String.format(Locale.US, "%.1f", stageStats.avgAttempts)}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TraceCard(
    trace: WaterTrace,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        onClick = onToggle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = trace.deviceName.ifBlank { "饮水机" },
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body1,
                )
                Text(
                    text = trace.outcome.label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = outcomeColor(trace.outcome),
                )
            }
            Text(
                text = "${formatTraceTime(trace.startedAt)} · 总耗时 ${formatDuration(trace.totalDurationMs)}" +
                    " · ${trace.source.ifBlank { "未知来源" }}",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
            Text(
                text = "trace ${shortId(trace.traceId)} · goodsId ${trace.goodsId.ifBlank { "-" }}",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
            if (trace.error.isNotBlank()) {
                Text(
                    text = trace.error,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                )
            }
            if (expanded) {
                PreferenceDivider()
                Text(
                    text = "用水时长 ${formatDuration(trace.waterDurationMs)}" +
                        " · 结束操作 ${formatDuration(trace.endOperationMs)}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                TraceStage.entries.forEach { stage ->
                    val record = trace.stage(stage)
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stage.label, style = MiuixTheme.textStyles.body2)
                            Text(
                                text = record?.let {
                                    "${it.outcome.label} · ${formatDuration(it.durationMs)} · " +
                                        "${it.attempts} 次"
                                } ?: "未到达",
                                style = MiuixTheme.textStyles.footnote1,
                                color = record?.let { outcomeColor(it.outcome) }
                                    ?: colors.onSurfaceSecondary,
                            )
                        }
                        if (record != null && record.error.isNotBlank()) {
                            Text(
                                text = record.error,
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.error,
                            )
                        }
                        if (record != null && record.fields.isNotEmpty()) {
                            Text(
                                text = record.fields.entries.joinToString(" · ") {
                                    "${it.key}=${it.value}"
                                },
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    settings: io.github.winddrift.fuckpangguai.diagnostics.DiagSettings,
    dryRun: Boolean,
    exporting: Boolean,
    onToggleLogging: (Boolean) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onMaxMbChange: (Int) -> Unit,
    onExport: (ExportMode) -> Unit,
    onClear: () -> Unit,
    onDryRunChange: (Boolean) -> Unit,
    onImportToken: (String) -> Unit,
) {
    var showDays by remember { mutableStateOf(false) }
    var showMaxMb by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PreferenceGroup(title = "记录") {
                SwitchPreference(
                    checked = settings.enabled,
                    onCheckedChange = onToggleLogging,
                    title = "记录关键事件",
                    summary = "Release/诊断构建默认开启；关闭后停止写入",
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "保留天数",
                    summary = "${settings.retentionDays} 天（1-30）",
                    onClick = { showDays = true },
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "存储上限",
                    summary = "${settings.maxMb} MB（1-100）",
                    onClick = { showMaxMb = true },
                )
            }
        }

        item {
            PreferenceGroup(title = "导出与清理") {
                BasicComponent(
                    title = "分享诊断包",
                    summary = "TXT + JSON 单 ZIP；导出前会再次提示隐私风险",
                    enabled = !exporting,
                    onClick = { onExport(ExportMode.Share) },
                )
                PreferenceDivider()
                BasicComponent(
                    title = "保存诊断包",
                    summary = "通过系统文件选择器保存 ZIP",
                    enabled = !exporting,
                    onClick = { onExport(ExportMode.Save) },
                )
                PreferenceDivider()
                BasicComponent(
                    title = "清空全部诊断日志",
                    summary = "删除事件、接水链路与崩溃记录，无法恢复",
                    onClick = onClear,
                )
            }
        }

        item {
            PreferenceGroup(title = "调试") {
                SwitchPreference(
                    checked = dryRun,
                    onCheckedChange = onDryRunChange,
                    title = "演练模式",
                    summary = "只展示请求，不真实调用 water/unlock",
                )
                if (BuildConfig.DEBUG) {
                    PreferenceDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        DebugTokenImportSection(onToken = onImportToken)
                    }
                }
                if (BuildConfig.DEBUG || BuildConfig.DIAGNOSTIC_LOGGING) {
                    PreferenceDivider()
                    BasicComponent(
                        title = "写入测试崩溃",
                        summary = "立即抛出一个未处理异常，用于验收崩溃记录；应用会退出",
                        onClick = { throw IllegalStateException("诊断测试崩溃") },
                    )
                }
            }
        }

        item {
            Text(
                text = "保留策略先按天数删除旧文件，再按总大小删除最旧文件；当天文件不会被大小清理删除。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }

    NumberSettingSheet(
        show = showDays,
        title = "保留天数",
        value = settings.retentionDays,
        range = 1..30,
        unit = "天",
        onDismiss = { showDays = false },
        onSave = onRetentionChange,
    )
    NumberSettingSheet(
        show = showMaxMb,
        title = "存储上限",
        value = settings.maxMb,
        range = 1..100,
        unit = "MB",
        onDismiss = { showMaxMb = false },
        onSave = onMaxMbChange,
    )
}

@Composable
private fun NumberSettingSheet(
    show: Boolean,
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var input by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(show, value) {
        if (show) input = value.toString()
    }
    val parsed = input.toIntOrNull()
    val valid = parsed != null && parsed in range

    OverlayBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(
                value = input,
                onValueChange = { text -> input = text.filter { it.isDigit() }.take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = "$title（${range.first}-${range.last} $unit）",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Button(
                onClick = {
                    parsed?.let(onSave)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = valid,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("保存")
            }
        }
    }
}

private val diagJson = Json { encodeDefaults = true }

private fun formatDiagTime(at: Long): String =
    TIME_FORMAT.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

private fun formatTraceTime(at: Long): String =
    TRACE_TIME_FORMAT.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

private fun formatDuration(ms: Long): String = when {
    ms <= 0L -> "0 ms"
    ms < 1_000L -> "$ms ms"
    ms < 60_000L -> String.format(Locale.US, "%.1f s", ms / 1_000.0)
    else -> {
        val minutes = ms / 60_000L
        val seconds = (ms % 60_000L) / 1_000L
        "$minutes 分 $seconds 秒"
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun shortId(id: String): String =
    if (id.length <= 18) id else "…" + id.takeLast(18)

@Composable
private fun levelColor(level: DiagLevel): Color = when (level) {
    DiagLevel.Error -> MiuixTheme.colorScheme.error
    DiagLevel.Warn -> MiuixTheme.colorScheme.error.copy(alpha = 0.8f)
    DiagLevel.Info -> MiuixTheme.colorScheme.primary
    DiagLevel.Debug -> MiuixTheme.colorScheme.onSurfaceSecondary
}

@Composable
private fun outcomeColor(outcome: TraceOutcome): Color = when (outcome) {
    TraceOutcome.Success -> MiuixTheme.colorScheme.primary
    TraceOutcome.Failed -> MiuixTheme.colorScheme.error
    TraceOutcome.Abandoned -> MiuixTheme.colorScheme.onSurfaceSecondary
    TraceOutcome.Running -> MiuixTheme.colorScheme.onSurface
    TraceOutcome.Interrupted -> MiuixTheme.colorScheme.error
}

@Composable
private fun outcomeColor(outcome: StageOutcome): Color = when (outcome) {
    StageOutcome.Success -> MiuixTheme.colorScheme.primary
    StageOutcome.Failed -> MiuixTheme.colorScheme.error
    StageOutcome.Abandoned -> MiuixTheme.colorScheme.onSurfaceSecondary
    StageOutcome.Running -> MiuixTheme.colorScheme.onSurface
}

private fun eventChips(
    filter: EventFilter,
    onFilterChange: ((EventFilter) -> EventFilter) -> Unit,
): List<Pair<String, () -> Unit>> = buildList {
    filter.levels.forEach { level ->
        add("级别 ${level.name}" to {
            onFilterChange { it.copy(levels = it.levels - level) }
        })
    }
    filter.flows.forEach { flow ->
        add("类别 ${flow.label}" to {
            onFilterChange { it.copy(flows = it.flows - flow) }
        })
    }
    if (filter.range != DiagTimeRange.All) {
        add("时间 ${filter.range.label}" to {
            onFilterChange { it.copy(range = DiagTimeRange.All) }
        })
    }
}

private fun traceChips(
    filter: TraceFilter,
    onFilterChange: ((TraceFilter) -> TraceFilter) -> Unit,
): List<Pair<String, () -> Unit>> = buildList {
    filter.outcomes.forEach { outcome ->
        add("结果 ${outcome.label}" to {
            onFilterChange { it.copy(outcomes = it.outcomes - outcome) }
        })
    }
    if (filter.range != DiagTimeRange.All) {
        add("时间 ${filter.range.label}" to {
            onFilterChange { it.copy(range = DiagTimeRange.All) }
        })
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val TRACE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
