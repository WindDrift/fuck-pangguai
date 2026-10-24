package io.github.winddrift.fuckpangguai.points

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.winddrift.fuckpangguai.storage.appDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

interface PointsRunStore {
    suspend fun load(accountId: String): PointsRunSnapshot

    suspend fun recordAttempt(accountId: String, date: String)

    suspend fun recordDone(accountId: String, date: String)

    suspend fun saveRecord(accountId: String, record: PointsRunRecord)
}

class DataStorePointsRunStore(
    private val context: Context,
) : PointsRunStore {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(accountId: String): PointsRunSnapshot {
        val preferences = context.appDataStore.data.first()
        val last = preferences[lastKey(accountId)]?.let { raw ->
            runCatching { json.decodeFromString<PointsRunRecord>(raw) }.getOrNull()
        }
        return PointsRunSnapshot(
            attemptDate = preferences[attemptKey(accountId)].orEmpty(),
            doneDate = preferences[doneKey(accountId)].orEmpty(),
            lastRun = last,
        )
    }

    override suspend fun recordAttempt(accountId: String, date: String) {
        context.appDataStore.edit { it[attemptKey(accountId)] = date }
    }

    override suspend fun recordDone(accountId: String, date: String) {
        context.appDataStore.edit { it[doneKey(accountId)] = date }
    }

    override suspend fun saveRecord(accountId: String, record: PointsRunRecord) {
        val trimmed = record.copy(logs = record.logs.takeLast(MAX_LOGS))
        context.appDataStore.edit {
            it[lastKey(accountId)] = json.encodeToString(PointsRunRecord.serializer(), trimmed)
        }
    }

    private fun attemptKey(accountId: String) = stringPreferencesKey("points_attempt_v1_$accountId")

    private fun doneKey(accountId: String) = stringPreferencesKey("points_done_v1_$accountId")

    private fun lastKey(accountId: String) = stringPreferencesKey("points_last_v1_$accountId")

    private companion object {
        const val MAX_LOGS = 50
    }
}
