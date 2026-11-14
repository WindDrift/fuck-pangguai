package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.diagnostics.FakeDiagSink
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPreviewTest {

    @Test
    fun `body shorter than the preview limit is not marked truncated`() {
        val event = run("{\"code\":0,\"data\":[]}", largePreview = false)

        assertNull(event.fields["truncated"])
        assertEquals("{\"code\":0,\"data\":[]}".length, event.fields.getValue("bytes").toInt())
    }

    @Test
    fun `body exactly filling the preview limit is marked truncated`() {
        val body = "{\"code\":0,\"data\":\"" + "x".repeat(4 * 1024) + "\"}"

        val event = run(body, largePreview = false)

        assertEquals("true", event.fields["truncated"])
        assertEquals((4 * 1024).toString(), event.fields.getValue("bytes"))
        assertTrue(event.fields.getValue("preview").contains("已截断"))
    }

    @Test
    fun `declared content length larger than the read bytes is truncated`() {
        // 声明长度大于实际可读字节：即使没读满上限，也必须标成截断。
        val event = run("{\"code\":0,\"data\":[", largePreview = false, declaredLength = 9_000L)

        assertEquals("true", event.fields["truncated"])
        assertEquals("18", event.fields.getValue("bytes"))
        assertTrue(event.fields.getValue("preview").contains("已截断"))
    }

    @Test
    fun `complete json within the limit is never reported as truncated`() {
        val event = run("{\"code\":0,\"data\":[]}", largePreview = false, declaredLength = 20L)

        assertNull(event.fields["truncated"])
        assertFalse(event.fields.getValue("preview").contains("已截断"))
    }

    @Test
    fun `diagnostic preview uses the larger limit`() {
        val body = "{\"code\":0,\"data\":\"" + "x".repeat(8 * 1024) + "\"}"

        val event = run(body, largePreview = true)

        assertNull(event.fields["truncated"])
        assertEquals((8 * 1024 + 20).toString(), event.fields.getValue("bytes"))
    }

    @Test
    fun `small response preview is redacted and not truncated`() {
        val event = run("{\"code\":0,\"token\":\"session-token\"}", largePreview = false)

        assertFalse(event.fields.getValue("preview").contains("session-token"))
        assertNull(event.fields["truncated"])
    }

    private fun run(
        body: String,
        largePreview: Boolean,
        declaredLength: Long? = null,
    ): io.github.winddrift.fuckpangguai.diagnostics.model.DiagEvent {
        val sink = FakeDiagSink()
        val interceptor = DiagnosticNetworkInterceptor(sink, largePreview)
        val bytes = body.toByteArray(Charsets.UTF_8)
        val responseBody = bytes.toResponseBody("application/json".toMediaType())
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.test/task/list").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(if (declaredLength == null) responseBody else declaredBody(bytes, declaredLength))
            .build()
        interceptor.intercept(
            object : Interceptor.Chain {
                override fun request(): Request = response.request

                override fun proceed(request: Request): Response = response

                override fun connection() = null

                override fun call() = throw UnsupportedOperationException("not needed")

                override fun connectTimeoutMillis(): Int = 0

                override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun readTimeoutMillis(): Int = 0

                override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this

                override fun writeTimeoutMillis(): Int = 0

                override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            },
        )
        return sink.events.single()
    }

    /** 声明一个比实际字节更多的长度，模拟服务端 Content-Length 大于预览上限。 */
    private fun declaredBody(bytes: ByteArray, declared: Long) = object : okhttp3.ResponseBody() {
        private val real = bytes.toResponseBody("application/json".toMediaType())

        override fun contentType() = real.contentType()

        override fun contentLength(): Long = declared

        override fun source() = real.source()
    }
}