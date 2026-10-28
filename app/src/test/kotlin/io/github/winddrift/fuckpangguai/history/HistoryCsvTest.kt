package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.water.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCsvTest {

    @Test
    fun `exported csv can be imported back`() {
        val record = HistoryRecord(
            id = 1,
            accountId = "account",
            goodsId = "goods-1",
            deviceName = "三教, 一楼\n饮水机 \"A\"",
            skuId = "sku-1",
            amount = "1.50",
            orderNo = "order-1",
            payOrderNo = "pay-1",
            payOrderId = "pay-id-1",
            identify = "identify-1",
            phase = SessionPhase.PAID,
            error = "余额不足, 已重试",
            startedAt = 1_700_000_000_000,
            updatedAt = 1_700_000_300_000,
            billState = BillState.NONE,
            bill = null,
            billError = "",
            billFetchedAt = 0,
        )

        val parsed = parseHistoryCsv(listOf(record).toCsv())
        assertTrue(parsed is HistoryCsvParseResult.Success)
        val success = parsed as HistoryCsvParseResult.Success
        assertEquals(0, success.invalidRows)
        assertEquals(1, success.rows.size)
        val row = success.rows.single()
        assertEquals(record.deviceName, row.deviceName)
        assertEquals(record.goodsId, row.goodsId)
        assertEquals(record.skuId, row.skuId)
        assertEquals(record.amount, row.amount)
        assertEquals(record.orderNo, row.orderNo)
        assertEquals(record.payOrderNo, row.payOrderNo)
        assertEquals(record.phase, row.phase)
        assertEquals(record.error, row.error)
        assertEquals(record.startedAt, row.startedAt)
        assertEquals(record.updatedAt, row.updatedAt)
    }

    @Test
    fun `parser tolerates bom and crlf line endings`() {
        val csv = "\uFEFF" + listOf(
            "时间,设备,goodsId,skuId,用量,订单号,结算订单号,状态,错误,开始时间,更新时间",
            "2023-11-14 22:13,一号机,g,s,0.80,order-2,,已支付,,1700000000000,1700000300000",
        ).joinToString("\r\n")

        val parsed = parseHistoryCsv(csv) as HistoryCsvParseResult.Success
        assertEquals(1, parsed.rows.size)
        assertEquals("一号机", parsed.rows.single().deviceName)
        assertEquals(SessionPhase.PAID, parsed.rows.single().phase)
    }

    @Test
    fun `unknown status falls back by amount`() {
        val csv = csv(
            "2023-11-14 22:13,付费机,g,s,2.00,order-3,,未知状态,,1700000000000,1700000300000",
            "2023-11-14 22:14,免费机,g,s,0,order-4,,未知状态,,1700000000000,1700000300000",
        )

        val rows = (parseHistoryCsv(csv) as HistoryCsvParseResult.Success).rows
        assertEquals(SessionPhase.PAID, rows[0].phase)
        assertEquals(SessionPhase.FINISHED_NO_CHARGE, rows[1].phase)
    }

    @Test
    fun `raw phase values are preserved`() {
        val csv = csv(
            "2023-11-14 22:13,设备,g,s,1.00,order-5,,settling,,1700000000000,1700000300000",
        )

        val row = (parseHistoryCsv(csv) as HistoryCsvParseResult.Success).rows.single()
        assertEquals(SessionPhase.SETTLING, row.phase)
    }

    @Test
    fun `invalid rows are counted and skipped`() {
        val csv = csv(
            "2023-11-14 22:13,设备,g,s,1.00,,,已支付,,1700000000000,1700000300000",
            ",设备,g,s,1.00,order-6,,已支付,,bad-time,bad-time",
            "2023-11-14 22:15,设备,g,s,1.00,order-7,,已支付,,1700000000000,1700000300000",
        )

        val parsed = parseHistoryCsv(csv) as HistoryCsvParseResult.Success
        assertEquals(2, parsed.invalidRows)
        assertEquals(listOf("order-7"), parsed.rows.map { it.orderNo })
    }

    @Test
    fun `missing required header is rejected`() {
        val csv = "时间,设备,订单号\n2023-11-14 22:13,设备,order-8\n"

        assertEquals(HistoryCsvParseResult.InvalidFormat, parseHistoryCsv(csv))
    }

    @Test
    fun `formatted time is used when epoch columns are missing values`() {
        val csv = "时间,设备,goodsId,skuId,用量,订单号,结算订单号,状态,错误,开始时间,更新时间\n" +
            "2023-11-14 22:13,设备,g,s,1.00,order-9,,已支付,,,\n"

        val row = (parseHistoryCsv(csv) as HistoryCsvParseResult.Success).rows.single()
        assertTrue(row.updatedAt > 0)
        assertEquals(row.updatedAt, row.startedAt)
    }

    private fun csv(vararg lines: String): String = buildString {
        appendLine("时间,设备,goodsId,skuId,用量,订单号,结算订单号,状态,错误,开始时间,更新时间")
        lines.forEach(::appendLine)
    }
}
