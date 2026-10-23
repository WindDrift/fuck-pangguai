package io.github.winddrift.pangguai.net

import java.io.IOException

class ApiException(
    val code: Int,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message
    is IOException -> "网络异常，请检查网络后重试"
    else -> message?.takeIf { it.isNotBlank() } ?: "请求失败，请稍后重试"
}

fun requireSuccess(code: Int, message: String) {
    if (code != 0) {
        throw ApiException(code, message.ifBlank { "请求失败（$code）" })
    }
}
