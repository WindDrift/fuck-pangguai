package io.github.winddrift.fuckpangguai.net.dto

import kotlinx.serialization.Serializable

/**
 * 官方订单列表/详情共用的账单模型。
 *
 * 列表接口不一定返回促销与交易明细，缺省时保持空列表；
 * 详情接口才会返回 promotionList 等完整字段。
 */
@Serializable
data class BillOrderDto(
    val id: String = "",
    val orderNo: String = "",
    val orderStatus: Int = 0,
    val orderType: Int = 0,
    val markPrice: String = "",
    val payPrice: String = "",
    val paymentDiscountPrice: String = "",
    val createTime: String = "",
    val payTime: String = "",
    val machineName: String = "",
    val shopId: String = "",
    val shopName: String = "",
    val payTypeName: String = "",
    val promotionList: List<BillPromotionDto> = emptyList(),
    val tradeOrderItem: List<BillTradeItemDto> = emptyList(),
)

@Serializable
data class BillPromotionDto(
    val promotionType: Int = 0,
    val discountAmount: String = "",
    val subsidyAmount: String = "",
)

@Serializable
data class BillTradeItemDto(
    val originPrice: String = "",
    val realPrice: String = "",
    val skuName: String = "",
    val goodsName: String = "",
)
