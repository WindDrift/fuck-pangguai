package io.github.winddrift.fuckpangguai.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.io.IOException

data class RequestLogEntry(
    val method: String,
    val url: String,
    val statusCode: Int?,
    val durationMs: Long,
    val requestHeaders: String,
    val requestBody: String,
    val responseBody: String,
    val error: String? = null,
) {
    fun toCurl(): String {
        val headerArgs = requestHeaders.lineSequence()
            .filter { it.isNotBlank() }
            .joinToString(" ") { "-H ${shellQuote(it)}" }
        val bodyArg = if (requestBody.isBlank()) "" else " --data ${shellQuote(requestBody)}"
        return "curl -X $method $headerArgs$bodyArg ${shellQuote(url)}"
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'''") + "'"
}

private val logJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun redactResponseBody(body: String): String {
    if (body.isBlank()) return body
    return runCatching {
        redactJsonElement(logJson.parseToJsonElement(body)).toString()
    }.getOrDefault(body)
}

private fun redactJsonElement(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(
        element.mapValues { (key, value) ->
            if (key.lowercase() in SENSITIVE_RESPONSE_FIELDS) {
                JsonPrimitive("<redacted>")
            } else {
                redactJsonElement(value)
            }
        },
    )

    is JsonArray -> JsonArray(element.map(::redactJsonElement))
    else -> element
}

private val SENSITIVE_RESPONSE_FIELDS = setOf(
    "token",
    "phone",
    "operatorphone",
    "realname",
    "idcardno",
    "headimageid",
    "mobtoken",
    "moboptoken",
    "alipayaccount",
    "prepayparam",
    "alipayorderno",
    "unionid",
    "openid",
)

object RequestLog {
    private const val MAX_ENTRIES = 100
    private val _entries = MutableStateFlow<List<RequestLogEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    fun add(entry: RequestLogEntry) {
        _entries.value = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}

class RequestLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAt = System.currentTimeMillis()
        val requestBody = request.body?.let(::bodyToString).orEmpty()
        var response: Response? = null
        var error: String? = null
        try {
            response = chain.proceed(request)
            return response
        } catch (throwable: Throwable) {
            error = throwable.message ?: throwable.javaClass.simpleName
            throw throwable
        } finally {
            val duration = System.currentTimeMillis() - startedAt
            RequestLog.add(
                RequestLogEntry(
                    method = request.method,
                    url = request.url.toString(),
                    statusCode = response?.code,
                    durationMs = duration,
                    requestHeaders = redactHeaders(request.headers),
                    requestBody = redactForm(requestBody),
                    responseBody = redactResponseBody(
                        runCatching {
                            response?.peekBody(MAX_RESPONSE_BYTES)?.string().orEmpty()
                        }.getOrDefault(""),
                    ),
                    error = error,
                ),
            )
        }
    }

    private fun bodyToString(body: okhttp3.RequestBody): String = try {
        Buffer().also(body::writeTo).readUtf8()
    } catch (_: IOException) {
        "<unreadable>"
    }

    private fun redactHeaders(headers: Headers): String = buildString {
        for (name in headers.names()) {
            if (isNotEmpty()) append('\n')
            val value = if (name.equals("Authorization", ignoreCase = true)) {
                "<redacted>"
            } else {
                headers[name].orEmpty()
            }
            append(name).append(": ").append(value)
        }
    }

    private fun redactForm(body: String): String = body.split('&').joinToString("&") { field ->
        val name = field.substringBefore('=', missingDelimiterValue = field)
        if (name in SENSITIVE_FIELDS) "$name=<redacted>" else field
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 512L * 1024L
        val SENSITIVE_FIELDS = setOf("token", "phone", "verify", "lat", "lng")
    }
}
