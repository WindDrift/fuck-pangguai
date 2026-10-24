package io.github.winddrift.fuckpangguai.points

import io.github.winddrift.fuckpangguai.account.AccountStore
import io.github.winddrift.fuckpangguai.net.ApiException
import io.github.winddrift.fuckpangguai.net.QiekjApi
import io.github.winddrift.fuckpangguai.net.dto.IntegralTaskDto
import io.github.winddrift.fuckpangguai.net.dto.SignInActDto
import io.github.winddrift.fuckpangguai.net.requireSuccess

interface PointsDataSource {
    suspend fun loadBalance(): String
    suspend fun loadSignInActivity(): SignInActDto
    suspend fun loadTodaySignIn(): Boolean
    suspend fun signIn(activityId: String)
    suspend fun loadTasks(): List<IntegralTaskDto>
    suspend fun queryTask(taskCode: String): IntegralTaskDto
    suspend fun completeTask(taskCode: String, subtaskCode: String?)
    suspend fun isCaptchaRequired(): Boolean
}

class QiekjPointsRepository(
    private val api: QiekjApi,
    private val deviceId: String,
) : PointsDataSource {

    override suspend fun loadBalance(): String {
        val response = api.userBalance(emptyMap())
        requireSuccess(response.code, response.msg)
        return response.data?.integral?.takeIf { it.isNotBlank() } ?: "0"
    }

    override suspend fun loadSignInActivity(): SignInActDto {
        val response = api.signInActList(emptyMap())
        requireSuccess(response.code, response.msg)
        return response.data ?: SignInActDto()
    }

    override suspend fun loadTodaySignIn(): Boolean {
        val response = api.todaySignIn(emptyMap())
        requireSuccess(response.code, response.msg)
        return response.data?.isSignIn == true
    }

    override suspend fun signIn(activityId: String) {
        val response = api.doUserSignIn(mapOf("activityId" to activityId))
        requireSuccess(response.code, response.msg)
    }

    override suspend fun loadTasks(): List<IntegralTaskDto> {
        val response = api.taskList(mapOf("deviceId" to deviceId))
        requireSuccess(response.code, response.msg)
        return response.data?.items.orEmpty()
    }

    override suspend fun queryTask(taskCode: String): IntegralTaskDto {
        val response = api.taskQueryByType(mapOf("taskCode" to taskCode))
        requireSuccess(response.code, response.msg)
        return response.data ?: throw ApiException(-1, "任务不存在")
    }

    override suspend fun completeTask(taskCode: String, subtaskCode: String?) {
        val body = buildMap {
            put("taskCode", taskCode)
            if (!subtaskCode.isNullOrBlank()) put("subtaskCode", subtaskCode)
        }
        val response = api.taskCompleted(body)
        requireSuccess(response.code, response.msg)
    }

    override suspend fun isCaptchaRequired(): Boolean {
        val response = api.isCaptcha(emptyMap())
        requireSuccess(response.code, response.msg)
        return response.data == true
    }
}

class AccountStorePointsSource(
    private val store: AccountStore,
) : PointsAccountSource {
    override val activeAccount = store.activeAccount

    override suspend fun initialize() = store.initialize()

    override suspend fun hasActiveSession(): Boolean = store.hasActiveSession()

    override fun activeLocalId(): String? = store.activeLocalId()
}
