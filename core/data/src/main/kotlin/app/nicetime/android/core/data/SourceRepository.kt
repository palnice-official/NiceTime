package app.nicetime.android.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.sourceDataStore by preferencesDataStore(name = "nicetime_sources")

@Serializable
data class SourceConfiguration(
    val instanceId: String = UUID.randomUUID().toString(),
    val manifestUrl: String,
    val displayName: String,
    val enabled: Boolean = true,
    val revision: Long = 1,
)

class SourceRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("sources_json")
    private val serializer = ListSerializer(SourceConfiguration.serializer())

    val sources: Flow<List<SourceConfiguration>> = context.sourceDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[key]?.let { encoded -> runCatching { json.decodeFromString(serializer, encoded) }.getOrDefault(emptyList()) }
                ?: emptyList()
        }

    suspend fun save(source: SourceConfiguration) {
        context.sourceDataStore.edit { preferences ->
            val existing = preferences[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList()) }.orEmpty()
            val updated = existing.filterNot { it.instanceId == source.instanceId } + source
            preferences[key] = json.encodeToString(serializer, updated)
        }
    }

    suspend fun setEnabled(instanceId: String, enabled: Boolean) {
        context.sourceDataStore.edit { preferences ->
            val existing = preferences[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList()) }.orEmpty()
            preferences[key] = json.encodeToString(serializer, existing.map {
                if (it.instanceId == instanceId) it.copy(enabled = enabled, revision = it.revision + 1) else it
            })
        }
    }

    suspend fun remove(instanceId: String) {
        context.sourceDataStore.edit { preferences ->
            val existing = preferences[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList()) }.orEmpty()
            preferences[key] = json.encodeToString(serializer, existing.filterNot { it.instanceId == instanceId })
        }
    }
}
