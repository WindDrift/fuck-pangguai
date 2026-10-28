package io.github.winddrift.fuckpangguai.diagnostics.model

import kotlinx.serialization.Serializable

@Serializable
data class StageStats(
    val stage: TraceStage,
    val success: Int = 0,
    val failed: Int = 0,
    val abandoned: Int = 0,
    val running: Int = 0,
    val participants: Int = 0,
    val avgDurationMs: Long = 0L,
    val maxDurationMs: Long = 0L,
    val avgAttempts: Double = 0.0,
)

@Serializable
data class TraceStats(
    val total: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val abandoned: Int = 0,
    val running: Int = 0,
    val interrupted: Int = 0,
    val avgTotalDurationMs: Long = 0L,
    val maxTotalDurationMs: Long = 0L,
    val stages: List<StageStats> = emptyList(),
) {
    fun stage(stage: TraceStage): StageStats =
        stages.firstOrNull { it.stage == stage } ?: StageStats(stage)
}

fun computeTraceStats(traces: List<WaterTrace>): TraceStats {
    if (traces.isEmpty()) return TraceStats(stages = TraceStage.entries.map(::StageStats))

    val stageStats = TraceStage.entries.map { stage ->
        var success = 0
        var failed = 0
        var abandoned = 0
        var running = 0
        var participants = 0
        var durationSum = 0L
        var maxDuration = 0L
        var attemptsSum = 0L

        for (trace in traces) {
            val record = trace.stage(stage)
            if (record == null) {
                if (trace.outcome == TraceOutcome.Running) running++ else abandoned++
                continue
            }
            when (record.outcome) {
                StageOutcome.Success -> success++
                StageOutcome.Failed -> failed++
                StageOutcome.Abandoned -> abandoned++
                StageOutcome.Running -> {
                    if (trace.outcome == TraceOutcome.Running) running++ else abandoned++
                }
            }
            if (record.outcome != StageOutcome.Running) {
                participants++
                durationSum += record.durationMs.coerceAtLeast(0L)
                maxDuration = maxOf(maxDuration, record.durationMs)
            }
            if (record.attempts > 0) attemptsSum += record.attempts
        }

        StageStats(
            stage = stage,
            success = success,
            failed = failed,
            abandoned = abandoned,
            running = running,
            participants = participants,
            avgDurationMs = if (participants > 0) durationSum / participants else 0L,
            maxDurationMs = maxDuration,
            avgAttempts = if (participants > 0) {
                attemptsSum.toDouble() / participants
            } else {
                0.0
            },
        )
    }

    val finished = traces.filter { it.outcome != TraceOutcome.Running }
    val totalSum = finished.sumOf { it.totalDurationMs }
    return TraceStats(
        total = traces.size,
        success = traces.count { it.outcome == TraceOutcome.Success },
        failed = traces.count { it.outcome == TraceOutcome.Failed },
        abandoned = traces.count { it.outcome == TraceOutcome.Abandoned },
        running = traces.count { it.outcome == TraceOutcome.Running },
        interrupted = traces.count { it.outcome == TraceOutcome.Interrupted },
        avgTotalDurationMs = if (finished.isNotEmpty()) totalSum / finished.size else 0L,
        maxTotalDurationMs = finished.maxOfOrNull { it.totalDurationMs } ?: 0L,
        stages = stageStats,
    )
}
