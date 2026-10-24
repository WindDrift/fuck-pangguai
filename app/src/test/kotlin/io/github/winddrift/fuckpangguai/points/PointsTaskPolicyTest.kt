package io.github.winddrift.fuckpangguai.points

import io.github.winddrift.fuckpangguai.net.dto.IntegralTaskDto
import io.github.winddrift.fuckpangguai.net.dto.SubtaskDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointsTaskPolicyTest {

    @Test
    fun `task type 7 with empty extensions is eligible when experiment and notifications are on`() {
        val decision = PointsTaskPolicy.classify(
            task = task(type = 7, extendMap = JsonObject(emptyMap())),
            experimentEnabled = true,
            notificationsEnabled = true,
        )
        assertEquals(PointsTaskDecision.AutoEligible("task-1"), decision)
    }

    @Test
    fun `experiment off blocks task type 7`() {
        val decision = decisionFor(task(type = 7), experiment = false, notifications = true)
        assertEquals(PointsTaskDecision.Skipped(PointsSkipReason.ExperimentOff), decision)
    }

    @Test
    fun `notifications off blocks task type 7`() {
        val decision = decisionFor(task(type = 7), experiment = true, notifications = false)
        assertEquals(PointsTaskDecision.Skipped(PointsSkipReason.NotificationDisabled), decision)
    }

    @Test
    fun `jump link is skipped`() {
        val decision = decisionFor(task(type = 7, jumpLink = "https://example.com"))
        assertEquals(PointsTaskDecision.Skipped(PointsSkipReason.HasJumpLink), decision)
    }

    @Test
    fun `non empty extension map is skipped`() {
        val decision = decisionFor(
            task(type = 7, extendMap = JsonObject(mapOf("androidTagId" to JsonPrimitive("x")))),
        )
        assertEquals(PointsTaskDecision.Skipped(PointsSkipReason.HasExtension), decision)
    }

    @Test
    fun `subtasks are skipped outside home browse`() {
        val decision = decisionFor(task(type = 7, subtasks = listOf(SubtaskDto(subtaskCode = "s"))))
        assertEquals(PointsTaskDecision.Skipped(PointsSkipReason.HasSubtask), decision)
    }

    @Test
    fun `non point award is skipped`() {
        val decision = decisionFor(task(type = 7, awardType = 3))
        assertEquals(PointsTaskDecision.Skipped(PointsSkipReason.NotPoints), decision)
    }

    @Test
    fun `completed task is skipped`() {
        val decision = decisionFor(task(type = 7, completedStatus = 1))
        assertEquals(PointsTaskDecision.Skipped(PointsSkipReason.Completed), decision)
    }

    @Test
    fun `daily limit is enforced`() {
        val decision = decisionFor(
            task(type = 7, completedFreq = 2, dailyTaskLimit = 2),
        )
        assertEquals(PointsTaskDecision.Skipped(PointsSkipReason.LimitReached), decision)
    }

    @Test
    fun `unknown task type is skipped`() {
        val decision = decisionFor(task(type = null))
        assertEquals(PointsTaskDecision.Skipped(PointsSkipReason.UnknownType), decision)
    }

    @Test
    fun `other task types require manual action`() {
        val decision = decisionFor(task(type = 8))
        assertEquals(PointsTaskDecision.Skipped(PointsSkipReason.ExternalAction), decision)
    }

    @Test
    fun `task type can be a numeric string`() {
        val decision = decisionFor(task(typeElement = JsonPrimitive("7")))
        assertTrue(decision is PointsTaskDecision.AutoEligible)
    }

    private fun decisionFor(
        task: IntegralTaskDto,
        experiment: Boolean = true,
        notifications: Boolean = true,
    ): PointsTaskDecision = PointsTaskPolicy.classify(task, experiment, notifications)

    private fun task(
        type: Int? = 7,
        typeElement: kotlinx.serialization.json.JsonElement? = null,
        jumpLink: String = "",
        extendMap: kotlinx.serialization.json.JsonElement? = null,
        subtasks: List<SubtaskDto> = emptyList(),
        awardType: Int = 1,
        completedStatus: Int = 0,
        completedFreq: Int = 0,
        dailyTaskLimit: Int = 1,
    ) = IntegralTaskDto(
        taskCode = "task-1",
        taskType = typeElement ?: type?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull,
        title = "任务",
        awardType = awardType,
        awardNumber = 1,
        completedStatus = completedStatus,
        completedFreq = completedFreq,
        dailyTaskLimit = dailyTaskLimit,
        status = 1,
        jumpLink = jumpLink,
        extendMap = extendMap,
        subtaskList = subtasks,
    )
}
