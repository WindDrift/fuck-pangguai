package io.github.winddrift.fuckpangguai.water

import io.github.winddrift.fuckpangguai.net.dto.AfterPay
import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.net.dto.MsgSyncBean
import io.github.winddrift.fuckpangguai.net.dto.Unlock
import io.github.winddrift.fuckpangguai.net.dto.WaterSync

interface WaterDataSource {
    suspend fun unlock(
        detail: GoodsDetail,
        skuId: String,
        lat: String,
        lng: String,
        payType: String = "",
        iotBOrderNo: String = "",
    ): Unlock

    suspend fun msgSync(msgId: String): MsgSyncBean
    fun isDeviceReady(bean: MsgSyncBean): Boolean
    suspend fun waterSync(skuId: String): WaterSync
    suspend fun endWater(goodsId: String)
    suspend fun afterPayCreate(identify: String): AfterPay
    suspend fun orderPayStatus(orderNo: String, payType: String): Boolean
}
