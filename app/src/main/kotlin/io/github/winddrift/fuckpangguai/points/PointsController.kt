package io.github.winddrift.fuckpangguai.points

import io.github.winddrift.fuckpangguai.diagnostics.DiagSink
import io.github.winddrift.fuckpangguai.diagnostics.NoopDiagSink
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import io.github.winddrift.fuckpangguai.net.ApiException
import io.github.winddrift.fuckpangguai.net.dto.IntegralTaskDto
import io.github.winddrift.fuckpangguai.net.dto.SubtaskDto
import io.github.winddrift.fuckpangguai.net.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import kotlin.random.Random

class PointsController(
    private val scope: CoroutineScope,
    private val accountSource: PointsAccountSource,
    private val dataSource: PointsDataSource,
    private val runStore: PointsRunStore,
    private val settings: PointsSettingsSource,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val notificationEnabled: () -> Boolean = { false },
    private val onPointsChanged: () -> Unit = {},
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val jitterRange: LongRange = 1_500L..3_000L,
    private val browseRange: LongRange = 3_000L..8_000L,
    private val random: Random = Random.Default,
    private val diagnostics: DiagSink = NoopDiagSink,
) {
    private val _state = MutableStateFlow(PointsUiState())
    val state = _state.asStateFlow()

    private var runJob: Job? = null
    private var refreshJob: Job? = null
    private var rawTasks: List<IntegralTaskDto> = emptyList()

    init {
        scope.launch {
            settings.autoEnabled.collect { enabled ->
                _state.update { it.copy(autoEnabled = enabled) }
            }
        }
        scope.launch {
            settings.runOnLaunch.collect { enabled ->
                _state.update { it.copy(runOnLaunch = enabled) }
            }
        }
        scope.launch {
            settings.experimentEnabled.collect { enabled ->
                _state.update { it.copy(experimentEnabled = enabled) }
                rebuildTasks()
            }
        }
        scope.launch {
            settings.riskAcknowledged.collect { acknowledged ->
                _state.update { it.copy(riskAcknowledged = acknowledged) }
            }
        }
        scope.launch {
            accountSource.activeAccount.collect { account ->
                val accountId = account?.localId
                if (accountId == _state.value.accountId) return@collect
                runJob?.cancel()
                refreshJob?.cancel()
                rawTasks = emptyList()
                _state.update {
                    it.copy(
                        accountId = accountId,
                        integral = "0",
                        signIn = SignInUiState.Unknown,
                        tasks = emptyList(),
                        loading = false,
                        tasksLoaded = false,
                        error = null,
                        running = false,
                        runStatus = "未运行",
                        lastRun = null,
                        logs = emptyList(),
                    )
                }
            }
        }
    }

    fun onAppForeground() {
        scope.launch {
            accountSource.initialize()
            val accountId = accountSource.activeLocalId() ?: return@launch
            if (!accountSource.hasActiveSession()) return@launch
            if (runJob?.isActive == true) return@launch
            val autoEnabled = safe { settings.autoEnabled.first() }.getOrDefault(false)
            val runOnLaunch = safe { settings.runOnLaunch.first() }.getOrDefault(false)
            if (!autoEnabled || !runOnLaunch) return@launch
            val attemptDate = safe { runStore.load(accountId) }.getOrNull()?.attemptDate.orEmpty()
            if (attemptDate == today()) return@launch
            startRun(accountId, PointsTrigger.AppStart)
        }
    }

    fun onScreenVisible() {
        scope.launch {
            accountSource.initialize()
            val accountId = accountSource.activeLocalId()
            if (accountId == null || !accountSource.hasActiveSession()) {
                _state.update { it.copy(accountId = accountId, loading = false) }
                return@launch
            }
            if (runJob?.isActive == true) return@launch
            val autoEnabled = safe { settings.autoEnabled.first() }.getOrDefault(false)
            val experimentEnabled = safe { settings.experimentEnabled.first() }.getOrDefault(false)
            updateIfActive(accountId) {
                it.copy(
                    autoEnabled = autoEnabled,
                    experimentEnabled = experimentEnabled,
                )
            }
            if (autoEnabled) {
                val attemptDate = safe { runStore.load(accountId) }.getOrNull()?.attemptDate.orEmpty()
                if (attemptDate != today()) {
                    startRun(accountId, PointsTrigger.PointsPage)
                    return@launch
                }
            }
            refreshPage(accountId)
        }
    }

    fun onAppBackground() {
        if (runJob?.isActive == true) {
            _state.update { it.copy(runStatus = "已取消") }
            runJob?.cancel()
        }
        refreshJob?.cancel()
    }

    fun runNow() {
        scope.launch {
            accountSource.initialize()
            val accountId = accountSource.activeLocalId() ?: return@launch
            if (!accountSource.hasActiveSession()) {
                _state.update { it.copy(error = "请先登录") }
                return@launch
            }
            if (runJob?.isActive == true) return@launch
            startRun(accountId, PointsTrigger.Manual)
        }
    }

    fun refresh() {
        scope.launch {
            accountSource.initialize()
            val accountId = accountSource.activeLocalId() ?: return@launch
            if (!accountSource.hasActiveSession()) return@launch
            if (runJob?.isActive == true) return@launch
            refreshPage(accountId)
        }
    }

    fun signInNow() {
        scope.launch {
            accountSource.initialize()
            val accountId = accountSource.activeLocalId() ?: return@launch
            if (!accountSource.hasActiveSession()) {
                _state.update { it.copy(error = "请先登录") }
                return@launch
            }
            if (runJob?.isActive == true) return@launch
            refreshJob?.cancel()
            runJob = scope.launch { signInOnce(accountId) }
        }
    }

    fun completeTask(taskCode: String) {
        val task = _state.value.tasks.firstOrNull { it.taskCode == taskCode } ?: return
        if (task.completed || task.decision !is PointsTaskDecision.AutoEligible) return
        scope.launch {
            accountSource.initialize()
            val accountId = accountSource.activeLocalId() ?: return@launch
            if (!accountSource.hasActiveSession()) return@launch
            if (runJob?.isActive == true) return@launch
            val rawTask = rawTasks.firstOrNull { it.taskCode == taskCode } ?: return@launch
            val experiment = safe { settings.experimentEnabled.first() }.getOrElse { return@launch }
            val decision = PointsTaskPolicy.classify(
                task = rawTask,
                experimentEnabled = experiment,
                notificationsEnabled = notificationEnabled(),
            )
            if (decision !is PointsTaskDecision.AutoEligible) return@launch
            if (!notificationEnabled()) {
                _state.update { it.copy(error = "需要先开启通知权限") }
                return@launch
            }
            refreshJob?.cancel()
            runJob = scope.launch { completeTaskOnce(accountId, taskCode) }
        }
    }

    fun setAutoEnabled(enabled: Boolean) {
        scope.launch { settings.setAutoEnabled(enabled) }
    }

    fun setRunOnLaunch(enabled: Boolean) {
        scope.launch { settings.setRunOnLaunch(enabled) }
    }

    fun setExperimentEnabled(enabled: Boolean) {
        scope.launch { settings.setExperimentEnabled(enabled) }
    }

    fun acknowledgeRisk() {
        scope.launch { settings.acknowledgeRisk() }
    }

    private fun startRun(accountId: String, trigger: PointsTrigger) {
        refreshJob?.cancel()
        runJob?.cancel()
        runJob = scope.launch { executeRun(accountId, trigger) }
    }

    private suspend fun signInOnce(accountId: String) {
        updateIfActive(accountId) {
            it.copy(running = true, runStatus = "签到中…", error = null)
        }
        try {
            val activity = dataSource.loadSignInActivity()
            when {
                activity.id.isBlank() -> {
                    updateIfActive(accountId) { it.copy(signIn = SignInUiState.NoActivity) }
                }

                dataSource.loadTodaySignIn() -> {
                    updateIfActive(accountId) { it.copy(signIn = SignInUiState.SignedIn) }
                }

                else -> {
                    updateIfActive(accountId) { it.copy(signIn = SignInUiState.SigningIn) }
                    dataSource.signIn(activity.id)
                    updateIfActive(accountId) {
                        it.copy(signIn = SignInUiState.SignedIn, error = null)
                    }
                    onPointsChanged()
                    loadPage(accountId)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            updateIfActive(accountId) {
                it.copy(error = throwable.userMessage(), signIn = SignInUiState.NotSignedIn)
            }
        } finally {
            updateIfActive(accountId) { it.copy(running = false, runStatus = "手动签到结束") }
        }
    }

    private suspend fun completeTaskOnce(accountId: String, taskCode: String) {
        updateIfActive(accountId) {
            it.copy(running = true, runStatus = "完成任务…", error = null)
        }
        try {
            if (dataSource.isCaptchaRequired()) {
                updateIfActive(accountId) { it.copy(error = "需要完成验证后才能继续") }
                return
            }
            delay(jitter())
            dataSource.completeTask(taskCode, null)
            onPointsChanged()
            loadPage(accountId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            updateIfActive(accountId) { it.copy(error = throwable.userMessage()) }
        } finally {
            updateIfActive(accountId) { it.copy(running = false, runStatus = "手动任务结束") }
        }
    }

    private suspend fun executeRun(accountId: String, trigger: PointsTrigger) {
        val today = today()
        val startedAt = nowMillis()
        val counters = RunCounters()
        val logs = mutableListOf<PointsLogEntry>()
        var signInDone = false
        var failure: String? = null
        var taskError: String? = null

        fun log(level: PointsLogLevel, message: String) {
            logs += PointsLogEntry(at = nowMillis(), level = level, message = message)
            if (logs.size > MAX_LOGS) logs.removeAt(0)
            updateIfActive(accountId) { it.copy(logs = logs.toList()) }
        }
        val logSink: (PointsLogLevel, String) -> Unit = { level, message ->
            log(level, message)
        }

        updateIfActive(accountId) {
            it.copy(
                running = true,
                runStatus = "运行中…",
                error = null,
                logs = emptyList(),
                tasksLoaded = false,
            )
        }

        try {
            val experimentEnabled = settings.experimentEnabled.first()
            updateIfActive(accountId) { it.copy(experimentEnabled = experimentEnabled) }
            runStore.recordAttempt(accountId, today)
            withTimeout(RUN_TIMEOUT_MS) {
                log(PointsLogLevel.Info, "开始运行（${trigger.label}）")

                val activity = dataSource.loadSignInActivity()
                when {
                    activity.id.isBlank() -> {
                        log(PointsLogLevel.Warning, "暂无可用签到活动")
                        updateIfActive(accountId) { it.copy(signIn = SignInUiState.NoActivity) }
                    }

                    dataSource.loadTodaySignIn() -> {
                        log(PointsLogLevel.Info, "今日已签到")
                        updateIfActive(accountId) { it.copy(signIn = SignInUiState.SignedIn) }
                    }

                    else -> {
                        updateIfActive(accountId) { it.copy(signIn = SignInUiState.SigningIn) }
                        dataSource.signIn(activity.id)
                        log(PointsLogLevel.Success, "签到成功")
                        updateIfActive(accountId) { it.copy(signIn = SignInUiState.SignedIn) }
                    }
                }
                signInDone = true
                runStore.recordDone(accountId, today)

                val tasks = safe { dataSource.loadTasks() }
                    .onFailure {
                        taskError = it.userMessage()
                        log(PointsLogLevel.Warning, "任务列表加载失败：${it.userMessage()}")
                    }
                    .getOrNull()
                if (tasks != null) {
                    rawTasks = tasks
                    rebuildTasks()
                    runTaskPhase(counters, experimentEnabled, logSink)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            failure = "运行超时"
            log(PointsLogLevel.Error, failure.orEmpty())
        } catch (cancellation: CancellationException) {
            log(PointsLogLevel.Warning, "运行已取消")
            throw cancellation
        } catch (throwable: Throwable) {
            failure = throwable.userMessage()
            log(PointsLogLevel.Error, "运行失败：${failure.orEmpty()}")
        } finally {
            val finishedAt = nowMillis()
            val record = PointsRunRecord(
                date = today,
                startedAt = startedAt,
                finishedAt = finishedAt,
                trigger = trigger,
                signIn = if (signInDone) "已完成" else "未完成",
                attempted = counters.attempted,
                completed = counters.completed,
                skipped = counters.skipped,
                captchaBlocked = counters.captchaBlocked,
                error = failure ?: taskError.orEmpty(),
                logs = logs.takeLast(MAX_LOGS),
            )
            withContext(NonCancellable) {
                safe { runStore.saveRecord(accountId, record) }
            }
            updateIfActive(accountId) {
                it.copy(
                    running = false,
                    runStatus = when {
                        failure != null -> "失败"
                        taskError != null -> "签到完成，任务加载失败"
                        signInDone -> "今日已完成"
                        else -> "未完成"
                    },
                    lastRun = record,
                    logs = record.logs,
                )
            }
            if (accountSource.activeLocalId() == accountId) {
                runCatching { onPointsChanged() }
                refreshPage(accountId)
            }
        }
    }

    private suspend fun runTaskPhase(
        counters: RunCounters,
        experimentEnabled: Boolean,
        log: (PointsLogLevel, String) -> Unit,
    ) {
        var captchaStreak = 0
        var networkErrors = 0

        suspend fun captchaRequired(): Boolean {
            val check = safe { dataSource.isCaptchaRequired() }
            if (check.isFailure) {
                counters.skipped += 1
                log(PointsLogLevel.Warning, "验证码检查失败，已跳过任务")
                return true
            }
            if (!check.getOrDefault(false)) {
                captchaStreak = 0
                return false
            }
            captchaStreak += 1
            counters.captchaBlocked += 1
            counters.skipped += 1
            log(PointsLogLevel.Warning, "任务需要验证，已跳过")
            return true
        }

        suspend fun runOne(taskCode: String, subtaskCode: String?, browseDelay: Boolean) {
            if (counters.attempted >= MAX_TASK_ATTEMPTS) return
            if (captchaStreak >= MAX_CAPTCHA_STREAK) return
            if (captchaRequired()) return
            if (browseDelay) delay(browseDelay()) else delay(jitter())
            try {
                counters.attempted += 1
                dataSource.completeTask(taskCode, subtaskCode)
                counters.completed += 1
                networkErrors = 0
                log(PointsLogLevel.Success, "任务完成：$taskCode")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (isAuthExpired(throwable)) throw throwable
                counters.skipped += 1
                if (throwable is IOException) networkErrors += 1
                log(PointsLogLevel.Warning, "任务失败：$taskCode（${throwable.userMessage()}）")
                if (networkErrors >= MAX_NETWORK_ERRORS) {
                    log(PointsLogLevel.Error, "连续网络错误，停止任务阶段")
                    throw StopTaskPhase
                }
            }
        }

        try {
            val parent = safe { dataSource.queryTask(PointsTaskPolicy.HOME_BROWSE_TASK_CODE) }
                .onFailure { log(PointsLogLevel.Warning, "首页浏览任务加载失败：${it.userMessage()}") }
                .getOrNull()
            if (parent != null && parent.status != 0 && parent.completedStatus != 1) {
                if (PointsTaskPolicy.limitReached(parent)) {
                    counters.skipped += 1
                    log(PointsLogLevel.Info, "首页浏览已达次数上限")
                } else {
                    val subtask = parent.subtaskList.firstOrNull { it.completedStatus == 0 }
                    when {
                        subtask == null -> log(PointsLogLevel.Info, "首页浏览无待完成子任务")
                        subtask.limitReached() -> {
                            counters.skipped += 1
                            log(PointsLogLevel.Info, "首页浏览子任务已达次数上限")
                        }

                        else -> {
                            val taskCode = subtask.taskCode.ifBlank { parent.taskCode }
                            val subtaskCode = subtask.subtaskCode.ifBlank { null }
                            runOne(taskCode, subtaskCode, browseDelay = true)
                        }
                    }
                }
            } else if (parent?.completedStatus == 1) {
                log(PointsLogLevel.Info, "首页浏览已完成")
            }

            if (experimentEnabled) {
                val candidates = rawTasks
                    .filter {
                        PointsTaskPolicy.classify(
                            task = it,
                            experimentEnabled = true,
                            notificationsEnabled = notificationEnabled(),
                        ) is PointsTaskDecision.AutoEligible
                    }
                    .take(MAX_DIRECT_TASKS)
                for (task in candidates) {
                    if (counters.attempted >= MAX_TASK_ATTEMPTS) break
                    if (captchaStreak >= MAX_CAPTCHA_STREAK) break
                    runOne(task.taskCode, null, browseDelay = false)
                }
            }
        } catch (stop: StopTaskPhase) {
            // 已记录停止原因。
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            if (isAuthExpired(throwable)) throw throwable
            log(PointsLogLevel.Warning, "任务阶段异常：${throwable.userMessage()}")
        }
    }

    private suspend fun loadPage(accountId: String) {
        updateIfActive(accountId) { it.copy(loading = true, error = null) }
        var error: String? = null

        val integral = safe { dataSource.loadBalance() }
            .onFailure {
                if (error == null) error = it.userMessage()
            }
            .getOrElse { _state.value.integral }

        val activity = safe { dataSource.loadSignInActivity() }
            .onFailure {
                if (error == null) error = it.userMessage()
            }
            .getOrNull()

        val signedIn = if (activity?.id?.isNotBlank() == true) {
            safe { dataSource.loadTodaySignIn() }.getOrDefault(false)
        } else {
            false
        }
        val signIn = when {
            activity == null -> SignInUiState.Unknown
            activity.id.isBlank() -> SignInUiState.NoActivity
            signedIn -> SignInUiState.SignedIn
            else -> SignInUiState.NotSignedIn
        }

        val tasks = safe { dataSource.loadTasks() }
            .onFailure {
                if (error == null) error = it.userMessage()
            }
            .getOrNull()

        val snapshot = safe { runStore.load(accountId) }.getOrNull()
        if (accountSource.activeLocalId() != accountId) return

        if (tasks != null) {
            rawTasks = tasks
        }
        _state.update {
            it.copy(
                accountId = accountId,
                integral = integral,
                signIn = signIn,
                tasks = if (tasks != null) buildTaskUis() else it.tasks,
                loading = false,
                tasksLoaded = tasks != null || it.tasksLoaded,
                error = error,
                lastRun = snapshot?.lastRun ?: it.lastRun,
                logs = snapshot?.lastRun?.logs ?: it.logs,
            )
        }
        diagnostics.event(
            if (error == null) DiagLevel.Debug else DiagLevel.Warn,
            DiagFlow.Points,
            "points_refresh",
            message = error.orEmpty(),
            fields = mapOf(
                "accountId" to accountId,
                "tasks" to tasks?.size.toString(),
                "integral" to integral,
            ),
        )
    }

    private fun refreshPage(accountId: String) {
        refreshJob?.cancel()
        refreshJob = scope.launch { loadPage(accountId) }
    }

    private fun rebuildTasks() {
        val tasks = buildTaskUis()
        _state.update { it.copy(tasks = tasks) }
    }

    private fun buildTaskUis(): List<PointsTaskUi> {
        val notifications = notificationEnabled()
        return rawTasks.map { task ->
            PointsTaskUi(
                taskCode = task.taskCode,
                title = task.title.ifBlank { "未命名任务" },
                awardNumber = task.awardNumber,
                completed = task.completedStatus == 1,
                decision = PointsTaskPolicy.classify(
                    task = task,
                    experimentEnabled = _state.value.experimentEnabled,
                    notificationsEnabled = notifications,
                ),
            )
        }.sortedWith(
            compareBy(
                { it.completed },
                { it.decision !is PointsTaskDecision.AutoEligible },
                { it.title },
            ),
        )
    }

    private fun updateIfActive(accountId: String, transform: (PointsUiState) -> PointsUiState) {
        if (accountSource.activeLocalId() == accountId) {
            _state.update(transform)
        }
    }

    private fun today(): String = LocalDate.now(clock).toString()

    private fun jitter(): Long = nextDelay(jitterRange)

    private fun browseDelay(): Long = nextDelay(browseRange)

    private fun nextDelay(range: LongRange): Long {
        if (range.isEmpty()) return 0
        return random.nextLong(range.first, range.last + 1)
    }

    private suspend fun <T> safe(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    private fun isAuthExpired(throwable: Throwable): Boolean =
        throwable is ApiException && throwable.code in AUTH_EXPIRED_CODES

    private fun SubtaskDto.limitReached(): Boolean {
        if (dailyTaskLimit > 0 && completedFreq >= dailyTaskLimit) return true
        if (totalTaskLimit > 0 && completedFreq >= totalTaskLimit) return true
        return false
    }

    private data class RunCounters(
        var attempted: Int = 0,
        var completed: Int = 0,
        var skipped: Int = 0,
        var captchaBlocked: Int = 0,
    )

    private object StopTaskPhase : Exception()

    private companion object {
        const val RUN_TIMEOUT_MS = 120_000L
        const val MAX_LOGS = 50
        const val MAX_TASK_ATTEMPTS = 5
        const val MAX_DIRECT_TASKS = 4
        const val MAX_CAPTCHA_STREAK = 2
        const val MAX_NETWORK_ERRORS = 3
        val AUTH_EXPIRED_CODES = setOf(2, 10, 126)
    }
}
