package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.net.dto.BillOrderDto
import io.github.winddrift.fuckpangguai.net.dto.BillPromotionDto
import io.github.winddrift.fuckpangguai.net.dto.BillTradeItemDto
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillMappingTest {

    @Test
    fun `official paid amount subtracts payment discount`() {
        assertEquals("1.30", officialPaidAmount("1.50", "0.20"))
        assertEquals("1.50", officialPaidAmount("1.50", "0"))
        assertEquals("0.00", officialPaidAmount("0", "0"))
        assertEquals("", officialPaidAmount("", "0.20"))
    }

    @Test
    fun `bill maps original discounts ticket and paid amount`() {
        val bill = order(
            markPrice = "2.00",
            payPrice = "1.50",
            paymentDiscountPrice = "0.20",
            promotions = listOf(
                BillPromotionDto(promotionType = 3, discountAmount = "0.20"),
                BillPromotionDto(promotionType = 4, discountAmount = "0.30"),
                BillPromotionDto(promotionType = 2, discountAmount = "0.00"),
            ),
        ).toBillInfo(fetchedAt = 123L)

        assertEquals("2.00", bill.originalAmount)
        assertEquals("1.30", bill.paidAmount)
        assertEquals("0.20", bill.paymentDiscountAmount)
        assertEquals(1, bill.promotions.size)
        assertEquals("优惠券", bill.promotions.single().label)
        assertEquals("0.20", bill.promotions.single().amount)
        assertEquals(30, bill.ticket?.billPoints)
        assertEquals("0.30", bill.ticket?.deductionYuan)
        assertEquals(123L, bill.fetchedAt)
    }

    @Test
    fun `multiple gold promotions are summed`() {
        val bill = order(
            promotions = listOf(
                BillPromotionDto(promotionType = 4, discountAmount = "0.10"),
                BillPromotionDto(promotionType = 4, discountAmount = "0.25"),
            ),
        ).toBillInfo(fetchedAt = 1L)

        assertEquals(35, bill.ticket?.billPoints)
        assertEquals("0.35", bill.ticket?.deductionYuan)
    }

    @Test
    fun `money formatting keeps two decimals and handles blanks`() {
        assertEquals("2.00", formatBillMoney("2"))
        assertEquals("1.24", formatBillMoney("1.235"))
        assertEquals("", formatBillMoney(" "))
        assertEquals("raw", formatBillMoney("raw"))
    }

    @Test
    fun `bill without gold promotion has no ticket`() {
        val bill = order(
            promotions = listOf(BillPromotionDto(promotionType = 3, discountAmount = "0.20")),
        ).toBillInfo(fetchedAt = 1L)

        assertNull(bill.ticket)
        assertEquals(1, bill.promotions.size)
    }

    @Test
    fun `original amount falls back to trade item origin prices`() {
        val bill = order(
            markPrice = "",
            tradeOrderItem = listOf(
                BillTradeItemDto(originPrice = "1.20"),
                BillTradeItemDto(originPrice = "0.80"),
            ),
        ).toBillInfo(fetchedAt = 1L)

        assertEquals("2.00", bill.originalAmount)
    }

    @Test
    fun `matched ticket log supplies points and principal split`() {
        val bill = order(
            promotions = listOf(BillPromotionDto(promotionType = 4, discountAmount = "0.30")),
        ).toBillInfo(fetchedAt = 1L)

        val merged = bill.withTicketLogs(
            listOf(
                TicketLog(
                    id = 7,
                    shopId = "shop-1",
                    shopName = "一号店",
                    type = 20,
                    orderNo = "pay-1",
                    amount = 30,
                    principalAmount = 20,
                    presentAmount = 10,
                    remark = "接水抵扣",
                    createdAt = "2026-10-25 12:00:00",
                ),
            ),
        )

        assertEquals(true, merged.ticket?.matchedLog)
        assertEquals(30, merged.ticket?.points)
        assertEquals(20, merged.ticket?.principalPoints)
        assertEquals(10, merged.ticket?.presentPoints)
        assertEquals("一号店", merged.ticket?.shopName)
        assertEquals("接水抵扣", merged.ticket?.remark)
    }

    @Test
    fun `missing ticket log keeps bill points and records error`() {
        val bill = order(
            promotions = listOf(BillPromotionDto(promotionType = 4, discountAmount = "0.30")),
        ).toBillInfo(fetchedAt = 1L)

        val merged = bill.withTicketLogs(emptyList(), logError = "网络异常")

        assertFalse(merged.ticket?.matchedLog == true)
        assertEquals(30, merged.ticket?.points)
        assertEquals("网络异常", merged.ticketLogError)
    }

    @Test
    fun `ticket income logs are ignored`() {
        val bill = order(
            promotions = listOf(BillPromotionDto(promotionType = 4, discountAmount = "0.30")),
        ).toBillInfo(fetchedAt = 1L)

        val merged = bill.withTicketLogs(
            listOf(TicketLog(type = 100, orderNo = "pay-1", amount = 500)),
        )

        assertFalse(merged.ticket?.matchedLog == true)
        assertEquals(30, merged.ticket?.points)
    }

    @Test
    fun `ticket log amount mismatch is exposed instead of hidden`() {
        val bill = order(
            promotions = listOf(BillPromotionDto(promotionType = 4, discountAmount = "0.30")),
        ).toBillInfo(fetchedAt = 1L)

        val merged = bill.withTicketLogs(
            listOf(TicketLog(type = 20, orderNo = "pay-1", amount = 25)),
        )

        assertEquals(25, merged.ticket?.points)
        assertEquals(25, merged.ticket?.logPoints)
        assertEquals(30, merged.ticket?.billPoints)
    }

    @Test
    fun `bill codec round trips cached ticket`() {
        val bill = order(
            promotions = listOf(BillPromotionDto(promotionType = 4, discountAmount = "0.30")),
        ).toBillInfo(fetchedAt = 9L).withTicketLogs(
            listOf(TicketLog(type = 20, orderNo = "pay-1", amount = 30)),
        )

        val decoded = BillCodec.decode(BillCodec.encode(bill))

        assertEquals(bill, decoded)
    }

    private fun order(
        markPrice: String = "2.00",
        payPrice: String = "1.50",
        paymentDiscountPrice: String = "0",
        promotions: List<BillPromotionDto> = emptyList(),
        tradeOrderItem: List<BillTradeItemDto> = emptyList(),
    ): BillOrderDto = BillOrderDto(
        id = "order-id",
        orderNo = "pay-1",
        orderStatus = 2,
        orderType = 3,
        markPrice = markPrice,
        payPrice = payPrice,
        paymentDiscountPrice = paymentDiscountPrice,
        createTime = "2026-10-25 11:50:00",
        payTime = "2026-10-25 12:00:00",
        machineName = "一号饮水机",
        shopId = "shop-1",
        shopName = "一号店",
        payTypeName = "校园卡",
        promotionList = promotions,
        tradeOrderItem = tradeOrderItem,
    )
}
