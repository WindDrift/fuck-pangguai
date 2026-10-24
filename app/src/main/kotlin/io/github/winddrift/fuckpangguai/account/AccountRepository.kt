package io.github.winddrift.fuckpangguai.account

import io.github.winddrift.fuckpangguai.core.AppConfig
import io.github.winddrift.fuckpangguai.history.HistoryRepository
import io.github.winddrift.fuckpangguai.net.ApiException
import io.github.winddrift.fuckpangguai.net.QiekjApi
import io.github.winddrift.fuckpangguai.net.requireSuccess

class AccountRepository(
    private val api: QiekjApi,
    private val accountStore: AccountStore,
    private val historyRepository: HistoryRepository,
) {
    val accounts = accountStore.accounts
    val activeAccount = accountStore.activeAccount

    fun activeLocalId(): String? = accountStore.activeLocalId()

    suspend fun initialize() = accountStore.initialize()

    suspend fun hasActiveSession(): Boolean = accountStore.hasActiveSession()

    suspend fun sendCode(phone: String) {
        val response = api.sendCode(
            mapOf(
                "phone" to phone,
                "template" to "reg",
            ),
        )
        requireSuccess(response.code, response.msg)
    }

    suspend fun loginAndActivate(phone: String, code: String): StoredAccount {
        val response = api.login(
            mapOf(
                "channel" to AppConfig.CHANNEL,
                "phone" to phone,
                "verify" to code,
            ),
        )
        requireSuccess(response.code, response.msg)
        val token = response.data?.token.orEmpty()
        if (token.isBlank()) {
            throw ApiException(response.code, "登录成功但未返回 token")
        }
        return accountStore.addOrMerge(token, phone)
    }

    suspend fun importAndActivate(token: String): StoredAccount =
        accountStore.addOrMerge(token, phoneHint = "")

    suspend fun switchTo(localId: String): StoredAccount? = accountStore.activate(localId)

    suspend fun rename(localId: String, alias: String) = accountStore.renameAlias(localId, alias)

    suspend fun delete(localId: String): StoredAccount? = accountStore.delete(localId)

    suspend fun logoutActive(): StoredAccount? = accountStore.deleteActive()

    suspend fun markInvalidIfActive(token: String): StoredAccount? =
        accountStore.markInvalidIfActive(token)

    suspend fun getPhone(localId: String): String? = accountStore.getPhone(localId)

    suspend fun refreshProfile(): ProfileUpdateResult? {
        val localId = accountStore.activeLocalId() ?: return null
        val response = api.userInfo(emptyMap())
        requireSuccess(response.code, response.msg)
        val profile = response.data ?: throw ApiException(-1, "账号资料为空")
        if (profile.id.isBlank() && profile.phone.isBlank()) {
            throw ApiException(-1, "账号资料为空")
        }
        val result = accountStore.updateProfile(
            localId = localId,
            serverId = profile.id,
            nickname = profile.userName,
            phone = profile.phone,
            avatarUrl = profile.headImageId,
        )
        result?.mergedFromLocalId?.let { from ->
            historyRepository.reassignAccount(from, result.account.localId)
        }
        return result
    }
}
