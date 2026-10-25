package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.net.dto.BillOrderDto
import io.github.winddrift.fuckpangguai.net.dto.ItemsList
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `order detail decodes promotion and trade items`() {
        val detail = json.decodeFromString<BillOrderDto>(
            """
            {
              "id": "order-1",
              "orderNo": "pay-1",
              "orderStatus": 2,
              "orderType": 3,
              "markPrice": "2.00",
              "payPrice": "1.50",
              "paymentDiscountPrice": "0.20",
              "createTime": "2026-10-25 11:50:00",
              "payTime": "2026-10-25 12:00:00",
              "machineName": "一号饮水机",
              "shopId": "shop-1",
              "shopName": "一号店",
              "payTypeName": "校园卡",
              "promotionList": [
                { "promotionType": 4, "discountAmount": "0.30", "subsidyAmount": null }
              ],
              "tradeOrderItem": [
                { "originPrice": "2.00", "realPrice": "1.50" }
              ],
              "unknownField": "ignored"
            }
            """.trimIndent(),
        )

        assertEquals("order-1", detail.id)
        assertEquals("pay-1", detail.orderNo)
        assertEquals("2.00", detail.markPrice)
        assertEquals("0.20", detail.paymentDiscountPrice)
        assertEquals(1, detail.promotionList.size)
        assertEquals(4, detail.promotionList.single().promotionType)
        assertEquals("0.30", detail.promotionList.single().discountAmount)
        assertEquals("", detail.promotionList.single().subsidyAmount)
        assertEquals("2.00", detail.tradeOrderItem.single().originPrice)
    }

    @Test
    fun `order list decodes items`() {
        val page = json.decodeFromString<ItemsList<BillOrderDto>>(
            """
            {
              "total": 1,
              "items": [
                { "id": "order-1", "orderNo": "pay-1", "orderStatus": 2, "payPrice": "1.50" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, page.total)
        assertEquals("pay-1", page.items.single().orderNo)
        assertTrue(page.items.single().promotionList.isEmpty())
    }

    @Test
    fun `ticket log decodes order number`() {
        val log = json.decodeFromString<TicketLog>(
            """
            {
              "id": 7,
              "shopId": "shop-1",
              "shopName": "一号店",
              "type": 20,
              "orderNo": "pay-1",
              "amount": 30,
              "principalAmount": 20,
              "presentAmount": 10,
              "afterAmount": 100,
              "remark": "接水抵扣",
              "createdAt": "2026-10-25 12:00:00"
            }
            """.trimIndent(),
        )

        assertEquals("pay-1", log.orderNo)
        assertEquals(30, log.amount)
        assertEquals(20, log.principalAmount)
        assertEquals(10, log.presentAmount)
    }
}
