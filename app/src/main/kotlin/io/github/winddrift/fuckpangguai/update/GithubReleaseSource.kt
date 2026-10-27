package io.github.winddrift.fuckpangguai.update

import io.github.winddrift.fuckpangguai.BuildConfig
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

internal interface ReleaseSource {
    suspend fun latest(): GithubRelease
}

internal class GithubHttpException(
    val code: Int,
    val rateLimited: Boolean,
) : IOException("GitHub HTTP $code")

/**
 * 只访问 GitHub 的独立客户端。
 *
 * 注意：不能复用 AppGraph 中的官方 API OkHttpClient，因为它会附加登录态与请求签名。
 */
internal class GithubReleaseSource(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ReleaseSource {

    override suspend fun latest(): GithubRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent())
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val rateLimited = response.code == 429 ||
                        (response.code == 403 &&
                            response.header("X-RateLimit-Remaining") == "0")
                    throw GithubHttpException(response.code, rateLimited)
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) throw GithubHttpException(response.code, rateLimited = false)
                json.decodeFromString(GithubRelease.serializer(), body)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }

    private fun userAgent(): String =
        "fuck-pangguai/${BuildConfig.VERSION_NAME} (+https://github.com/WindDrift/fuck-pangguai)"

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/WindDrift/fuck-pangguai/releases/latest"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

internal fun Throwable.toUpdateError(): UpdateError = when (this) {
    is GithubHttpException -> if (rateLimited) {
        UpdateError.RateLimited
    } else {
        UpdateError.Http(code)
    }

    is UnknownHostException, is ConnectException -> UpdateError.NoNetwork
    is SocketTimeoutException, is InterruptedIOException -> UpdateError.Timeout
    else -> UpdateError.Unknown
}
