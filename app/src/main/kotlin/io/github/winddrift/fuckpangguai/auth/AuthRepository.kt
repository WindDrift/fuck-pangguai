package io.github.winddrift.fuckpangguai.auth

import io.github.winddrift.fuckpangguai.core.AppConfig
import io.github.winddrift.fuckpangguai.net.QiekjApi
import io.github.winddrift.fuckpangguai.net.requireSuccess
import io.github.winddrift.fuckpangguai.storage.TokenStore

class AuthRepository(
    private val api: QiekjApi,
    private val tokenStore: TokenStore,
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
        tokenStore.save(token)
    }

    suspend fun checkLogin(): Boolean {
        tokenStore.load() ?: return false
        val response = api.checkLogin(emptyMap())
        if (!response.isSuccess) {
            tokenStore.clear()
            return false
        }
        response.data?.token?.takeIf { it.isNotBlank() }?.let { tokenStore.save(it) }
        return true
    }

    suspend fun importToken(token: String) {
        tokenStore.save(token)
    }

    suspend fun logout() {
        tokenStore.clear()
    }
}
