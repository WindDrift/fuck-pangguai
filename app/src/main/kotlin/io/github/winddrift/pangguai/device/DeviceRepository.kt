package io.github.winddrift.pangguai.device

import io.github.winddrift.pangguai.core.AppConfig
import io.github.winddrift.pangguai.net.ApiException
import io.github.winddrift.pangguai.net.QiekjApi
import io.github.winddrift.pangguai.net.dto.GoodsDetail
import io.github.winddrift.pangguai.net.dto.PayChannelRoute
import io.github.winddrift.pangguai.net.dto.ScanV2Bean
import io.github.winddrift.pangguai.net.dto.Sku

data class ResolvedDevice(
    val scan: ScanV2Bean,
    val detail: GoodsDetail,
    val sourceKey: String,
    val sourceValue: String,
)

class DeviceRepository(private val api: QiekjApi) {

    suspend fun resolve(key: String, value: String): ResolvedDevice {
        val scanResponse = api.scanV2(mapOf(key to value))
        if (!scanResponse.isSuccess) {
            throw ApiException(scanResponse.code, scanResponse.msg)
        }
        val scan = scanResponse.data ?: throw ApiException(-1, "未识别到设备")
        if (scan.id.isBlank()) throw ApiException(-1, "设备信息为空")
        if (scan.categoryCode != AppConfig.WATER_CATEGORY) {
            throw ApiException(-1, "这不是饮水机（${scan.categoryName.ifBlank { scan.categoryCode }}）")
        }

        val detailResponse = api.goodsDetails(mapOf("goodsId" to scan.id))
        if (!detailResponse.isSuccess) {
            throw ApiException(detailResponse.code, detailResponse.msg)
        }
        val detail = detailResponse.data ?: throw ApiException(-1, "设备详情为空")
        return ResolvedDevice(scan, detail, sourceKey = key, sourceValue = value)
    }

    suspend fun loadSkus(goodsId: String): List<Sku> {
        val response = api.skus(mapOf("goodsId" to goodsId))
        if (!response.isSuccess) throw ApiException(response.code, response.msg)
        return response.data.orEmpty()
    }

    suspend fun loadChannels(detail: GoodsDetail): List<PayChannelRoute> {
        val response = api.afterPayChannels(
            mapOf(
                "imei" to detail.imei,
                "useTicket" to "0",
                "shopId" to detail.orgId,
                "goodsCategory" to detail.categoryCode,
            ),
        )
        if (!response.isSuccess) throw ApiException(response.code, response.msg)
        return response.data.orEmpty()
    }

    suspend fun bindChannel(method: Int) {
        val response = api.addAfterPayChannel(mapOf("method" to method.toString()))
        if (!response.isSuccess) throw ApiException(response.code, response.msg)
    }
}
