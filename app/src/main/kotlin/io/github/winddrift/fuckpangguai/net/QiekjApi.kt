package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.net.dto.AfterPay
import io.github.winddrift.fuckpangguai.net.dto.ApiResponse
import io.github.winddrift.fuckpangguai.net.dto.BillOrderDto
import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.net.dto.IntegralLimitRuleDto
import io.github.winddrift.fuckpangguai.net.dto.IntegralTaskDto
import io.github.winddrift.fuckpangguai.net.dto.ItemsList
import io.github.winddrift.fuckpangguai.net.dto.LoginBean
import io.github.winddrift.fuckpangguai.net.dto.MsgSyncBean
import io.github.winddrift.fuckpangguai.net.dto.PayChannel
import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.PrePay
import io.github.winddrift.fuckpangguai.net.dto.ScanV2Bean
import io.github.winddrift.fuckpangguai.net.dto.SignInActDto
import io.github.winddrift.fuckpangguai.net.dto.Sku
import io.github.winddrift.fuckpangguai.net.dto.Ticket
import io.github.winddrift.fuckpangguai.net.dto.TicketGoods
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import io.github.winddrift.fuckpangguai.net.dto.TodaySignInDto
import io.github.winddrift.fuckpangguai.net.dto.Unlock
import io.github.winddrift.fuckpangguai.net.dto.UserBalance
import io.github.winddrift.fuckpangguai.net.dto.UserProfile
import io.github.winddrift.fuckpangguai.net.dto.VipOrderDetail
import io.github.winddrift.fuckpangguai.net.dto.VirtualOrder
import io.github.winddrift.fuckpangguai.net.dto.WaterSync
import kotlinx.serialization.json.JsonElement
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.HeaderMap
import retrofit2.http.POST

interface QiekjApi {

    @FormUrlEncoded
    @POST("common/sms/sendCode")
    suspend fun sendCode(@FieldMap body: Map<String, String>): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("user/reg")
    suspend fun login(@FieldMap body: Map<String, String>): ApiResponse<LoginBean>

    @FormUrlEncoded
    @POST("user/checkLogin")
    suspend fun checkLogin(@FieldMap body: Map<String, String>): ApiResponse<LoginBean>

    @FormUrlEncoded
    @POST("goods/scan/v2")
    suspend fun scanV2(@FieldMap body: Map<String, String>): ApiResponse<ScanV2Bean>

    @FormUrlEncoded
    @POST("goods/normal/details")
    suspend fun goodsDetails(@FieldMap body: Map<String, String>): ApiResponse<GoodsDetail>

    @FormUrlEncoded
    @POST("goods/normal/skus")
    suspend fun skus(@FieldMap body: Map<String, String>): ApiResponse<List<Sku>>

    @FormUrlEncoded
    @POST("payChannelRoute/afterPay")
    suspend fun afterPayChannels(@FieldMap body: Map<String, String>): ApiResponse<List<PayChannelRoute>>

    @FormUrlEncoded
    @POST("payChannelRoute/addUserAfterPayChannel")
    suspend fun addAfterPayChannel(@FieldMap body: Map<String, String>): ApiResponse<String>

    @FormUrlEncoded
    @POST("goods/water/unlock")
    suspend fun waterUnlock(
        @HeaderMap headers: Map<String, String>,
        @FieldMap body: Map<String, String>,
    ): ApiResponse<Unlock>

    @FormUrlEncoded
    @POST("machine/msg/sync")
    suspend fun msgSync(@FieldMap body: Map<String, String>): ApiResponse<MsgSyncBean>

    @FormUrlEncoded
    @POST("machine/device_is_start")
    suspend fun deviceStartStatus(@FieldMap body: Map<String, String>): ApiResponse<Boolean>

    @FormUrlEncoded
    @POST("/goods/water/sync")
    suspend fun waterSync(@FieldMap body: Map<String, String>): ApiResponse<WaterSync>

    @FormUrlEncoded
    @POST("goods/endWater")
    suspend fun endWater(@FieldMap body: Map<String, String>): ApiResponse<Boolean>

    @FormUrlEncoded
    @POST("order/afterPay/creating")
    suspend fun afterPayCreate(@FieldMap body: Map<String, String>): ApiResponse<AfterPay>

    @FormUrlEncoded
    @POST("order/sync")
    suspend fun orderSync(@FieldMap body: Map<String, String>): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("order/pay_status")
    suspend fun orderPayStatus(@FieldMap body: Map<String, String>): ApiResponse<Boolean>

    @FormUrlEncoded
    @POST("order/list")
    suspend fun orderList(@FieldMap body: Map<String, String>): ApiResponse<ItemsList<BillOrderDto>>

    @FormUrlEncoded
    @POST("order/detail")
    suspend fun orderDetail(@FieldMap body: Map<String, String>): ApiResponse<BillOrderDto>

    @FormUrlEncoded
    @POST("user/info")
    suspend fun userInfo(@FieldMap body: Map<String, String>): ApiResponse<UserProfile>

    @FormUrlEncoded
    @POST("user/balance")
    suspend fun userBalance(@FieldMap body: Map<String, String>): ApiResponse<UserBalance>

    @FormUrlEncoded
    @POST("tokenCoin/userTokenCoins")
    suspend fun userTokenCoins(@FieldMap body: Map<String, String>): ApiResponse<ItemsList<Ticket>>

    @FormUrlEncoded
    @POST("tokenCoin/userTokenCoinLogs")
    suspend fun userTokenCoinLogs(@FieldMap body: Map<String, String>): ApiResponse<ItemsList<TicketLog>>

    @FormUrlEncoded
    @POST("tokenCoin/refund/apply")
    suspend fun tokenCoinRefundApply(@FieldMap body: Map<String, String>): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("tokenCoin/goods")
    suspend fun ticketGoods(@FieldMap body: Map<String, String>): ApiResponse<TicketGoods>

    @FormUrlEncoded
    @POST("order/createVirtualOrder")
    suspend fun createVirtualOrder(@FieldMap body: Map<String, String>): ApiResponse<VirtualOrder>

    @FormUrlEncoded
    @POST("order/vip/detail")
    suspend fun vipOrderDetail(@FieldMap body: Map<String, String>): ApiResponse<VipOrderDetail>

    @FormUrlEncoded
    @POST("pay/ticket/getAvailablePayTypeList")
    suspend fun ticketPayChannels(@FieldMap body: Map<String, String>): ApiResponse<List<PayChannel>>

    @FormUrlEncoded
    @POST("pay/unify/prePay")
    suspend fun ticketPrePay(@FieldMap body: Map<String, String>): ApiResponse<PrePay>

    @FormUrlEncoded
    @POST("signin/signInAcList")
    suspend fun signInActList(@FieldMap body: Map<String, String>): ApiResponse<SignInActDto>

    @FormUrlEncoded
    @POST("signin/todaySignIn")
    suspend fun todaySignIn(@FieldMap body: Map<String, String>): ApiResponse<TodaySignInDto>

    @FormUrlEncoded
    @POST("signin/doUserSignIn")
    suspend fun doUserSignIn(@FieldMap body: Map<String, String>): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("task/list")
    suspend fun taskList(@FieldMap body: Map<String, String>): ApiResponse<ItemsList<IntegralTaskDto>>

    @FormUrlEncoded
    @POST("task/queryByType")
    suspend fun taskQueryByType(@FieldMap body: Map<String, String>): ApiResponse<IntegralTaskDto>

    @FormUrlEncoded
    @POST("task/completed")
    suspend fun taskCompleted(@FieldMap body: Map<String, String>): ApiResponse<JsonElement>

    @FormUrlEncoded
    @POST("integralCaptcha/isCaptcha")
    suspend fun isCaptcha(@FieldMap body: Map<String, String>): ApiResponse<Boolean>

    @FormUrlEncoded
    @POST("userIntegral/limitRule")
    suspend fun userIntegralRule(@FieldMap body: Map<String, String>): ApiResponse<IntegralLimitRuleDto>

    @FormUrlEncoded
    @POST("userIntegral/checkUserIsRisk")
    suspend fun checkUserIsRisk(@FieldMap body: Map<String, String>): ApiResponse<Boolean>
}
