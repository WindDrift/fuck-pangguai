package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.DiagEvent
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagTimeRange
import io.github.winddrift.fuckpangguai.diagnostics.model.EventFilter
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceFilter
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import io.github.winddrift.fuckpangguai.diagnostics.model.applyFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagFilterTest {

    private val now = 10_000_000L

    private fun event(
        at: Long,
        level: DiagLevel = DiagLevel.Info,
        flow: DiagFlow = DiagFlow.Scan,
        name: String = "event",
        message: String = "",
        fields: Map<String, String> = emptyMap(),
    ) = DiagEvent(
        id = "$at-$name",
        at = at,
        level = level,
        flow = flow,
        name = name,
        message = message,
        fields = fields,
    )

    @Test
    fun `query matches name message flow and field values case insensitively`() {
        val events = listOf(
            event(now, name = "qr_accepted", message = "来源 Camera"),
            event(now - 1, name = "unlock_ok", fields = mapOf("channel" to "Alipay")),
        )

        assertEquals(listOf("qr_accepted"), events.applyFilter(EventFilter(query = "camera"), now).map { it.name })
        assertEquals(listOf("qr_accepted"), events.applyFilter(EventFilter(query = "QR_"), now).map { it.name })
        assertEquals(listOf("unlock_ok"), events.applyFilter(EventFilter(query = "alipay"), now).map { it.name })
    }

    @Test
    fun `level and flow filters combine`() {
        val events = listOf(
            event(now, DiagLevel.Warn, DiagFlow.Network),
            event(now, DiagLevel.Error, DiagFlow.Network),
            event(now, DiagLevel.Error, DiagFlow.Water),
        )

        val filtered = events.applyFilter(
            EventFilter(levels = setOf(DiagLevel.Error), flows = setOf(DiagFlow.Network)),
            now,
        )

        assertEquals(1, filtered.size)
        assertEquals(DiagFlow.Network, filtered.single().flow)
    }

    @Test
    fun `time range and limit are applied`() {
        val events = (0..5).map { index -> event(now - index * 60_000L) }

        val recent = events.applyFilter(EventFilter(range = DiagTimeRange.Last15Minutes), now)
        assertEquals(6, recent.size)
        assertTrue(recent.all { it.at >= now - 15 * 60_000L })
        assertEquals(3, events.applyFilter(EventFilter(limit = 3), now).size)
        assertTrue(events.applyFilter(EventFilter(limit = 0), now).isEmpty())
    }

    @Test
    fun `trace filter matches device goods id and outcome`() {
        val traces = listOf(
            trace("t1", now, "A 栋饮水机", "goods-1", TraceOutcome.Success),
            trace("t2", now - 60_000, "B 栋", "goods-2", TraceOutcome.Failed),
        )

        assertEquals(1, traces.applyFilter(TraceFilter(deviceQuery = "a 栋"), now).size)
        assertEquals(1, traces.applyFilter(TraceFilter(deviceQuery = "goods-2"), now).size)
        assertEquals(
            listOf("t1"),
            traces.applyFilter(TraceFilter(outcomes = setOf(TraceOutcome.Success)), now)
                .map { it.traceId },
        )
        assertTrue(traces.applyFilter(TraceFilter(deviceQuery = "missing"), now).isEmpty())
    }

    @Test
    fun `active filter flag ignores blank query`() {
        assertFalse(EventFilter().hasActiveFilters)
        assertFalse(EventFilter(query = "  ").hasActiveFilters)
        assertTrue(EventFilter(levels = setOf(DiagLevel.Warn)).hasActiveFilters)
        assertFalse(TraceFilter().hasActiveFilters)
        assertTrue(TraceFilter(outcomes = setOf(TraceOutcome.Failed)).hasActiveFilters)
    }

    private fun trace(
        id: String,
        at: Long,
        name: String,
        goodsId: String,
        outcome: TraceOutcome,
    ) = WaterTrace(
        traceId = id,
        startedAt = at,
        deviceName = name,
        goodsId = goodsId,
        outcome = outcome,
    )
}
