package io.github.winddrift.fuckpangguai.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

sealed interface UpdateError {
    data object NoNetwork : UpdateError
    data object Timeout : UpdateError
    data object RateLimited : UpdateError
    data class Http(val code: Int) : UpdateError
    data object Unknown : UpdateError
}

fun UpdateError.userMessage(): String = when (this) {
    UpdateError.NoNetwork -> "网络不可用"
    UpdateError.Timeout -> "请求超时"
    UpdateError.RateLimited -> "GitHub 请求过于频繁"
    is UpdateError.Http -> "GitHub 返回 HTTP $code"
    UpdateError.Unknown -> "检查失败"
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState

    data class Available(
        val version: String,
        val tag: String,
        val notes: String,
        val releaseUrl: String,
    ) : UpdateState

    data class Failed(val error: UpdateError) : UpdateState
}
