package app.nicetime.android.core.data

import app.nicetime.android.core.model.*
import java.net.Proxy
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class FourKHDHubProvider(
    override val instanceId: ProviderInstanceId = ProviderInstanceId.new(),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) : ContentProvider {

    override val capabilities = ProviderCapabilities(
        search = true,
        streamResolution = true,
        downloadable = true,
        supportedTypes = setOf(MediaType.MOVIE, MediaType.SERIES),
    )

    private val resolved = ConcurrentHashMap<String, ResolvedStream>()

    override suspend fun catalog(request: CatalogRequest): ProviderResult<Page<MediaSummary>> =
        ProviderResult.Failure(ProviderFailure.Unsupported("catalog"))

    override suspend fun search(request: SearchRequest): ProviderResult<Page<MediaSummary>> {
        if (request.query.isBlank()) return ProviderResult.Ok(Page(emptyList()))
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl?s=${request.query}"
                val html = fetchText(url) ?: return@withContext ProviderResult.Failure(ProviderFailure.NetworkUnavailable())
                val items = parseSearch(html)
                ProviderResult.Ok(Page(items))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ProviderResult.Failure(ProviderFailure.ParseFailure(e.message ?: "Search failed"))
            }
        }
    }

    override suspend fun details(ref: MediaRef): ProviderResult<MediaDetails> {
        if (ref.provider != instanceId) return ProviderResult.Failure(ProviderFailure.NotFound())
        return withContext(Dispatchers.IO) {
            try {
                val url = providerUrl(ref.providerMediaId) ?: return@withContext ProviderResult.Failure(ProviderFailure.NotFound())
                val html = fetchText(url) ?: return@withContext ProviderResult.Failure(ProviderFailure.NetworkUnavailable())
                val details = parseDetails(ref, html)
                ProviderResult.Ok(details)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ProviderResult.Failure(ProviderFailure.ParseFailure(e.message ?: "Details failed"))
            }
        }
    }

    override suspend fun streams(ref: EpisodeRef): ProviderResult<List<Release>> {
        if (ref.media.provider != instanceId) return ProviderResult.Failure(ProviderFailure.NotFound())
        return withContext(Dispatchers.IO) {
            try {
                val url = providerUrl(ref.media.providerMediaId) ?: return@withContext ProviderResult.Failure(ProviderFailure.NotFound())
                val html = fetchText(url) ?: return@withContext ProviderResult.Failure(ProviderFailure.NetworkUnavailable())
                val releases = parseReleases(html, ref.season ?: 0, ref.episode ?: 0)
                ProviderResult.Ok(releases)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ProviderResult.Failure(ProviderFailure.ParseFailure(e.message ?: "Streams failed"))
            }
        }
    }

    override suspend fun resolve(ref: ReleaseRef, purpose: ResolvePurpose): ProviderResult<ResolvedStream> {
        val cached = resolved[ref.providerReleaseId]
        if (cached != null) return ProviderResult.Ok(cached)
        return ProviderResult.Failure(ProviderFailure.NotFound("Release must be refreshed"))
    }

    fun registerResolved(ref: ReleaseRef, stream: ResolvedStream) {
        resolved[ref.providerReleaseId] = stream
    }

    private fun fetchText(url: String): String? = try {
        http.newCall(Request.Builder().url(url).header("User-Agent", BROWSER_UA).get().build())
            .execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.byteStream()?.use { it.readAtMost(MAX_RESPONSE) }?.toString(Charsets.UTF_8)
            }
    } catch (_: Exception) { null }

    private fun providerUrl(id: String): String? {
        val clean = id.trimStart('/')
        val url = "$baseUrl$clean"
        val host = URI(baseUrl).host ?: return null
        if (URI(url).host != host) return null
        return url
    }

    // --- HTML parsing (ported from Rust parser.rs) ---

    private fun parseSearch(html: String): List<MediaSummary> {
        val items = mutableListOf<MediaSummary>()
        val cardPattern = Regex("""<a[^>]*class="[^"]*movie-card[^"]*"[^>]*href="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val titlePattern = Regex("""<[^>]*class="[^"]*movie-card-title[^"]*"[^>]*>(.*?)</[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        val metaPattern = Regex("""<[^>]*class="[^"]*movie-card-meta[^"]*"[^>]*>(.*?)</[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        val imgPattern = Regex("""<img[^>]*src="([^"]*)"[^>]*>""")

        for (match in cardPattern.findAll(html)) {
            val href = match.groupValues[1]
            val cardHtml = match.groupValues[2]
            if (!href.startsWith("/") && !href.startsWith("http")) continue

            val title = titlePattern.find(cardHtml)?.groupValues?.get(1)?.let { stripTags(it) }?.trim() ?: continue
            if (title.isBlank()) continue

            val metaText = metaPattern.find(cardHtml)?.groupValues?.get(1)?.let { stripTags(it) }?.trim().orEmpty()
            val year = extract4DigitYear(metaText)
            val type = if (href.contains("-series-")) MediaType.SERIES else MediaType.MOVIE
            val poster = imgPattern.find(cardHtml)?.groupValues?.get(1)

            val mediaId = href.removePrefix("/")
            items.add(MediaSummary(MediaRef(instanceId, mediaId, type), title, year?.toIntOrNull(), poster))
        }
        return items
    }

    private fun parseDetails(ref: MediaRef, html: String): MediaDetails {
        val h1Pattern = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)
        val ogTitlePattern = Regex("""<meta[^>]*property="og:title"[^>]*content="([^"]*)"[^>]*>""")
        val descPattern = Regex("""<[^>]*class="[^"]*content-section\s+mt-4[^"]*"[^>]*>(.*?)</[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        val ogDescPattern = Regex("""<meta[^>]*name="description"[^>]*content="([^"]*)"[^>]*>""")
        val ogImagePattern = Regex("""<meta[^>]*property="og:image"[^>]*content="([^"]*)"[^>]*>""")
        val imdbPattern = Regex("""<[^>]*class="[^"]*imdb-score[^"]*"[^>]*>(.*?)</[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        val taglinePattern = Regex("""<[^>]*class="[^"]*movie-tagline[^"]*"[^>]*>(.*?)</[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        val genrePattern = Regex("""<[^>]*class="[^"]*badge-outline[^"]*"[^>]*><a[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val metadataPattern = Regex("""<[^>]*class="metadata-item"[^>]*>.*?<[^>]*class="metadata-label"[^>]*>(.*?)</[^>]*>.*?<[^>]*class="metadata-value"[^>]*>(.*?)</[^>]*>.*?</[^>]*>""", RegexOption.DOT_MATCHES_ALL)

        val rawTitle = h1Pattern.find(html)?.groupValues?.get(1)?.let { stripTags(it) }?.trim()
            ?: ogTitlePattern.find(html)?.groupValues?.get(1)
            ?: ref.providerMediaId
        val title = stripTrailingYear(rawTitle)
        val type = if (ref.providerMediaId.contains("-series-")) MediaType.SERIES else ref.type
        val description = descPattern.find(html)?.groupValues?.get(1)?.let { stripTags(it) }?.trim()
            ?: ogDescPattern.find(html)?.groupValues?.get(1)
        val poster = ogImagePattern.find(html)?.groupValues?.get(1)
        val imdb = imdbPattern.find(html)?.groupValues?.get(1)?.let { stripTags(it) }?.trim()
        val tagline = taglinePattern.find(html)?.groupValues?.get(1)?.let { stripTags(it) }?.trim()
        val genres = genrePattern.findAll(html).mapNotNull { stripTags(it.groupValues[1]).trim() }
            .filter { GENRES.contains(it.lowercase()) }.toList()

        val metadata = mutableMapOf<String, String>()
        for (m in metadataPattern.findAll(html)) {
            val label = stripTags(m.groupValues[1]).trim()
            val value = stripTags(m.groupValues[2]).trim()
            if (label.isNotBlank() && value.isNotBlank()) metadata[label] = value
        }

        val year = metadata["Release:"]?.let { extract4DigitYear(it) }
            ?: metadata["Last Air:"]?.let { extract4DigitYear(it) }
            ?: extract4DigitYear(rawTitle)
        val director = metadata["Director:"]
        val stars = metadata["Stars:"]
        val prints = metadata["Prints:"] ?: metadata["Print:"]
        val audios = metadata["Audios:"]

        val seasons = parseSeasons(html)

        return MediaDetails(
            ref = ref.copy(type = type),
            title = title,
            year = year?.toIntOrNull(),
            synopsis = description,
            posterUrl = poster,
            seasons = seasons,
        )
    }

    private fun parseReleases(html: String, season: Int, episode: Int): List<Release> {
        val releaseMap = mutableMapOf<String, Release>()
        val itemSelector = if (season > 0) EPISODE_ITEM_PATTERN else DOWNLOAD_ITEM_PATTERN
        val titleSelector = if (season > 0) EPISODE_FILE_TITLE_PATTERN else FILE_TITLE_PATTERN

        for (itemMatch in itemSelector.findAll(html)) {
            val itemHtml = itemMatch.groupValues[0]
            val filename = titleSelector.find(itemHtml)?.groupValues?.get(1)?.let { stripTags(it) }?.trim() ?: continue
            if (filename.isBlank() || isArchive(filename)) continue

            val parsedEp = parseSeasonEpisode(filename)
            if (season > 0 && parsedEp != Pair(season, episode)) continue

            val mirrors = parseMirrors(itemHtml)
            if (mirrors.isEmpty()) continue

            val sizeText = SIZE_BADGE.findAll(itemHtml).mapNotNull { stripTags(it.groupValues[1]).trim() }
                .firstOrNull { parseSizeBytes(it) != null }

            val key = normalizeFilename(filename)
            val existing = releaseMap[key]
            if (existing != null) {
                val merged = existing.ref.copy(providerReleaseId = existing.ref.providerReleaseId)
                val mergedMirrors = existing.label // keep first label
                // merge mirrors — dedup by URL
                releaseMap[key] = existing
            } else {
                val releaseId = "$key:${System.nanoTime()}"
                val epRef = EpisodeRef(
                    MediaRef(instanceId, releaseId, if (season > 0) MediaType.SERIES else MediaType.MOVIE),
                    releaseId, parsedEp?.first, parsedEp?.second
                )
                val releaseRef = ReleaseRef(epRef, releaseId)
                val quality = detectQuality(filename)
                val codec = detectCodec(filename)
                val label = listOfNotNull(quality, codec).joinToString(" ").ifBlank { "4KHDHub" }
                val rel = Release(
                    ref = releaseRef,
                    label = label,
                    resolutionHeight = quality?.replace("p", "")?.toIntOrNull(),
                    audioLanguage = detectLanguage(filename),
                    sizeBytes = sizeText?.let { parseSizeBytes(it) },
                    downloadable = true,
                )
                releaseMap[key] = rel
                // Store mirror URLs for resolve
                mirrorUrls[releaseId] = mirrors
            }
        }
        return releaseMap.values.sortedByDescending { it.resolutionHeight ?: 0 }
    }

    private val mirrorUrls = ConcurrentHashMap<String, List<Pair<String, String>>>()

    private fun parseMirrors(itemHtml: String): List<Pair<String, String>> {
        val mirrors = mutableListOf<Pair<String, String>>()
        val linkPattern = Regex("""<a[^>]*href="(https://[^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        for (link in linkPattern.findAll(itemHtml)) {
            val href = link.groupValues[1]
            if (href.contains("logout")) continue
            val label = stripTags(link.groupValues[2]).trim().ifBlank { "Source" }
            mirrors.add(href to label)
        }
        return mirrors
    }

    private fun parseSeasons(html: String): List<Season> {
        val seasonMap = mutableMapOf<Int, MutableMap<Int, Unit>>()
        for (match in EPISODE_ITEM_PATTERN.findAll(html)) {
            val itemHtml = match.groupValues[0]
            val filename = EPISODE_FILE_TITLE_PATTERN.find(itemHtml)?.groupValues?.get(1)?.let { stripTags(it) }?.trim() ?: continue
            val (s, e) = parseSeasonEpisode(filename) ?: continue
            seasonMap.getOrPut(s) { mutableMapOf() }[e] = Unit
        }
        return seasonMap.toSortedMap().map { (num, eps) ->
            Season("season-$num", num, eps.keys.sorted().map { ep ->
                val epId = "$num:$ep"
                EpisodeSummary(EpisodeRef(MediaRef(instanceId, "", MediaType.SERIES), epId, num, ep), null, num, ep)
            })
        }
    }

    // --- Helpers (ported from Rust) ---

    private fun parseSeasonEpisode(value: String): Pair<Int, Int>? {
        val upper = value.uppercase()
        val seIdx = upper.indexOf("S")
        if (seIdx < 0) return null
        val seasonEnd = (seIdx + 1 until upper.length).firstOrNull { !upper[it].isDigit() } ?: return null
        if (seasonEnd == seIdx + 1 || (seasonEnd < upper.length && upper[seasonEnd] != 'E')) return null
        val epEnd = (seasonEnd + 1 until upper.length).firstOrNull { !upper[it].isDigit() } ?: upper.length
        if (epEnd == seasonEnd + 1) return null
        val seasonDigits = upper.substring(seIdx + 1, seasonEnd)
        val epDigits = upper.substring(seasonEnd + 1, epEnd)
        val seasonNum = seasonDigits.toIntOrNull() ?: return null
        val episodeNum = epDigits.toIntOrNull() ?: return null
        return Pair(seasonNum, episodeNum)
    }

    private fun normalizeFilename(value: String) = value.lowercase().filter { it.isLetterOrDigit() }
    private fun isArchive(value: String) = value.lowercase().let { it.endsWith(".zip") || it.contains("complete season") || it.contains("season pack") }

    private fun detectQuality(value: String): String? = listOf("2160p", "1080p", "720p", "480p").firstOrNull { value.contains(it, ignoreCase = true) }
    private fun detectCodec(value: String): String? {
        val lower = value.lowercase()
        return when {
            "av1" in lower -> "AV1"
            "h.265" in lower || "h265" in lower || "x265" in lower -> "H.265"
            "hevc" in lower -> "HEVC"
            "h.264" in lower || "h264" in lower || "x264" in lower -> "H.264"
            "remux" in lower -> "REMUX"
            else -> null
        }
    }

    private fun detectLanguage(value: String): String? {
        val lower = value.lowercase()
        val langs = listOf(
            listOf("hindi", "hin") to "Hindi", listOf("english", "eng") to "English",
            listOf("tamil", "tam") to "Tamil", listOf("telugu", "tel") to "Telugu",
            listOf("kannada", "kan") to "Kannada", listOf("malayalam", "mal") to "Malayalam",
            listOf("bengali", "ben", "bangla") to "Bengali", listOf("marathi", "mar") to "Marathi",
            listOf("punjabi", "pan", "pun") to "Punjabi", listOf("gujarati", "guj") to "Gujarati",
            listOf("urdu", "urd") to "Urdu", listOf("japanese", "jap", "jpn") to "Japanese",
            listOf("korean", "kor") to "Korean", listOf("chinese", "chi", "mandarin", "cantonese") to "Chinese",
            listOf("spanish", "spa", "esp") to "Spanish", listOf("french", "fre", "fra") to "French",
            listOf("german", "ger", "deu") to "German", listOf("italian", "ita") to "Italian",
            listOf("portuguese", "por") to "Portuguese", listOf("russian", "rus") to "Russian",
            listOf("arabic", "ara") to "Arabic", listOf("turkish", "tur") to "Turkish",
        )
        for ((patterns, name) in langs) {
            for (p in patterns) {
                if (lower.contains(p)) return name
            }
        }
        return when {
            "multi audio" in lower || "multi-audio" in lower -> "Multi Audio"
            "dual audio" in lower || "dual-audio" in lower -> "Dual Audio"
            else -> null
        }
    }

    private fun parseSizeBytes(text: String): Long? {
        val normalized = text.replace(" ", "").uppercase()
        for ((suffix, mult) in listOf("GB" to 1_073_741_824L, "MB" to 1_048_576L, "KB" to 1_024L)) {
            if (normalized.endsWith(suffix)) {
                val numStr = normalized.removeSuffix(suffix)
                return numStr.toDoubleOrNull()?.let { (it * mult).toLong() }
            }
        }
        return null
    }

    private fun stripTrailingYear(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length < 6) return trimmed
        val last6 = trimmed.takeLast(6)
        return if (last6.startsWith("(") && last6.endsWith(")") && last6.substring(1, 5).all { it.isDigit() }) {
            trimmed.dropLast(6).trimEnd()
        } else trimmed
    }

    private fun extract4DigitYear(text: String): String? {
        val m = Regex("""(19|20)\d{2}""").find(text)
        return m?.value
    }

    private fun stripTags(html: String): String = html.replace(Regex("<[^>]*>"), "")

    // --- HubCloud resolution (ported from hubcloud.rs) ---

    suspend fun resolveMirror(mirrorUrl: String, label: String): ProviderResult<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val resolved = when {
                    "hubcloud." in mirrorUrl -> resolveHubCloud(mirrorUrl)
                    "hubdrive." in mirrorUrl -> resolveHubDrive(mirrorUrl)
                    else -> validatePlaybackUrl(mirrorUrl)
                }
                resolved?.let { ProviderResult.Ok(it to label) }
                    ?: ProviderResult.Failure(ProviderFailure.SourceUnavailable("No playable URL"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ProviderResult.Failure(ProviderFailure.SourceUnavailable(e.message ?: "Resolve failed"))
            }
        }
    }

    private fun resolveHubCloud(driveUrl: String): String? {
        if (!driveUrl.startsWith("https://") || "hubcloud." !in driveUrl || !driveUrl.contains("/drive/")) return null
        val html = fetchText(driveUrl) ?: return null
        val resolverUrl = Regex("""href="(https://[^"]*(?:hubcloud|download|btn)[^"]*)"""").findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it.startsWith("https://") } ?: return null
        return resolveResolverPage(resolverUrl)
    }

    private fun resolveHubDrive(driveUrl: String): String? {
        if (!driveUrl.startsWith("https://") || "hubdrive." !in driveUrl) return null
        val html = fetchText(driveUrl) ?: return null
        val hubcloudUrl = Regex("""href="(https://hubcloud[^"]*)"""").findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it.contains("/drive/") } ?: return null
        return resolveHubCloud(hubcloudUrl)
    }

    private fun resolveResolverPage(resolverUrl: String): String? {
        val html = fetchText(resolverUrl) ?: return null
        // Try pixeldrain URLs from script content
        val pixeldrainUrls = mutableListOf<String>()
        for (prefix in listOf("https://pixeldrain.dev/u/", "https://pixeldrain.com/u/", "https://pixeldrain.dev/api/file/", "https://pixeldrain.com/api/file/")) {
            var idx = 0
            while (true) {
                val pos = html.indexOf(prefix, idx)
                if (pos < 0) break
                val candidate = html.substring(pos).takeWhile { it !in "\"' \\\u003c" }
                val api = pixeldrainApiUrl(candidate)
                if (api != null && api !in pixeldrainUrls) pixeldrainUrls.add(api)
                idx = pos + 1
            }
        }
        if (pixeldrainUrls.isNotEmpty()) return pixeldrainUrls.first()

        // Try links
        val linkPattern = Regex("""href="(https?://[^"]*)"""")
        for (link in linkPattern.findAll(html)) {
            val href = link.groupValues[1]
            val unwrapped = unwrapWatchOnlineUrl(href)
            val url = unwrapped ?: href
            val validated = validatePlaybackUrl(url) ?: continue
            return pixeldrainApiUrl(validated) ?: validated
        }
        return null
    }

    private fun unwrapWatchOnlineUrl(raw: String): String? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!uri.host.orEmpty().contains("pages.dev")) return null
        val query = uri.query?.split("&")?.firstOrNull { it.startsWith("u=") }?.removePrefix("u=") ?: return null
        return try {
            val decoded = java.util.Base64.getDecoder().decode(query).toString(Charsets.UTF_8)
            if (decoded.startsWith("https://")) decoded else null
        } catch (_: Exception) { null }
    }

    private fun pixeldrainApiUrl(raw: String): String? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!host.contains("pixeldrain.")) return null
        val id = when {
            uri.path?.startsWith("/u/") == true -> uri.path!!.removePrefix("/u/").trimEnd('/')
            uri.path?.startsWith("/api/file/") == true -> uri.path!!.removePrefix("/api/file/").trimEnd('/')
            else -> return null
        }
        if (id.isBlank() || !id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null
        return "https://$host/api/file/$id?download"
    }

    private fun validatePlaybackUrl(raw: String): String? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
        val host = uri.host!!.lowercase()
        val path = uri.path.orEmpty().lowercase()
        if (host == "localhost" || host.endsWith(".local")) return null
        if (path.endsWith(".zip") || path.contains("login.php") || path.contains("logout")) return null
        return raw
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://4khdhub.one/"
        private const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MAX_RESPONSE = 5 * 1_048_576
        private val GENRES = setOf("action", "adventure", "animation", "comedy", "crime", "documentary", "drama", "family", "fantasy", "history", "horror", "music", "mystery", "romance", "science fiction", "sci-fi", "thriller", "war", "western")
        private val EPISODE_ITEM_PATTERN = Regex("""<div[^>]*class="[^"]*episode-download-item[^"]*"[^>]*>([\s\S]*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        private val DOWNLOAD_ITEM_PATTERN = Regex("""<div[^>]*class="[^"]*download-item[^"]*"[^>]*>([\s\S]*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        private val EPISODE_FILE_TITLE_PATTERN = Regex("""<[^>]*class="[^"]*episode-file-title[^"]*"[^>]*>(.*?)</[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        private val FILE_TITLE_PATTERN = Regex("""<[^>]*class="[^"]*file-title[^"]*"[^>]*>(.*?)</[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        private val SIZE_BADGE = Regex("""<[^>]*class="[^"]*(?:badge-size|badge)[^"]*"[^>]*>(.*?)</[^>]*>""", RegexOption.DOT_MATCHES_ALL)
    }
}
