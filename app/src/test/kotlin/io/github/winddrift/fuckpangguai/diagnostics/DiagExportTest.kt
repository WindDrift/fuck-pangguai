package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.CrashEntry
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagEvent
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.StageRecord
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStage
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import io.github.winddrift.fuckpangguai.diagnostics.model.computeTraceStats
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class DiagExportTest {

    private val trace = WaterTrace(
        traceId = "trace-1",
        startedAt = 1_000,
        updatedAt = 5_000,
        deviceName = "水机",
        goodsId = "goods-1",
        source = "Qr",
        decodeMs = 120,
        outcome = TraceOutcome.Success,
        endedAt = 5_000,
        stages = listOf(
            StageRecord(
                stage = TraceStage.ScanDecode,
                startedAt = 1_000,
                endedAt = 1_120,
                durationMs = 120,
                outcome = StageOutcome.Success,
                attempts = 1,
            ),
            StageRecord(
                stage = TraceStage.DeviceResolve,
                startedAt = 1_120,
                endedAt = 1_920,
                durationMs = 800,
                outcome = StageOutcome.Success,
                attempts = 2,
                fields = mapOf("goodsId" to "goods-1"),
            ),
        ),
    )

    private val event = DiagEvent(
        id = "1",
        at = 1_000,
        level = DiagLevel.Info,
        flow = DiagFlow.Scan,
        name = "qr_accepted",
        message = "来源 Qr",
        traceId = "trace-1",
        fields = mapOf("source" to "Qr"),
    )

    private val crash = CrashEntry(
        id = "2",
        at = 2_000,
        threadName = "main",
        exception = "java.lang.IllegalStateException: boom",
        stack = "at io.github.winddrift.fuckpangguai.MainActivity.onCreate(MainActivity.kt:1)",
        traceId = "trace-1",
        trace = trace,
    )

    private fun input() = DiagExportInput(
        generatedAt = 10_000,
        appVersion = "1.14.0-abc1234",
        buildType = "release",
        settings = DiagSettings(),
        events = listOf(event),
        traces = listOf(trace),
        crashes = listOf(crash),
        stats = computeTraceStats(listOf(trace)),
        filterDescription = "全部",
    )

    @Test
    fun `zip contains text json and readme`() {
        val entries = readZip(DiagExport.buildZip(input()))

        assertEquals(setOf("diagnostics.txt", "diagnostics.json", "README.txt"), entries.keys)
        val text = entries.getValue("diagnostics.txt")
        assertTrue(text.contains("trace-1"))
        assertTrue(text.contains("扫码识别"))
        assertTrue(text.contains("qr_accepted"))
        assertTrue(text.contains("IllegalStateException"))

        val json = Json.parseToJsonElement(entries.getValue("diagnostics.json")).jsonObject
        assertEquals(1, json.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(
            1,
            json.getValue("counts").jsonObject.getValue("traces").jsonPrimitive.int,
        )
        assertTrue(json.getValue("traces").toString().contains("trace-1"))

        assertTrue(entries.getValue("README.txt").contains("脱敏"))
    }

    @Test
    fun `json keeps full event and trace fields`() {
        val json = DiagExport.buildJson(input())

        assertTrue(json.contains("\"fields\""))
        assertTrue(json.contains("goods-1"))
        assertTrue(json.contains("\"buildType\": \"release\""))
        assertTrue(json.contains("\"avgDurationMs\""))
    }

    @Test
    fun `text warns about running traces and crash trace`() {
        val running = trace.copy(outcome = TraceOutcome.Running, endedAt = 0L)
        val text = DiagExport.buildText(
            input().copy(traces = listOf(running), stats = computeTraceStats(listOf(running))),
        )

        assertTrue(text.contains("仍在进行"))
        assertTrue(text.contains("当前 trace"))
    }

    private fun readZip(bytes: ByteArray): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return result
    }
}
