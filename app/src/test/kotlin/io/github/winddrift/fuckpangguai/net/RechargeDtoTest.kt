package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.net.dto.PrePay
import io.github.winddrift.fuckpangguai.net.dto.TicketGoods
import io.github.winddrift.fuckpangguai.net.dto.VipOrderDetail
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RechargeDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `ticket goods decode rewards balance and unknown fields`() {
        val goods = json.decodeFromString<TicketGoods>(
            """
            {
              "shopId": "shop-1",
              "shopName": "一号店",
              "goodsId": "goods-1",
              "goodsType": "1",
              "refundRemark": "退款请联系商家",
              "isAllowRefund": 0,
              "shopTokenCoinRewards": [
                {
                  "id": "reward-1",
                  "shopId": "shop-1",
                  "shopTokenCoinId": 9,
                  "reach": 1000,
                  "reward": 20,
                  "cashValue": "10.00",
                  "isSelect": true,
                  "unknown": "ignored"
                }
              ],
              "userTokenCoinDTO": {
                "totalAmount": 500,
                "principalAmount": "5.00",
                "presentAmount": "0.00"
              },
              "unknownField": "ignored"
            }
            """.trimIndent(),
        )

        assertEquals("shop-1", goods.shopId)
        assertEquals("退款请联系商家", goods.refundRemark)
        assertEquals(0, goods.isAllowRefund)
        val reward = goods.shopTokenCoinRewards.single()
        assertEquals("reward-1", reward.id)
        assertEquals(1000, reward.reach)
        assertEquals(20, reward.reward)
        assertEquals("10.00", reward.cashValue)
        assertEquals(500, goods.userTokenCoinDTO?.totalAmount)
        assertEquals("5.00", goods.userTokenCoinDTO?.principalAmount)
    }

    @Test
    fun `vip order detail decodes status and ignores unknown fields`() {
        val detail = json.decodeFromString<VipOrderDetail>(
            """
            {
              "id": "order-1",
              "orderNo": "no-1",
              "orderStatus": 2,
              "payPrice": "10.00",
              "payContentCode": "4",
              "payType": 13,
              "shopId": "shop-1",
              "shopName": "一号店",
              "alipayOrderNo": "2026...",
              "unknownField": true
            }
            """.trimIndent(),
        )

        assertEquals("order-1", detail.id)
        assertEquals("no-1", detail.orderNo)
        assertEquals(2, detail.orderStatus)
        assertEquals("10.00", detail.payPrice)
        assertEquals(13, detail.payType)
        assertEquals("2026...", detail.alipayOrderNo)
    }

    @Test
    fun `prepay missing fields use safe defaults`() {
        val prePay = json.decodeFromString<PrePay>(
            """{"prepayParam":"order-info","unknown":1}""",
        )

        assertEquals("order-info", prePay.prepayParam)
        assertTrue(!prePay.exist)
        assertEquals("", prePay.gateway)
        assertEquals("", prePay.messageId)
        assertTrue(prePay.payTypeList.isEmpty())
        assertNull(json.decodeFromString<PrePay>("{}").prepayParam.takeIf { it.isNotEmpty() })
    }
}
