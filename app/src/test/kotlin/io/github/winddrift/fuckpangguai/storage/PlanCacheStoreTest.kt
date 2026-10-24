package io.github.winddrift.fuckpangguai.storage

import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.Sku
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlanCacheStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `find returns entry within seven days`() = runTest {
        var now = 1_000L
        val store = PlanCacheStore(FakeStorage(), { now })
        store.save(entry(fetchedAt = now))

        now += 7 * DAY_MS - 1
        assertNotNull(store.find("goods-1", "account-1"))
    }

    @Test
    fun `find misses entry after seven days`() = runTest {
        var now = 1_000L
        val store = PlanCacheStore(FakeStorage(), { now })
        store.save(entry(fetchedAt = now))

        now += 7 * DAY_MS + 1
        assertNull(store.find("goods-1", "account-1"))
    }

    @Test
    fun `save keeps at most fifty entries`() = runTest {
        var now = 1_000L
        val storage = FakeStorage()
        val store = PlanCacheStore(storage, { now })
        repeat(51) { index ->
            now += 1
            store.save(entry(goodsId = "goods-$index", fetchedAt = now))
        }

        assertNull(store.find("goods-0", "account-1"))
        assertNotNull(store.find("goods-50", "account-1"))

        val decoded = json.decodeFromString(
            ListSerializer(CachedPlans.serializer()),
            storage.value!!,
        )
        assertEquals(50, decoded.size)
    }

    @Test
    fun `accounts are isolated`() = runTest {
        var now = 1_000L
        val store = PlanCacheStore(FakeStorage(), { now })
        store.save(entry(accountId = "account-1", fetchedAt = now, skuName = "A"))
        store.save(
            entry(
                goodsId = "goods-1",
                accountId = "account-2",
                fetchedAt = now,
                skuName = "B",
            ),
        )

        assertEquals("A", store.find("goods-1", "account-1")?.skus?.first()?.name)
        assertEquals("B", store.find("goods-1", "account-2")?.skus?.first()?.name)
    }

    @Test
    fun `corrupt json is treated as miss and recovers on save`() = runTest {
        var now = 1_000L
        val store = PlanCacheStore(FakeStorage("not json"), { now })

        assertNull(store.find("goods-1", "account-1"))

        store.save(entry(fetchedAt = now))
        assertNotNull(store.find("goods-1", "account-1"))
    }

    @Test
    fun `older entries do not overwrite newer ones`() = runTest {
        var now = 2_000L
        val store = PlanCacheStore(FakeStorage(), { now })
        store.save(entry(fetchedAt = now, skuName = "new"))
        store.save(entry(fetchedAt = now - 1, skuName = "old"))

        assertEquals("new", store.find("goods-1", "account-1")?.skus?.first()?.name)
    }

    @Test
    fun `clear removes only the target entry`() = runTest {
        var now = 1_000L
        val store = PlanCacheStore(FakeStorage(), { now })
        store.save(entry(accountId = "account-1", fetchedAt = now))
        store.save(entry(goodsId = "goods-2", accountId = "account-1", fetchedAt = now))
        store.save(entry(goodsId = "goods-1", accountId = "account-2", fetchedAt = now))

        store.clear("goods-1", "account-1")

        assertNull(store.find("goods-1", "account-1"))
        assertNotNull(store.find("goods-2", "account-1"))
        assertNotNull(store.find("goods-1", "account-2"))
    }

    @Test
    fun `clear account removes all of its entries`() = runTest {
        var now = 1_000L
        val store = PlanCacheStore(FakeStorage(), { now })
        store.save(entry(accountId = "account-1", fetchedAt = now))
        store.save(entry(goodsId = "goods-2", accountId = "account-1", fetchedAt = now))
        store.save(entry(accountId = "account-2", fetchedAt = now))

        store.clearAccount("account-1")

        assertNull(store.find("goods-1", "account-1"))
        assertNull(store.find("goods-2", "account-1"))
        assertNotNull(store.find("goods-1", "account-2"))
    }

    @Test
    fun `serializes sku and channel fields`() = runTest {
        var now = 1_000L
        val store = PlanCacheStore(FakeStorage(), { now })
        store.save(entry(fetchedAt = now))

        val cached = store.find("goods-1", "account-1")

        assertNotNull(cached)
        assertEquals("sku-1", cached!!.skus.single().skuId)
        assertEquals("1 元", cached.skus.single().name)
        assertEquals(15, cached.channels.single().method)
        assertEquals("支付宝免密", cached.channels.single().methodName)
        assertEquals("imei-1|org-1|04", cached.channelKey)
    }

    @Test
    fun `does not save incomplete entries`() = runTest {
        var now = 1_000L
        val store = PlanCacheStore(FakeStorage(), { now })
        store.save(entry(fetchedAt = now, skus = emptyList()))

        assertNull(store.find("goods-1", "account-1"))
    }

    private fun entry(
        goodsId: String = "goods-1",
        accountId: String = "account-1",
        fetchedAt: Long,
        skuName: String = "1 元",
        skus: List<Sku> = listOf(
            Sku(skuId = "sku-1", name = skuName, price = "0.2", unit = "L"),
        ),
    ) = CachedPlans(
        goodsId = goodsId,
        accountId = accountId,
        channelKey = "imei-1|org-1|04",
        skus = skus,
        channels = listOf(
            PayChannelRoute(method = 15, methodName = "支付宝免密", checked = true),
        ),
        fetchedAt = fetchedAt,
    )

    private class FakeStorage(var value: String? = null) : PlanCacheStorage {
        override suspend fun read(): String? = value

        override suspend fun write(value: String) {
            this.value = value
        }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
