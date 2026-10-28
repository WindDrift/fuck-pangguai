package io.github.winddrift.fuckpangguai.diagnostics

import android.content.Context
import io.github.winddrift.fuckpangguai.diagnostics.model.CrashEntry
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagEvent
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.StageRecord
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class DiagnosticsSnapshot(
    val events: List<DiagEvent>,
    val traces: List<WaterTrace>,
    val crashes: List<CrashEntry>,
)

data class DiagStorageStats(
    val eventCount: Int,
    val traceCount: Int,
    val crashCount: Int,
    val bytes: Long,
)

class Diagnostics(
    context: Context,
    private val settingsStore: DiagSettingsStore,
    private val appVersion: String,
    private val buildType: String,
    private val scope: CoroutineScope,
    private val previewLimit: Int = 4 * 1024,
    private val recordDebug: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis,
) : DiagSink {

    private val store = DiagStore(File(context.filesDir, "diagnostics"), clock)
    private val writer = DiagWriter(
        store = store,
        scope = scope,
        enabled = { settings.value.enabled },
        limits = { settings.value },
        clock = clock,
    )

    val settings: StateFlow<DiagSettings> = settingsStore.settings.stateIn(
        scope,
        SharingStarted.Eagerly,
        DiagSettings(),
    )
    val events: StateFlow<List<DiagEvent>> = writer.events
    val traces: StateFlow<List<WaterTrace>> = writer.traces

    private val _recentCrashes = MutableStateFlow<List<CrashEntry>>(emptyList())
    val recentCrashes: StateFlow<List<CrashEntry>> = _recentCrashes.asStateFlow()

    private val lock = Any()
    private val activeTraces = LinkedHashMap<String, WaterTrace>()
    private var lastTraceId: String? = null
    private val sequence = AtomicInteger(0)

    init {
        scope.launch(Dispatchers.IO) {
            runCatching { _recentCrashes.value = store.readCrashes(MAX_CRASHES) }
        }
        scope.launch {
            settingsStore.settings.collect { current ->
                withContext(Dispatchers.IO) {
                    store.cleanup(
                        current.retentionDays,
                        current.maxMb.toLong() * BYTES_PER_MB,
                    )
                }
            }
        }
    }

    fun start(activeTraceProvider: suspend () -> String?) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val now = clock()
                val activeId = activeTraceProvider().orEmpty()
                store.readTraces(RECOVERY_TRACE_LIMIT).forEach { trace ->
                    if (trace.outcome != TraceOutcome.Running) return@forEach
                    if (trace.traceId == activeId) return@forEach
                    if (now - trace.updatedAt < STALE_TRACE_MS) return@forEach
                    writer.submitTrace(markInterrupted(trace, now))
                }
            }
        }
    }

    override fun event(
        level: DiagLevel,
        flow: DiagFlow,
        name: String,
        message: String,
        traceId: String,
        fields: Map<String, String>,
    ) {
        if (!settings.value.enabled) return
        if (level == DiagLevel.Debug && !recordDebug) return
        val at = clock()
        writer.submitEvent(
            DiagEvent(
                id = "$at-${sequence.incrementAndGet()}",
                at = at,
                level = level,
                flow = flow,
                name = name.take(MAX_NAME_LENGTH),
                message = message.take(MAX_TEXT_LENGTH),
                traceId = traceId,
                fields = sanitizeFields(fields),
            ),
        )
    }

    override fun startTrace(trace: WaterTrace) {
        if (!settings.value.enabled) return
        val sanitized = sanitizeTrace(trace)
        synchronized(lock) {
            if (activeTraces.containsKey(sanitized.traceId)) return
            activeTraces[sanitized.traceId] = sanitized
            lastTraceId = sanitized.traceId
        }
        writer.submitTrace(sanitized)
    }

    override fun stageStart(
        traceId: String,
        stage: TraceStage,
        startedAt: Long,
        attempts: Int,
        fields: Map<String, String>,
    ) {
        if (!settings.value.enabled) return
        updateTrace(traceId, startedAt) { trace ->
            if (trace.outcome != TraceOutcome.Running) return@updateTrace trace
            val existing = trace.stage(stage)
            if (existing?.reached == true) return@updateTrace trace
            val record = StageRecord(
                stage = stage,
                startedAt = existing?.startedAt ?: startedAt,
                attempts = maxOf(existing?.attempts ?: 0, attempts),
                fields = capFields(existing?.fields.orEmpty(), fields),
            )
            trace.withStage(stage) { record }.copy(updatedAt = clock())
        }
    }

    override fun stageFinished(
        traceId: String,
        stage: TraceStage,
        outcome: StageOutcome,
        endedAt: Long,
        attempts: Int,
        error: String,
        fields: Map<String, String>,
    ) {
        if (!settings.value.enabled) return
        if (outcome == StageOutcome.Running) return
        updateTrace(traceId, endedAt) { trace ->
            if (trace.outcome != TraceOutcome.Running) return@updateTrace trace
            val existing = trace.stage(stage)
            if (existing?.reached == true) return@updateTrace trace
            val startedAt = existing?.startedAt ?: endedAt
            val record = StageRecord(
                stage = stage,
                startedAt = startedAt,
                endedAt = endedAt,
                durationMs = (endedAt - startedAt).coerceAtLeast(0L),
                outcome = outcome,
                attempts = maxOf(existing?.attempts ?: 0, attempts),
                error = error.take(MAX_TEXT_LENGTH),
                fields = capFields(existing?.fields.orEmpty(), fields),
            )
            trace.withStage(stage) { record }.copy(updatedAt = clock())
        }
    }

    override fun finishTrace(
        traceId: String,
        outcome: TraceOutcome,
        endedAt: Long,
        error: String,
    ) {
        if (!settings.value.enabled) return
        if (outcome == TraceOutcome.Running) return
        updateTrace(traceId, endedAt) { trace ->
            if (trace.outcome != TraceOutcome.Running) return@updateTrace trace
            val stages = trace.stages.map { record ->
                if (record.outcome == StageOutcome.Running) {
                    record.copy(
                        endedAt = endedAt,
                        durationMs = (endedAt - record.startedAt).coerceAtLeast(0L),
                        outcome = StageOutcome.Abandoned,
                    )
                } else {
                    record
                }
            }
            trace.copy(
                stages = stages,
                outcome = outcome,
                endedAt = endedAt,
                error = error.take(MAX_TEXT_LENGTH),
                updatedAt = clock(),
            )
        }
    }

    override fun replaceTrace(trace: WaterTrace) {
        if (!settings.value.enabled) return
        if (trace.traceId.isBlank()) return
        val sanitized = sanitizeTrace(trace)
        synchronized(lock) {
            val existing = activeTraces[sanitized.traceId]
            if (existing != null && existing.outcome != TraceOutcome.Running &&
                sanitized.outcome == TraceOutcome.Running
            ) {
                return
            }
            if (existing != null && sanitized.updatedAt < existing.updatedAt) return
            activeTraces[sanitized.traceId] = sanitized
            lastTraceId = sanitized.traceId
        }
        writer.submitTrace(sanitized)
    }

    override fun currentTrace(): WaterTrace? = synchronized(lock) {
        val last = lastTraceId?.let(activeTraces::get)
        if (last != null && last.outcome == TraceOutcome.Running) {
            return@synchronized last
        }
        activeTraces.values
            .filter { it.outcome == TraceOutcome.Running }
            .maxByOrNull { it.startedAt }
            ?: activeTraces.values.maxByOrNull { it.startedAt }
    }

    override suspend fun findTrace(traceId: String): WaterTrace? {
        if (traceId.isBlank()) return null
        synchronized(lock) { activeTraces[traceId] }?.let { return it }
        val trace = withContext(Dispatchers.IO) {
            store.readTraces(Int.MAX_VALUE).firstOrNull { it.traceId == traceId }
        }
        if (trace != null) {
            synchronized(lock) {
                activeTraces[trace.traceId] = trace
                lastTraceId = trace.traceId
            }
        }
        return trace
    }

    override fun recentEvents(limit: Int): List<DiagEvent> = writer.events.value.take(limit)

    fun recordCrashSync(entry: CrashEntry) {
        val at = entry.at
        val event = DiagEvent(
            id = "$at-crash-${sequence.incrementAndGet()}",
            at = at,
            level = DiagLevel.Error,
            flow = DiagFlow.Crash,
            name = "crash",
            message = entry.exception.take(MAX_TEXT_LENGTH),
            traceId = entry.traceId,
            fields = mapOf(
                "thread" to entry.threadName.take(MAX_TEXT_LENGTH),
                "version" to entry.appVersion,
                "buildType" to entry.buildType,
            ),
        )
        writer.submitCrashSync(entry, event)
        _recentCrashes.value = (listOf(entry) + _recentCrashes.value).take(MAX_CRASHES)
    }

    fun flushAsync() {
        scope.launch { writer.flush() }
    }

    suspend fun clear() {
        synchronized(lock) {
            activeTraces.clear()
            lastTraceId = null
        }
        _recentCrashes.value = emptyList()
        writer.clear()
    }

    suspend fun storageStats(): DiagStorageStats = withContext(Dispatchers.IO) {
        DiagStorageStats(
            eventCount = store.eventLineCount(),
            traceCount = store.traceCount(),
            crashCount = store.crashCount(),
            bytes = store.totalBytes(),
        )
    }

    suspend fun snapshot(): DiagnosticsSnapshot {
        writer.flush()
        return withContext(Dispatchers.IO) {
            DiagnosticsSnapshot(
                events = store.readEvents(Int.MAX_VALUE),
                traces = store.readTraces(Int.MAX_VALUE),
                crashes = store.readCrashes(Int.MAX_VALUE),
            )
        }
    }

    val appBuildType: String get() = buildType
    val appVersionName: String get() = appVersion

    private fun updateTrace(
        traceId: String,
        now: Long,
        block: (WaterTrace) -> WaterTrace,
    ) {
        if (traceId.isBlank()) return
        val updated = synchronized(lock) {
            val trace = activeTraces[traceId] ?: WaterTrace(
                traceId = traceId,
                startedAt = now,
                updatedAt = now,
            )
            val next = block(trace)
            activeTraces[traceId] = next
            lastTraceId = traceId
            next
        }
        writer.submitTrace(updated)
    }

    private fun sanitizeTrace(trace: WaterTrace): WaterTrace = trace.copy(
        deviceName = trace.deviceName.take(MAX_NAME_LENGTH),
        goodsId = trace.goodsId.take(MAX_NAME_LENGTH),
        source = trace.source.take(MAX_NAME_LENGTH),
        error = trace.error.take(MAX_TEXT_LENGTH),
        stages = trace.stages.map { record ->
            record.copy(
                error = record.error.take(MAX_TEXT_LENGTH),
                fields = sanitizeFields(record.fields),
            )
        },
    )

    private fun sanitizeFields(fields: Map<String, String>): Map<String, String> =
        fields.entries
            .take(MAX_FIELDS)
            .associate { (key, value) ->
                val limit = if (key == PREVIEW_KEY) previewLimit else MAX_TEXT_LENGTH
                key.take(MAX_NAME_LENGTH) to value.take(limit)
            }

    private fun capFields(
        existing: Map<String, String>,
        added: Map<String, String>,
    ): Map<String, String> = (existing + sanitizeFields(added))
        .entries
        .take(MAX_FIELDS)
        .associate { it.key to it.value }

    private fun markInterrupted(trace: WaterTrace, now: Long): WaterTrace {
        val endedAt = if (trace.updatedAt > 0L) trace.updatedAt else now
        return trace.copy(
            stages = trace.stages.map { record ->
                if (record.outcome == StageOutcome.Running) {
                    record.copy(
                        endedAt = endedAt,
                        durationMs = (endedAt - record.startedAt).coerceAtLeast(0L),
                        outcome = StageOutcome.Abandoned,
                    )
                } else {
                    record
                }
            },
            outcome = TraceOutcome.Interrupted,
            endedAt = endedAt,
            error = "进程中断或超过 10 分钟未更新",
            updatedAt = now,
        )
    }

    private companion object {
        const val MAX_NAME_LENGTH = 128
        const val MAX_TEXT_LENGTH = 512
        const val MAX_FIELDS = 40
        const val PREVIEW_KEY = "preview"
        const val STALE_TRACE_MS = 10 * 60_000L
        const val RECOVERY_TRACE_LIMIT = 500
        const val MAX_CRASHES = 20
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
