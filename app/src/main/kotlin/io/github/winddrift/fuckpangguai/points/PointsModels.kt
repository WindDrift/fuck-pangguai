package io.github.winddrift.fuckpangguai.points

import io.github.winddrift.fuckpangguai.account.StoredAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

interface PointsSettingsSource {
    val autoEnabled: Flow<Boolean>
    val runOnLaunch: Flow<Boolean>
    val experimentEnabled: Flow<Boolean>
    val riskAcknowledged: Flow<Boolean>

    suspend fun setAutoEnabled(enabled: Boolean)
    suspend fun setRunOnLaunch(enabled: Boolean)
    suspend fun setExperimentEnabled(enabled: Boolean)
    suspend fun acknowledgeRisk()
}

interface PointsAccountSource {
    val activeAccount: StateFlow<StoredAccount?>
    suspend fun initialize()
    suspend fun hasActiveSession(): Boolean
    fun activeLocalId(): String?
}

enum class PointsTrigger(val label: String) {
    AppStart("启动"),
    PointsPage("积分页"),
    Manual("手动"),
}

@Serializable
enum class PointsLogLevel {
    Info,
    Success,
    Warning,
    Error,
}

@Serializable
data class PointsLogEntry(
    val at: Long = 0L,
    val level: PointsLogLevel = PointsLogLevel.Info,
    val message: String = "",
)

@Serializable
data class PointsRunRecord(
    val date: String = "",
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val trigger: PointsTrigger = PointsTrigger.Manual,
    val signIn: String = "",
    val attempted: Int = 0,
    val completed: Int = 0,
    val skipped: Int = 0,
    val captchaBlocked: Int = 0,
    val error: String = "",
    val logs: List<PointsLogEntry> = emptyList(),
)

data class PointsRunSnapshot(
    val attemptDate: String = "",
    val doneDate: String = "",
    val lastRun: PointsRunRecord? = null,
)

sealed interface SignInUiState {
    data object Unknown : SignInUiState
    data object NoActivity : SignInUiState
    data object SignedIn : SignInUiState
    data object NotSignedIn : SignInUiState
    data object SigningIn : SignInUiState
}

enum class PointsSkipReason(val label: String) {
    Disabled("任务已下架"),
    Completed("已完成"),
    LimitReached("已达次数上限"),
    NotPoints("非积分奖励"),
    HasSubtask("需要完成子任务"),
    HasJumpLink("需要打开外部页面"),
    HasExtension("需要广告或第三方能力"),
    ExperimentOff("直接任务实验未开启"),
    ExternalAction("需要手动完成"),
    NotificationDisabled("需开启通知权限"),
    UnknownType("未知任务类型"),
    EmptyCode("任务码为空"),
}

sealed interface PointsTaskDecision {
    data class AutoEligible(val taskCode: String) : PointsTaskDecision
    data class Skipped(val reason: PointsSkipReason) : PointsTaskDecision
}

data class PointsTaskUi(
    val taskCode: String,
    val title: String,
    val awardNumber: Int,
    val completed: Boolean,
    val decision: PointsTaskDecision,
)

data class PointsUiState(
    val accountId: String? = null,
    val integral: String = "0",
    val signIn: SignInUiState = SignInUiState.Unknown,
    val tasks: List<PointsTaskUi> = emptyList(),
    val loading: Boolean = false,
    val tasksLoaded: Boolean = false,
    val error: String? = null,
    val running: Boolean = false,
    val runStatus: String = "未运行",
    val lastRun: PointsRunRecord? = null,
    val logs: List<PointsLogEntry> = emptyList(),
    val autoEnabled: Boolean = false,
    val runOnLaunch: Boolean = true,
    val experimentEnabled: Boolean = false,
    val riskAcknowledged: Boolean = false,
)
