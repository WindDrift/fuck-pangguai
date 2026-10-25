package io.github.winddrift.fuckpangguai.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class TicketGoods(
    val shopId: String = "",
    val shopName: String = "",
    val goodsId: String = "",
    val goodsType: String = "",
    val refundRemark: String = "",
    val isAllowRefund: Int = 1,
    val shopTokenCoinRewards: List<TicketReward> = emptyList(),
    val userTokenCoinDTO: TicketBalance? = null,
)

@Serializable
data class TicketReward(
    val id: String = "",
    val shopId: String = "",
    val shopTokenCoinId: Int = 0,
    val reach: Int = 0,
    val reward: Int = 0,
    val cashValue: String = "",
)

@Serializable
data class TicketBalance(
    val id: String = "",
    val userId: String = "",
    val shopId: String = "",
    val totalAmount: Int = 0,
    val principalAmount: String = "0",
    val presentAmount: String = "0",
    val createdAt: String = "",
)

@Serializable
data class VirtualOrder(
    val orderId: String = "",
    val orderNo: String = "",
    val payContentCode: Int = 0,
    val price: String = "",
    val shopName: String = "",
)

@Serializable
data class VipOrderDetail(
    val id: String = "",
    val orderId: String = "",
    val orderNo: String = "",
    val orderStatus: Int = 0,
    val payPrice: String = "",
    val payContentCode: String = "",
    val payType: Int = 0,
    val shopId: String = "",
    val shopName: String = "",
    val alipayOrderNo: String = "",
    val payTime: Long = 0L,
)

@Serializable
data class PayChannel(
    val payType: Int = 0,
    val payTypeName: String = "",
    val logoUrl: String = "",
    val defaultFlag: Int = 0,
    val checked: Boolean = false,
)

@Serializable
data class PrePay(
    val exist: Boolean = false,
    val gateway: String = "",
    val messageId: String = "",
    val prepayParam: String = "",
    val payTypeList: List<Int> = emptyList(),
)
