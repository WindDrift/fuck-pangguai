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
    ): PointsTaskDecision {
        if (task.taskCode.isBlank()) return skip(PointsSkipReason.EmptyCode)
        if (task.status == 0) return skip(PointsSkipReason.Disabled)
        if (task.completedStatus == 1) return skip(PointsSkipReason.Completed)
        if (limitReached(task)) return skip(PointsSkipReason.LimitReached)
        if (task.awardType != 1) return skip(PointsSkipReason.NotPoints)
        if (task.jumpLink.isNotBlank()) return skip(PointsSkipReason.HasJumpLink)
        if (task.extendMap.isExtensionPresent()) return skip(PointsSkipReason.HasExtension)
        if (task.subtaskList.isNotEmpty()) return skip(PointsSkipReason.HasSubtask)

        val taskType = task.taskType.asIntOrNull() ?: return skip(PointsSkipReason.UnknownType)
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

    fun JsonElement?.asIntOrNull(): Int? =
        (this as? JsonPrimitive)?.content?.trim()?.toIntOrNull()

    fun JsonElement?.isExtensionPresent(): Boolean {
        if (this == null || this == JsonNull) return false
        return this !is JsonObject || this.isNotEmpty()
    }

    private fun skip(reason: PointsSkipReason): PointsTaskDecision =
        PointsTaskDecision.Skipped(reason)
}
