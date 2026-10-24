package io.github.winddrift.fuckpangguai.net

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

class SessionExpiryNotifier {
    private val _events = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = _events.asSharedFlow()

    internal fun notify(token: String) {
        _events.tryEmit(token)
    }
}

class SessionExpiryInterceptor(
    private val notifier: SessionExpiryNotifier,
) : Interceptor {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val reportedToken = AtomicReference<String?>(null)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val token = request.header("Authorization").orEmpty()
        if (!shouldReportSessionExpiry(request.url.encodedPath, token)) return response

        val code = readResponseCode(response)
        if (code != null && isSessionExpiredCode(code)) {
            if (reportedToken.getAndSet(token) != token) notifier.notify(token)
        } else {
            reportedToken.compareAndSet(token, null)
        }
        return response
    }

    private fun readResponseCode(response: Response): Int? = runCatching {
        val body = response.peekBody(MAX_PEEK_BYTES).string()
        json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.intOrNull
    }.getOrNull()

    private companion object {
        const val MAX_PEEK_BYTES = 64L * 1024L
    }
}
