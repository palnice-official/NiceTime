package app.nicetime.android.core.data

import app.nicetime.android.core.model.ProviderFailure
import app.nicetime.android.core.model.ProviderResult
import java.net.Proxy
import java.net.URI
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class AddonManifest(
    val id: String,
    val version: String? = null,
    val name: String,
    val description: String? = null,
    val types: List<String> = emptyList(),
    val catalogs: List<AddonCatalog> = emptyList(),
)

@Serializable
data class AddonCatalog(val type: String, val id: String, val name: String? = null)

class AddonClient internal constructor(
    private val http: OkHttpClient = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun fetchManifest(input: String): ProviderResult<AddonManifest> = withContext(Dispatchers.IO) {
        val url = runCatching { normalizeManifestUrl(input) }.getOrElse {
            return@withContext ProviderResult.Failure(ProviderFailure.ParseFailure("Invalid addon URL"))
        }
        try {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ProviderResult.Failure(
                        if (response.code == 401 || response.code == 403) ProviderFailure.AuthExpired()
                        else ProviderFailure.SourceUnavailable("Addon returned HTTP ${response.code}"),
                    )
                }
                val body = response.body ?: return@withContext ProviderResult.Failure(ProviderFailure.ParseFailure("Empty manifest"))
                if (body.contentLength() > MAX_MANIFEST_BYTES) {
                    return@withContext ProviderResult.Failure(ProviderFailure.ParseFailure("Manifest is too large"))
                }
                val bytes = body.byteStream().use { it.readAtMost(MAX_MANIFEST_BYTES + 1) }
                if (bytes.size > MAX_MANIFEST_BYTES) {
                    return@withContext ProviderResult.Failure(ProviderFailure.ParseFailure("Manifest is too large"))
                }
                val manifest = json.decodeFromString<AddonManifest>(bytes.toString(Charsets.UTF_8))
                if (manifest.id.isBlank() || manifest.name.isBlank()) {
                    ProviderResult.Failure(ProviderFailure.ParseFailure("Manifest is missing required fields"))
                } else {
                    ProviderResult.Ok(manifest)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            ProviderResult.Failure(ProviderFailure.NetworkUnavailable())
        } catch (error: Exception) {
            ProviderResult.Failure(ProviderFailure.ParseFailure(error.message ?: "Could not read source response"))
        }
    }

    companion object {
        const val MAX_MANIFEST_BYTES = 1_048_576

        fun create(): AddonClient = AddonClient()

        fun normalizeManifestUrl(input: String): String {
            val raw = input.trim()
            require(raw.length in 1..2_048)
            val uri = URI(raw)
            require(uri.userInfo == null)
            val scheme = uri.scheme?.lowercase()
            require(!uri.host.isNullOrBlank())
            val host = uri.host.lowercase().trimEnd('.')
            val loopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
            require(scheme == "https" || (scheme == "http" && loopback))
            val path = uri.path.orEmpty().trimEnd('/')
            val manifestPath = if (path.endsWith("/manifest.json") || path == "/manifest.json") path else "$path/manifest.json"
            return URI(scheme, null, uri.host, uri.port, manifestPath, uri.query, null).toASCIIString()
        }
    }
}

internal fun InputStream.readAtMost(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, 16_384))
    val buffer = ByteArray(8_192)
    var remaining = limit
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        output.write(buffer, 0, count)
        remaining -= count
    }
    return output.toByteArray()
}
