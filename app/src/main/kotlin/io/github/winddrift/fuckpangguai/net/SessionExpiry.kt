package io.github.winddrift.fuckpangguai.net

private val EXPIRED_CODES = setOf(2, 10, 126)
private val AUTH_PATHS = setOf(
    "/common/sms/sendCode",
    "/user/reg",
    "/user/checkLogin",
)

internal fun isSessionExpiredCode(code: Int): Boolean = code in EXPIRED_CODES

internal fun isAuthPath(encodedPath: String): Boolean = encodedPath in AUTH_PATHS

internal fun shouldReportSessionExpiry(encodedPath: String, token: String): Boolean =
    token.isNotBlank() && !isAuthPath(encodedPath)
