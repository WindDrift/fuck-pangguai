package io.github.winddrift.fuckpangguai.water

import kotlinx.serialization.Serializable

@Serializable
data class WaterSession(
    val goodsId: String,
    val skuId: String,
    val deviceName: String,
    val imei: String,
    val orderNo: String,
    val msgId: String,
    val supportAppUserFinish: Boolean,
    val startedAt: Long,
    val phase: String = SessionPhase.USING,
    val amount: String = "",
    val identify: String = "",
    val payOrderNo: String = "",
    val payOrderId: String = "",
    val payType: String = "0",
    val settleAttempted: Boolean = false,
    val error: String = "",
    val updatedAt: Long = startedAt,
    val accountId: String = "",
    val traceId: String = "",
    val waterConfirmedAt: Long = 0L,
    val endRequestedAt: Long = 0L,
    val endedAt: Long = 0L,
    val syncCount: Int = 0,
    val settleStartedAt: Long = 0L,
    val payPollAttempts: Int = 0,
)

object SessionPhase {
    const val USING = "using"
    const val ENDING = "ending"
    const val SETTLING = "settling"
    const val PAID = "paid"
    const val FINISHED_NO_CHARGE = "finished_no_charge"
    const val PAY_UNKNOWN = "pay_unknown"
    const val FAULT = "fault"

    val ACTIVE = setOf(USING, ENDING, SETTLING)
    val TERMINAL = setOf(PAID, FINISHED_NO_CHARGE, PAY_UNKNOWN, FAULT)
}
