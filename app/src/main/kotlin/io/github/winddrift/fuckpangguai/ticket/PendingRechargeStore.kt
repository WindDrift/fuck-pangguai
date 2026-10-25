package io.github.winddrift.fuckpangguai.ticket

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.winddrift.fuckpangguai.storage.appDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PendingRecharge(
    val accountLocalId: String,
    val shopId: String,
    val shopName: String,
    val orderId: String,
    val orderNo: String,
    val price: String,
    val points: Int,
    val createdAt: Long,
)

interface PendingRechargeStore {
    suspend fun load(accountId: String): PendingRecharge?
    suspend fun save(pending: PendingRecharge)
    suspend fun clear(accountId: String)
}

class DataStorePendingRechargeStore(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : PendingRechargeStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun load(accountId: String): PendingRecharge? {
        if (accountId.isBlank()) return null
        val raw = context.appDataStore.data.first()[key(accountId)] ?: return null
        val pending = runCatching {
            json.decodeFromString(PendingRecharge.serializer(), raw)
        }.getOrNull()
        if (pending == null || pending.accountLocalId != accountId) {
            clear(accountId)
            return null
        }
        if (now() - pending.createdAt > MAX_AGE_MS) {
            clear(accountId)
            return null
        }
        return pending
    }

    override suspend fun save(pending: PendingRecharge) {
        if (pending.accountLocalId.isBlank()) return
        val encoded = json.encodeToString(PendingRecharge.serializer(), pending)
        context.appDataStore.edit { it[key(pending.accountLocalId)] = encoded }
    }

    override suspend fun clear(accountId: String) {
        if (accountId.isBlank()) return
        context.appDataStore.edit { it.remove(key(accountId)) }
    }

    private fun key(accountId: String) = stringPreferencesKey("pending_recharge_v1_$accountId")

    private companion object {
        const val MAX_AGE_MS = 2 * 60 * 60 * 1000L
    }
}
