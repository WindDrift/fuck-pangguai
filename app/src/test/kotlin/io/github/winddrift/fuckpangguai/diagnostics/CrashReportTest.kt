package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {

    private val trace = WaterTrace(
        traceId = "trace-crash",
        startedAt = 1_000,
        outcome = TraceOutcome.Running,
    )

    @Test
    fun `crash entry captures exception stack and current trace`() {
        val recorder = CrashRecorder(
            appVersion = "1.14.0-abc1234",
            buildType = "diagnostic",
            traceProvider = { trace },
            onCrash = {},
            clock = { 42L },
        )

        val entry = recorder.buildEntry(Thread.currentThread(), IllegalStateException("boom"))

        assertEquals(42L, entry.at)
        assertTrue(entry.exception.contains("IllegalStateException"))
        assertTrue(entry.exception.contains("boom"))
        assertTrue(entry.stack.contains("CrashReportTest"))
        assertEquals("trace-crash", entry.traceId)
        assertEquals(trace, entry.trace)
        assertEquals("diagnostic", entry.buildType)
    }

    @Test
    fun `crash without a current trace still records the stack`() {
        val recorder = CrashRecorder(
            appVersion = "1.14.0-abc1234",
            buildType = "release",
            traceProvider = { null },
            onCrash = {},
        )

        val entry = recorder.buildEntry(Thread.currentThread(), RuntimeException("oops"))

        assertEquals("", entry.traceId)
        assertEquals(null, entry.trace)
        assertTrue(entry.stack.isNotBlank())
    }

    @Test
    fun `handle delivers the crash entry once`() {
        val delivered = mutableListOf<String>()
        val recorder = CrashRecorder(
            appVersion = "v",
            buildType = "release",
            traceProvider = { null },
            onCrash = { delivered += it.exception },
        )

        recorder.handle(Thread.currentThread(), IllegalStateException("handled"))

        assertEquals(1, delivered.size)
        assertTrue(delivered.single().contains("handled"))
    }
}
