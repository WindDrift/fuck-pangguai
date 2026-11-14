package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.diagnostics.DiagSink
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagFlow
import io.github.winddrift.fuckpangguai.diagnostics.model.DiagLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

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
        val failureKind = failure?.let(::classifyNetworkFailure)
        val preview = response?.let { readPreview(it) } ?: Preview()
        val businessCode = preview.text.takeIf { it.isNotBlank() }?.let(::businessCode)
        val level = networkEventLevel(
            status = status,
            failure = failureKind,
            businessCode = businessCode,
            path = path,
        )
        val fields = buildMap {
            put("method", method)
            put("path", path)
            put("status", status?.toString() ?: "-")
            put("durationMs", durationMs.toString())
            if (preview.text.isNotBlank()) put("preview", preview.text)
            if (preview.bytes > 0) put("bytes", preview.bytes.toString())
            if (preview.truncated) put("truncated", "true")
            if (businessCode != null) put("code", businessCode.toString())
            if (failureKind == NetworkFailureKind.Canceled) put("canceled", "true")
            if (failureKind == NetworkFailureKind.Timeout) put("timeout", "true")
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

    private fun readPreview(response: Response): Preview = runCatching {
        val raw = response.peekBody(previewBytes).bytes()
        if (raw.isEmpty()) return Preview()
        // peekBody 最多读 previewBytes 个字节，因此"读满"或"声明长度更大"都意味着被截断。
        val declared = runCatching { response.body?.contentLength() ?: -1L }.getOrDefault(-1L)
        val truncated = raw.size >= previewBytes || (declared >= 0L && declared > raw.size)
        Preview(
            text = redactResponseBody(String(raw, Charsets.UTF_8), truncated),
            bytes = raw.size,
            truncated = truncated,
        )
    }.getOrDefault(Preview())

    private fun businessCode(preview: String): Int? = runCatching {
        json.parseToJsonElement(preview).jsonObject["code"]?.jsonPrimitive?.intOrNull
    }.getOrNull()

    internal companion object {
        const val PREVIEW_BYTES = 4L * 1024L
        const val DIAGNOSTIC_PREVIEW_BYTES = 16L * 1024L

        internal val CORE_PATHS = setOf(
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

        internal val POLLING_PATHS = setOf(
            "machine/msg/sync",
            "goods/water/sync",
            "order/pay_status",
        )
    }
}

/** 网络失败分类：客户端主动取消、超时、其余 IO 与其他异常。 */
enum class NetworkFailureKind {
    Canceled,
    Timeout,
    Io,
    Other,
}

private const val SOCKET_CLOSED = "socket closed"
private const val MAX_CAUSE_DEPTH = 8
private const val CANCELED = "canceled"
private const val CANCELED_BRITISH = "cancelled"

/**
 * 把异常归类成 [NetworkFailureKind]。
 *
 * 用户切页、退出确认页触发的协程取消与主动断链在日志里和真实失败长得一样，
 * 但它们的 message 是 "Canceled"/"Socket closed"，必须单独归类，避免淹没真实错误。
 */
internal fun classifyNetworkFailure(throwable: Throwable): NetworkFailureKind {
    val direct = classifyDirect(throwable)
    if (direct != null) return direct
    // 包装层把取消包成别的异常时，原因链里仍然能看出取消语义。
    var cause = throwable.cause
    var depth = 0
    while (cause != null && cause !== throwable && depth < MAX_CAUSE_DEPTH) {
        val nested = classifyDirect(cause)
        if (nested == NetworkFailureKind.Canceled || nested == NetworkFailureKind.Timeout) {
            return nested
        }
        cause = cause.cause
        depth += 1
    }
    return NetworkFailureKind.Other
}

/** 只看异常自身；返回 null 表示自身不是 IO 类失败，交由原因链继续判定。 */
private fun classifyDirect(throwable: Throwable): NetworkFailureKind? {
    // 先按 message 判取消：OkHttp 主动断链只会给出文本，没有专门的异常类型。
    val message = throwable.message.orEmpty().trim().lowercase()
    if (message == CANCELED || message == CANCELED_BRITISH || message == SOCKET_CLOSED) {
        return NetworkFailureKind.Canceled
    }
    // 协程取消不是 IOException，不单独判定就会落到 Other 记成 Error。
    if (throwable is CancellationException) return NetworkFailureKind.Canceled
    if (throwable is SocketTimeoutException) return NetworkFailureKind.Timeout
    if (throwable is InterruptedIOException) {
        // InterruptedIOException 也覆盖超时；message 已在上方按取消判定过。
        return NetworkFailureKind.Timeout
    }
    if (throwable is IOException) return NetworkFailureKind.Io
    return null
}

/**
 * 事件级别：主动取消降为 Debug，超时与连接失败记 Warn，其余异常保持 Error。
 *
 * 纯函数，便于 JVM 单测；不读取响应体，也不改变异常上抛。
 */
internal fun networkEventLevel(
    status: Int?,
    failure: NetworkFailureKind?,
    businessCode: Int?,
    path: String,
    corePaths: Set<String> = DiagnosticNetworkInterceptor.CORE_PATHS,
    pollingPaths: Set<String> = DiagnosticNetworkInterceptor.POLLING_PATHS,
): DiagLevel = when {
    // 取消是用户切页或退出确认页的行为，不该污染错误统计。
    failure == NetworkFailureKind.Canceled -> DiagLevel.Debug
    // 超时与连接失败属于可重试的网络抖动，记 Warn；未知异常仍是 Error。
    failure == NetworkFailureKind.Timeout || failure == NetworkFailureKind.Io -> DiagLevel.Warn
    failure != null -> DiagLevel.Error
    status != null && status >= 500 -> DiagLevel.Error
    status != null && status >= 400 -> DiagLevel.Warn
    businessCode != null && businessCode != 0 -> DiagLevel.Warn
    path in pollingPaths -> DiagLevel.Debug
    path in corePaths -> DiagLevel.Info
    else -> DiagLevel.Debug
}

/** 响应预览：脱敏后的文本、读取字节数与是否被截断。 */
internal data class Preview(
    val text: String = "",
    val bytes: Int = 0,
    val truncated: Boolean = false,
)
