package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.net.dto.AfterPay
import io.github.winddrift.fuckpangguai.net.dto.ApiResponse
import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.net.dto.ItemsList
import io.github.winddrift.fuckpangguai.net.dto.LoginBean
import io.github.winddrift.fuckpangguai.net.dto.MsgSyncBean
import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.ScanV2Bean
import io.github.winddrift.fuckpangguai.net.dto.Sku
import io.github.winddrift.fuckpangguai.net.dto.Ticket
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import io.github.winddrift.fuckpangguai.net.dto.Unlock
import io.github.winddrift.fuckpangguai.net.dto.UserBalance
import io.github.winddrift.fuckpangguai.net.dto.UserProfile
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
}
