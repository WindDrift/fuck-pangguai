package io.github.winddrift.fuckpangguai.points

import io.github.winddrift.fuckpangguai.account.StoredAccount
import io.github.winddrift.fuckpangguai.net.ApiException
import io.github.winddrift.fuckpangguai.net.dto.IntegralTaskDto
import io.github.winddrift.fuckpangguai.net.dto.SignInActDto
import io.github.winddrift.fuckpangguai.net.dto.SubtaskDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PointsControllerTest {

    @Test
    fun `app foreground signs in and completes home browse once per day`() = runTest {
        val deps = Deps()
        deps.settings.auto.value = true
        deps.settings.launch.value = true
        deps.data.queryTaskHandler = { code ->
            if (code == PointsTaskPolicy.HOME_BROWSE_TASK_CODE) homeTask() else null
        }
        val controller = deps.controller(this)
        advanceUntilIdle()

        controller.onAppForeground()
        advanceUntilIdle()

        assertEquals(1, deps.data.signInCalls)
        assertEquals(listOf("browse-task" to "browse-sub"), deps.data.completed)
        assertEquals("2026-10-24", deps.store.attemptDate)
        assertEquals("2026-10-24", deps.store.doneDate)
        assertEquals(1, controller.state.value.lastRun?.completed)

        controller.onAppForeground()
        advanceUntilIdle()
        assertEquals(1, deps.data.signInCalls)
    }

    @Test
    fun `manual run works while automatic switch is off`() = runTest {
        val deps = Deps()
        val controller = deps.controller(this)
        advanceUntilIdle()

        controller.runNow()
        advanceUntilIdle()

        assertEquals(1, deps.data.signInCalls)
        assertEquals("2026-10-24", deps.store.attemptDate)
        assertEquals("2026-10-24", deps.store.doneDate)
    }

    @Test
    fun `run on launch switch off still allows points page trigger`() = runTest {
        val deps = Deps()
        deps.settings.auto.value = true
        deps.settings.launch.value = false
        val controller = deps.controller(this)
        advanceUntilIdle()

        controller.onAppForeground()
        advanceUntilIdle()
        assertEquals(0, deps.data.signInCalls)

        controller.onScreenVisible()
        advanceUntilIdle()
        assertEquals(1, deps.data.signInCalls)
    }

    @Test
    fun `failed sign in does not mark done and does not auto retry same day`() = runTest {
        val deps = Deps()
        deps.settings.auto.value = true
        deps.settings.launch.value = true
        deps.data.signInError = ApiException(500, "签到失败")
        val controller = deps.controller(this)
        advanceUntilIdle()

        controller.onAppForeground()
        advanceUntilIdle()

        assertEquals(1, deps.data.signInCalls)
        assertEquals("2026-10-24", deps.store.attemptDate)
        assertEquals("", deps.store.doneDate)

        controller.onAppForeground()
        advanceUntilIdle()
        assertEquals(1, deps.data.signInCalls)
        assertFalse(controller.state.value.running)
        assertEquals("失败", controller.state.value.runStatus)
    }

    @Test
    fun `two captcha responses stop direct task phase`() = runTest {
        val deps = Deps()
        deps.settings.auto.value = true
        deps.settings.launch.value = true
        deps.settings.experiment.value = true
        deps.data.captcha = true
        deps.data.tasks = listOf(
            directTask("direct-1"),
            directTask("direct-2"),
        )
        val controller = deps.controller(this)
        advanceUntilIdle()

        controller.onAppForeground()
        advanceUntilIdle()

        assertEquals(0, deps.data.completed.size)
        assertEquals(2, controller.state.value.lastRun?.captchaBlocked)
        assertEquals(0, controller.state.value.lastRun?.completed)
    }

    @Test
    fun `experiment off skips task type 7 tasks`() = runTest {
        val deps = Deps()
        deps.settings.auto.value = true
        deps.settings.launch.value = true
        deps.settings.experiment.value = false
        deps.data.tasks = listOf(directTask("direct-1"))
        val controller = deps.controller(this)
        advanceUntilIdle()

        controller.onAppForeground()
        advanceUntilIdle()

        assertTrue(deps.data.completed.isEmpty())
        assertEquals(
            PointsTaskDecision.Skipped(PointsSkipReason.ExperimentOff),
            controller.state.value.tasks.first().decision,
        )
    }

    @Test
    fun `farm switch alone triggers launch run and completes all tasks`() = runTest {
        val deps = Deps()
        deps.settings.auto.value = false
        deps.settings.launch.value = true
        deps.settings.farm.value = true
        deps.data.tasks = listOf(
            IntegralTaskDto(
                taskCode = "pack",
                taskType = JsonPrimitive(621),
                title = "任务包",
                awardType = 1,
                completedStatus = 0,
                status = 1,
                subtaskList = listOf(
                    SubtaskDto(
                        taskCode = "pack-sub-task",
                        subtaskCode = "pack-sub",
                        completedStatus = 0,
                        dailyTaskLimit = 1,
                    ),
                ),
            ),
            IntegralTaskDto(
                taskCode = "video",
                taskType = JsonPrimitive(637),
                title = "看视频",
                awardType = 1,
                completedStatus = 0,
                status = 1,
                dailyTaskLimit = 2,
                extendMap = JsonPrimitive("ad"),
            ),
        )
        deps.data.queryTaskHandler = { null }
        val controller = deps.controller(this)
        advanceUntilIdle()

        controller.onAppForeground()
        advanceUntilIdle()

        assertEquals(1, deps.data.signInCalls)
        assertEquals(listOf("pack-sub-task" to "pack-sub"), deps.data.completed.take(1))
        assertEquals(2, deps.data.completed.count { it.first == "video" })
        assertTrue(controller.state.value.lastRun?.farm == true)
        assertEquals(3, controller.state.value.lastRun?.completed)
    }

    @Test
    fun `farm mode stops completing after two captcha responses`() = runTest {
        val deps = Deps()
        deps.settings.auto.value = true
        deps.settings.farm.value = true
        deps.data.captcha = true
        deps.data.tasks = listOf(directTask("task-1"), directTask("task-2"))
        deps.data.queryTaskHandler = { null }
        val controller = deps.controller(this)
        advanceUntilIdle()

        controller.onAppForeground()
        advanceUntilIdle()

        assertEquals(0, deps.data.completed.size)
        assertEquals(2, controller.state.value.lastRun?.captchaBlocked)
    }

    @Test
    fun `account switch resets points state`() = runTest {
        val deps = Deps()
        val controller = deps.controller(this)
        advanceUntilIdle()

        controller.refresh()
        advanceUntilIdle()
        assertEquals("account-a", controller.state.value.accountId)

        deps.account.active.value = StoredAccount(localId = "account-b")
        advanceUntilIdle()

        assertEquals("account-b", controller.state.value.accountId)
        assertTrue(controller.state.value.tasks.isEmpty())
        assertFalse(controller.state.value.running)
    }

    private fun homeTask() = IntegralTaskDto(
        taskCode = PointsTaskPolicy.HOME_BROWSE_TASK_CODE,
        taskType = JsonPrimitive(-1),
        title = "首页浏览",
        awardType = 1,
        awardNumber = 1,
        completedStatus = 0,
        status = 1,
        dailyTaskLimit = 1,
        subtaskList = listOf(
            SubtaskDto(
                taskCode = "browse-task",
                subtaskCode = "browse-sub",
                subtaskName = "浏览商品",
                awardNumber = 1,
                completedStatus = 0,
                dailyTaskLimit = 1,
            ),
        ),
    )

    private fun directTask(code: String) = IntegralTaskDto(
        taskCode = code,
        taskType = JsonPrimitive(7),
        title = "通知任务",
        awardType = 1,
        awardNumber = 1,
        completedStatus = 0,
        status = 1,
        dailyTaskLimit = 1,
    )

    private class Deps {
        val settings = FakeSettings()
        val data = FakeData()
        val store = FakeRunStore()
        val account = FakeAccount()

        fun controller(test: TestScope) = PointsController(
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(test.testScheduler)),
            accountSource = account,
            dataSource = data,
            runStore = store,
            settings = settings,
            clock = Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC),
            notificationEnabled = { true },
            jitterRange = 0L..0L,
            browseRange = 0L..0L,
            nowMillis = { 1_000L },
        )
    }

    private class FakeSettings : PointsSettingsSource {
        val auto = MutableStateFlow(false)
        val launch = MutableStateFlow(true)
        val experiment = MutableStateFlow(false)
        val farm = MutableStateFlow(false)
        val deduct = MutableStateFlow(false)
        val risk = MutableStateFlow(false)

        override val autoEnabled = auto
        override val runOnLaunch = launch
        override val experimentEnabled = experiment
        override val farmEnabled = farm
        override val deductEnabled = deduct
        override val riskAcknowledged = risk

        override suspend fun setAutoEnabled(enabled: Boolean) {
            auto.value = enabled
        }

        override suspend fun setRunOnLaunch(enabled: Boolean) {
            launch.value = enabled
        }

        override suspend fun setExperimentEnabled(enabled: Boolean) {
            experiment.value = enabled
        }

        override suspend fun setFarmEnabled(enabled: Boolean) {
            farm.value = enabled
        }

        override suspend fun setDeductEnabled(enabled: Boolean) {
            deduct.value = enabled
        }

        override suspend fun acknowledgeRisk() {
            risk.value = true
        }
    }

    private class FakeAccount : PointsAccountSource {
        val active = MutableStateFlow<StoredAccount?>(StoredAccount(localId = "account-a"))
        override val activeAccount = active
        override suspend fun initialize() = Unit
        override suspend fun hasActiveSession() = active.value != null
        override fun activeLocalId(): String? = active.value?.localId
    }

    private class FakeData : PointsDataSource {
        var balance = "400"
        var activity = SignInActDto(id = "activity-1", status = 1)
        var signedIn = false
        var tasks: List<IntegralTaskDto> = emptyList()
        var captcha = false
        var signInError: Throwable? = null
        var queryTaskHandler: ((String) -> IntegralTaskDto?)? = null
        var signInCalls = 0
        val completed = mutableListOf<Pair<String, String?>>()

        override suspend fun loadBalance(): String = balance

        override suspend fun loadSignInActivity(): SignInActDto = activity

        override suspend fun loadTodaySignIn(): Boolean = signedIn

        override suspend fun signIn(activityId: String) {
            signInCalls += 1
            signInError?.let { throw it }
            signedIn = true
        }

        override suspend fun loadTasks(): List<IntegralTaskDto> = tasks

        override suspend fun queryTask(taskCode: String): IntegralTaskDto =
            queryTaskHandler?.invoke(taskCode) ?: throw ApiException(404, "任务不存在")

        override suspend fun completeTask(taskCode: String, subtaskCode: String?) {
            completed += taskCode to subtaskCode
        }

        override suspend fun isCaptchaRequired(): Boolean = captcha
    }

    private class FakeRunStore : PointsRunStore {
        var attemptDate = ""
        var doneDate = ""
        var last: PointsRunRecord? = null

        override suspend fun load(accountId: String) = PointsRunSnapshot(attemptDate, doneDate, last)

        override suspend fun recordAttempt(accountId: String, date: String) {
            attemptDate = date
        }

        override suspend fun recordDone(accountId: String, date: String) {
            doneDate = date
        }

        override suspend fun saveRecord(accountId: String, record: PointsRunRecord) {
            last = record
        }
    }
}
