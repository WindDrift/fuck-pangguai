package io.github.winddrift.fuckpangguai.water

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.winddrift.fuckpangguai.storage.appDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class WaterSessionStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _session = MutableStateFlow<WaterSession?>(null)
    val session = _session.asStateFlow()

    suspend fun load(): WaterSession? {
        val raw = context.appDataStore.data.first()[SESSION].orEmpty()
        val value = if (raw.isBlank()) {
            null
        } else {
            runCatching { json.decodeFromString<WaterSession>(raw) }.getOrNull()
        }
        _session.value = value
        return value
    }

    suspend fun save(session: WaterSession) {
        _session.value = session
        context.appDataStore.edit {
            it[SESSION] = json.encodeToString(WaterSession.serializer(), session)
        }
    }

    suspend fun clear() {
        _session.value = null
        context.appDataStore.edit { it.remove(SESSION) }
    }

    private companion object {
        val SESSION = stringPreferencesKey("water_session")
    }
}
