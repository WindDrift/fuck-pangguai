package io.github.winddrift.fuckpangguai.device

import io.github.winddrift.fuckpangguai.core.AppConfig
import io.github.winddrift.fuckpangguai.net.ApiException
import io.github.winddrift.fuckpangguai.net.QiekjApi
import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.ScanV2Bean
import io.github.winddrift.fuckpangguai.net.dto.Sku
import io.github.winddrift.fuckpangguai.storage.CachedPlans
import io.github.winddrift.fuckpangguai.storage.PlanCacheSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

data class ResolvedDevice(
    val scan: ScanV2Bean,
    val detail: GoodsDetail,
    val sourceKey: String,
    val sourceValue: String,
)

class DeviceRepository internal constructor(
    private val api: DeviceApi,
    private val planCache: PlanCacheSource?,
    private val accountIdProvider: () -> String?,
    private val clock: () -> Long,
) : DeviceDataSource {

    constructor(
        api: QiekjApi,
        planCache: PlanCacheSource? = null,
        accountIdProvider: () -> String? = { null },
        clock: () -> Long = System::currentTimeMillis,
    ) : this(RetrofitDeviceApi(api), planCache, accountIdProvider, clock)

    private data class CacheKey(val goodsId: String, val accountId: String)

    private val clearedAt = ConcurrentHashMap<CacheKey, Long>()
    private val accountClearedAt = ConcurrentHashMap<String, Long>()

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

    override suspend fun loadSkus(goodsId: String): List<Sku> {
        val response = api.skus(mapOf("goodsId" to goodsId))
        if (!response.isSuccess) throw ApiException(response.code, response.msg)
        return response.data.orEmpty()
    }

    override suspend fun loadChannels(detail: GoodsDetail): List<PayChannelRoute> {
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

    override suspend fun bindChannel(method: Int) {
        val response = api.addAfterPayChannel(mapOf("method" to method.toString()))
        if (!response.isSuccess) throw ApiException(response.code, response.msg)
    }

    override suspend fun readCachedPlans(device: ResolvedDevice): DevicePlans? {
        val accountId = currentAccountId() ?: return null
        val cached = try {
            planCache?.find(device.scan.id, accountId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Throwable) {
            null
        } ?: return null
        if (cached.channelKey != channelKey(device.detail)) return null
        return DevicePlans(
            skus = cached.skus,
            channels = cached.channels,
            fetchedAt = cached.fetchedAt,
            fromCache = true,
        )
    }

    override suspend fun loadPlans(device: ResolvedDevice): DevicePlans = coroutineScope {
        val startedAt = clock()
        val accountId = currentAccountId()
        val key = accountId?.let { CacheKey(device.scan.id, it) }

        val skusDeferred = async { loadSkus(device.scan.id) }
        val channelsDeferred = async { loadChannels(device.detail) }
        val skus = skusDeferred.await()
        val channels = channelsDeferred.await()
        val plans = DevicePlans(
            skus = skus,
            channels = channels,
            fetchedAt = startedAt,
            fromCache = false,
        )

        if (accountId == null || key == null || skus.isEmpty() || channels.isEmpty()) {
            return@coroutineScope plans
        }
        if (wasClearedAfter(key, accountId, startedAt)) {
            return@coroutineScope plans
        }

        try {
            planCache?.save(
                CachedPlans(
                    goodsId = device.scan.id,
                    accountId = accountId,
                    channelKey = channelKey(device.detail),
                    skus = skus,
                    channels = channels,
                    fetchedAt = startedAt,
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Throwable) {
        }
        plans
    }

    override suspend fun refreshPlans(device: ResolvedDevice) {
        loadPlans(device)
    }

    suspend fun prefetchPlans(device: ResolvedDevice) {
        val detail = device.detail
        if (detail.categoryCode != AppConfig.WATER_CATEGORY) return
        if (detail.isBluetooth == 1) return
        if (!detail.deviceErrorCode.isNullOrBlank()) return
        try {
            loadPlans(device)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Throwable) {
        }
    }

    override suspend fun clearCachedPlans(device: ResolvedDevice) {
        val accountId = currentAccountId() ?: return
        clearedAt[CacheKey(device.scan.id, accountId)] = clock()
        planCache?.clear(device.scan.id, accountId)
    }

    suspend fun clearAccountCache(accountId: String) {
        if (accountId.isBlank()) return
        accountClearedAt[accountId] = clock()
        planCache?.clearAccount(accountId)
    }

    private fun currentAccountId(): String? =
        accountIdProvider()?.takeIf { it.isNotBlank() }

    private fun wasClearedAfter(key: CacheKey, accountId: String, startedAt: Long): Boolean =
        (clearedAt[key] ?: 0L) >= startedAt ||
            (accountClearedAt[accountId] ?: 0L) >= startedAt

    private fun channelKey(detail: GoodsDetail): String =
        "${detail.imei}|${detail.orgId}|${detail.categoryCode}"
}
