package io.github.winddrift.pangguai.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.winddrift.pangguai.BuildConfig
import io.github.winddrift.pangguai.storage.appDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AppSettingsStore(private val context: Context) {

    suspend fun isOneTapEnabled(): Boolean =
        context.appDataStore.data.first()[ONE_TAP] ?: false

    suspend fun setOneTapEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[ONE_TAP] = enabled }
    }

    suspend fun isDryRun(): Boolean =
        context.appDataStore.data.first()[DRY_RUN] ?: BuildConfig.DEBUG

    suspend fun setDryRun(enabled: Boolean) {
        context.appDataStore.edit { it[DRY_RUN] = enabled }
    }

    private companion object {
        val ONE_TAP = booleanPreferencesKey("one_tap_unlock")
        val DRY_RUN = booleanPreferencesKey("dry_run")
    }
}

@Serializable
data class ConfirmedDevice(
    val goodsId: String,
    val skuId: String,
    val channelMethod: Int,
    val deviceName: String,
    val updatedAt: Long,
)

class DeviceConfigStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun find(goodsId: String): ConfirmedDevice? =
        load().firstOrNull { it.goodsId == goodsId }

    suspend fun save(device: ConfirmedDevice) {
        val updated = load().filterNot { it.goodsId == device.goodsId } + device
        val encoded = json.encodeToString(
            ListSerializer(ConfirmedDevice.serializer()),
            updated,
        )
        context.appDataStore.edit { it[CONFIRMED] = encoded }
    }

    private suspend fun load(): List<ConfirmedDevice> {
        val raw = context.appDataStore.data.first()[CONFIRMED].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ConfirmedDevice.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private companion object {
        val CONFIRMED = stringPreferencesKey("confirmed_devices")
    }
}
