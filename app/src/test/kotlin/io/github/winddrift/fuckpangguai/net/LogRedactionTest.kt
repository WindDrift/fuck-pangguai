package io.github.winddrift.fuckpangguai.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactionTest {

    @Test
    fun `response redacts sensitive fields recursively`() {
        val raw = """
            {"code":0,"token":"session-token","data":{
              "phone":"13800000000","operatorPhone":"13900000000",
              "realName":"张三","idCardNo":"110101199001010000",
              "headImageId":"https://img.example/avatar.png","userName":"小明",
              "nested":[{"token":"nested-token","shopName":"一号店"}]
            }}
        """.trimIndent()

        val redacted = redactResponseBody(raw)

        assertFalse(redacted.contains("session-token"))
        assertFalse(redacted.contains("13800000000"))
        assertFalse(redacted.contains("13900000000"))
        assertFalse(redacted.contains("张三"))
        assertFalse(redacted.contains("110101199001010000"))
        assertFalse(redacted.contains("img.example"))
        assertFalse(redacted.contains("nested-token"))
        assertTrue(redacted.contains("小明"))
        assertTrue(redacted.contains("一号店"))
        assertEquals(7, "\"<redacted>\"".toRegex().findAll(redacted).count())
    }

    @Test
    fun `truncated json is reported as truncated instead of non json`() {
        val raw = "{\"code\":0,\"data\":[{\"taskCode\":\"browse\","

        val redacted = redactResponseBody(raw, truncated = true)

        assertTrue(redacted.contains("已截断"))
        assertTrue(redacted.contains("JSON"))
        assertFalse(redacted.contains("非 JSON"))
        assertFalse(redacted.contains("browse"))
        assertFalse(redacted.contains("taskCode"))
    }

    @Test
    fun `truncated non json keeps the non json wording`() {
        val redacted = redactResponseBody("<html>ok</html>", truncated = true)

        assertTrue(redacted.contains("非 JSON"))
        assertFalse(redacted.contains("已截断"))
    }

    @Test
    fun `valid json stays redacted even when the read was truncated`() {
        val raw = """{"code":0,"token":"session-token","data":{"userName":"小明"}}"""

        val redacted = redactResponseBody(raw, truncated = true)

        assertFalse(redacted.contains("session-token"))
        assertTrue(redacted.contains("小明"))
        assertTrue(redacted.contains("<redacted>"))
    }

    @Test
    fun `non json body is omitted instead of copied`() {
        val redacted = redactResponseBody("<html>ok</html>")

        assertFalse(redacted.contains("<html>ok</html>"))
        assertTrue(redacted.contains("省略"))
        assertTrue(redacted.contains("非 JSON"))
    }
}
