package io.github.winddrift.fuckpangguai.points

import io.github.winddrift.fuckpangguai.net.dto.IntegralTaskDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object PointsTaskPolicy {

    const val HOME_BROWSE_TASK_CODE = "8b475b42-df8b-4039-b4c1-f9a0174a611a"

    fun classify(
        task: IntegralTaskDto,
        experimentEnabled: Boolean,
        notificationsEnabled: Boolean,
        farmEnabled: Boolean = false,
    ): PointsTaskDecision {
        if (task.taskCode.isBlank()) return skip(PointsSkipReason.EmptyCode)
        if (task.status == 0) return skip(PointsSkipReason.Disabled)
        if (task.completedStatus == 1) return skip(PointsSkipReason.Completed)
        if (limitReached(task)) return skip(PointsSkipReason.LimitReached)
        if (task.awardType != 1) return skip(PointsSkipReason.NotPoints)

        val taskType = task.taskType.asIntOrNull()
        if (farmEnabled) {
            if (taskType == 7 && !notificationsEnabled) return skip(PointsSkipReason.NotificationDisabled)
            return PointsTaskDecision.AutoEligible(task.taskCode)
        }

        if (task.jumpLink.isNotBlank()) return skip(PointsSkipReason.HasJumpLink)
        if (task.extendMap.isExtensionPresent()) return skip(PointsSkipReason.HasExtension)
        if (task.subtaskList.isNotEmpty()) return skip(PointsSkipReason.HasSubtask)

        if (taskType == null) return skip(PointsSkipReason.UnknownType)
        if (taskType != 7) return skip(PointsSkipReason.ExternalAction)
        if (!experimentEnabled) return skip(PointsSkipReason.ExperimentOff)
        if (!notificationsEnabled) return skip(PointsSkipReason.NotificationDisabled)
        return PointsTaskDecision.AutoEligible(task.taskCode)
    }

    fun limitReached(task: IntegralTaskDto): Boolean {
        if (task.dailyTaskLimit > 0 && task.completedFreq >= task.dailyTaskLimit) return true
        if (task.totalTaskLimit > 0 && task.completedFreq >= task.totalTaskLimit) return true
        return false
    }

    /** 刷积分模式下，单个任务本轮最多尝试的次数；次数上限未知时只试一次。 */
    fun remainingAttempts(task: IntegralTaskDto, cap: Int): Int {
        val daily = if (task.dailyTaskLimit > 0) task.dailyTaskLimit - task.completedFreq else Int.MAX_VALUE
        val total = if (task.totalTaskLimit > 0) task.totalTaskLimit - task.completedFreq else Int.MAX_VALUE
        if (daily == Int.MAX_VALUE && total == Int.MAX_VALUE) return 1
        return minOf(daily, total, cap).coerceAtLeast(0)
    }

    fun JsonElement?.asIntOrNull(): Int? =
        (this as? JsonPrimitive)?.content?.trim()?.toIntOrNull()

    fun JsonElement?.isExtensionPresent(): Boolean {
        if (this == null || this == JsonNull) return false
        return this !is JsonObject || this.isNotEmpty()
    }

    private fun skip(reason: PointsSkipReason): PointsTaskDecision =
        PointsTaskDecision.Skipped(reason)
}