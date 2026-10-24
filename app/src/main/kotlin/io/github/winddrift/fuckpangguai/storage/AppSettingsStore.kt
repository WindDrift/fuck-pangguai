package io.github.winddrift.fuckpangguai.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.winddrift.fuckpangguai.BuildConfig
import io.github.winddrift.fuckpangguai.storage.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class AppThemeMode { System, Light, Dark }

data class ThemeSettings(
    val mode: AppThemeMode = AppThemeMode.System,
    val dynamicColor: Boolean = false,
    val floatingNavBar: Boolean = false,
)

class AppSettingsStore(private val context: Context) : SettingsSource {

    override suspend fun isOneTapEnabled(): Boolean =
        context.appDataStore.data.first()[ONE_TAP] ?: false

    suspend fun setOneTapEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[ONE_TAP] = enabled }
    }

    override suspend fun isDryRun(): Boolean =
        context.appDataStore.data.first()[DRY_RUN] ?: BuildConfig.DEBUG

    suspend fun setDryRun(enabled: Boolean) {
        context.appDataStore.edit { it[DRY_RUN] = enabled }
    }

    val themeSettings: Flow<ThemeSettings> = context.appDataStore.data.map { preferences ->
        ThemeSettings(
            mode = preferences[THEME_MODE]
                ?.let { stored -> AppThemeMode.entries.firstOrNull { it.name == stored } }
                ?: AppThemeMode.System,
            dynamicColor = preferences[DYNAMIC_COLOR] ?: false,
            floatingNavBar = preferences[FLOATING_NAV_BAR] ?: false,
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.appDataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.appDataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setFloatingNavBar(enabled: Boolean) {
        context.appDataStore.edit { it[FLOATING_NAV_BAR] = enabled }
    }

    private companion object {
        val ONE_TAP = booleanPreferencesKey("one_tap_unlock")
        val DRY_RUN = booleanPreferencesKey("dry_run")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val FLOATING_NAV_BAR = booleanPreferencesKey("floating_nav_bar")
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

class DeviceConfigStore(private val context: Context) : DeviceConfigSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun find(goodsId: String): ConfirmedDevice? =
        load().firstOrNull { it.goodsId == goodsId }

    override suspend fun save(device: ConfirmedDevice) {
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
