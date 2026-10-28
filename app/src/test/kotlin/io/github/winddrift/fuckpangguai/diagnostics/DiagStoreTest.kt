package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.DiagEvent
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.StageRecord
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DiagStoreTest {

    private lateinit var root: File
    private var now: Long = 0L

    @Before
    fun setUp() {
        root = Files.createTempDirectory("diag-store").toFile()
        now = Instant.parse("2026-10-20T04:00:00Z").toEpochMilli()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun store() = DiagStore(root) { now }

    private fun event(at: Long, name: String = "test") = DiagEvent(
        id = "$at-$name",
        at = at,
        level = DiagLevel.Info,
        flow = DiagFlow.App,
        name = name,
    )

    private fun trace(
        id: String,
        updatedAt: Long,
        outcome: TraceOutcome = TraceOutcome.Running,
    ) = WaterTrace(
        traceId = id,
        startedAt = updatedAt - 1_000,
        updatedAt = updatedAt,
        deviceName = "水机",
        outcome = outcome,
        stages = listOf(
            StageRecord(
                stage = TraceStage.ScanDecode,
                startedAt = updatedAt - 1_000,
                endedAt = updatedAt - 900,
                durationMs = 100,
                outcome = StageOutcome.Success,
            ),
        ),
    )

    @Test
    fun `events are read newest first and bad lines are skipped`() {
        val store = store()
        store.appendEvent(event(now - 1_000, "old"))
        store.appendEvent(event(now, "new"))
        val day = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
        File(root, "events/$day.ndjson").appendText("{broken\n")

        val events = store.readEvents()

        assertEquals(listOf("new", "old"), events.map { it.name })
    }

    @Test
    fun `trace snapshots keep the latest version per trace id`() {
        val store = store()
        store.appendTrace(trace("t1", now - 5_000))
        store.appendTrace(trace("t1", now, TraceOutcome.Success))
        store.appendTrace(trace("t2", now - 1_000))

        val traces = store.readTraces()

        assertEquals(2, traces.size)
        assertEquals(TraceOutcome.Success, traces.first { it.traceId == "t1" }.outcome)
        assertEquals("t1", traces.first().traceId)
    }

    @Test
    fun `cleanup deletes day files older than retention`() {
        val store = store()
        val old = now - 10L * 24 * 60 * 60 * 1000
        store.appendEvent(event(old, "expired"))
        store.appendEvent(event(now, "today"))

        store.cleanup(retentionDays = 7, maxBytes = 100L * 1024 * 1024)

        assertEquals(listOf("today"), store.readEvents().map { it.name })
    }

    @Test
    fun `cleanup by size removes oldest files but keeps today`() {
        val store = store()
        val threeDaysAgo = now - 3L * 24 * 60 * 60 * 1000
        store.appendEvent(event(threeDaysAgo, "big").copy(message = "x".repeat(700_000)))
        store.appendEvent(event(now, "today"))

        store.cleanup(retentionDays = 30, maxBytes = 1L)

        val events = store.readEvents()
        assertEquals(listOf("today"), events.map { it.name })
        assertTrue(store.totalBytes() < 512L * 1024L)
    }

    @Test
    fun `crash entries survive and are read newest first`() {
        val store = store()
        store.appendCrash(crash(now - 1_000, "first"))
        store.appendCrash(crash(now, "second"))

        assertEquals(listOf("second", "first"), store.readCrashes().map { it.exception })
    }

    @Test
    fun `clear removes everything and counts stay consistent`() {
        val store = store()
        store.appendEvent(event(now))
        store.appendTrace(trace("t1", now))
        store.appendCrash(crash(now, "boom"))

        assertEquals(1, store.eventLineCount())
        assertEquals(1, store.traceCount())
        assertEquals(1, store.crashCount())

        store.clear()

        assertEquals(0, store.eventLineCount())
        assertEquals(0, store.traceCount())
        assertEquals(0L, store.totalBytes())
    }

    private fun crash(at: Long, message: String) = io.github.winddrift.fuckpangguai.diagnostics.model.CrashEntry(
        id = "$at-$message",
        at = at,
        threadName = "main",
        exception = message,
        stack = "stack",
    )
}
