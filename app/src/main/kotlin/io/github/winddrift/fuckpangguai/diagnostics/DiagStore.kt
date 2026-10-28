package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.CrashEntry
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagEvent
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DiagStore(
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val eventsDir = File(root, "events")
    private val tracesDir = File(root, "traces")
    private val crashesDir = File(root, "crashes")

    @Synchronized
    fun appendEvent(event: DiagEvent) {
        append(eventsDir, event.at, json.encodeToString(DiagEvent.serializer(), event))
    }

    @Synchronized
    fun appendTrace(trace: WaterTrace) {
        append(tracesDir, trace.updatedAt, json.encodeToString(WaterTrace.serializer(), trace))
    }

    @Synchronized
    fun appendCrash(entry: CrashEntry) {
        append(crashesDir, entry.at, json.encodeToString(CrashEntry.serializer(), entry))
    }

    fun readEvents(limit: Int = DEFAULT_EVENT_LIMIT): List<DiagEvent> =
        readLines(eventsDir)
            .mapNotNull { line -> runCatching { json.decodeFromString(DiagEvent.serializer(), line) }.getOrNull() }
            .sortedByDescending { it.at }
            .take(limit)

    fun readTraces(limit: Int = DEFAULT_TRACE_LIMIT): List<WaterTrace> {
        val latest = HashMap<String, WaterTrace>()
        readLines(tracesDir).forEach { line ->
            val trace = runCatching {
                json.decodeFromString(WaterTrace.serializer(), line)
            }.getOrNull() ?: return@forEach
            val existing = latest[trace.traceId]
            if (existing == null || trace.updatedAt >= existing.updatedAt) {
                latest[trace.traceId] = trace
            }
        }
        return latest.values
            .sortedByDescending { it.startedAt }
            .take(limit)
    }

    fun readCrashes(limit: Int = DEFAULT_CRASH_LIMIT): List<CrashEntry> =
        readLines(crashesDir)
            .mapNotNull { line -> runCatching { json.decodeFromString(CrashEntry.serializer(), line) }.getOrNull() }
            .sortedByDescending { it.at }
            .take(limit)

    @Synchronized
    fun cleanup(retentionDays: Int, maxBytes: Long) {
        val now = clock()
        val today = dayOf(now)
        val cutoff = today.minusDays(retentionDays.coerceAtLeast(1).toLong())
        listOf(eventsDir, tracesDir, crashesDir).forEach { dir ->
            dayFiles(dir).forEach { file ->
                val date = dateOf(file) ?: return@forEach
                if (date < cutoff) file.delete()
            }
        }

        val files = listOf(eventsDir, tracesDir, crashesDir)
            .flatMap(::dayFiles)
            .filter { dateOf(it) != today }
            .sortedWith(compareBy<File>({ dateOf(it) }, { it.name }))
        var total = totalBytes()
        val budget = maxBytes.coerceAtLeast(MIN_BUDGET_BYTES)
        for (file in files) {
            if (total <= budget) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    @Synchronized
    fun clear() {
        root.deleteRecursively()
    }

    fun totalBytes(): Long =
        listOf(root, eventsDir, tracesDir, crashesDir)
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile }.toList() }
            .distinct()
            .sumOf { it.length() }

    fun eventLineCount(): Int = countLines(eventsDir)

    fun traceCount(): Int = readTraces(Int.MAX_VALUE).size

    fun crashCount(): Int = readCrashes(Int.MAX_VALUE).size

    private fun append(dir: File, at: Long, line: String) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            File(dir, "${dayOf(at)}.ndjson").appendText(line + "\n")
        }
    }

    private fun readLines(dir: File): List<String> {
        if (!dir.isDirectory) return emptyList()
        return dayFiles(dir)
            .flatMap { file ->
                runCatching {
                    file.bufferedReader().use { reader ->
                        reader.lineSequence().filter { it.isNotBlank() }.toList()
                    }
                }.getOrDefault(emptyList())
            }
    }

    private fun countLines(dir: File): Int {
        if (!dir.isDirectory) return 0
        return dayFiles(dir).sumOf { file ->
            runCatching {
                file.bufferedReader().use { reader ->
                    reader.lineSequence().count { it.isNotBlank() }
                }
            }.getOrDefault(0)
        }
    }

    private fun dayFiles(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(NDJSON_SUFFIX) }
            .sortedBy { it.name }
    }

    private fun dateOf(file: File): LocalDate? = runCatching {
        LocalDate.parse(file.name.removeSuffix(NDJSON_SUFFIX))
    }.getOrNull()

    private fun dayOf(at: Long): LocalDate =
        Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    companion object {
        const val DEFAULT_EVENT_LIMIT = 2_000
        const val DEFAULT_TRACE_LIMIT = 300
        const val DEFAULT_CRASH_LIMIT = 20
        private const val NDJSON_SUFFIX = ".ndjson"
        private const val MIN_BUDGET_BYTES = 512L * 1024L
    }
}
