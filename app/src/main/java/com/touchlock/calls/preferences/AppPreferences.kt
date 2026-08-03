package com.touchlock.calls.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "touch_lock_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_ENABLED = booleanPreferencesKey("touch_lock_enabled")
        val KEY_DELAY_SECONDS = intPreferencesKey("lock_delay_seconds")
    }

    val isEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_ENABLED] ?: true
        }

    val lockDelaySecondsFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_DELAY_SECONDS] ?: 2
        }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ENABLED] = enabled
        }
    }

    suspend fun setLockDelaySeconds(delaySeconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DELAY_SECONDS] = delaySeconds
        }
    }
}
