package io.github.winddrift.fuckpangguai.confirm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class UnlockOutcome {
    Unlocked,
    Failed,
}

/**
 * 一次解锁的分阶段耗时。
 *
 * 只记录时间与通道信息，不保存订单号、消息 ID、设备标识、坐标或任何登录凭据。
 */
data class UnlockTiming(
    val atMillis: Long,
    val outcome: UnlockOutcome,
    val acceleration: UnlockAcceleration = UnlockAcceleration.Conservative,
    val channelMethod: Int,
    val channelName: String,
    val prebindAttempted: Boolean,
    val prebound: Boolean,
    val bindSkipped: Boolean,
    val bindWaitMs: Long,
    val bindCallMs: Long,
    val unlockCallMs: Long,
    val syncPollCount: Int,
    val syncDurationMs: Long,
    val syncSource: String = "",
    val deviceStartProbes: Int = 0,
    val totalMs: Long,
    val errorKind: String? = null,
) {
    fun toText(): String {
        val result = if (outcome == UnlockOutcome.Unlocked) "成功" else "失败"
        val channel = channelName.takeIf { it.isNotBlank() }
            ?.let { "#$channelMethod $it" }
            ?: "#$channelMethod"
        val bind = when {
            prebound && bindSkipped -> "预绑定命中"
            prebound -> "等预绑定 ${bindWaitMs}ms"
            bindSkipped -> "已绑定，跳过"
            else -> "等待 ${bindWaitMs}ms + 调用 ${bindCallMs}ms"
        }
        val probe = if (syncSource == "deviceStart") {
            "（deviceStart 探测 ${deviceStartProbes} 次）"
        } else {
            ""
        }
        val error = errorKind?.let { " · $it" }.orEmpty()
        return "${timeText()} $result · ${acceleration.displayName} · 总 ${totalMs}ms｜$channel｜$bind" +
            "｜unlock ${unlockCallMs}ms｜同步 ${syncPollCount} 次/${syncDurationMs}ms$probe$error"
    }

    private fun timeText(): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()))

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

fun interface UnlockTimingSink {
    fun record(entry: UnlockTiming)
}

object NoopUnlockTimingSink : UnlockTimingSink {
    override fun record(entry: UnlockTiming) = Unit
}

/** 仅 Debug 注入的内存日志；最多保留 20 条，新记录在前。 */
object UnlockTimingLog : UnlockTimingSink {
    private const val MAX_ENTRIES = 20
    private val _entries = MutableStateFlow<List<UnlockTiming>>(emptyList())
    val entries = _entries.asStateFlow()

    override fun record(entry: UnlockTiming) {
        _entries.update { current -> (listOf(entry) + current).take(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
