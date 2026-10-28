package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.StageRecord
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import io.github.winddrift.fuckpangguai.water.SessionPhase
import io.github.winddrift.fuckpangguai.water.WaterSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterTraceTrackerTest {

    private var now = 100_000L
    private val tracker = WaterTraceTracker { now }

    private fun baseTrace() = WaterTrace(
        traceId = "trace-1",
        startedAt = 1_000,
        updatedAt = 1_000,
        deviceName = "水机",
        goodsId = "goods-1",
        stages = listOf(
            success(TraceStage.ScanDecode, 1_000, 120),
            success(TraceStage.DeviceResolve, 1_120, 800),
            success(TraceStage.ConfirmReady, 1_920, 300),
            success(TraceStage.Unlock, 2_220, 1_500),
        ),
    )

    private fun session(
        phase: String = SessionPhase.USING,
        startedAt: Long = 4_000,
        updatedAt: Long = 5_000,
        amount: String = "",
        waterConfirmedAt: Long = 0L,
        endRequestedAt: Long = 0L,
        endedAt: Long = 0L,
        syncCount: Int = 0,
        settleStartedAt: Long = 0L,
        payPollAttempts: Int = 0,
        error: String = "",
    ) = WaterSession(
        goodsId = "goods-1",
        skuId = "sku-1",
        deviceName = "水机",
        imei = "860",
        orderNo = "order",
        msgId = "msg",
        supportAppUserFinish = true,
        startedAt = startedAt,
        phase = phase,
        amount = amount,
        identify = if (amount.toDoubleOrNull()?.let { it > 0 } == true) "identify" else "",
        error = error,
        updatedAt = updatedAt,
        traceId = "trace-1",
        waterConfirmedAt = waterConfirmedAt,
        endRequestedAt = endRequestedAt,
        endedAt = endedAt,
        syncCount = syncCount,
        settleStartedAt = settleStartedAt,
        payPollAttempts = payPollAttempts,
    )

    @Test
    fun `water start is confirmed by first positive amount`() {
        val trace = tracker.track(
            baseTrace(),
            session(waterConfirmedAt = 5_000, syncCount = 4, updatedAt = 5_000),
        )

        val start = trace.stage(TraceStage.WaterStart)!!
        assertEquals(StageOutcome.Success, start.outcome)
        assertEquals(4_000, start.startedAt)
        assertEquals(5_000, start.endedAt)
        assertEquals(1_000, start.durationMs)
        assertEquals(4, start.attempts)
        assertEquals(TraceOutcome.Running, trace.outcome)
        assertTrue(trace.stage(TraceStage.WaterEnd) == null)
    }

    @Test
    fun `ending water records duration and end operation`() {
        val trace = tracker.track(
            baseTrace(),
            session(
                phase = SessionPhase.SETTLING,
                updatedAt = 66_000,
                waterConfirmedAt = 9_000,
                endRequestedAt = 60_000,
                endedAt = 65_000,
                syncCount = 12,
                settleStartedAt = 66_000,
                amount = "0.5",
            ),
        )

        val end = trace.stage(TraceStage.WaterEnd)!!
        assertEquals(StageOutcome.Success, end.outcome)
        assertEquals(9_000, end.startedAt)
        assertEquals(65_000, end.endedAt)
        assertEquals(56_000, end.durationMs)
        assertEquals(5_000, trace.endOperationMs)
        assertEquals(61_000, trace.waterDurationMs)

        val settle = trace.stage(TraceStage.SettlePay)!!
        assertEquals(StageOutcome.Running, settle.outcome)
        assertEquals(1, settle.attempts)
        assertEquals(TraceOutcome.Running, trace.outcome)
    }

    @Test
    fun `paid settlement finishes the trace`() {
        val trace = tracker.track(
            baseTrace(),
            session(
                phase = SessionPhase.PAID,
                updatedAt = 70_000,
                waterConfirmedAt = 9_000,
                endRequestedAt = 60_000,
                endedAt = 68_000,
                syncCount = 12,
                settleStartedAt = 66_000,
                payPollAttempts = 3,
                amount = "0.5",
            ),
        )

        val settle = trace.stage(TraceStage.SettlePay)!!
        assertEquals(StageOutcome.Success, settle.outcome)
        assertEquals(4, settle.attempts)
        assertEquals(TraceOutcome.Success, trace.outcome)
        assertEquals(68_000, trace.endedAt)
    }

    @Test
    fun `no charge with zero amount is abandoned`() {
        val trace = tracker.track(
            baseTrace(),
            session(
                phase = SessionPhase.FINISHED_NO_CHARGE,
                updatedAt = 70_000,
                waterConfirmedAt = 9_000,
                endedAt = 68_000,
                settleStartedAt = 66_000,
                amount = "0",
            ),
        )

        assertEquals(StageOutcome.Abandoned, trace.stage(TraceStage.SettlePay)!!.outcome)
        assertEquals(TraceOutcome.Abandoned, trace.outcome)
    }

    @Test
    fun `no charge with positive amount is failed`() {
        val trace = tracker.track(
            baseTrace(),
            session(
                phase = SessionPhase.FINISHED_NO_CHARGE,
                updatedAt = 70_000,
                waterConfirmedAt = 9_000,
                endedAt = 68_000,
                settleStartedAt = 66_000,
                amount = "1.2",
            ),
        )

        val settle = trace.stage(TraceStage.SettlePay)!!
        assertEquals(StageOutcome.Failed, settle.outcome)
        assertEquals("缺少结算单号或金额异常", settle.error)
        assertEquals(TraceOutcome.Failed, trace.outcome)
    }

    @Test
    fun `device fault before water marks both stages failed`() {
        val trace = tracker.track(
            baseTrace(),
            session(
                phase = SessionPhase.FAULT,
                updatedAt = 3_000,
                endedAt = 3_000,
                error = "设备异常",
                syncCount = 1,
            ),
        )

        val start = trace.stage(TraceStage.WaterStart)!!
        assertEquals(StageOutcome.Failed, start.outcome)
        assertEquals("未确认出水", start.error)
        assertEquals(StageOutcome.Failed, trace.stage(TraceStage.WaterEnd)!!.outcome)
        assertEquals(TraceOutcome.Failed, trace.outcome)
    }

    @Test
    fun `finished trace ignores later session updates`() {
        val paid = tracker.track(
            baseTrace(),
            session(
                phase = SessionPhase.PAID,
                updatedAt = 70_000,
                waterConfirmedAt = 9_000,
                endedAt = 68_000,
                settleStartedAt = 66_000,
                amount = "0.5",
            ),
        )

        now = 200_000
        val again = tracker.track(
            paid,
            session(phase = SessionPhase.FAULT, updatedAt = 80_000, error = "late"),
        )

        assertEquals(TraceOutcome.Success, again.outcome)
        assertEquals(paid.stages, again.stages)
    }

    private fun success(stage: TraceStage, startedAt: Long, duration: Long) = StageRecord(
        stage = stage,
        startedAt = startedAt,
        endedAt = startedAt + duration,
        durationMs = duration,
        outcome = StageOutcome.Success,
        attempts = 1,
    )
}
