package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.DiagEvent
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace

interface DiagSink {
    fun event(
        level: DiagLevel,
        flow: DiagFlow,
        name: String,
        message: String = "",
        traceId: String = "",
        fields: Map<String, String> = emptyMap(),
    )

    fun startTrace(trace: WaterTrace)

    fun stageStart(
        traceId: String,
        stage: TraceStage,
        startedAt: Long,
        attempts: Int = 0,
        fields: Map<String, String> = emptyMap(),
    )

    fun stageFinished(
        traceId: String,
        stage: TraceStage,
        outcome: StageOutcome,
        endedAt: Long,
        attempts: Int,
        error: String = "",
        fields: Map<String, String> = emptyMap(),
    )

    fun finishTrace(
        traceId: String,
        outcome: TraceOutcome,
        endedAt: Long,
        error: String = "",
    )

    fun replaceTrace(trace: WaterTrace)

    fun currentTrace(): WaterTrace?

    suspend fun findTrace(traceId: String): WaterTrace?

    fun recentEvents(limit: Int = 20): List<DiagEvent> = emptyList()
}

object NoopDiagSink : DiagSink {
    override fun event(
        level: DiagLevel,
        flow: DiagFlow,
        name: String,
        message: String,
        traceId: String,
        fields: Map<String, String>,
    ) = Unit

    override fun startTrace(trace: WaterTrace) = Unit

    override fun stageStart(
        traceId: String,
        stage: TraceStage,
        startedAt: Long,
        attempts: Int,
        fields: Map<String, String>,
    ) = Unit

    override fun stageFinished(
        traceId: String,
        stage: TraceStage,
        outcome: StageOutcome,
        endedAt: Long,
        attempts: Int,
        error: String,
        fields: Map<String, String>,
    ) = Unit

    override fun finishTrace(
        traceId: String,
        outcome: TraceOutcome,
        endedAt: Long,
        error: String,
    ) = Unit

    override fun replaceTrace(trace: WaterTrace) = Unit

    override fun currentTrace(): WaterTrace? = null

    override suspend fun findTrace(traceId: String): WaterTrace? = null
}
