package io.github.winddrift.fuckpangguai.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.winddrift.fuckpangguai.BuildConfig
import io.github.winddrift.fuckpangguai.points.PointsSettingsSource
import io.github.winddrift.fuckpangguai.storage.appDataStore
import io.github.winddrift.fuckpangguai.water.WaterConversion
import io.github.winddrift.fuckpangguai.water.WaterConversionValidation
import io.github.winddrift.fuckpangguai.water.validateWaterConversion
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

class AppSettingsStore(private val context: Context) : SettingsSource, PointsSettingsSource {

    override suspend fun isOneTapEnabled(): Boolean =
        context.appDataStore.data.first()[ONE_TAP] ?: false

    suspend fun setOneTapEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[ONE_TAP] = enabled }
    }

    override suspend fun isDryRun(): Boolean =
        context.appDataStore.data.first()[DRY_RUN] ?: BuildConfig.DEFAULT_DRY_RUN

    suspend fun setDryRun(enabled: Boolean) {
        context.appDataStore.edit { it[DRY_RUN] = enabled }
    }

    val skipDeviceInfo: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[SKIP_DEVICE_INFO] ?: false
    }

    suspend fun setSkipDeviceInfo(enabled: Boolean) {
        context.appDataStore.edit { it[SKIP_DEVICE_INFO] = enabled }
    }

    val autoStartCamera: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[AUTO_START_CAMERA] ?: true
    }

    suspend fun setAutoStartCamera(enabled: Boolean) {
        context.appDataStore.edit { it[AUTO_START_CAMERA] = enabled }
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

    val assetsHidden: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[ASSETS_HIDDEN] ?: false
    }

    suspend fun setAssetsHidden(enabled: Boolean) {
        context.appDataStore.edit { it[ASSETS_HIDDEN] = enabled }
    }

    override val autoEnabled: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[POINTS_AUTO_ENABLED] ?: false
    }

    override val runOnLaunch: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[POINTS_RUN_ON_LAUNCH] ?: true
    }

    override val experimentEnabled: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[POINTS_EXPERIMENT_ENABLED] ?: false
    }

    override val riskAcknowledged: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[POINTS_RISK_ACKNOWLEDGED] ?: false
    }

    override suspend fun setAutoEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[POINTS_AUTO_ENABLED] = enabled }
    }

    override suspend fun setRunOnLaunch(enabled: Boolean) {
        context.appDataStore.edit { it[POINTS_RUN_ON_LAUNCH] = enabled }
    }

    override suspend fun setExperimentEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[POINTS_EXPERIMENT_ENABLED] = enabled }
    }

    override suspend fun acknowledgeRisk() {
        context.appDataStore.edit { it[POINTS_RISK_ACKNOWLEDGED] = true }
    }

    val waterConversion: Flow<WaterConversion?> = context.appDataStore.data.map { preferences ->
        preferences.toWaterConversion()
    }

    suspend fun getWaterConversion(): WaterConversion? =
        context.appDataStore.data.first().toWaterConversion()

    suspend fun setWaterConversion(conversion: WaterConversion?) {
        context.appDataStore.edit { preferences ->
            if (conversion == null) {
                preferences.remove(WATER_USAGE_BASE)
                preferences.remove(WATER_VOLUME_ML)
                preferences.remove(WATER_CUP_ML)
            } else {
                preferences[WATER_USAGE_BASE] = conversion.usageBase.toPlainString()
                preferences[WATER_VOLUME_ML] = conversion.waterMl.toPlainString()
                val cupMl = conversion.cupMl
                if (cupMl == null) {
                    preferences.remove(WATER_CUP_ML)
                } else {
                    preferences[WATER_CUP_ML] = cupMl.toPlainString()
                }
            }
        }
    }

    private fun androidx.datastore.preferences.core.Preferences.toWaterConversion(): WaterConversion? {
        val validation = validateWaterConversion(
            usageBaseText = this[WATER_USAGE_BASE].orEmpty(),
            waterMlText = this[WATER_VOLUME_ML].orEmpty(),
            cupMlText = this[WATER_CUP_ML].orEmpty(),
        )
        return (validation as? WaterConversionValidation.Valid)?.conversion
    }

    private companion object {
        val ONE_TAP = booleanPreferencesKey("one_tap_unlock")
        val SKIP_DEVICE_INFO = booleanPreferencesKey("skip_device_info")
        val AUTO_START_CAMERA = booleanPreferencesKey("auto_start_camera")
        val DRY_RUN = booleanPreferencesKey("dry_run")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val FLOATING_NAV_BAR = booleanPreferencesKey("floating_nav_bar")
        val ASSETS_HIDDEN = booleanPreferencesKey("assets_hidden")
        val POINTS_AUTO_ENABLED = booleanPreferencesKey("points_auto_enabled")
        val POINTS_RUN_ON_LAUNCH = booleanPreferencesKey("points_run_on_launch")
        val POINTS_EXPERIMENT_ENABLED = booleanPreferencesKey("points_direct_task_experiment")
        val POINTS_RISK_ACKNOWLEDGED = booleanPreferencesKey("points_risk_acknowledged")
        val WATER_USAGE_BASE = stringPreferencesKey("water_usage_base")
        val WATER_VOLUME_ML = stringPreferencesKey("water_volume_ml")
        val WATER_CUP_ML = stringPreferencesKey("water_cup_ml")
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
