package io.github.winddrift.fuckpangguai.nfc

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.winddrift.fuckpangguai.storage.appDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class NfcTagBinding(
    val tagId: String,
    val sn: String,
    val deviceName: String,
    val goodsId: String,
    val updatedAt: Long,
)

interface NfcTagSource {
    suspend fun find(tagId: String): NfcTagBinding?
    suspend fun bind(binding: NfcTagBinding)
}

class NfcTagStore(private val context: Context) : NfcTagSource {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): List<NfcTagBinding> {
        val raw = context.appDataStore.data.first()[TAGS].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(NfcTagBinding.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    override suspend fun find(tagId: String): NfcTagBinding? =
        load().firstOrNull { it.tagId == tagId }

    override suspend fun bind(binding: NfcTagBinding) {
        val existing = load().filterNot { it.tagId == binding.tagId }
        val encoded = json.encodeToString(
            ListSerializer(NfcTagBinding.serializer()),
            existing + binding,
        )
        context.appDataStore.edit { it[TAGS] = encoded }
    }

    suspend fun remove(tagId: String) {
        val remaining = load().filterNot { it.tagId == tagId }
        val encoded = json.encodeToString(
            ListSerializer(NfcTagBinding.serializer()),
            remaining,
        )
        context.appDataStore.edit { it[TAGS] = encoded }
    }

    private companion object {
        val TAGS = stringPreferencesKey("nfc_tag_bindings")
    }
}
