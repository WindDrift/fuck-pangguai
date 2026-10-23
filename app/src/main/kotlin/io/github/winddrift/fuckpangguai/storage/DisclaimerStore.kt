package io.github.winddrift.fuckpangguai.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import io.github.winddrift.fuckpangguai.storage.appDataStore
import kotlinx.coroutines.flow.first

class DisclaimerStore(private val context: Context) {

    suspend fun isAccepted(): Boolean = context.appDataStore.data.first()[ACCEPTED] ?: false

    suspend fun accept() {
        context.appDataStore.edit { it[ACCEPTED] = true }
    }

    private companion object {
        val ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
    }
}
