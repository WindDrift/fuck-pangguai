package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.DiagEvent
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.StageRecord
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace

class FakeDiagSink : DiagSink {

    val events = mutableListOf<DiagEvent>()
    val traces = LinkedHashMap<String, WaterTrace>()

    override fun event(
        level: DiagLevel,
        flow: DiagFlow,
        name: String,
        message: String,
        traceId: String,
        fields: Map<String, String>,
    ) {
        events += DiagEvent(
            id = "${events.size}",
            at = 0L,
            level = level,
            flow = flow,
            name = name,
            message = message,
            traceId = traceId,
            fields = fields,
        )
    }

    override fun startTrace(trace: WaterTrace) {
        traces.putIfAbsent(trace.traceId, trace)
    }

    override fun stageStart(
        traceId: String,
        stage: TraceStage,
        startedAt: Long,
        attempts: Int,
        fields: Map<String, String>,
    ) {
        update(traceId) { trace ->
            if (trace.stage(stage)?.reached == true) return@update trace
            trace.withStage(stage) {
                StageRecord(
                    stage = stage,
                    startedAt = trace.stage(stage)?.startedAt ?: startedAt,
                    attempts = attempts,
                    fields = fields,
                )
            }
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
        update(traceId) { trace ->
            if (trace.stage(stage)?.reached == true) return@update trace
            val startedAt = trace.stage(stage)?.startedAt ?: endedAt
            trace.withStage(stage) {
                StageRecord(
                    stage = stage,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    durationMs = (endedAt - startedAt).coerceAtLeast(0L),
                    outcome = outcome,
                    attempts = attempts,
                    error = error,
                    fields = fields,
                )
            }
        }
    }

    override fun finishTrace(
        traceId: String,
        outcome: TraceOutcome,
        endedAt: Long,
        error: String,
    ) {
        update(traceId) { trace ->
            if (trace.outcome != TraceOutcome.Running) return@update trace
            trace.copy(outcome = outcome, endedAt = endedAt, error = error)
        }
    }

    override fun replaceTrace(trace: WaterTrace) {
        traces[trace.traceId] = trace
    }

    override fun currentTrace(): WaterTrace? =
        traces.values.lastOrNull { it.outcome == TraceOutcome.Running }

    override suspend fun findTrace(traceId: String): WaterTrace? = traces[traceId]

    fun trace(traceId: String): WaterTrace = traces.getValue(traceId)

    private fun update(traceId: String, block: (WaterTrace) -> WaterTrace) {
        val current = traces[traceId] ?: return
        traces[traceId] = block(current)
    }
}
