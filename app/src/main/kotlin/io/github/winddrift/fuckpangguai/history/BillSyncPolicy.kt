package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.net.dto.BillOrderDto
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import io.github.winddrift.fuckpangguai.water.SessionPhase

internal const val BILL_AUTO_SYNC_WINDOW_MS = 90L * 24 * 60 * 60 * 1000
private const val MATCHED_TTL_MS = 24L * 60 * 60 * 1000
private const val PENDING_TTL_MS = 5L * 60 * 1000
private const val UNMATCHED_TTL_MS = 10L * 60 * 1000
private const val ERROR_TTL_MS = 5L * 60 * 1000

internal fun HistoryRecord.isWithinBillWindow(now: Long): Boolean =
    now - updatedAt <= BILL_AUTO_SYNC_WINDOW_MS

internal fun HistoryRecord.expectsBill(): Boolean =
    bill != null || phase != SessionPhase.FINISHED_NO_CHARGE

internal fun HistoryRecord.billCandidateKeys(): List<String> =
    listOf(payOrderNo, payOrderId, identify, orderNo)
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("local-") }
        .distinct()

internal fun HistoryRecord.shouldSyncBill(now: Long, includeOld: Boolean): Boolean {
    if (!expectsBill()) return false
    if (!includeOld && !isWithinBillWindow(now)) return false
    val age = now - billFetchedAt
    return when (billState) {
        BillState.MATCHED -> {
            if (bill == null) return true
            val pending = bill.orderStatus == 0 || bill.orderStatus == 1
            val ttl = when {
                pending -> PENDING_TTL_MS
                bill.ticketLogError.isNotBlank() -> UNMATCHED_TTL_MS
                else -> MATCHED_TTL_MS
            }
            age >= ttl
        }
        BillState.UNMATCHED -> age >= UNMATCHED_TTL_MS
        BillState.ERROR -> age >= ERROR_TTL_MS
        BillState.NONE -> true
    }
}

internal fun BillOrderDto.matchesKeys(keys: Set<String>): Boolean =
    (id.isNotBlank() && id in keys) || (orderNo.isNotBlank() && orderNo in keys)

internal fun TicketLog.matchesOrder(keys: Set<String>): Boolean =
    type != 100 && orderNo.isNotBlank() && orderNo in keys
