package io.github.winddrift.fuckpangguai.device

import io.github.winddrift.fuckpangguai.net.QiekjApi
import io.github.winddrift.fuckpangguai.net.dto.ApiResponse
import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.ScanV2Bean
import io.github.winddrift.fuckpangguai.net.dto.Sku

internal interface DeviceApi {
    suspend fun scanV2(body: Map<String, String>): ApiResponse<ScanV2Bean>
    suspend fun goodsDetails(body: Map<String, String>): ApiResponse<GoodsDetail>
    suspend fun skus(body: Map<String, String>): ApiResponse<List<Sku>>
    suspend fun afterPayChannels(body: Map<String, String>): ApiResponse<List<PayChannelRoute>>
    suspend fun addAfterPayChannel(body: Map<String, String>): ApiResponse<String>
}

internal class RetrofitDeviceApi(private val api: QiekjApi) : DeviceApi {
    override suspend fun scanV2(body: Map<String, String>) = api.scanV2(body)

    override suspend fun goodsDetails(body: Map<String, String>) = api.goodsDetails(body)

    override suspend fun skus(body: Map<String, String>) = api.skus(body)

    override suspend fun afterPayChannels(body: Map<String, String>) = api.afterPayChannels(body)

    override suspend fun addAfterPayChannel(body: Map<String, String>) =
        api.addAfterPayChannel(body)
}
