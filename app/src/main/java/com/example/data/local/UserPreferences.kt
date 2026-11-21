package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.util.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sabqr_preferences")

data class AppPreferencesState(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val vibrateOnScan: Boolean = true,
    val autoSyncOffline: Boolean = true,
    val darkTheme: Boolean = false,
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val VIBRATE_ON_SCAN = booleanPreferencesKey("vibrate_on_scan")
        val AUTO_SYNC_OFFLINE = booleanPreferencesKey("auto_sync_offline")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val preferencesFlow: Flow<AppPreferencesState> = context.dataStore.data.map { prefs ->
        val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.ENGLISH.code
        val lang = if (langCode == AppLanguage.BENGALI.code) AppLanguage.BENGALI else AppLanguage.ENGLISH
        AppPreferencesState(
            language = lang,
            vibrateOnScan = prefs[Keys.VIBRATE_ON_SCAN] ?: true,
            autoSyncOffline = prefs[Keys.AUTO_SYNC_OFFLINE] ?: true,
            darkTheme = prefs[Keys.DARK_THEME] ?: false,
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setVibrateOnScan(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATE_ON_SCAN] = enabled }
    }

    suspend fun setAutoSyncOffline(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SYNC_OFFLINE] = enabled }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }
}
