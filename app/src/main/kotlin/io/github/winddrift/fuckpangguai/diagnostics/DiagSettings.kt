package io.github.winddrift.fuckpangguai.diagnostics

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.winddrift.fuckpangguai.storage.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class DiagSettings(
    val enabled: Boolean = true,
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    val maxMb: Int = DEFAULT_MAX_MB,
) {
    companion object {
        const val DEFAULT_RETENTION_DAYS = 7
        const val MIN_RETENTION_DAYS = 1
        const val MAX_RETENTION_DAYS = 30
        const val DEFAULT_MAX_MB = 10
        const val MIN_MAX_MB = 1
        const val MAX_MAX_MB = 100
    }
}

class DiagSettingsStore(private val context: Context) {

    val settings: Flow<DiagSettings> = context.appDataStore.data.map { preferences ->
        DiagSettings(
            enabled = preferences[ENABLED] ?: true,
            retentionDays = (preferences[RETENTION_DAYS] ?: DiagSettings.DEFAULT_RETENTION_DAYS)
                .coerceIn(DiagSettings.MIN_RETENTION_DAYS, DiagSettings.MAX_RETENTION_DAYS),
            maxMb = (preferences[MAX_MB] ?: DiagSettings.DEFAULT_MAX_MB)
                .coerceIn(DiagSettings.MIN_MAX_MB, DiagSettings.MAX_MAX_MB),
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setRetentionDays(days: Int) {
        context.appDataStore.edit {
            it[RETENTION_DAYS] = days.coerceIn(
                DiagSettings.MIN_RETENTION_DAYS,
                DiagSettings.MAX_RETENTION_DAYS,
            )
        }
    }

    suspend fun setMaxMb(maxMb: Int) {
        context.appDataStore.edit {
            it[MAX_MB] = maxMb.coerceIn(DiagSettings.MIN_MAX_MB, DiagSettings.MAX_MAX_MB)
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("diag_enabled")
        val RETENTION_DAYS = intPreferencesKey("diag_retention_days")
        val MAX_MB = intPreferencesKey("diag_max_mb")
    }
}
