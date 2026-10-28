package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.diagnostics.DiagSink
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 在所有构建记录请求摘要：方法、路径、状态码、耗时与脱敏响应预览。
 *
 * 不记录请求头、请求体与查询参数；响应预览经过统一脱敏，非 JSON 响应不落原文。
 */
class DiagnosticNetworkInterceptor(
    private val sink: DiagSink,
    largePreview: Boolean,
) : Interceptor {

    private val previewBytes = if (largePreview) DIAGNOSTIC_PREVIEW_BYTES else PREVIEW_BYTES
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath.trimStart('/')
        val startedAt = System.currentTimeMillis()
        var response: Response? = null
        var failure: Throwable? = null
        try {
            response = chain.proceed(request)
            return response
        } catch (throwable: Throwable) {
            failure = throwable
            throw throwable
        } finally {
            runCatching {
                val duration = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                report(path, request.method, response, failure, duration)
            }
        }
    }

    private fun report(
        path: String,
        method: String,
        response: Response?,
        failure: Throwable?,
        durationMs: Long,
    ) {
        val status = response?.code
        val preview = response?.let { readPreview(it) }.orEmpty()
        val businessCode = preview.takeIf { it.isNotBlank() }?.let(::businessCode)
        val level = when {
            failure != null || (status != null && status >= 500) -> DiagLevel.Error
            status != null && status >= 400 -> DiagLevel.Warn
            businessCode != null && businessCode != 0 -> DiagLevel.Warn
            path in POLLING_PATHS -> DiagLevel.Debug
            path in CORE_PATHS -> DiagLevel.Info
            else -> DiagLevel.Debug
        }
        val fields = buildMap {
            put("method", method)
            put("path", path)
            put("status", status?.toString() ?: "-")
            put("durationMs", durationMs.toString())
            if (preview.isNotBlank()) put("preview", preview)
            if (businessCode != null) put("code", businessCode.toString())
            failure?.let { put("error", it.message ?: it.javaClass.simpleName) }
        }
        val message = buildString {
            append(method)
            append(' ')
            append(path)
            append(" → ")
            append(status?.toString() ?: failure?.javaClass?.simpleName ?: "-")
            append(" · ")
            append(durationMs)
            append("ms")
        }
        sink.event(level, DiagFlow.Network, "http", message, fields = fields)
    }

    private fun readPreview(response: Response): String = runCatching {
        val body = response.peekBody(previewBytes).string()
        if (body.isBlank()) return ""
        val redacted = redactResponseBody(body)
        if (redacted.length > previewBytes) {
            redacted.take(previewBytes.toInt()) + "…<truncated>"
        } else {
            redacted
        }
    }.getOrDefault("")

    private fun businessCode(preview: String): Int? = runCatching {
        json.parseToJsonElement(preview).jsonObject["code"]?.jsonPrimitive?.intOrNull
    }.getOrNull()

    private companion object {
        const val PREVIEW_BYTES = 4L * 1024L
        const val DIAGNOSTIC_PREVIEW_BYTES = 16L * 1024L

        val CORE_PATHS = setOf(
            "goods/scan/v2",
            "goods/normal/details",
            "goods/water/unlock",
            "order/afterPay/creating",
            "payChannelRoute/afterPay",
            "payChannelRoute/addUserAfterPayChannel",
            "order/createVirtualOrder",
            "pay/unify/prePay",
            "user/reg",
            "user/checkLogin",
        )

        val POLLING_PATHS = setOf(
            "machine/msg/sync",
            "goods/water/sync",
            "order/pay_status",
        )
    }
}
