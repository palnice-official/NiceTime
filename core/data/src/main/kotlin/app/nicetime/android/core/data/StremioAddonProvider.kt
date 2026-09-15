package app.nicetime.android.core.data

import app.nicetime.android.core.model.*
import java.net.Proxy
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

class StremioAddonProvider(
    override val instanceId: ProviderInstanceId,
    manifestUrl: String,
    private val manifest: AddonManifest,
    private val http: OkHttpClient = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build(),
) : ContentProvider {
    private val baseUrl = AddonClient.normalizeManifestUrl(manifestUrl).substringBeforeLast("/manifest.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val resolved = ConcurrentHashMap<ReleaseRef, ResolvedStream>()
    private val supported = manifest.types.mapNotNull {
        when (it.lowercase()) { "movie" -> MediaType.MOVIE; "series" -> MediaType.SERIES; else -> null }
    }.toSet().ifEmpty { setOf(MediaType.MOVIE, MediaType.SERIES) }

    override val capabilities = ProviderCapabilities(
        catalog = manifest.catalogs.isNotEmpty(),
        search = manifest.catalogs.isNotEmpty(),
        pagination = true,
        streamResolution = true,
        downloadable = true,
        supportedTypes = supported,
    )

    override suspend fun catalog(request: CatalogRequest): ProviderResult<Page<MediaSummary>> {
        val catalog = manifest.catalogs.firstOrNull { typeOf(it.type) in supported }
            ?: return ProviderResult.Failure(ProviderFailure.Unsupported("catalog"))
        val skip = (request.page - 1).coerceAtLeast(0) * PAGE_SIZE
        val endpoint = "$baseUrl/catalog/${catalog.type}/${encodePath(catalog.id)}/skip=$skip.json"
        return fetch(endpoint).map { payload -> Page(parseMetas(payload), nextCursor = if (parseMetas(payload).size >= PAGE_SIZE) "${request.page + 1}" else null) }
    }

    override suspend fun search(request: SearchRequest): ProviderResult<Page<MediaSummary>> {
        if (request.query.isBlank()) return ProviderResult.Ok(Page(emptyList()))
        val results = mutableListOf<MediaSummary>()
        var firstFailure: ProviderFailure? = null
        val catalogs = manifest.catalogs.filter { typeOf(it.type) in supported }.distinctBy { it.type }
        for (catalog in catalogs) {
            val query = URLEncoder.encode(request.query.trim(), Charsets.UTF_8.name()).replace("+", "%20")
            val endpoint = "$baseUrl/catalog/${catalog.type}/${encodePath(catalog.id)}/search=$query.json"
            when (val response = fetch(endpoint)) {
                is ProviderResult.Ok -> results += parseMetas(response.value)
                is ProviderResult.Failure -> if (firstFailure == null) firstFailure = response.failure
            }
        }
        return if (results.isNotEmpty() || firstFailure == null) ProviderResult.Ok(Page(results.distinctBy { it.ref }))
        else ProviderResult.Failure(firstFailure)
    }

    override suspend fun details(ref: MediaRef): ProviderResult<MediaDetails> {
        if (ref.provider != instanceId) return ProviderResult.Failure(ProviderFailure.NotFound())
        val type = if (ref.type == MediaType.SERIES) "series" else "movie"
        return fetch("$baseUrl/meta/$type/${encodePath(ref.providerMediaId)}.json").map { payload ->
            val meta = payload.jsonObject["meta"] ?: payload
            parseDetails(meta.jsonObject, ref)
        }
    }

    override suspend fun streams(ref: EpisodeRef): ProviderResult<List<Release>> {
        if (ref.media.provider != instanceId) return ProviderResult.Failure(ProviderFailure.NotFound())
        val type = if (ref.media.type == MediaType.SERIES) "series" else "movie"
        val streamId = if (ref.media.type == MediaType.SERIES) ref.providerEpisodeId else ref.media.providerMediaId
        return fetch("$baseUrl/stream/$type/${encodePath(streamId)}.json").map { payload ->
            val array = when (payload) {
                is JsonArray -> payload
                else -> payload.jsonObject["streams"]?.jsonArray ?: JsonArray(emptyList())
            }
            array.mapNotNull { element ->
                val item = element.jsonObject
                val uri = item.string("url")?.takeIf { isPlayable(it) } ?: return@mapNotNull null
                val releaseRef = ReleaseRef(ref, sha256(uri).take(24))
                val label = item.string("title") ?: item.string("name") ?: manifest.name
                val stream = ResolvedStream(
                    release = releaseRef,
                    uri = uri,
                    allowedHosts = listOfNotNull(runCatching { URI(uri).host }.getOrNull()).toSet(),
                    sourceLabel = manifest.name,
                )
                resolved[releaseRef] = stream
                Release(
                    ref = releaseRef,
                    label = label.lineSequence().firstOrNull()?.take(120) ?: manifest.name,
                    resolutionHeight = Regex("(?i)(2160|1080|720|480)p").find(label)?.groupValues?.get(1)?.toIntOrNull(),
                    sizeBytes = parseSize(item.string("description") ?: label),
                    downloadable = true,
                )
            }
        }
    }

    override suspend fun resolve(ref: ReleaseRef, purpose: ResolvePurpose): ProviderResult<ResolvedStream> =
        resolved[ref]?.let { ProviderResult.Ok(it) }
            ?: ProviderResult.Failure(ProviderFailure.NotFound("Release must be refreshed"))

    private suspend fun fetch(endpoint: String): ProviderResult<JsonElement> = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(endpoint).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext ProviderResult.Failure(
                    if (response.code == 404) ProviderFailure.NotFound() else ProviderFailure.SourceUnavailable("${manifest.name} returned HTTP ${response.code}"),
                )
                val body = response.body ?: return@withContext ProviderResult.Failure(ProviderFailure.ParseFailure("Empty response"))
                if (body.contentLength() > MAX_RESPONSE_BYTES) return@withContext ProviderResult.Failure(ProviderFailure.ParseFailure("Response is too large"))
                val bytes = body.byteStream().use { it.readAtMost(MAX_RESPONSE_BYTES + 1) }
                if (bytes.size > MAX_RESPONSE_BYTES) ProviderResult.Failure(ProviderFailure.ParseFailure("Response is too large"))
                else ProviderResult.Ok(json.parseToJsonElement(bytes.toString(Charsets.UTF_8)))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            ProviderResult.Failure(ProviderFailure.NetworkUnavailable())
        } catch (_: Exception) {
            ProviderResult.Failure(ProviderFailure.ParseFailure())
        }
    }

    private fun parseMetas(payload: JsonElement): List<MediaSummary> {
        val array = when (payload) {
            is JsonArray -> payload
            else -> payload.jsonObject["metas"]?.jsonArray
                ?: payload.jsonObject["items"]?.jsonArray
                ?: payload.jsonObject["results"]?.jsonArray
                ?: JsonArray(emptyList())
        }
        return array.mapNotNull { element ->
            val item = element.jsonObject
            val id = item.string("id") ?: return@mapNotNull null
            val title = item.string("name") ?: item.string("title") ?: id
            val type = typeOf(item.string("type")) ?: MediaType.MOVIE
            MediaSummary(MediaRef(instanceId, id, type), title, parseYear(item), item.string("poster") ?: item.string("cover"))
        }
    }

    private fun parseDetails(item: JsonObject, ref: MediaRef): MediaDetails {
        val episodes = item["videos"]?.jsonArray.orEmpty().mapNotNull { element ->
            val video = element.jsonObject
            val id = video.string("id") ?: return@mapNotNull null
            EpisodeSummary(
                ref = EpisodeRef(ref, id, video.int("season"), video.int("episode") ?: video.int("number")),
                title = video.string("title") ?: video.string("name"),
                season = video.int("season"),
                episode = video.int("episode") ?: video.int("number"),
                stillUrl = video.string("thumbnail"),
            )
        }
        val seasons = episodes.groupBy { it.season }.map { (number, values) -> Season("season-${number ?: 0}", number, values) }
        return MediaDetails(
            ref = ref,
            title = item.string("name") ?: item.string("title") ?: ref.providerMediaId,
            year = parseYear(item),
            synopsis = item.string("description") ?: item.string("overview") ?: item.string("synopsis"),
            posterUrl = item.string("poster") ?: item.string("cover"),
            backdropUrl = item.string("background"),
            seasons = seasons,
        )
    }

    private fun typeOf(value: String?): MediaType? = when (value?.lowercase()) { "movie" -> MediaType.MOVIE; "series" -> MediaType.SERIES; else -> null }
    private fun parseYear(item: JsonObject): Int? = listOf("year", "releaseInfo", "released").firstNotNullOfOrNull { key -> Regex("(19|20)\\d{2}").find(item.string(key).orEmpty())?.value?.toIntOrNull() }
    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()
    private fun encodePath(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20").replace("%3A", ":")
    private fun isPlayable(value: String) = runCatching { URI(value) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrBlank() } == true
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun parseSize(value: String): Long? {
        val match = Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*(GB|MB)").find(value) ?: return null
        val multiplier = if (match.groupValues[2].equals("GB", true)) 1_073_741_824.0 else 1_048_576.0
        return (match.groupValues[1].toDouble() * multiplier).toLong()
    }

    private companion object { const val PAGE_SIZE = 20; const val MAX_RESPONSE_BYTES = 5 * 1_048_576 }
}
