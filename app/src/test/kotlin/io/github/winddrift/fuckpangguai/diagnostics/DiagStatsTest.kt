package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.StageRecord
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import io.github.winddrift.fuckpangguai.diagnostics.model.computeTraceStats
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagStatsTest {

    @Test
    fun `unreached stages count as abandoned and do not join averages`() {
        val success = fullTrace(TraceOutcome.Success, base = 1_000)
        val failed = WaterTrace(
            traceId = "failed",
            startedAt = 2_000,
            outcome = TraceOutcome.Failed,
            endedAt = 2_500,
            stages = listOf(
                stage(TraceStage.ScanDecode, 2_000, 100, StageOutcome.Success, attempts = 1),
                stage(TraceStage.DeviceResolve, 2_100, 400, StageOutcome.Failed, attempts = 2),
            ),
        )
        val running = WaterTrace(
            traceId = "running",
            startedAt = 3_000,
            updatedAt = 3_200,
            stages = listOf(
                stage(TraceStage.ScanDecode, 3_000, 50, StageOutcome.Success, attempts = 1),
                StageRecord(stage = TraceStage.DeviceResolve, startedAt = 3_050, attempts = 2),
            ),
        )

        val stats = computeTraceStats(listOf(success, failed, running))

        assertEquals(3, stats.total)
        assertEquals(1, stats.success)
        assertEquals(1, stats.failed)
        assertEquals(1, stats.running)

        val decode = stats.stage(TraceStage.ScanDecode)
        assertEquals(3, decode.success)
        assertEquals(3, decode.participants)
        assertEquals(83L, decode.avgDurationMs)
        assertEquals(100L, decode.maxDurationMs)

        val resolve = stats.stage(TraceStage.DeviceResolve)
        assertEquals(1, resolve.success)
        assertEquals(1, resolve.failed)
        assertEquals(1, resolve.running)
        assertEquals(2, resolve.participants)
        assertEquals(250L, resolve.avgDurationMs)

        val confirm = stats.stage(TraceStage.ConfirmReady)
        assertEquals(1, confirm.success)
        assertEquals(1, confirm.abandoned)
        assertEquals(1, confirm.running)
        assertEquals(1, confirm.participants)
    }

    @Test
    fun `failed traces contribute their own duration to totals`() {
        val success = fullTrace(TraceOutcome.Success, base = 1_000, stageMs = 10)
        val failed = WaterTrace(
            traceId = "failed",
            startedAt = 5_000,
            outcome = TraceOutcome.Failed,
            endedAt = 5_300,
            stages = listOf(stage(TraceStage.ScanDecode, 5_000, 300, StageOutcome.Failed, attempts = 1)),
        )

        val stats = computeTraceStats(listOf(success, failed))

        assertEquals(2, stats.total)
        assertEquals(2, stats.failed + stats.success)
        assertEquals(185L, stats.avgTotalDurationMs)
        assertEquals(300L, stats.maxTotalDurationMs)
    }

    @Test
    fun `empty list returns zeroed stage stats`() {
        val stats = computeTraceStats(emptyList())
        assertEquals(0, stats.total)
        assertEquals(TraceStage.entries.size, stats.stages.size)
        assertEquals(0L, stats.stage(TraceStage.Unlock).avgDurationMs)
    }

    @Test
    fun `average attempts only counts reached records`() {
        val traces = listOf(
            WaterTrace(
                traceId = "a",
                startedAt = 1_000,
                outcome = TraceOutcome.Failed,
                endedAt = 2_000,
                stages = listOf(
                    stage(TraceStage.DeviceResolve, 1_000, 500, StageOutcome.Success, attempts = 2),
                ),
            ),
            WaterTrace(
                traceId = "b",
                startedAt = 3_000,
                outcome = TraceOutcome.Failed,
                endedAt = 4_000,
                stages = listOf(
                    stage(TraceStage.DeviceResolve, 3_000, 500, StageOutcome.Failed, attempts = 4),
                ),
            ),
        )

        val resolve = computeTraceStats(traces).stage(TraceStage.DeviceResolve)
        assertEquals(3.0, resolve.avgAttempts, 0.001)
    }

    private fun stage(
        stage: TraceStage,
        startedAt: Long,
        duration: Long,
        outcome: StageOutcome,
        attempts: Int,
    ) = StageRecord(
        stage = stage,
        startedAt = startedAt,
        endedAt = startedAt + duration,
        durationMs = duration,
        outcome = outcome,
        attempts = attempts,
    )

    private fun fullTrace(
        outcome: TraceOutcome,
        base: Long,
        stageMs: Long = 100,
    ): WaterTrace {
        val stages = TraceStage.entries.mapIndexed { index, stage ->
            stage(TraceStage.entries[index], base + index * stageMs, stageMs, StageOutcome.Success, 1)
        }
        return WaterTrace(
            traceId = "full-$outcome-$base",
            startedAt = base,
            updatedAt = base,
            outcome = outcome,
            endedAt = base + stages.sumOf { it.durationMs },
            stages = stages,
        )
    }
}
