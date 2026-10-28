package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.water.SessionPhase
import io.github.winddrift.fuckpangguai.water.WaterConversion
import io.github.winddrift.fuckpangguai.water.formatWaterVolume
import io.github.winddrift.fuckpangguai.water.parsePureUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class HistorySummaryTest {

    @Test
    fun `summary aggregates original spend and pure usage`() {
        val records = listOf(
            record(amount = "1.50", bill = bill(orderStatus = 2, originalAmount = "2.00")),
            record(amount = "1.00", bill = bill(orderStatus = 3, originalAmount = "1.00")),
            record(amount = "2.00", bill = bill(orderStatus = 4, originalAmount = "5.00")),
            record(amount = "1.00", bill = bill(orderStatus = 0, originalAmount = "3.00")),
            record(
                amount = "3.00",
                billState = BillState.ERROR,
                bill = bill(orderStatus = 2, originalAmount = "4.00"),
            ),
            record(amount = "1.00", billState = BillState.UNMATCHED, bill = null),
            record(amount = "1.5L", bill = bill(orderStatus = 2, originalAmount = "1.00")),
            record(amount = " ", bill = bill(orderStatus = 2, originalAmount = "")),
            record(amount = "1,5", bill = bill(orderStatus = 2, originalAmount = "2.00")),
        )

        val summary = records.summarizeHistory()

        assertEquals(9, summary.visibleCount)
        assertEquals(4, summary.spendCoverage)
        assertEquals(6, summary.usageCoverage)
        assertEquals(2, summary.averageCoverage)
        assertEquals(0, summary.totalSpend!!.compareTo(BigDecimal("6.00")))
        assertEquals(0, summary.totalUsage!!.compareTo(BigDecimal("9.5")))
        assertEquals(BigDecimal("0.83"), summary.usagePerYuan)
        assertEquals("¥6.00", formatSummaryMoney(summary.totalSpend))
        assertEquals("9.5", formatSummaryUsage(summary.totalUsage))
        assertEquals("1 元 ≈ 0.83 用量", formatSummaryAverage(summary.usagePerYuan))
    }

    @Test
    fun `no eligible spend or usage shows dash`() {
        val records = listOf(
            record(amount = "1.00", billState = BillState.UNMATCHED, bill = null),
            record(amount = "1.00", bill = bill(orderStatus = 5, originalAmount = "2.00")),
        )

        val summary = records.summarizeHistory()

        assertNull(summary.totalSpend)
        assertNull(summary.usagePerYuan)
        assertEquals("—", formatSummaryMoney(summary.totalSpend))
        assertEquals("2", formatSummaryUsage(summary.totalUsage))
        assertEquals("—", formatSummaryAverage(summary.usagePerYuan))
    }

    @Test
    fun `no parseable usage shows dash`() {
        val summary = listOf(
            record(amount = "1.5L", bill = bill(orderStatus = 2, originalAmount = "1.00")),
        ).summarizeHistory()

        assertNull(summary.totalUsage)
        assertNull(summary.usagePerYuan)
        assertEquals("—", formatSummaryUsage(summary.totalUsage))
    }

    @Test
    fun `water total merges pure usage and unit records`() {
        val records = listOf(
            record(amount = "100", bill = bill(orderStatus = 2, originalAmount = "2.00")),
            record(amount = "1.2L", bill = bill(orderStatus = 2, originalAmount = "3.00")),
            record(amount = "300ml", bill = bill(orderStatus = 3, originalAmount = "1.00")),
            record(amount = "30分钟", bill = bill(orderStatus = 2, originalAmount = "4.00")),
            record(amount = "50", bill = bill(orderStatus = 2, originalAmount = "2.00")),
            record(amount = "1.5L", billState = BillState.UNMATCHED, bill = null),
        )
        val conversion = WaterConversion(
            usageBase = BigDecimal("100"),
            waterMl = BigDecimal("500"),
            cupMl = BigDecimal("250"),
        )

        val summary = records.summarizeHistory(conversion)

        assertEquals(6, summary.visibleCount)
        assertEquals(5, summary.spendCoverage)
        assertEquals(2, summary.usageCoverage)
        assertEquals(2, summary.averageCoverage)
        assertEquals(5, summary.waterCoverage)
        assertEquals(4, summary.waterAverageCoverage)
        assertEquals(0, summary.totalSpend!!.compareTo(BigDecimal("12.00")))
        assertEquals(0, summary.totalUsage!!.compareTo(BigDecimal("150")))
        assertEquals(0, summary.totalWaterMl!!.compareTo(BigDecimal("3750")))
        assertEquals(BigDecimal("37.50"), summary.usagePerYuan)
        assertEquals(BigDecimal("281.25"), summary.waterPerYuanMl)
        assertEquals("150", formatSummaryUsage(summary.totalUsage))
        assertEquals("3.75 L", formatWaterVolume(summary.totalWaterMl))
        assertEquals(
            "1 元 ≈ 37.50 用量 · 281 mL · 约 1.1 杯",
            formatSummaryAverage(summary.usagePerYuan, summary.waterPerYuanMl, BigDecimal("250")),
        )
    }

    @Test
    fun `unit tagged records alone contribute to water but not usage`() {
        val summary = listOf(
            record(amount = "1.2L", bill = bill(orderStatus = 2, originalAmount = "2.00")),
            record(amount = "300ml", bill = bill(orderStatus = 2, originalAmount = "1.00")),
        ).summarizeHistory(
            WaterConversion(
                usageBase = BigDecimal("100"),
                waterMl = BigDecimal("500"),
                cupMl = null,
            ),
        )

        assertNull(summary.totalUsage)
        assertEquals(0, summary.totalWaterMl!!.compareTo(BigDecimal("1500")))
        assertEquals(2, summary.waterCoverage)
        assertNull(summary.usagePerYuan)
        assertEquals(BigDecimal("500.00"), summary.waterPerYuanMl)
        assertEquals("—", formatSummaryUsage(summary.totalUsage))
    }

    @Test
    fun `conversion off leaves water fields empty`() {
        val summary = listOf(record(amount = "100")).summarizeHistory()

        assertNull(summary.totalWaterMl)
        assertEquals(0, summary.waterCoverage)
        assertEquals(0, summary.waterAverageCoverage)
        assertNull(summary.waterPerYuanMl)
    }

    @Test
    fun `summary average includes water and cups when configured`() {
        val summary = listOf(
            record(amount = "100", bill = bill(orderStatus = 2, originalAmount = "1.00")),
        ).summarizeHistory(
            WaterConversion(
                usageBase = BigDecimal("100"),
                waterMl = BigDecimal("500"),
                cupMl = BigDecimal("250"),
            ),
        )

        assertEquals(
            "1 元 ≈ 100.00 用量 · 500 mL · 约 2 杯",
            formatSummaryAverage(summary.usagePerYuan, summary.waterPerYuanMl, BigDecimal("250")),
        )
    }

    @Test
    fun `pure usage parser rejects units commas negatives and exponents`() {
        listOf("1.5", " 1.5 ", ".5", "0", "12").forEach { value ->
            assertTrue("expected valid: $value", parsePureUsage(value) != null)
        }
        listOf("1.5L", "1,5", "-1", "1e3", "abc", "").forEach { value ->
            assertNull("expected invalid: $value", parsePureUsage(value))
        }
    }

    @Test
    fun `only matched paid or completed bills count toward original spend`() {
        val records = (0..5).map { status ->
            record(
                amount = "1.00",
                bill = bill(orderStatus = status, originalAmount = "1.00"),
            )
        } + record(
            amount = "1.00",
            billState = BillState.ERROR,
            bill = bill(orderStatus = 2, originalAmount = "9.00"),
        )

        val summary = records.summarizeHistory()

        assertEquals(2, summary.spendCoverage)
        assertEquals(BigDecimal("2.00"), summary.totalSpend)
        assertEquals(7, summary.visibleCount)
    }

    private fun record(
        amount: String,
        billState: BillState = BillState.MATCHED,
        bill: BillInfo? = bill(),
    ): HistoryRecord = HistoryRecord(
        id = 1,
        accountId = "account",
        goodsId = "goods",
        deviceName = "饮水机",
        skuId = "sku",
        amount = amount,
        orderNo = "order",
        payOrderNo = "pay",
        payOrderId = "",
        identify = "",
        phase = SessionPhase.PAID,
        error = "",
        startedAt = 1_000,
        updatedAt = 2_000,
        billState = billState,
        bill = bill,
        billError = "",
        billFetchedAt = 2_000,
    )

    private fun bill(
        orderStatus: Int = 2,
        originalAmount: String = "2.00",
    ): BillInfo = BillInfo(
        orderStatus = orderStatus,
        originalAmount = originalAmount,
        paidAmount = originalAmount,
    )
}
