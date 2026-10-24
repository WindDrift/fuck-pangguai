package io.github.winddrift.fuckpangguai.account

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.winddrift.fuckpangguai.net.dto.Ticket
import io.github.winddrift.fuckpangguai.storage.appDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AssetSnapshot(
    val fetchedAt: Long = 0L,
    val balance: String = "",
    val tokenCoin: Int = 0,
    val integral: String = "",
    val tickets: List<Ticket> = emptyList(),
    val principalAmount: Int = 0,
    val presentAmount: Int = 0,
    val totalAmount: Int = 0,
    val ticketsComplete: Boolean = true,
)

class AssetSnapshotStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(localId: String): AssetSnapshot? = withContext(Dispatchers.IO) {
        val raw = context.appDataStore.data.first()[key(localId)] ?: return@withContext null
        runCatching { json.decodeFromString<AssetSnapshot>(raw) }.getOrNull()
    }

    suspend fun save(localId: String, snapshot: AssetSnapshot) = withContext(Dispatchers.IO) {
        val encoded = json.encodeToString(AssetSnapshot.serializer(), snapshot)
        context.appDataStore.edit { it[key(localId)] = encoded }
    }

    suspend fun delete(localId: String) = withContext(Dispatchers.IO) {
        context.appDataStore.edit { it.remove(key(localId)) }
    }

    private fun key(localId: String) = stringPreferencesKey("asset_snapshot_v1_$localId")
}
