package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.net.dto.BillOrderDto
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import io.github.winddrift.fuckpangguai.water.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillSyncPolicyTest {

    private val now = 10_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `records inside window sync when no bill`() {
        val record = record(updatedAt = now - 10 * day)
        assertTrue(record.shouldSyncBill(now, includeOld = false))
    }

    @Test
    fun `records outside window wait for explicit retry`() {
        val record = record(updatedAt = now - 100 * day)
        assertFalse(record.shouldSyncBill(now, includeOld = false))
        assertTrue(record.shouldSyncBill(now, includeOld = true))
    }

    @Test
    fun `no charge session without bill is skipped`() {
        val record = record(
            phase = SessionPhase.FINISHED_NO_CHARGE,
            updatedAt = now - day,
        )
        assertFalse(record.shouldSyncBill(now, includeOld = true))
    }

    @Test
    fun `matched bill refreshes after a day`() {
        val fresh = record(
            updatedAt = now - day,
            billState = BillState.MATCHED,
            bill = bill(orderStatus = 2),
            billFetchedAt = now - 60 * 60 * 1000,
        )
        val stale = fresh.copy(billFetchedAt = now - 25 * 60 * 60 * 1000)
        assertFalse(fresh.shouldSyncBill(now, includeOld = false))
        assertTrue(stale.shouldSyncBill(now, includeOld = false))
    }

    @Test
    fun `ticket log failure retries after ten minutes`() {
        val record = record(
            updatedAt = now - day,
            billState = BillState.MATCHED,
            bill = bill(orderStatus = 2).copy(ticketLogError = "网络异常"),
            billFetchedAt = now - 11 * 60 * 1000,
        )
        assertTrue(record.shouldSyncBill(now, includeOld = false))
    }

    @Test
    fun `pending bill refreshes after five minutes`() {
        val record = record(
            updatedAt = now - day,
            billState = BillState.MATCHED,
            bill = bill(orderStatus = 0),
            billFetchedAt = now - 6 * 60 * 1000,
        )
        assertTrue(record.shouldSyncBill(now, includeOld = false))
    }

    @Test
    fun `unmatched retries after ten minutes`() {
        val record = record(
            updatedAt = now - day,
            billState = BillState.UNMATCHED,
            billFetchedAt = now - 5 * 60 * 1000,
        )
        assertFalse(record.shouldSyncBill(now, includeOld = false))
        assertTrue(record.copy(billFetchedAt = now - 11 * 60 * 1000).shouldSyncBill(now, false))
    }

    @Test
    fun `error retries after five minutes`() {
        val record = record(
            updatedAt = now - day,
            billState = BillState.ERROR,
            billFetchedAt = now - 4 * 60 * 1000,
        )
        assertFalse(record.shouldSyncBill(now, includeOld = false))
        assertTrue(record.copy(billFetchedAt = now - 6 * 60 * 1000).shouldSyncBill(now, false))
    }

    @Test
    fun `candidate keys follow pay order settle identify and unlock order`() {
        val record = record(
            payOrderNo = "pay-1",
            payOrderId = "id-1",
            identify = "identify-1",
            orderNo = "unlock-1",
        )
        assertEquals(
            listOf("pay-1", "id-1", "identify-1", "unlock-1"),
            record.billCandidateKeys(),
        )

        val duplicate = record.copy(
            payOrderNo = "same",
            payOrderId = "same",
            identify = " ",
        )
        assertEquals(listOf("same", "unlock-1"), duplicate.billCandidateKeys())

        val synthetic = record.copy(
            payOrderNo = "",
            payOrderId = "",
            identify = "",
            orderNo = "local-123",
        )
        assertTrue(synthetic.billCandidateKeys().isEmpty())
    }

    @Test
    fun `order matches by id or order number`() {
        val order = BillOrderDto(id = "id-1", orderNo = "pay-1")
        assertTrue(order.matchesKeys(setOf("id-1")))
        assertTrue(order.matchesKeys(setOf("pay-1")))
        assertFalse(order.matchesKeys(setOf("other")))
    }

    @Test
    fun `ticket log requires order number and excludes income`() {
        val log = TicketLog(type = 20, orderNo = "pay-1")
        assertTrue(log.matchesOrder(setOf("pay-1")))
        assertFalse(log.copy(type = 100).matchesOrder(setOf("pay-1")))
        assertFalse(log.copy(orderNo = "").matchesOrder(setOf("pay-1")))
    }

    private fun bill(orderStatus: Int) = BillInfo(
        orderNo = "pay-1",
        orderStatus = orderStatus,
        paidAmount = "1.00",
        fetchedAt = now,
    )

    private fun record(
        phase: String = SessionPhase.PAID,
        updatedAt: Long = now - day,
        payOrderNo: String = "pay-1",
        payOrderId: String = "",
        identify: String = "",
        orderNo: String = "unlock-1",
        billState: BillState = BillState.NONE,
        bill: BillInfo? = null,
        billFetchedAt: Long = 0L,
    ): HistoryRecord = HistoryRecord(
        id = 1L,
        accountId = "account",
        goodsId = "goods",
        deviceName = "饮水机",
        skuId = "sku",
        amount = "1.00",
        orderNo = orderNo,
        payOrderNo = payOrderNo,
        payOrderId = payOrderId,
        identify = identify,
        phase = phase,
        error = "",
        startedAt = updatedAt - 60_000,
        updatedAt = updatedAt,
        billState = billState,
        bill = bill,
        billError = "",
        billFetchedAt = billFetchedAt,
    )
}
