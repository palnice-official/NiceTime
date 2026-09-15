package app.nicetime.android.core.data

import app.nicetime.android.core.model.ContentProvider
import app.nicetime.android.core.model.ProviderFailure
import app.nicetime.android.core.model.ProviderInstanceId
import app.nicetime.android.core.model.ProviderResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Creates providers from persisted source records without exposing HTTP implementation types. */
class ProviderFactory {
    private val addonClient = AddonClient.create()
    private val cache = ConcurrentHashMap<String, CachedProvider>()

    suspend fun create(source: SourceConfiguration): ProviderResult<ContentProvider> {
        if (!source.enabled) return ProviderResult.Failure(ProviderFailure.Unsupported("source disabled"))
        cache[source.instanceId]?.takeIf { it.revision == source.revision }?.let { return ProviderResult.Ok(it.provider) }
        val id = runCatching { ProviderInstanceId(UUID.fromString(source.instanceId)) }.getOrElse {
            return ProviderResult.Failure(ProviderFailure.ParseFailure("Invalid source identity"))
        }
        return addonClient.fetchManifest(source.manifestUrl).map { manifest ->
            StremioAddonProvider(id, source.manifestUrl, manifest).also { cache[source.instanceId] = CachedProvider(source.revision, it) }
        }
    }

    fun remove(instanceId: String) { cache.remove(instanceId) }

    private data class CachedProvider(val revision: Long, val provider: ContentProvider)
}
