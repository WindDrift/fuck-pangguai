package io.github.winddrift.fuckpangguai.history

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class BillState(val storage: String) {
    NONE("none"),
    MATCHED("matched"),
    UNMATCHED("unmatched"),
    ERROR("error"),
    ;

    companion object {
        fun fromStorage(value: String): BillState =
            entries.firstOrNull { it.storage == value } ?: NONE
    }
}

@Serializable
data class BillInfo(
    val orderId: String = "",
    val orderNo: String = "",
    val orderStatus: Int = 0,
    val orderType: Int = 0,
    val originalAmount: String = "",
    val payAmount: String = "",
    val paymentDiscountAmount: String = "",
    val paidAmount: String = "",
    val promotions: List<BillDiscount> = emptyList(),
    val ticket: BillTicket? = null,
    val ticketLogError: String = "",
    val shopId: String = "",
    val shopName: String = "",
    val machineName: String = "",
    val payTypeName: String = "",
    val createTime: String = "",
    val payTime: String = "",
    val fetchedAt: Long = 0L,
)

@Serializable
data class BillDiscount(
    val type: Int,
    val label: String,
    val amount: String,
)

/**
 * 通用小票抵扣信息。
 *
 * [billPoints] 来自订单详情的 GOLD 优惠，是账单口径；
 * [points] 在匹配到流水后取流水合计，否则等于 [billPoints]。
 * 两者不一致时由界面提示，不以任何一方静默覆盖另一方。
 */
@Serializable
data class BillTicket(
    val billPoints: Int = 0,
    val points: Int = 0,
    val principalPoints: Int = 0,
    val presentPoints: Int = 0,
    val deductionYuan: String = "",
    val shopId: String = "",
    val shopName: String = "",
    val remark: String = "",
    val createdAt: String = "",
    val matchedLog: Boolean = false,
    val logPoints: Int = 0,
)

internal object BillCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(bill: BillInfo): String = json.encodeToString(BillInfo.serializer(), bill)

    fun decode(raw: String): BillInfo? {
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString(BillInfo.serializer(), raw) }.getOrNull()
    }
}
