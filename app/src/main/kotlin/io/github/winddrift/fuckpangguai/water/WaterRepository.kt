package io.github.winddrift.fuckpangguai.water

import io.github.winddrift.fuckpangguai.net.ApiException
import io.github.winddrift.fuckpangguai.net.QiekjApi
import io.github.winddrift.fuckpangguai.net.dto.AfterPay
import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.net.dto.MsgSyncBean
import io.github.winddrift.fuckpangguai.net.dto.Unlock
import io.github.winddrift.fuckpangguai.net.dto.WaterSync

class WaterRepository(private val api: QiekjApi) : WaterDataSource {

    override suspend fun unlock(
        detail: GoodsDetail,
        skuId: String,
        lat: String,
        lng: String,
        payType: String,
        iotBOrderNo: String,
        usePoints: Boolean,
    ): Unlock {
        val headers = mapOf(
            "imei" to detail.imei,
            "categoryCode" to detail.categoryCode,
            "lat" to lat,
            "lng" to lng,
        )
        val body = mutableMapOf(
            "skuId" to skuId,
            "promotions" to WaterDefaults.promotions(usePoints),
        )
        if (payType.isNotEmpty()) body["payType"] = payType
        if (iotBOrderNo.isNotEmpty()) body["iotBOrderNo"] = iotBOrderNo

        val response = api.waterUnlock(headers, body)
        if (!response.isSuccess) throw ApiException(response.code, response.msg)
        return response.data ?: throw ApiException(-1, "解锁响应为空")
    }

    override suspend fun msgSync(msgId: String): MsgSyncBean {
        val response = api.msgSync(mapOf("msgId" to msgId))
        if (!response.isSuccess) throw ApiException(response.code, response.msg)
        return response.data ?: MsgSyncBean()
    }

    override fun isDeviceReady(bean: MsgSyncBean): Boolean =
        bean.state.equals("OK", ignoreCase = true) || bean.code == "05"

    override suspend fun queryDeviceStarted(orderNo: String): Boolean? {
        val response = api.deviceStartStatus(mapOf("orderNo" to orderNo))
        return if (response.isSuccess) response.data else null
    }

    override suspend fun waterSync(skuId: String): WaterSync {
        val response = api.waterSync(mapOf("skuId" to skuId))
        if (!response.isSuccess) throw ApiException(response.code, response.msg)
        return response.data ?: WaterSync()
    }

    override suspend fun endWater(goodsId: String) {
        val response = api.endWater(mapOf("goodsId" to goodsId))
        if (!response.isSuccess) throw ApiException(response.code, response.msg)
    }

    override suspend fun afterPayCreate(identify: String): AfterPay {
        val response = api.afterPayCreate(mapOf("orderNo" to identify))
        if (!response.isSuccess) throw ApiException(response.code, response.msg)
        return response.data ?: throw ApiException(-1, "结算单响应为空")
    }

    override suspend fun orderPayStatus(orderNo: String, payType: String): Boolean {
        val response = api.orderPayStatus(mapOf("orderNo" to orderNo, "payType" to payType))
        if (!response.isSuccess) throw ApiException(response.code, response.msg)
        return response.data == true
    }

}
