package io.github.winddrift.fuckpangguai.diagnostics.model

import kotlinx.serialization.Serializable

@Serializable
enum class DiagLevel { Debug, Info, Warn, Error }

@Serializable
enum class DiagFlow(
    val label: String,
) {
    App("应用"),
    Account("账号"),
    Scan("扫码"),
    Device("设备"),
    Confirm("确认/解锁"),
    Water("接水"),
    Network("网络"),
    History("历史"),
    Points("积分"),
    Ticket("小票"),
    Update("更新"),
    Crash("崩溃"),
    Other("其他"),
}

@Serializable
data class DiagEvent(
    val id: String,
    val at: Long,
    val level: DiagLevel,
    val flow: DiagFlow,
    val name: String,
    val message: String = "",
    val traceId: String = "",
    val fields: Map<String, String> = emptyMap(),
)

@Serializable
enum class TraceStage(
    val label: String,
) {
    ScanDecode("扫码识别"),
    DeviceResolve("设备解析"),
    ConfirmReady("确认页就绪"),
    Unlock("解锁"),
    WaterStart("出水确认"),
    WaterEnd("结束接水"),
    SettlePay("结算支付"),
}

@Serializable
enum class StageOutcome(
    val label: String,
) {
    Running("进行中"),
    Success("成功"),
    Failed("失败"),
    Abandoned("放弃"),
}

@Serializable
data class StageRecord(
    val stage: TraceStage,
    val startedAt: Long,
    val endedAt: Long = 0L,
    val durationMs: Long = 0L,
    val outcome: StageOutcome = StageOutcome.Running,
    val attempts: Int = 0,
    val error: String = "",
    val fields: Map<String, String> = emptyMap(),
) {
    val reached: Boolean get() = outcome != StageOutcome.Running
}

@Serializable
enum class TraceOutcome(
    val label: String,
) {
    Running("进行中"),
    Success("成功"),
    Failed("失败"),
    Abandoned("放弃"),
    Interrupted("中断"),
}

@Serializable
data class WaterTrace(
    val traceId: String,
    val startedAt: Long,
    val updatedAt: Long = startedAt,
    val source: String = "",
    val deviceName: String = "",
    val goodsId: String = "",
    val decodeMs: Long = 0L,
    val stages: List<StageRecord> = emptyList(),
    val outcome: TraceOutcome = TraceOutcome.Running,
    val endedAt: Long = 0L,
    val error: String = "",
    val waterDurationMs: Long = 0L,
    val endOperationMs: Long = 0L,
) {
    val totalDurationMs: Long
        get() = ((if (endedAt > 0L) endedAt else updatedAt) - startedAt).coerceAtLeast(0L)

    fun stage(stage: TraceStage): StageRecord? = stages.firstOrNull { it.stage == stage }

    fun withStage(
        stage: TraceStage,
        block: (StageRecord?) -> StageRecord,
    ): WaterTrace {
        val existing = stage(stage)
        val updated = block(existing)
        val merged = (stages.filterNot { it.stage == stage } + updated)
            .sortedBy { it.stage.ordinal }
        return copy(stages = merged)
    }
}

@Serializable
data class CrashEntry(
    val id: String,
    val at: Long,
    val threadName: String,
    val exception: String,
    val stack: String,
    val traceId: String = "",
    val trace: WaterTrace? = null,
    val appVersion: String = "",
    val buildType: String = "",
)
