package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.CrashEntry
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagEvent
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DiagWriter(
    private val store: DiagStore,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
    private val limits: () -> DiagSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _events = MutableStateFlow<List<DiagEvent>>(emptyList())
    val events = _events.asStateFlow()

    private val _traces = MutableStateFlow<List<WaterTrace>>(emptyList())
    val traces = _traces.asStateFlow()

    private val channel = Channel<Op>(Channel.UNLIMITED)
    private val lock = Any()
    private val latestTraces = HashMap<String, WaterTrace>()
    private val lastPersistedAt = HashMap<String, Long>()
    private var writeCount = 0

    init {
        scope.launch(Dispatchers.IO) {
            runCatching {
                _events.value = mergeEvents(
                    store.readEvents(DiagStore.DEFAULT_EVENT_LIMIT),
                    _events.value,
                )
                _traces.value = mergeTraces(
                    store.readTraces(DiagStore.DEFAULT_TRACE_LIMIT),
                    _traces.value,
                )
            }
        }
        scope.launch(Dispatchers.IO) {
            for (op in channel) {
                when (op) {
                    is Op.Event -> {
                        store.appendEvent(op.event)
                        onWrite()
                    }

                    is Op.Trace -> {
                        store.appendTrace(op.trace)
                        onWrite()
                    }

                    is Op.Flush -> op.ack.complete(Unit)
                    is Op.Clear -> {
                        synchronized(lock) {
                            latestTraces.clear()
                            lastPersistedAt.clear()
                        }
                        store.clear()
                        _events.value = emptyList()
                        _traces.value = emptyList()
                        op.ack.complete(Unit)
                    }
                }
            }
        }
    }

    fun submitEvent(event: DiagEvent) {
        if (!enabled()) return
        _events.value = (listOf(event) + _events.value).take(MAX_EVENTS)
        channel.trySend(Op.Event(event))
    }

    fun submitTrace(trace: WaterTrace) {
        if (!enabled()) return
        _traces.value = upsertTrace(_traces.value, trace)
        synchronized(lock) { latestTraces[trace.traceId] = trace }

        val now = clock()
        val force = trace.outcome != TraceOutcome.Running || trace.endedAt > 0L
        val last = synchronized(lock) { lastPersistedAt[trace.traceId] ?: 0L }
        if (force || now - last >= TRACE_PERSIST_INTERVAL_MS) {
            synchronized(lock) { lastPersistedAt[trace.traceId] = now }
            channel.trySend(Op.Trace(trace))
        }
    }

    /** 崩溃路径同步写盘；即使日志开关关闭也保留崩溃记录。 */
    @Synchronized
    fun submitCrashSync(entry: CrashEntry, event: DiagEvent) {
        store.appendCrash(entry)
        store.appendEvent(event)
        _events.value = (listOf(event) + _events.value).take(MAX_EVENTS)
    }

    suspend fun flush() {
        val toPersist = synchronized(lock) { latestTraces.values.toList() }
        toPersist.forEach { channel.trySend(Op.Trace(it)) }
        val ack = CompletableDeferred<Unit>()
        channel.trySend(Op.Flush(ack))
        withTimeoutOrNull(FLUSH_TIMEOUT_MS) { ack.await() }
    }

    suspend fun clear() {
        val ack = CompletableDeferred<Unit>()
        channel.trySend(Op.Clear(ack))
        withTimeoutOrNull(CLEAR_TIMEOUT_MS) { ack.await() }
    }

    @Synchronized
    private fun onWrite() {
        writeCount += 1
        if (writeCount < CLEANUP_INTERVAL_WRITES) return
        writeCount = 0
        val current = limits()
        store.cleanup(current.retentionDays, current.maxMb.toLong() * BYTES_PER_MB)
    }

    private fun mergeEvents(
        loaded: List<DiagEvent>,
        current: List<DiagEvent>,
    ): List<DiagEvent> = (loaded + current)
        .distinctBy { it.id }
        .sortedByDescending { it.at }
        .take(MAX_EVENTS)

    private fun mergeTraces(
        loaded: List<WaterTrace>,
        current: List<WaterTrace>,
    ): List<WaterTrace> = (loaded + current)
        .groupBy { it.traceId }
        .map { (_, traces) -> traces.maxBy { it.updatedAt } }
        .sortedByDescending { it.startedAt }
        .take(MAX_TRACES)

    private fun upsertTrace(current: List<WaterTrace>, trace: WaterTrace): List<WaterTrace> =
        (current.filterNot { it.traceId == trace.traceId } + trace)
            .sortedByDescending { it.startedAt }
            .take(MAX_TRACES)

    private sealed interface Op {
        data class Event(val event: DiagEvent) : Op
        data class Trace(val trace: WaterTrace) : Op
        data class Flush(val ack: CompletableDeferred<Unit>) : Op
        data class Clear(val ack: CompletableDeferred<Unit>) : Op
    }

    private companion object {
        const val MAX_EVENTS = 2_000
        const val MAX_TRACES = 300
        const val CLEANUP_INTERVAL_WRITES = 100
        const val TRACE_PERSIST_INTERVAL_MS = 10_000L
        const val FLUSH_TIMEOUT_MS = 3_000L
        const val CLEAR_TIMEOUT_MS = 5_000L
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
