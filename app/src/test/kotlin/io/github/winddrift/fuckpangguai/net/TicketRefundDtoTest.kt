package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.net.dto.Ticket
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketRefundDtoTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `ticket refund fields decode from list payload`() {
        val payload = """
            {
              "id": "ticket-1",
              "shopId": "shop-1",
              "shopName": "店铺",
              "totalAmount": 1200,
              "principalAmount": 1000,
              "presentAmount": 200,
              "isAllowRefund": 1,
              "isRefundNeedFile": 1,
              "uploadFileRemark": "请上传支付凭证",
              "hasProcessingRefund": true
            }
        """.trimIndent()

        val ticket = json.decodeFromString(Ticket.serializer(), payload)

        assertEquals(1, ticket.isAllowRefund)
        assertEquals(1, ticket.isRefundNeedFile)
        assertEquals("请上传支付凭证", ticket.uploadFileRemark)
        assertTrue(ticket.hasProcessingRefund)
        assertEquals(1000, ticket.principalAmount)
    }

    @Test
    fun `missing refund fields default to no refund`() {
        val ticket = json.decodeFromString(Ticket.serializer(), """{"shopId":"shop-1"}""")
        assertEquals(0, ticket.isAllowRefund)
        assertEquals(0, ticket.isRefundNeedFile)
        assertEquals(false, ticket.hasProcessingRefund)
    }
}