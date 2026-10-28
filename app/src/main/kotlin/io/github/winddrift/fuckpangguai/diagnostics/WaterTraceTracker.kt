package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.StageRecord
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import io.github.winddrift.fuckpangguai.net.dto.WaterSync
import io.github.winddrift.fuckpangguai.water.SessionPhase
import io.github.winddrift.fuckpangguai.water.WaterSession

/**
 * 把持久化会话归约为 7 段 trace。纯 JVM，不依赖 Android，便于前台服务与测试复用。
 *
 * 阶段一旦有终态就不再改写（first-write-wins）；会话重启、重复 sync 都不会覆盖
 * 已记录的出水/结束/结算时间。
 */
class WaterTraceTracker(
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun track(
        trace: WaterTrace,
        session: WaterSession,
        sync: WaterSync? = null,
    ): WaterTrace {
        if (trace.outcome != TraceOutcome.Running) return trace
        var current = trace.copy(
            deviceName = session.deviceName,
            goodsId = session.goodsId,
            updatedAt = clock(),
        )
        current = updateWaterStart(current, session)
        current = updateWaterEnd(current, session)
        current = updateSettlement(current, session)
        current = finalize(current, session)
        return current
    }

    private fun updateWaterStart(trace: WaterTrace, session: WaterSession): WaterTrace {
        val existing = trace.stage(TraceStage.WaterStart)
        if (existing?.reached == true) return trace
        val startedAt = existing?.startedAt ?: session.startedAt
        val attempts = session.syncCount
        val confirmedAt = session.waterConfirmedAt
        val endedAt = endTime(session)
        val record = when {
            confirmedAt > 0L -> StageRecord(
                stage = TraceStage.WaterStart,
                startedAt = startedAt,
                endedAt = confirmedAt,
                durationMs = (confirmedAt - startedAt).coerceAtLeast(0L),
                outcome = StageOutcome.Success,
                attempts = attempts,
            )

            endedAt > 0L -> StageRecord(
                stage = TraceStage.WaterStart,
                startedAt = startedAt,
                endedAt = endedAt,
                durationMs = (endedAt - startedAt).coerceAtLeast(0L),
                outcome = StageOutcome.Failed,
                attempts = attempts,
                error = "未确认出水",
            )

            else -> StageRecord(
                stage = TraceStage.WaterStart,
                startedAt = startedAt,
                attempts = attempts,
            )
        }
        return trace.withStage(TraceStage.WaterStart) { record }
    }

    private fun updateWaterEnd(trace: WaterTrace, session: WaterSession): WaterTrace {
        val existing = trace.stage(TraceStage.WaterEnd)
        if (existing?.reached == true) return trace
        val endedAt = endTime(session)
        val sessionFinished = endedAt > 0L || session.phase in SessionPhase.SETTLING ||
            session.phase == SessionPhase.PAID ||
            session.phase == SessionPhase.FINISHED_NO_CHARGE ||
            session.phase == SessionPhase.PAY_UNKNOWN ||
            session.phase == SessionPhase.FAULT
        if (!sessionFinished) return trace

        val startedAt = session.waterConfirmedAt.takeIf { it > 0L } ?: session.startedAt
        val finishAt = endedAt.takeIf { it > 0L } ?: session.updatedAt
        val failed = session.phase == SessionPhase.FAULT
        val operationMs = if (session.endRequestedAt > 0L) {
            (finishAt - session.endRequestedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        val record = StageRecord(
            stage = TraceStage.WaterEnd,
            startedAt = startedAt,
            endedAt = finishAt,
            durationMs = (finishAt - startedAt).coerceAtLeast(0L),
            outcome = if (failed) StageOutcome.Failed else StageOutcome.Success,
            attempts = 1 + if (session.endRequestedAt > 0L) 1 else 0,
            error = if (failed) session.error.ifBlank { "设备异常" } else "",
        )
        return trace.withStage(TraceStage.WaterEnd) { record }
            .copy(endOperationMs = operationMs)
    }

    private fun updateSettlement(trace: WaterTrace, session: WaterSession): WaterTrace {
        val existing = trace.stage(TraceStage.SettlePay)
        if (existing?.reached == true) return trace
        val started = session.settleStartedAt > 0L || session.phase == SessionPhase.SETTLING ||
            session.phase == SessionPhase.PAID ||
            session.phase == SessionPhase.FINISHED_NO_CHARGE ||
            session.phase == SessionPhase.PAY_UNKNOWN ||
            session.phase == SessionPhase.FAULT
        if (!started) return trace

        val startedAt = session.settleStartedAt.takeIf { it > 0L } ?: session.updatedAt
        val attempts = 1 + session.payPollAttempts
        val amount = session.amount.toDoubleOrNull() ?: 0.0
        val outcome = when (session.phase) {
            SessionPhase.PAID -> StageOutcome.Success
            SessionPhase.FINISHED_NO_CHARGE -> if (amount <= 0.0) {
                StageOutcome.Abandoned
            } else {
                StageOutcome.Failed
            }

            SessionPhase.PAY_UNKNOWN, SessionPhase.FAULT -> StageOutcome.Failed
            else -> StageOutcome.Running
        }
        val error = when {
            session.phase == SessionPhase.FINISHED_NO_CHARGE && amount > 0.0 ->
                "缺少结算单号或金额异常"

            outcome == StageOutcome.Failed -> session.error.ifBlank { "结算或支付未确认" }
            else -> ""
        }
        val finishAt = if (outcome != StageOutcome.Running) {
            session.endedAt.takeIf { it > 0L } ?: session.updatedAt
        } else {
            0L
        }
        val record = StageRecord(
            stage = TraceStage.SettlePay,
            startedAt = startedAt,
            endedAt = finishAt,
            durationMs = if (finishAt > 0L) (finishAt - startedAt).coerceAtLeast(0L) else 0L,
            outcome = outcome,
            attempts = attempts,
            error = error,
        )
        return trace.withStage(TraceStage.SettlePay) { record }
    }

    private fun finalize(trace: WaterTrace, session: WaterSession): WaterTrace {
        val endedAt = endTime(session)
        val waterDuration = if (endedAt > 0L) {
            (endedAt - session.startedAt).coerceAtLeast(0L)
        } else {
            (clock() - session.startedAt).coerceAtLeast(0L)
        }
        val settled = trace.stage(TraceStage.SettlePay)?.takeIf { it.reached }
        val outcome = when {
            settled == null -> if (session.phase == SessionPhase.FAULT) {
                TraceOutcome.Failed
            } else {
                TraceOutcome.Running
            }

            settled.outcome == StageOutcome.Success -> TraceOutcome.Success
            settled.outcome == StageOutcome.Abandoned -> TraceOutcome.Abandoned
            else -> TraceOutcome.Failed
        }
        if (outcome == TraceOutcome.Running) {
            return trace.copy(waterDurationMs = waterDuration)
        }
        return trace.copy(
            outcome = outcome,
            endedAt = endedAt.takeIf { it > 0L } ?: session.updatedAt,
            error = trace.error.ifBlank { session.error },
            waterDurationMs = waterDuration,
            updatedAt = clock(),
        )
    }

    private fun endTime(session: WaterSession): Long =
        if (session.endedAt > 0L) session.endedAt else 0L
}
