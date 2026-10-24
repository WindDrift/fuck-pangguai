package io.github.winddrift.fuckpangguai.auth

import io.github.winddrift.fuckpangguai.account.AccountStore
import io.github.winddrift.fuckpangguai.core.AppConfig
import io.github.winddrift.fuckpangguai.net.QiekjApi
import io.github.winddrift.fuckpangguai.net.requireSuccess

class AuthRepository(
    private val api: QiekjApi,
    private val accountStore: AccountStore,
) {

    suspend fun sendCode(phone: String) {
        val response = api.sendCode(
            mapOf(
                "phone" to phone,
                "template" to "reg",
            ),
        )
        requireSuccess(response.code, response.msg)
    }

    suspend fun login(phone: String, code: String) {
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
            throw io.github.winddrift.fuckpangguai.net.ApiException(response.code, "登录成功但未返回 token")
        }
        accountStore.addOrMerge(token, phone)
    }

    /**
     * Startup only checks the locally stored token. The original user/checkLogin endpoint is a
     * 秒验 flow that expects mobToken/mobOpToken; calling it with an empty body makes the server
     * reject a valid token, which previously cleared the login state on every restart.
     */
    suspend fun hasStoredToken(): Boolean = accountStore.hasActiveSession()

    suspend fun importToken(token: String) {
        accountStore.addOrMerge(token, phoneHint = "")
    }

    suspend fun logout() {
        accountStore.deleteActive()
    }
}
