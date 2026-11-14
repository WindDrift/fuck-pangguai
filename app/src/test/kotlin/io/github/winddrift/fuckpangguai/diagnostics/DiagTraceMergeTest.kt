package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.StageRecord
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagTraceMergeTest {

    private fun scanTrace() = WaterTrace(
        traceId = "trace-1",
        startedAt = 1_000,
        updatedAt = 1_000,
        source = "扫码",
        stages = listOf(
            StageRecord(
                stage = TraceStage.ScanDecode,
                startedAt = 1_000,
                endedAt = 1_120,
                durationMs = 120,
                outcome = StageOutcome.Success,
                attempts = 1,
            ),
        ),
    )

    private fun confirmTrace() = WaterTrace(
        traceId = "trace-1",
        startedAt = 5_000,
        updatedAt = 5_000,
        source = "确认页",
        deviceName = "水机",
        goodsId = "goods-1",
        decodeMs = 120,
    )

    @Test
    fun `empty device info is filled by the later trace`() {
        val merged = mergeTraceStart(scanTrace(), confirmTrace())

        assertEquals("水机", merged.deviceName)
        assertEquals("goods-1", merged.goodsId)
        assertEquals("扫码", merged.source)
        assertEquals(120, merged.decodeMs)
    }

    @Test
    fun `existing non empty device info is not overwritten`() {
        val existing = scanTrace().copy(deviceName = "老水机", goodsId = "goods-old")

        val merged = mergeTraceStart(existing, confirmTrace())

        assertEquals("老水机", merged.deviceName)
        assertEquals("goods-old", merged.goodsId)
    }

    @Test
    fun `terminal trace is never rewritten`() {
        val finished = scanTrace().copy(outcome = TraceOutcome.Failed, endedAt = 2_000)

        val merged = mergeTraceStart(finished, confirmTrace())

        assertEquals(finished, merged)
        assertEquals("", merged.deviceName)
    }

    @Test
    fun `updatedAt takes the larger value and startedAt is preserved`() {
        val merged = mergeTraceStart(scanTrace(), confirmTrace())

        assertEquals(1_000, merged.startedAt)
        assertEquals(5_000, merged.updatedAt)
        assertEquals(1, merged.stages.size)
    }

    @Test
    fun `decode time only fills when the existing one is missing`() {
        val withDecode = scanTrace().copy(decodeMs = 90)

        assertEquals(90, mergeTraceStart(withDecode, confirmTrace()).decodeMs)
        assertEquals(0, mergeTraceStart(scanTrace(), confirmTrace().copy(decodeMs = 0)).decodeMs)
    }

    @Test
    fun `fake sink applies the same merge semantics`() {
        val sink = FakeDiagSink()
        sink.startTrace(scanTrace())
        sink.startTrace(confirmTrace())

        val stored = sink.trace("trace-1")
        assertEquals("水机", stored.deviceName)
        assertEquals("goods-1", stored.goodsId)
    }
}