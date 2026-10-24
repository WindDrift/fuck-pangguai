package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.net.dto.ApiResponse
import io.github.winddrift.fuckpangguai.net.dto.IntegralTaskDto
import io.github.winddrift.fuckpangguai.net.dto.SignInActDto
import io.github.winddrift.fuckpangguai.net.dto.TodaySignInDto
import io.github.winddrift.fuckpangguai.points.PointsLogEntry
import io.github.winddrift.fuckpangguai.points.PointsLogLevel
import io.github.winddrift.fuckpangguai.points.PointsRunRecord
import io.github.winddrift.fuckpangguai.points.PointsTaskPolicy
import io.github.winddrift.fuckpangguai.points.PointsTrigger
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointsDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `task type supports number string and missing`() {
        val numeric = json.decodeFromString<IntegralTaskDto>(
            """{"taskCode":"a","taskType":7,"status":1,"awardType":1,"unknown":"ignored"}""",
        )
        assertEquals(7, PointsTaskPolicy.run { numeric.taskType.asIntOrNull() })

        val textual = json.decodeFromString<IntegralTaskDto>(
            """{"taskCode":"b","taskType":"7","status":1,"awardType":1}""",
        )
        assertEquals(7, PointsTaskPolicy.run { textual.taskType.asIntOrNull() })

        val missing = json.decodeFromString<IntegralTaskDto>(
            """{"taskCode":"c","status":1,"awardType":1}""",
        )
        assertEquals(null, PointsTaskPolicy.run { missing.taskType.asIntOrNull() })
    }

    @Test
    fun `empty and null extensions are not present`() {
        val empty = json.decodeFromString<IntegralTaskDto>(
            """{"taskCode":"a","extendMap":{},"status":1,"awardType":1}""",
        )
        assertFalse(PointsTaskPolicy.run { empty.extendMap.isExtensionPresent() })

        val missing = json.decodeFromString<IntegralTaskDto>(
            """{"taskCode":"b","status":1,"awardType":1}""",
        )
        assertFalse(PointsTaskPolicy.run { missing.extendMap.isExtensionPresent() })

        val content = json.decodeFromString<IntegralTaskDto>(
            """{"taskCode":"c","extendMap":{"androidTagId":"x"},"status":1,"awardType":1}""",
        )
        assertTrue(PointsTaskPolicy.run { content.extendMap.isExtensionPresent() })
    }

    @Test
    fun `sign in activity and today status parse`() {
        val activity = json.decodeFromString<ApiResponse<SignInActDto>>(
            """{"code":0,"data":{"id":"600001","status":1,"info":"ignored"}}""",
        )
        assertEquals("600001", activity.data?.id)
        assertEquals(1, activity.data?.status)

        val today = json.decodeFromString<ApiResponse<TodaySignInDto>>(
            """{"code":0,"data":{"isSignIn":true}}""",
        )
        assertEquals(true, today.data?.isSignIn)
    }

    @Test
    fun `subtask list parses and missing fields default`() {
        val task = json.decodeFromString<IntegralTaskDto>(
            """{"taskCode":"a","taskType":1,"status":1,"awardType":1,
               "subtaskList":[{"taskCode":"t","subtaskCode":"s","completedStatus":0}]}""",
        )
        assertEquals(1, task.subtaskList.size)
        assertEquals("s", task.subtaskList.first().subtaskCode)
        assertEquals(0, task.subtaskList.first().awardNumber)
    }

    @Test
    fun `run record survives serialization`() {
        val record = PointsRunRecord(
            date = "2026-10-24",
            trigger = PointsTrigger.PointsPage,
            signIn = "已完成",
            attempted = 2,
            completed = 1,
            skipped = 1,
            captchaBlocked = 1,
            logs = listOf(
                PointsLogEntry(at = 1L, level = PointsLogLevel.Warning, message = "任务需要验证"),
            ),
        )
        val raw = json.encodeToString(PointsRunRecord.serializer(), record)
        assertEquals(record, json.decodeFromString(PointsRunRecord.serializer(), raw))
    }
}
