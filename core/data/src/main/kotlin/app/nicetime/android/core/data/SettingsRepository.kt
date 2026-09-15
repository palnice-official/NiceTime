package app.nicetime.android.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.niceTimeDataStore by preferencesDataStore(name = "nicetime_settings")

data class AppSettings(
    val darkTheme: Boolean = false,
    val wifiOnlyDownloads: Boolean = true,
    val historyEnabled: Boolean = true,
    val movieBoxEnabled: Boolean = true,
    val fourKHDHubEnabled: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val darkTheme = booleanPreferencesKey("dark_theme")
        val wifiOnlyDownloads = booleanPreferencesKey("wifi_only_downloads")
        val historyEnabled = booleanPreferencesKey("history_enabled")
        val movieBoxEnabled = booleanPreferencesKey("moviebox_enabled")
        val fourKHDHubEnabled = booleanPreferencesKey("fourkhdhub_enabled")
    }

    val settings: Flow<AppSettings> = context.niceTimeDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            AppSettings(
                darkTheme = preferences[Keys.darkTheme] ?: false,
                wifiOnlyDownloads = preferences[Keys.wifiOnlyDownloads] ?: true,
                historyEnabled = preferences[Keys.historyEnabled] ?: true,
                movieBoxEnabled = preferences[Keys.movieBoxEnabled] ?: true,
                fourKHDHubEnabled = preferences[Keys.fourKHDHubEnabled] ?: true,
            )
        }

    suspend fun setDarkTheme(enabled: Boolean) = set(Keys.darkTheme, enabled)
    suspend fun setWifiOnlyDownloads(enabled: Boolean) = set(Keys.wifiOnlyDownloads, enabled)
    suspend fun setHistoryEnabled(enabled: Boolean) = set(Keys.historyEnabled, enabled)
    suspend fun setMovieBoxEnabled(enabled: Boolean) = set(Keys.movieBoxEnabled, enabled)
    suspend fun setFourKHDHubEnabled(enabled: Boolean) = set(Keys.fourKHDHubEnabled, enabled)

    private suspend fun set(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.niceTimeDataStore.edit { it[key] = value }
    }
}
