package app.nicetime.android.core.data

import app.nicetime.android.core.model.*
import java.io.IOException
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
        catalog = true,
        search = true,
        streamResolution = true,
        downloadable = true,
        supportedTypes = setOf(MediaType.MOVIE, MediaType.SERIES),
    )

    private val resolved = ConcurrentHashMap<String, ResolvedStream>()

    /** Latest uploads. Used to populate Home so discovery is real content, not a hardcoded list. */
    override suspend fun catalog(request: CatalogRequest): ProviderResult<Page<MediaSummary>> =
        withContext(Dispatchers.IO) {
            try {
                if (request.page > 1) return@withContext ProviderResult.Ok(Page(emptyList()))
                val items = parseSearch(fetchText(baseUrl))
                // An empty page is a legitimate outcome (markup drift or rate limit), not a
                // failure that should surface as a red error on Home.
                ProviderResult.Ok(Page(items, null))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ProviderResult.Failure(failureFor(e))
            }
        }

    override suspend fun search(request: SearchRequest): ProviderResult<Page<MediaSummary>> {
        if (request.query.isBlank()) return ProviderResult.Ok(Page(emptyList()))
        return withContext(Dispatchers.IO) {
            try {
                ProviderResult.Ok(Page(parseSearch(fetchText("$baseUrl?s=${request.query}"))))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ProviderResult.Failure(failureFor(e))
            }
        }
    }

    override suspend fun details(ref: MediaRef): ProviderResult<MediaDetails> {
        if (ref.provider != instanceId) return ProviderResult.Failure(ProviderFailure.NotFound())
        return withContext(Dispatchers.IO) {
            try {
                val url = providerUrl(ref.providerMediaId) ?: return@withContext ProviderResult.Failure(ProviderFailure.NotFound())
                ProviderResult.Ok(parseDetails(ref, fetchText(url)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ProviderResult.Failure(failureFor(e))
            }
        }
    }

    override suspend fun streams(ref: EpisodeRef): ProviderResult<List<Release>> {
        if (ref.media.provider != instanceId) return ProviderResult.Failure(ProviderFailure.NotFound())
        return withContext(Dispatchers.IO) {
            try {
                val url = providerUrl(ref.media.providerMediaId) ?: return@withContext ProviderResult.Failure(ProviderFailure.NotFound())
                ProviderResult.Ok(parseReleases(fetchText(url), ref))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ProviderResult.Failure(failureFor(e))
            }
        }
    }

    /**
     * Resolves a release to a playable URL. Always re-reads the source page instead of trusting a
     * previously resolved URL: these mirrors hand out short-lived links, and a cached one is the
     * classic way an app keeps failing long after the app itself is fine.
     */
    override suspend fun resolve(ref: ReleaseRef, purpose: ResolvePurpose): ProviderResult<ResolvedStream> {
        resolved[ref.providerReleaseId]?.let { cached ->
            if (!isStale(cached)) return ProviderResult.Ok(cached)
        }
        return withContext(Dispatchers.IO) {
            try {
                val mediaId = ref.episode.media.providerMediaId
                val url = providerUrl(mediaId) ?: return@withContext ProviderResult.Failure(ProviderFailure.NotFound())
                val mirrors = mirrorsFor(fetchText(url), ref)
                if (mirrors.isEmpty()) {
                    return@withContext ProviderResult.Failure(ProviderFailure.NotFound("This release is no longer published. Choose another."))
                }
                val failures = mutableListOf<String>()
                for ((mirrorUrl, _) in mirrors) {
                    when (val direct = resolveMirror(mirrorUrl)) {
                        is ProviderResult.Ok -> {
                            val stream = ResolvedStream(
                                release = ref,
                                uri = direct.value,
                                mimeType = mimeTypeFor(direct.value),
                                requestHeaders = mapOf("User-Agent" to BROWSER_UA),
                                allowedHosts = setOfNotNull(runCatching { URI(direct.value).host }.getOrNull()),
                                sourceLabel = "4KHDHub",
                            )
                            resolved[ref.providerReleaseId] = stream
                            return@withContext ProviderResult.Ok(stream)
                        }
                        is ProviderResult.Failure -> failures += direct.failure.message
                    }
                }
                ProviderResult.Failure(ProviderFailure.SourceUnavailable("No 4KHDHub mirror responded: ${failures.joinToString("; ")}"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ProviderResult.Failure(failureFor(e))
            }
        }
    }

    /** Short-lived mirror links are re-resolved once they are more than this old. */
    private fun isStale(stream: ResolvedStream): Boolean {
        val expires = stream.expiresAtEpochMs ?: return false
        return System.currentTimeMillis() >= expires
    }

    private fun mimeTypeFor(url: String): String? = when {
        url.substringBefore('?').endsWith(".mpd", true) -> "application/dash+xml"
        url.substringBefore('?').endsWith(".m3u8", true) -> "application/x-mpegURL"
        else -> null
    }

    /** Fetches a page, throwing a typed error so callers can classify it. */
    private fun fetchText(url: String): String =
        http.newCall(Request.Builder().url(url).header("User-Agent", BROWSER_UA).get().build())
            .execute().use { response ->
                if (!response.isSuccessful) throw HttpStatusException(response.code, url)
                response.body?.byteStream()?.use { it.readAtMost(MAX_RESPONSE) }?.toString(Charsets.UTF_8).orEmpty()
            }

    private fun providerUrl(id: String): String? {
        val clean = id.trimStart('/')
        val url = "$baseUrl$clean"
        val host = URI(baseUrl).host ?: return null
        if (URI(url).host != host) return null
        return url
    }

    private class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

    /** Maps a thrown error to the failure the UI can explain. Order matters: subclasses first. */
    private fun failureFor(error: Throwable): ProviderFailure = when (error) {
        is HttpStatusException -> when (error.code) {
            401, 403 -> ProviderFailure.AuthExpired("4KHDHub refused the request (HTTP ${error.code}). Check the source in You.")
            404 -> ProviderFailure.NotFound("That title is no longer on 4KHDHub.")
            429 -> ProviderFailure.RateLimited(null)
            in 500..599 -> ProviderFailure.SourceUnavailable("4KHDHub is having trouble (HTTP ${error.code})")
            else -> ProviderFailure.SourceUnavailable("4KHDHub returned HTTP ${error.code}")
        }
        is java.net.SocketTimeoutException -> ProviderFailure.Timeout()
        is java.net.UnknownHostException -> ProviderFailure.NetworkUnavailable("4KHDHub could not be reached")
        is IOException -> ProviderFailure.NetworkUnavailable()
        else -> ProviderFailure.ParseFailure(error.message ?: "Could not read the 4KHDHub response")
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

        val detailsRef = ref.copy(type = type)
        val seasons = parseSeasons(html, detailsRef)

        return MediaDetails(
            ref = detailsRef,
            title = title,
            year = year?.toIntOrNull(),
            synopsis = description,
            posterUrl = poster,
            seasons = seasons,
        )
    }

    /**
     * Builds the playable candidates for one episode, carrying the **caller's** media identity
     * through. Releases used to invent a fresh identity per parse, so the details screen could
     * never match one and always reported "No supported releases".
     */
    private fun parseReleases(html: String, ref: EpisodeRef): List<Release> {
        val series = ref.media.type == MediaType.SERIES
        val season = ref.season ?: 0
        val episode = ref.episode ?: 0
        val titleSelector = if (series) EPISODE_FILE_TITLE_PATTERN else FILE_TITLE_PATTERN
        val releaseMap = mutableMapOf<String, Release>()

        for (itemHtml in itemBlocks(html, series)) {
            val filename = titleSelector.find(itemHtml)?.groupValues?.get(1)?.let { stripTags(it) }?.trim() ?: continue
            if (filename.isBlank() || isArchive(filename)) continue

            val parsedEp = parseSeasonEpisode(filename)
            if (series && parsedEp != Pair(season, episode)) continue

            val mirrors = parseMirrors(itemHtml)
            if (mirrors.isEmpty()) continue

            val sizeText = SIZE_BADGE.findAll(itemHtml).mapNotNull { stripTags(it.groupValues[1]).trim() }
                .firstOrNull { parseSizeBytes(it) != null }

            // Deterministic and derived from the page, so a release can be re-resolved after
            // process death instead of depending on an in-memory cache.
            val key = normalizeFilename(filename)
            val releaseId = "${ref.media.providerMediaId}#$key"
            val quality = detectQuality(filename)
            val codec = detectCodec(filename)
            val label = listOfNotNull(quality, codec).joinToString(" ").ifBlank { "4KHDHub" }
            releaseMap.getOrPut(key) {
                mirrorUrls[releaseId] = mirrors
                Release(
                    ref = ReleaseRef(
                        EpisodeRef(ref.media, releaseId, parsedEp?.first ?: ref.season, parsedEp?.second ?: ref.episode),
                        releaseId,
                    ),
                    label = label,
                    resolutionHeight = quality?.replace("p", "")?.toIntOrNull(),
                    audioLanguage = detectLanguage(filename),
                    sizeBytes = sizeText?.let { parseSizeBytes(it) },
                    downloadable = true,
                )
            }
        }
        return releaseMap.values.sortedByDescending { it.resolutionHeight ?: 0 }
    }

    /** Finds the mirrors belonging to one release on a freshly fetched page. */
    private fun mirrorsFor(html: String, ref: ReleaseRef): List<Pair<String, String>> {
        val series = ref.episode.media.type == MediaType.SERIES
        val titleSelector = if (series) EPISODE_FILE_TITLE_PATTERN else FILE_TITLE_PATTERN
        val wanted = ref.providerReleaseId.substringAfter('#', "")
        if (wanted.isEmpty()) return mirrorUrls[ref.providerReleaseId].orEmpty()
        for (itemHtml in itemBlocks(html, series)) {
            val filename = titleSelector.find(itemHtml)?.groupValues?.get(1)?.let { stripTags(it) }?.trim() ?: continue
            if (normalizeFilename(filename) != wanted) continue
            val mirrors = parseMirrors(itemHtml)
            if (mirrors.isNotEmpty()) {
                mirrorUrls[ref.providerReleaseId] = mirrors
                return mirrors
            }
        }
        return mirrorUrls[ref.providerReleaseId].orEmpty()
    }

    private val mirrorUrls = ConcurrentHashMap<String, List<Pair<String, String>>>()

    /**
     * Splits a page into one block per download item. Matching to the next item's opening tag
     * (instead of a non-greedy `.*?</div>`) is what keeps nested markup from truncating a block
     * before its mirror links.
     */
    private fun itemBlocks(html: String, series: Boolean): List<String> {
        val startPattern = if (series) EPISODE_ITEM_START_PATTERN else DOWNLOAD_ITEM_START_PATTERN
        val starts = startPattern.findAll(html).map { it.range.first }.toList()
        if (starts.isEmpty()) return emptyList()
        return starts.mapIndexed { index, start -> html.substring(start, starts.getOrNull(index + 1) ?: html.length) }
    }

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

    /**
     * Builds the selectable entries for the details screen.
     *
     * Episodes carry the **real** media id: an empty one made `streams()` re-fetch the site root
     * and parse the wrong page. The parsed filename is kept as the title, which is what the UI
     * actually shows next to the episode number.
     *
     * A movie page has no episode markup at all, so without a synthetic entry the details screen
     * offers nothing to select and the release list never loads — a movie would be unplayable.
     */
    private fun parseSeasons(html: String, ref: MediaRef): List<Season> {
        val seasonMap = mutableMapOf<Int, MutableMap<Int, String>>()
        for (itemHtml in itemBlocks(html, series = true)) {
            val filename = EPISODE_FILE_TITLE_PATTERN.find(itemHtml)?.groupValues?.get(1)?.let { stripTags(it) }?.trim() ?: continue
            val (s, e) = parseSeasonEpisode(filename) ?: continue
            seasonMap.getOrPut(s) { mutableMapOf() }[e] = filename
        }
        if (seasonMap.isEmpty() && ref.type == MediaType.MOVIE) {
            return listOf(Season("movie", null, listOf(EpisodeSummary(EpisodeRef(ref, ref.providerMediaId, null, null), ref.providerMediaId, null, null))))
        }
        val seriesRef = ref.copy(type = MediaType.SERIES)
        return seasonMap.toSortedMap().map { (num, eps) ->
            Season("season-$num", num, eps.entries.sortedBy { it.key }.map { (ep, filename) ->
                EpisodeSummary(EpisodeRef(seriesRef, "$num:$ep", num, ep), filename, num, ep)
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

    /** Resolves one mirror page to a direct, playable URL. */
    suspend fun resolveMirror(mirrorUrl: String): ProviderResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val resolved = when {
                    "hubcloud." in mirrorUrl -> resolveHubCloud(mirrorUrl)
                    "hubdrive." in mirrorUrl -> resolveHubDrive(mirrorUrl)
                    else -> validatePlaybackUrl(mirrorUrl)
                }
                resolved?.let { ProviderResult.Ok(it) }
                    ?: ProviderResult.Failure(ProviderFailure.SourceUnavailable("No playable link on this mirror"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                ProviderResult.Failure(failureFor(e))
            }
        }
    }

    /** Best-effort fetch for the resolver chain: a dead mirror just means "try the next one". */
    private fun fetchTextOrNull(url: String): String? = try {
        fetchText(url)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun resolveHubCloud(driveUrl: String): String? {
        if (!driveUrl.startsWith("https://") || "hubcloud." !in driveUrl || !driveUrl.contains("/drive/")) return null
        val html = fetchTextOrNull(driveUrl) ?: return null
        val resolverUrl = Regex("""href="(https://[^"]*(?:hubcloud|download|btn)[^"]*)"""").findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it.startsWith("https://") } ?: return null
        return resolveResolverPage(resolverUrl)
    }

    private fun resolveHubDrive(driveUrl: String): String? {
        if (!driveUrl.startsWith("https://") || "hubdrive." !in driveUrl) return null
        val html = fetchTextOrNull(driveUrl) ?: return null
        val hubcloudUrl = Regex("""href="(https://hubcloud[^"]*)"""").findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it.contains("/drive/") } ?: return null
        return resolveHubCloud(hubcloudUrl)
    }

    private fun resolveResolverPage(resolverUrl: String): String? {
        val html = fetchTextOrNull(resolverUrl) ?: return null
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
        private val EPISODE_ITEM_START_PATTERN = Regex("""<div[^>]*class="[^"]*episode-download-item[^"]*"[^>]*>""")
        /**
         * The lookbehind matters: without it `class="episode-download-item"` would also satisfy
         * the plain `download-item` token and every series block would be parsed twice.
         */
        private val DOWNLOAD_ITEM_START_PATTERN = Regex("""<div[^>]*class="[^"]*(?<!episode-)download-item[^"]*"[^>]*>""")
        private val EPISODE_FILE_TITLE_PATTERN = Regex("""<[^>]*class="[^"]*episode-file-title[^"]*"[^>]*>(.*?)</[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        private val FILE_TITLE_PATTERN = Regex("""<[^>]*class="[^"]*(?<!episode-)file-title[^"]*"[^>]*>(.*?)</[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        private val SIZE_BADGE = Regex("""<[^>]*class="[^"]*(?:badge-size|badge)[^"]*"[^>]*>(.*?)</[^>]*>""", RegexOption.DOT_MATCHES_ALL)
    }
}
