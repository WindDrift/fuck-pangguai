package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.CrashEntry
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicInteger

class CrashRecorder(
    private val appVersion: String,
    private val buildType: String,
    private val traceProvider: () -> WaterTrace?,
    private val onCrash: (CrashEntry) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var installed = false

    private val sequence = AtomicInteger(0)

    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { handle(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun handle(thread: Thread, error: Throwable) {
        val entry = buildEntry(thread, error)
        runCatching { onCrash(entry) }
    }

    internal fun buildEntry(thread: Thread, error: Throwable): CrashEntry {
        val at = clock()
        val trace = runCatching(traceProvider).getOrNull()
        return CrashEntry(
            id = "$at-${sequence.incrementAndGet()}",
            at = at,
            threadName = thread.name.take(128),
            exception = error.toString().take(MAX_EXCEPTION_LENGTH),
            stack = stackTrace(error),
            traceId = trace?.traceId.orEmpty(),
            trace = trace,
            appVersion = appVersion,
            buildType = buildType,
        )
    }

    private fun stackTrace(error: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { error.printStackTrace(it) }
        return writer.toString()
            .take(MAX_STACK_CHARS)
    }

    private companion object {
        const val MAX_STACK_CHARS = 16 * 1024
        const val MAX_EXCEPTION_LENGTH = 512
    }
}
