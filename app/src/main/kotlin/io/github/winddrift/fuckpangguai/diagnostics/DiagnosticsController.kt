package io.github.winddrift.fuckpangguai.diagnostics

import android.content.Context
import android.net.Uri
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagTimeRange
import io.github.winddrift.fuckpangguai.diagnostics.model.EventFilter
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceFilter
import io.github.winddrift.fuckpangguai.diagnostics.model.applyFilter
import io.github.winddrift.fuckpangguai.diagnostics.model.computeTraceStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DiagnosticsController(
    context: Context,
    val diagnostics: Diagnostics,
    private val settingsStore: DiagSettingsStore,
    private val scope: CoroutineScope,
) {
    private val exportAndroid = DiagExportAndroid(context)

    private val _eventFilter = MutableStateFlow(EventFilter())
    val eventFilter = _eventFilter.asStateFlow()

    private val _traceFilter = MutableStateFlow(TraceFilter())
    val traceFilter = _traceFilter.asStateFlow()

    private val _storageStats = MutableStateFlow<DiagStorageStats?>(null)
    val storageStats = _storageStats.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting = _exporting.asStateFlow()

    fun updateEventFilter(transform: (EventFilter) -> EventFilter) {
        _eventFilter.update(transform)
    }

    fun clearEventFilters() {
        _eventFilter.update { EventFilter(limit = it.limit) }
    }

    fun loadMoreEvents() {
        _eventFilter.update { it.copy(limit = it.limit + EventFilter.PAGE_SIZE) }
    }

    fun updateTraceFilter(transform: (TraceFilter) -> TraceFilter) {
        _traceFilter.update(transform)
    }

    fun clearTraceFilters() {
        _traceFilter.value = TraceFilter()
    }

    fun filterByTrace(traceId: String) {
        _traceFilter.value = TraceFilter(deviceQuery = traceId)
    }

    suspend fun setLoggingEnabled(enabled: Boolean) = settingsStore.setEnabled(enabled)

    suspend fun setRetentionDays(days: Int) {
        settingsStore.setRetentionDays(days)
        refreshStats()
    }

    suspend fun setMaxMb(maxMb: Int) {
        settingsStore.setMaxMb(maxMb)
        refreshStats()
    }

    fun refreshStats() {
        scope.launch {
            _storageStats.value = runCatching { diagnostics.storageStats() }.getOrNull()
        }
    }

    suspend fun clearAll() {
        diagnostics.clear()
        refreshStats()
    }

    suspend fun exportZip(): Result<File> {
        _exporting.value = true
        return try {
            runCatching {
                val snapshot = diagnostics.snapshot()
                val now = System.currentTimeMillis()
                val filteredTraces = snapshot.traces.applyFilter(traceFilter.value, now)
                val input = DiagExportInput(
                    generatedAt = now,
                    appVersion = diagnostics.appVersionName,
                    buildType = diagnostics.appBuildType,
                    settings = diagnostics.settings.value,
                    events = snapshot.events,
                    traces = snapshot.traces,
                    crashes = snapshot.crashes,
                    stats = computeTraceStats(snapshot.traces),
                    filterDescription = filterDescription(),
                    filteredStats = computeTraceStats(filteredTraces),
                )
                withContext(Dispatchers.IO) { exportAndroid.write(input) }
            }
        } finally {
            _exporting.value = false
        }
    }

    fun share(file: File) {
        exportAndroid.share(file)
    }

    suspend fun save(uri: Uri, file: File): Boolean = withContext(Dispatchers.IO) {
        exportAndroid.save(uri, file)
    }

    fun filterDescription(): String {
        val event = eventFilter.value
        val trace = traceFilter.value
        val parts = buildList {
            if (event.hasActiveFilters) {
                add(
                    "事件: " + buildList {
                        if (event.query.isNotBlank()) add("搜索=${event.query}")
                        if (event.levels.isNotEmpty()) add("级别=${event.levels.joinToString("/") { it.name }}")
                        if (event.flows.isNotEmpty()) add("类别=${event.flows.joinToString("/") { it.label }}")
                        if (event.range != DiagTimeRange.All) add("时间=${event.range.label}")
                    }.joinToString(", "),
                )
            }
            if (trace.hasActiveFilters) {
                add(
                    "接水: " + buildList {
                        if (trace.deviceQuery.isNotBlank()) add("搜索=${trace.deviceQuery}")
                        if (trace.outcomes.isNotEmpty()) {
                            add("结果=${trace.outcomes.joinToString("/") { it.label }}")
                        }
                        if (trace.range != DiagTimeRange.All) add("时间=${trace.range.label}")
                    }.joinToString(", "),
                )
            }
        }
        return parts.joinToString("；").ifBlank { "全部（未筛选）" }
    }
}
