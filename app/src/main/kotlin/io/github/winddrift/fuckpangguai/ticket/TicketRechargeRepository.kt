package io.github.winddrift.fuckpangguai.ticket

import io.github.winddrift.fuckpangguai.net.ApiException
import io.github.winddrift.fuckpangguai.net.QiekjApi
import io.github.winddrift.fuckpangguai.net.dto.ApiResponse
import io.github.winddrift.fuckpangguai.net.dto.PayChannel
import io.github.winddrift.fuckpangguai.net.dto.PrePay
import io.github.winddrift.fuckpangguai.net.dto.TicketGoods
import io.github.winddrift.fuckpangguai.net.dto.VipOrderDetail
import io.github.winddrift.fuckpangguai.net.dto.VirtualOrder
import io.github.winddrift.fuckpangguai.net.requireSuccess

interface TicketRechargeDataSource {
    suspend fun loadGoods(shopId: String, deviceGoodsId: String?): TicketGoods
    suspend fun createOrder(
        shopId: String,
        goodsId: String,
        goodsType: String,
        num: String,
        coinRewardId: String,
    ): VirtualOrder
    suspend fun loadOrderDetail(orderId: String): VipOrderDetail
    suspend fun loadPayChannels(shopId: String): List<PayChannel>
    suspend fun prePay(orderNo: String, payType: String): PrePay
    suspend fun queryPayStatus(orderNo: String, payType: String): Boolean
}

class QiekjTicketRechargeRepository(
    private val api: QiekjApi,
) : TicketRechargeDataSource {

    override suspend fun loadGoods(shopId: String, deviceGoodsId: String?): TicketGoods =
        api.ticketGoods(
            buildMap {
                put("shopId", shopId)
                deviceGoodsId?.takeIf { it.isNotBlank() }?.let { put("deviceGoodsId", it) }
            },
        ).requireData()

    override suspend fun createOrder(
        shopId: String,
        goodsId: String,
        goodsType: String,
        num: String,
        coinRewardId: String,
    ): VirtualOrder = api.createVirtualOrder(
        mapOf(
            "shopId" to shopId,
            "goodsId" to goodsId,
            "goodsType" to goodsType,
            "Num" to num,
            "tokenCoinRewardId" to coinRewardId,
        ),
    ).requireData()

    override suspend fun loadOrderDetail(orderId: String): VipOrderDetail =
        api.vipOrderDetail(mapOf("orderId" to orderId)).requireData()

    override suspend fun loadPayChannels(shopId: String): List<PayChannel> {
        val response = api.ticketPayChannels(mapOf("shopId" to shopId))
        requireSuccess(response.code, response.msg)
        return response.data.orEmpty()
    }

    override suspend fun prePay(orderNo: String, payType: String): PrePay =
        api.ticketPrePay(mapOf("orderNo" to orderNo, "payType" to payType)).requireData()

    override suspend fun queryPayStatus(orderNo: String, payType: String): Boolean =
        api.orderPayStatus(mapOf("orderNo" to orderNo, "payType" to payType)).requireData()
}

private fun <T> ApiResponse<T>.requireData(): T {
    requireSuccess(code, msg)
    return data ?: throw ApiException(code, "响应为空")
}
