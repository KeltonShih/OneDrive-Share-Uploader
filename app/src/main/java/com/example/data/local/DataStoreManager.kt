package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.ConflictBehavior
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    companion object {
        val DEFAULT_FOLDER_KEY = stringPreferencesKey("default_folder")
        val CONFLICT_BEHAVIOR_KEY = stringPreferencesKey("conflict_behavior")
        val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only")
        val RULES_ENABLED_KEY = booleanPreferencesKey("rules_enabled")
        val LANGUAGE_CODE_KEY = stringPreferencesKey("language_code")
    }

    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            defaultFolder = preferences[DEFAULT_FOLDER_KEY] ?: "/Upload",
            conflictBehavior = try {
                ConflictBehavior.valueOf(preferences[CONFLICT_BEHAVIOR_KEY] ?: ConflictBehavior.RENAME.name)
            } catch (e: Exception) {
                ConflictBehavior.RENAME
            },
            wifiOnly = preferences[WIFI_ONLY_KEY] ?: false,
            rulesEnabled = preferences[RULES_ENABLED_KEY] ?: false,
            languageCode = preferences[LANGUAGE_CODE_KEY] ?: AppSettings().languageCode
        )
    }

    suspend fun updateDefaultFolder(folder: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_FOLDER_KEY] = folder
        }
    }

    suspend fun updateConflictBehavior(behavior: ConflictBehavior) {
        context.dataStore.edit { preferences ->
            preferences[CONFLICT_BEHAVIOR_KEY] = behavior.name
        }
    }

    suspend fun updateWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WIFI_ONLY_KEY] = wifiOnly
        }
    }

    suspend fun updateRulesEnabled(rulesEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RULES_ENABLED_KEY] = rulesEnabled
        }
    }

    suspend fun updateLanguageCode(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_CODE_KEY] = languageCode
        }
    }
}
