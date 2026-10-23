package io.github.winddrift.fuckpangguai.device

import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.Sku

interface DeviceDataSource {
    suspend fun loadSkus(goodsId: String): List<Sku>
    suspend fun loadChannels(detail: GoodsDetail): List<PayChannelRoute>
    suspend fun bindChannel(method: Int)
}
