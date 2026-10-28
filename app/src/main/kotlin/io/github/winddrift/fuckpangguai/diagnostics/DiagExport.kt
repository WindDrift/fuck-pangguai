package io.github.winddrift.fuckpangguai.diagnostics

import io.github.winddrift.fuckpangguai.diagnostics.model.CrashEntry
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagEvent
import io.github.winddrift.fuckpangguai.diagnostics.model.StageOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceOutcome
import io.github.winddrift.fuckpangguai.diagnostics.model.TraceStats
import io.github.winddrift.fuckpangguai.diagnostics.model.WaterTrace
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DiagExportInput(
    val generatedAt: Long,
    val appVersion: String,
    val buildType: String,
    val settings: DiagSettings,
    val events: List<DiagEvent>,
    val traces: List<WaterTrace>,
    val crashes: List<CrashEntry>,
    val stats: TraceStats,
    val filterDescription: String = "",
    val filteredStats: TraceStats? = null,
)

object DiagExport {

    const val SCHEMA_VERSION = 1
    const val TXT_NAME = "diagnostics.txt"
    const val JSON_NAME = "diagnostics.json"
    const val README_NAME = "README.txt"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val zone: ZoneId = ZoneId.systemDefault()

    fun buildZip(input: DiagExportInput): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(TXT_NAME))
            zip.write(buildText(input).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(JSON_NAME))
            zip.write(buildJson(input).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(README_NAME))
            zip.write(readme().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    fun buildJson(input: DiagExportInput): String {
        val document = ExportDocument(
            schemaVersion = SCHEMA_VERSION,
            generatedAt = input.generatedAt,
            app = AppInfo(input.appVersion, input.buildType),
            settings = input.settings,
            filter = input.filterDescription,
            counts = Counts(
                events = input.events.size,
                traces = input.traces.size,
                crashes = input.crashes.size,
            ),
            stats = input.stats,
            filteredStats = input.filteredStats,
            events = input.events,
            traces = input.traces,
            crashes = input.crashes,
        )
        return json.encodeToString(document)
    }

    fun buildText(input: DiagExportInput): String = buildString {
        appendLine("胖乖生活 诊断日志")
        appendLine("生成时间: ${time(input.generatedAt)}")
        appendLine("版本: ${input.appVersion} (${input.buildType})")
        appendLine(
            "日志设置: ${if (input.settings.enabled) "开启" else "关闭"}，" +
                "保留 ${input.settings.retentionDays} 天，" +
                "上限 ${input.settings.maxMb} MB",
        )
        if (input.filterDescription.isNotBlank()) {
            appendLine("当前筛选: ${input.filterDescription}")
        }
        appendLine(
            "计数: 事件 ${input.events.size} · 接水 ${input.traces.size} · " +
                "崩溃 ${input.crashes.size}",
        )
        appendLine()
        appendStats(input.stats)
        if (input.filteredStats != null) {
            appendLine()
            appendLine("== 当前筛选统计 ==")
            appendStats(input.filteredStats)
        }
        appendLine()
        appendLine("== 接水全链路 ==")
        if (input.traces.isEmpty()) {
            appendLine("（无）")
        } else {
            input.traces.forEach { trace ->
                appendTrace(trace)
            }
        }
        appendLine()
        appendLine("== 事件 ==")
        if (input.events.isEmpty()) {
            appendLine("（无）")
        } else {
            input.events.forEach { event ->
                appendLine(
                    "${time(event.at)} [${event.level}] ${event.flow.label}/${event.name}" +
                        if (event.traceId.isNotBlank()) " trace=${event.traceId}" else "",
                )
                if (event.message.isNotBlank()) appendLine("  ${event.message}")
                if (event.fields.isNotEmpty()) {
                    event.fields.forEach { (key, value) ->
                        appendLine("  $key=$value")
                    }
                }
            }
        }
        appendLine()
        appendLine("== 崩溃 ==")
        if (input.crashes.isEmpty()) {
            appendLine("（无）")
        } else {
            input.crashes.forEach { crash ->
                appendLine("${time(crash.at)} ${crash.threadName} ${crash.exception}")
                if (crash.traceId.isNotBlank()) appendLine("  trace=${crash.traceId}")
                crash.trace?.let { trace ->
                    appendLine(
                        "  当前 trace: ${trace.traceId} ${trace.outcome.label} " +
                            trace.stages.joinToString(", ") { "${it.stage.label}=${it.outcome.label}" },
                    )
                }
                appendLine(crash.stack)
                appendLine()
            }
        }
    }

    fun readme(): String = """
        胖乖生活 诊断导出
        ================

        diagnostics.txt  — 人类可读的事件、接水全链路与崩溃摘要
        diagnostics.json — 同内容的机器可读版本（schemaVersion=$SCHEMA_VERSION）

        字段说明
        --------
        events[]  应用关键事件：时间、级别、类别、名称、消息、traceId 与字段。
        traces[]  一次接水全链路：7 个阶段（扫码识别、设备解析、确认页就绪、
                  解锁、出水确认、结束接水、结算支付）的开始/结束时间、耗时、
                  结果、尝试次数与错误。
        crashes[] 未处理异常堆栈与崩溃时的当前 trace。

        脱敏规则
        --------
        - 不保存请求体、请求头、原始二维码、SN/IMEI、坐标、token、手机号。
        - 网络事件只保留方法、路径、状态码、耗时与脱敏后的响应预览。
        - 响应中的 token/手机号/证件号/定位等字段替换为 <redacted>；
          非 JSON 响应不保存原文。
        - 设备只保留名称与 goodsId；账号只保留本地 localId。
        - 订单号、msgId 等仍可能用于关联，导出前请确认分享范围。

        隐私提醒
        --------
        导出物可能包含设备标识、订单号与脱敏响应片段。分享或上传到公开渠道前，
        请逐项检查并手动打码。应用不会自动上传这些日志。
    """.trimIndent()

    private fun StringBuilder.appendStats(stats: TraceStats) {
        appendLine("== 统计 ==")
        appendLine(
            "接水总计 ${stats.total} · 成功 ${stats.success} · 失败 ${stats.failed} · " +
                "放弃 ${stats.abandoned} · 进行中 ${stats.running} · 中断 ${stats.interrupted}",
        )
        appendLine("总耗时平均 ${stats.avgTotalDurationMs}ms · 最大 ${stats.maxTotalDurationMs}ms")
        stats.stages.forEach { stage ->
            appendLine(
                "[${stage.stage.label}] 成功 ${stage.success} · 失败 ${stage.failed} · " +
                    "放弃 ${stage.abandoned} · 进行中 ${stage.running} · " +
                    "平均 ${stage.avgDurationMs}ms · 最大 ${stage.maxDurationMs}ms · " +
                    "参与 ${stage.participants} · " +
                    "平均尝试 ${String.format(Locale.US, "%.1f", stage.avgAttempts)}",
            )
        }
    }

    private fun StringBuilder.appendTrace(trace: WaterTrace) {
        appendLine(
            "[${trace.traceId}] ${time(trace.startedAt)} ${trace.deviceName.ifBlank { "未知设备" }}" +
                " goodsId=${trace.goodsId.ifBlank { "-" }} · ${trace.outcome.label}" +
                " · 总耗时 ${trace.totalDurationMs}ms" +
                if (trace.source.isNotBlank()) " · 来源 ${trace.source}" else "",
        )
        if (trace.waterDurationMs > 0L) appendLine("  用水时长 ${trace.waterDurationMs}ms")
        if (trace.endOperationMs > 0L) appendLine("  结束操作耗时 ${trace.endOperationMs}ms")
        if (trace.error.isNotBlank()) appendLine("  错误: ${trace.error}")
        trace.stages.forEach { record ->
            val result = when (record.outcome) {
                StageOutcome.Running -> "进行中"
                StageOutcome.Success -> "成功"
                StageOutcome.Failed -> "失败"
                StageOutcome.Abandoned -> "放弃"
            }
            appendLine(
                "  ${record.stage.label}: $result ${record.durationMs}ms" +
                    " attempts=${record.attempts}" +
                    if (record.error.isNotBlank()) " error=${record.error}" else "",
            )
            record.fields.forEach { (key, value) ->
                appendLine("    $key=$value")
            }
        }
        if (trace.outcome == TraceOutcome.Running) appendLine("  （仍在进行）")
        appendLine()
    }

    private fun time(at: Long): String =
        timeFormat.format(Instant.ofEpochMilli(at).atZone(zone))

    @Serializable
    private data class ExportDocument(
        val schemaVersion: Int,
        val generatedAt: Long,
        val app: AppInfo,
        val settings: DiagSettings,
        val filter: String,
        val counts: Counts,
        val stats: TraceStats,
        val filteredStats: TraceStats? = null,
        val events: List<DiagEvent>,
        val traces: List<WaterTrace>,
        val crashes: List<CrashEntry>,
    )

    @Serializable
    private data class AppInfo(
        val version: String,
        val buildType: String,
    )

    @Serializable
    private data class Counts(
        val events: Int,
        val traces: Int,
        val crashes: Int,
    )
}
