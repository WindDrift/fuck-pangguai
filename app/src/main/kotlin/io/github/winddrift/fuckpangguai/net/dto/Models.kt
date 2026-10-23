package io.github.winddrift.fuckpangguai.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val code: Int = -1,
    val msg: String = "",
    val data: T? = null,
    val t: Long = 0L,
) {
    val isSuccess: Boolean get() = code == 0
}

@Serializable
data class LoginBean(
    val code: String = "",
    val token: String = "",
    val newRegister: Boolean = false,
    val homeType: Int = 0,
    val adId: String = "",
    val forcedBind: Boolean = false,
)

@Serializable
data class ScanV2Bean(
    val categoryCode: String = "",
    val categoryName: String = "",
    val id: String = "",
    val mpPositionType: Int = 0,
    val shopCanReserve: Boolean = false,
    val shopId: Int = 0,
    val shopWhitelist: String = "",
    val type: String = "",
    val jumpToAlipayFlag: Boolean = false,
    val jumpToAlipayFlag2: Boolean = false,
    val jumpToDevicePageFlag: Boolean = false,
)

@Serializable
data class GoodsDetail(
    val goodsId: String = "",
    val imei: String = "",
    val categoryCode: String = "",
    val orgId: String = "",
    val shopId: String = "",
    val name: String = "",
    val deviceErrorCode: String? = null,
    val deviceErrorMsg: String? = null,
    val isBluetooth: Int = 0,
    val payment: String = "",
    val payModel: Int = 0,
    val tradeMain: Int = 0,
    val supportAppUserFinish: Boolean = false,
    val userShowUseWaterDetail: Boolean = false,
    val extAttr: String = "",
    val payTypes: List<Int> = emptyList(),
    val announcementContent: String = "",
)

@Serializable
data class Sku(
    val skuId: String = "",
    val name: String = "",
    val price: String = "",
    val discountPrice: String = "",
    val amount: Int = 0,
    val unit: String = "",
    val defaultModel: Boolean = false,
    val scanAvailable: Boolean = false,
    val channelStatus: String = "",
    val extAttr: String = "",
)

@Serializable
data class PayChannelRoute(
    val method: Int = 0,
    val methodName: String = "",
    val defaultFlag: Boolean = false,
    val checked: Boolean = false,
    val confirmSelectedStatus: Boolean = false,
    val thirdMethod: Int = 0,
    val thirdChannel: String = "",
    val thirdPayBindStatus: Int = 0,
    val autoPayState: Int = 0,
    val campusCardBalance: String = "",
    val logoUrl: String = "",
    val remark: String = "",
    val sort: Int = 0,
)

@Serializable
data class Unlock(
    val orderNo: String = "",
    val msgId: String = "",
)

@Serializable
data class MsgSyncBean(
    val state: String = "",
    val code: String = "",
)

@Serializable
data class WaterSync(
    val status: Int = 0,
    val workStatus: Int = 0,
    val amount: String = "",
    val identify: String = "",
)

@Serializable
data class AfterPay(
    val orderId: String = "",
    val orderNo: String = "",
    val orderStatus: Int = 0,
    val payType: String = "0",
)
