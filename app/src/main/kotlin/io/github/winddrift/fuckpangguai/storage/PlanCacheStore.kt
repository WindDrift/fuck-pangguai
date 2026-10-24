package io.github.winddrift.fuckpangguai.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute
import io.github.winddrift.fuckpangguai.net.dto.Sku
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class CachedPlans(
    val goodsId: String = "",
    val accountId: String = "",
    val channelKey: String = "",
    val skus: List<Sku> = emptyList(),
    val channels: List<PayChannelRoute> = emptyList(),
    val fetchedAt: Long = 0L,
)

interface PlanCacheSource {
    suspend fun find(goodsId: String, accountId: String): CachedPlans?
    suspend fun save(entry: CachedPlans)
    suspend fun clear(goodsId: String, accountId: String)
    suspend fun clearAccount(accountId: String)
}

internal interface PlanCacheStorage {
    suspend fun read(): String?
    suspend fun write(value: String)
}

private val Context.planCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "plan_cache",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private class DataStorePlanCacheStorage(
    private val dataStore: DataStore<Preferences>,
) : PlanCacheStorage {
    override suspend fun read(): String? = dataStore.data.first()[PLANS]

    override suspend fun write(value: String) {
        dataStore.edit { it[PLANS] = value }
    }

    private companion object {
        val PLANS = stringPreferencesKey("plans")
    }
}

class PlanCacheStore internal constructor(
    private val storage: PlanCacheStorage,
    private val clock: () -> Long,
) : PlanCacheSource {

    constructor(context: Context) : this(
        DataStorePlanCacheStorage(context.planCacheDataStore),
        System::currentTimeMillis,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }
    private val mutex = Mutex()

    override suspend fun find(goodsId: String, accountId: String): CachedPlans? =
        mutex.withLock {
            val now = clock()
            readLocked().firstOrNull {
                it.goodsId == goodsId && it.accountId == accountId && it.isUsable(now)
            }
        }

    override suspend fun save(entry: CachedPlans): Unit = mutex.withLock {
        if (!entry.isComplete()) return@withLock
        val now = clock()
        val entries = readLocked().filter { it.isUsable(now) }
        val existing = entries.firstOrNull {
            it.goodsId == entry.goodsId && it.accountId == entry.accountId
        }
        if (existing != null && existing.fetchedAt >= entry.fetchedAt) return@withLock

        val next = (entries.filterNot {
            it.goodsId == entry.goodsId && it.accountId == entry.accountId
        } + entry)
            .sortedByDescending { it.fetchedAt }
            .take(MAX_ENTRIES)
        writeLocked(next)
    }

    override suspend fun clear(goodsId: String, accountId: String): Unit = mutex.withLock {
        writeLocked(
            readLocked().filterNot {
                it.goodsId == goodsId && it.accountId == accountId
            },
        )
    }

    override suspend fun clearAccount(accountId: String): Unit = mutex.withLock {
        writeLocked(readLocked().filterNot { it.accountId == accountId })
    }

    private suspend fun readLocked(): List<CachedPlans> {
        val raw = runCatching { storage.read() }.getOrNull()
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(CachedPlans.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private suspend fun writeLocked(entries: List<CachedPlans>) {
        val encoded = runCatching {
            json.encodeToString(ListSerializer(CachedPlans.serializer()), entries)
        }.getOrNull() ?: return
        runCatching { storage.write(encoded) }
    }

    private fun CachedPlans.isComplete(): Boolean =
        goodsId.isNotBlank() &&
            accountId.isNotBlank() &&
            channelKey.isNotBlank() &&
            skus.isNotEmpty() &&
            channels.isNotEmpty()

    private fun CachedPlans.isUsable(now: Long): Boolean {
        val age = now - fetchedAt
        return isComplete() && age in 0..TTL_MS
    }

    private companion object {
        const val TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_ENTRIES = 50
    }
}
