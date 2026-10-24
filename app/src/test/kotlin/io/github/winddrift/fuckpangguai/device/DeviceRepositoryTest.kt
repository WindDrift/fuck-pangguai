package io.github.winddrift.fuckpangguai.device

import io.github.winddrift.fuckpangguai.net.dto.ApiResponse
import io.github.winddrift.fuckpangguai.net.dto.GoodsDetail
import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.ScanV2Bean
import io.github.winddrift.fuckpangguai.net.dto.Sku
import io.github.winddrift.fuckpangguai.storage.CachedPlans
import io.github.winddrift.fuckpangguai.storage.PlanCacheSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRepositoryTest {

    @Test
    fun `loadPlans starts both requests before waiting`() = runTest {
        val api = FakeDeviceApi()
        api.skusGate = CompletableDeferred()
        api.channelsGate = CompletableDeferred()
        val repository = repository(api)

        val deferred = async { repository.loadPlans(device()) }
        runCurrent()

        assertEquals(1, api.skusCalls)
        assertEquals(1, api.channelsCalls)

        api.skusGate!!.complete(Unit)
        api.channelsGate!!.complete(Unit)
        val plans = deferred.await()

        assertEquals(1, plans.skus.size)
        assertEquals(1, plans.channels.size)
    }

    @Test
    fun `loadPlans stores plans for the current account`() = runTest {
        val cache = FakePlanCache()
        val repository = repository(FakeDeviceApi(), cache, accountId = "account-1")

        repository.loadPlans(device())

        val entry = cache.entries.single()
        assertEquals("goods-1", entry.goodsId)
        assertEquals("account-1", entry.accountId)
        assertEquals("imei-1|org-1|04", entry.channelKey)
        assertEquals("sku-1", entry.skus.single().skuId)
    }

    @Test
    fun `readCachedPlans returns cached plans`() = runTest {
        val cache = FakePlanCache()
        cache.save(cachedEntry())
        val repository = repository(FakeDeviceApi(), cache, accountId = "account-1")

        val plans = repository.readCachedPlans(device())

        assertNotNull(plans)
        assertTrue(plans!!.fromCache)
        assertEquals("cached", plans.skus.single().name)
    }

    @Test
    fun `readCachedPlans rejects a different channel key`() = runTest {
        val cache = FakePlanCache()
        cache.save(cachedEntry(channelKey = "other|org|04"))
        val repository = repository(FakeDeviceApi(), cache, accountId = "account-1")

        assertNull(repository.readCachedPlans(device()))
    }

    @Test
    fun `prefetch failure is silent and does not write cache`() = runTest {
        val api = FakeDeviceApi()
        api.skusError = IllegalStateException("boom")
        val cache = FakePlanCache()
        val repository = repository(api, cache, accountId = "account-1")

        repository.prefetchPlans(device())

        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `null account skips cache`() = runTest {
        val cache = FakePlanCache()
        val repository = repository(FakeDeviceApi(), cache, accountId = null)

        repository.loadPlans(device())

        assertTrue(cache.entries.isEmpty())
        assertNull(repository.readCachedPlans(device()))
    }

    @Test
    fun `clearCachedPlans removes the device entry`() = runTest {
        val cache = FakePlanCache()
        cache.save(cachedEntry())
        val repository = repository(FakeDeviceApi(), cache, accountId = "account-1")

        repository.clearCachedPlans(device())

        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `clear during load prevents stale cache write`() = runTest {
        var now = 1_000L
        val api = FakeDeviceApi()
        api.skusGate = CompletableDeferred()
        api.channelsGate = CompletableDeferred()
        val cache = FakePlanCache()
        val repository = DeviceRepository(api, cache, { "account-1" }, { now })

        val deferred = async { repository.loadPlans(device()) }
        runCurrent()

        now = 2_000L
        repository.clearCachedPlans(device())

        api.skusGate!!.complete(Unit)
        api.channelsGate!!.complete(Unit)
        deferred.await()

        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `refresh failure leaves existing cache intact`() = runTest {
        val api = FakeDeviceApi()
        api.skusError = IllegalStateException("boom")
        val cache = FakePlanCache()
        cache.save(cachedEntry())
        val repository = repository(api, cache, accountId = "account-1")

        var failed = false
        try {
            repository.refreshPlans(device())
        } catch (_: Throwable) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(1, cache.entries.size)
    }

    private fun repository(
        api: FakeDeviceApi,
        cache: PlanCacheSource? = null,
        accountId: String? = "account-1",
        clock: () -> Long = { 1_000L },
    ) = DeviceRepository(api, cache, { accountId }, clock)

    private fun device(goodsId: String = "goods-1") = ResolvedDevice(
        scan = ScanV2Bean(categoryCode = "04", categoryName = "饮水机", id = goodsId),
        detail = GoodsDetail(
            goodsId = goodsId,
            imei = "imei-1",
            categoryCode = "04",
            orgId = "org-1",
            shopId = "shop-1",
            name = "水机",
        ),
        sourceKey = "SN",
        sourceValue = "imei-1",
    )

    private fun cachedEntry(channelKey: String = "imei-1|org-1|04") = CachedPlans(
        goodsId = "goods-1",
        accountId = "account-1",
        channelKey = channelKey,
        skus = listOf(Sku(skuId = "sku-1", name = "cached", price = "0.2", unit = "L")),
        channels = listOf(PayChannelRoute(method = 15, methodName = "支付宝免密")),
        fetchedAt = 1_000L,
    )

    private class FakeDeviceApi : DeviceApi {
        var skusCalls = 0
        var channelsCalls = 0
        var skusGate: CompletableDeferred<Unit>? = null
        var channelsGate: CompletableDeferred<Unit>? = null
        var skusError: Throwable? = null
        var skusResult: List<Sku> = listOf(
            Sku(skuId = "sku-1", name = "1 元", price = "0.2", unit = "L"),
        )
        var channelsResult: List<PayChannelRoute> = listOf(
            PayChannelRoute(method = 15, methodName = "支付宝免密", checked = true),
        )

        override suspend fun scanV2(body: Map<String, String>): ApiResponse<ScanV2Bean> =
            error("not used")

        override suspend fun goodsDetails(body: Map<String, String>): ApiResponse<GoodsDetail> =
            error("not used")

        override suspend fun skus(body: Map<String, String>): ApiResponse<List<Sku>> {
            skusCalls += 1
            skusGate?.await()
            skusError?.let { throw it }
            return ApiResponse(code = 0, msg = "ok", data = skusResult)
        }

        override suspend fun afterPayChannels(
            body: Map<String, String>,
        ): ApiResponse<List<PayChannelRoute>> {
            channelsCalls += 1
            channelsGate?.await()
            return ApiResponse(code = 0, msg = "ok", data = channelsResult)
        }

        override suspend fun addAfterPayChannel(body: Map<String, String>): ApiResponse<String> =
            ApiResponse(code = 0, msg = "ok", data = "ok")
    }

    private class FakePlanCache : PlanCacheSource {
        val entries = mutableListOf<CachedPlans>()

        override suspend fun find(goodsId: String, accountId: String): CachedPlans? =
            entries.lastOrNull { it.goodsId == goodsId && it.accountId == accountId }

        override suspend fun save(entry: CachedPlans) {
            entries.removeAll {
                it.goodsId == entry.goodsId && it.accountId == entry.accountId
            }
            entries.add(entry)
        }

        override suspend fun clear(goodsId: String, accountId: String) {
            entries.removeAll { it.goodsId == goodsId && it.accountId == accountId }
        }

        override suspend fun clearAccount(accountId: String) {
            entries.removeAll { it.accountId == accountId }
        }
    }
}
