package io.github.winddrift.fuckpangguai.device

import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.Sku

data class DevicePlans(
    val skus: List<Sku>,
    val channels: List<PayChannelRoute>,
    val fetchedAt: Long,
    val fromCache: Boolean,
)

interface DeviceDataSource {
    suspend fun loadSkus(goodsId: String): List<Sku>
    suspend fun loadChannels(detail: GoodsDetail): List<PayChannelRoute>
    suspend fun bindChannel(method: Int)

    suspend fun readCachedPlans(device: ResolvedDevice): DevicePlans? = null

    suspend fun loadPlans(device: ResolvedDevice): DevicePlans =
        DevicePlans(
            skus = loadSkus(device.scan.id),
            channels = loadChannels(device.detail),
            fetchedAt = 0L,
            fromCache = false,
        )

    suspend fun refreshPlans(device: ResolvedDevice) {
        loadPlans(device)
    }

    suspend fun clearCachedPlans(device: ResolvedDevice) = Unit
}
