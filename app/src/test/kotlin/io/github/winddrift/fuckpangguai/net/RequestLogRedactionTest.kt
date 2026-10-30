package io.github.winddrift.fuckpangguai.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestLogRedactionTest {

    @Test
    fun `prepay param and alipay order number are redacted`() {
        val raw = """
            {"code":0,"data":{
              "prepayParam":"signed-order-info",
              "alipayOrderNo":"2026102522001",
              "payPrice":"10.00",
              "nested":{"prePayParam":"nested-order-info"}
            }}
        """.trimIndent()

        val redacted = redactResponseBody(raw)

        assertFalse(redacted.contains("signed-order-info"))
        assertFalse(redacted.contains("2026102522001"))
        assertFalse(redacted.contains("nested-order-info"))
        assertTrue(redacted.contains("10.00"))
        assertTrue(redacted.contains("<redacted>"))
    }

    @Test
    fun `refund form redacts alipay account and real name`() {
        val body = "shopIds=shop-1&alipayAccount=13800138000&name=张三&applyFileList="

        val redacted = redactFormBody(body)

        assertFalse(redacted.contains("13800138000"))
        assertFalse(redacted.contains("张三"))
        assertTrue(redacted.contains("shopIds=shop-1"))
        assertTrue(redacted.contains("alipayAccount=<redacted>"))
        assertTrue(redacted.contains("name=<redacted>"))
    }
}
