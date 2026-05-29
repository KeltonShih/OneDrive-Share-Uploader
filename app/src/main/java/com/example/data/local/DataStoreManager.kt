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
import com.example.data.model.UploadDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    companion object {
        val DEFAULT_FOLDER_KEY = stringPreferencesKey("default_folder")
        val CONFLICT_BEHAVIOR_KEY = stringPreferencesKey("conflict_behavior")
        val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only")
        val RULES_ENABLED_KEY = booleanPreferencesKey("rules_enabled")
        val LANGUAGE_CODE_KEY = stringPreferencesKey("language_code")
        val DESTINATIONS_JSON_KEY = stringPreferencesKey("destinations_json")
    }

    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val defaultFolder = preferences[DEFAULT_FOLDER_KEY] ?: "/Upload"
        val destinations = parseDestinations(
            preferences[DESTINATIONS_JSON_KEY],
            defaultFolder
        )
        AppSettings(
            defaultFolder = defaultFolder,
            conflictBehavior = try {
                ConflictBehavior.valueOf(preferences[CONFLICT_BEHAVIOR_KEY] ?: ConflictBehavior.RENAME.name)
            } catch (e: Exception) {
                ConflictBehavior.RENAME
            },
            wifiOnly = preferences[WIFI_ONLY_KEY] ?: false,
            rulesEnabled = preferences[RULES_ENABLED_KEY] ?: false,
            languageCode = preferences[LANGUAGE_CODE_KEY] ?: AppSettings().languageCode,
            uploadDestinations = destinations
        )
    }

    suspend fun updateDefaultFolder(folder: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_FOLDER_KEY] = folder
            val destinations = parseDestinations(preferences[DESTINATIONS_JSON_KEY], folder)
            val updated = destinations
                .sortedBy { it.sortOrder }
                .mapIndexed { index, destination ->
                    if (index == 0) destination.copy(folderPath = folder) else destination
                }
            preferences[DESTINATIONS_JSON_KEY] = encodeDestinations(updated)
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

    suspend fun updateUploadDestinations(destinations: List<UploadDestination>) {
        context.dataStore.edit { preferences ->
            val normalized = normalizeDestinations(
                destinations,
                preferences[DEFAULT_FOLDER_KEY] ?: "/Upload"
            )
            preferences[DESTINATIONS_JSON_KEY] = encodeDestinations(normalized)
            preferences[DEFAULT_FOLDER_KEY] = normalized
                .sortedBy { it.sortOrder }
                .firstOrNull()
                ?.folderPath
                ?: "/Upload"
        }
    }

    private fun parseDestinations(json: String?, defaultFolder: String): List<UploadDestination> {
        if (json.isNullOrBlank()) {
            return listOf(UploadDestination.default(defaultFolder))
        }

        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val id = item.optString("id").takeIf { it.isNotBlank() }
                        ?: "destination-$index"
                    val name = item.optString("displayName").takeIf { it.isNotBlank() }
                        ?: UploadDestination.DEFAULT_NAME
                    val folder = item.optString("folderPath").takeIf { it.isNotBlank() }
                        ?: defaultFolder
                    val accountId = item.optString("driveAccountId").takeIf { it.isNotBlank() }
                    add(
                        UploadDestination(
                            id = id,
                            displayName = name,
                            folderPath = normalizeFolder(folder),
                            driveAccountId = accountId,
                            driveAccountLabel = item.optString("driveAccountLabel").takeIf { it.isNotBlank() }
                                ?: if (accountId == null) {
                                    UploadDestination.ACCOUNT_NOT_SELECTED_LABEL
                                } else {
                                    UploadDestination.CURRENT_ACCOUNT_LABEL
                                },
                            isEnabled = item.optBoolean("isEnabled", true) && accountId != null,
                            sortOrder = item.optInt("sortOrder", index)
                        )
                    )
                }
            }
        }.getOrElse {
            listOf(UploadDestination.default(defaultFolder))
        }.let { normalizeDestinations(it, defaultFolder) }
    }

    private fun encodeDestinations(destinations: List<UploadDestination>): String {
        val array = JSONArray()
        normalizeDestinations(destinations, "/Upload").forEach { destination ->
            array.put(JSONObject().apply {
                put("id", destination.id)
                put("displayName", destination.displayName)
                put("folderPath", destination.folderPath)
                put("driveAccountId", destination.driveAccountId)
                put("driveAccountLabel", destination.driveAccountLabel)
                put("isEnabled", destination.isEnabled)
                put("sortOrder", destination.sortOrder)
            })
        }
        return array.toString()
    }

    private fun normalizeDestinations(
        destinations: List<UploadDestination>,
        defaultFolder: String
    ): List<UploadDestination> {
        val fallback = UploadDestination.default(defaultFolder)
        val source = destinations.ifEmpty { listOf(fallback) }
        return source
            .mapIndexed { index, destination ->
                val folderPath = normalizeFolder(destination.folderPath.ifBlank { defaultFolder })
                val name = resolvedDestinationName(destination.displayName, folderPath)
                destination.copy(
                    displayName = name,
                    folderPath = folderPath,
                    driveAccountLabel = destination.driveAccountLabel
                        ?: if (destination.driveAccountId == null) {
                            UploadDestination.ACCOUNT_NOT_SELECTED_LABEL
                        } else {
                            UploadDestination.CURRENT_ACCOUNT_LABEL
                        },
                    isEnabled = destination.isEnabled && destination.driveAccountId != null,
                    sortOrder = index
                )
            }
    }

    private fun normalizeFolder(path: String): String {
        val trimmed = path.trim().trim('/')
        return if (trimmed.isBlank()) "/" else "/$trimmed"
    }

    private fun resolvedDestinationName(displayName: String, folderPath: String): String {
        val trimmedName = displayName.trim()
        if (trimmedName.isNotBlank() && trimmedName != UploadDestination.DEFAULT_NAME) {
            return trimmedName
        }
        return folderPath.trim()
            .trim('/')
            .substringAfterLast('/')
            .ifBlank { UploadDestination.DEFAULT_NAME }
    }
}
