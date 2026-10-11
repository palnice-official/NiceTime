package app.nicetime.android.core.data

import app.nicetime.android.core.model.*
import java.net.Proxy
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

internal object MovieBoxCrypto {
    private const val SECRET = "76iRl07s0xSN9jqmEWAt79EBJZulIQIsV64FZr2O"
    private const val BODY_LIMIT = 102_400

    fun clientToken(timestampMs: Long): String =
        "$timestampMs,${md5(timestampMs.toString().reversed().toByteArray())}"

    fun canonical(method: String, accept: String?, contentType: String?, url: String, body: String?, timestampMs: Long): String {
        val uri = URI(url)
        val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }
            .sortedBy { it.substringBefore('=') }.joinToString("&")
        val path = uri.rawPath + if (query.isEmpty()) "" else "?$query"
        val bytes = body?.toByteArray(Charsets.UTF_8)
        val hash = bytes?.let { md5(it.copyOfRange(0, minOf(it.size, BODY_LIMIT))) }.orEmpty()
        return listOf(method.uppercase(), accept.orEmpty(), contentType.orEmpty(), bytes?.size?.toString().orEmpty(), timestampMs.toString(), hash, path).joinToString("\n")
    }

    fun signature(method: String, accept: String?, contentType: String?, url: String, body: String?, timestampMs: Long): String {
        val key = Base64.getDecoder().decode(SECRET.padEnd((SECRET.length + 3) / 4 * 4, '='))
        val mac = Mac.getInstance("HmacMD5").apply { init(SecretKeySpec(key, "HmacMD5")) }
        val signed = mac.doFinal(canonical(method, accept, contentType, url, body, timestampMs).toByteArray())
        return "$timestampMs|2|${Base64.getEncoder().encodeToString(signed)}"
    }

    private fun md5(bytes: ByteArray) = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
}

internal data class MovieBoxSession(val token: String, val userId: String?, val expiresAt: Long?, val createdAt: Long) {
    fun isValid(nowSeconds: Long): Boolean = token.isNotBlank() && (expiresAt?.let { nowSeconds + 60 < it } ?: (nowSeconds < createdAt + 7 * 24 * 3600))

    companion object {
        fun fromToken(token: String, explicitUserId: String?, nowSeconds: Long): MovieBoxSession {
            val claims = runCatching {
                val part = token.split('.').getOrNull(1) ?: return@runCatching null
                val decoded = Base64.getUrlDecoder().decode(part.padEnd((part.length + 3) / 4 * 4, '='))
                Json.parseToJsonElement(decoded.toString(Charsets.UTF_8)).jsonObject
            }.getOrNull()
            return MovieBoxSession(token, explicitUserId ?: claims?.stringValue("userId", "uid", "sub"), claims?.longValue("exp"), nowSeconds)
        }
    }
}

class MovieBoxProvider(
    override val instanceId: ProviderInstanceId = ProviderInstanceId.new(),
    private val http: OkHttpClient = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build(),
    private val hosts: List<String> = DEFAULT_HOSTS,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val deviceId: String = java.util.UUID.randomUUID().toString().replace("-", "").take(32),
    private val spoofedIp: String = "49.36.10.10",
) : ContentProvider {
    override val capabilities = ProviderCapabilities(catalog = true, search = true, pagination = true, streamResolution = true, downloadable = true, supportedTypes = setOf(MediaType.MOVIE, MediaType.SERIES))
    private val json = Json { ignoreUnknownKeys = true }
    private val loginMutex = Mutex()
    @Volatile private var session: MovieBoxSession? = null
    private val clientInfo: String = defaultClientInfo(deviceId)
    private var activeHost = 0
    private val resolved = ConcurrentHashMap<ReleaseRef, ResolvedStream>()

    override suspend fun catalog(request: CatalogRequest): ProviderResult<Page<MediaSummary>> {
        val result = call("GET", "/wefeed-mobile-bff/tab-operating?page=${request.page}&tabId=&version=")
        return result.map { root ->
            val data = root.obj("data") ?: root
            val items = mutableListOf<MediaSummary>()
            val seen = mutableSetOf<String>()
            val groups = data["items"]?.arrayOrNull() ?: root["items"]?.arrayOrNull() ?: root.arrayOrNull().orEmpty()
            for (group in groups) {
                val groupObj = group.objOrNull() ?: continue
                val subjects = mutableListOf<JsonElement>()
                groupObj["banner"]?.objOrNull()?.get("banners")?.arrayOrNull()?.forEach { b -> b.objOrNull()?.get("subject")?.let { subjects.add(it) } }
                groupObj["customData"]?.objOrNull()?.get("items")?.arrayOrNull()?.forEach { c -> c.objOrNull()?.get("subject")?.let { subjects.add(it) } }
                groupObj["subjects"]?.arrayOrNull()?.forEach { subjects.add(it) }
                for (s in subjects) {
                    val item = s.objOrNull() ?: continue
                    val id = item.stringValue("subjectId", "id") ?: continue
                    if (!seen.add(id)) continue
                    val type = if (item.longValue("subjectType", "stype") == 2L) MediaType.SERIES else MediaType.MOVIE
                    items.add(MediaSummary(MediaRef(instanceId, id, type), item.stringValue("title", "name") ?: id, year(item), item.obj("cover")?.stringValue("url") ?: item.stringValue("coverUrl", "poster", "pic")))
                }
            }
            Page(items, if (items.isNotEmpty()) (request.page + 1).toString() else null)
        }
    }

    override suspend fun search(request: SearchRequest): ProviderResult<Page<MediaSummary>> {
        if (request.query.isBlank()) return ProviderResult.Ok(Page(emptyList()))
        val payload = buildJsonObject { put("keyword", request.query); put("page", request.page); put("perPage", 15); put("subjectType", 0) }.toString()
        return call("POST", "/wefeed-mobile-bff/subject-api/search/v2", payload).map { root ->
            val results = root.obj("data")?.get("results") ?: root["results"]
            val subjects = results?.arrayOrNull()?.firstOrNull()?.objOrNull()?.get("subjects")?.arrayOrNull()
                ?: root.obj("data")?.get("list")?.arrayOrNull() ?: root["list"]?.arrayOrNull().orEmpty()
            val items = subjects.mapNotNull(::summary)
            Page(items, if (items.size >= 15) (request.page + 1).toString() else null)
        }
    }

    override suspend fun details(ref: MediaRef): ProviderResult<MediaDetails> {
        if (ref.provider != instanceId) return ProviderResult.Failure(ProviderFailure.NotFound())
        return call("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=${ref.providerMediaId}").map { root ->
            val subject = root.obj("data")?.obj("subject") ?: root.obj("subject") ?: root
            val nativeId = subject.stringValue("subjectId", "id") ?: ref.providerMediaId
            val actualRef = MediaRef(instanceId, nativeId, if (subject.longValue("subjectType", "stype") == 2L) MediaType.SERIES else ref.type)
            val seasons = subject.obj("seasons")?.get("seasons")?.arrayOrNull().orEmpty().map { seasonValue ->
                val s = seasonValue.objOrNull() ?: JsonObject(emptyMap())
                val number = s.longValue("se")?.toInt()
                val episodeNumbers = s["episodeNumbers"]?.arrayOrNull()?.mapNotNull { it.primitiveOrNull()?.intOrNull }
                    ?: (1..(s.longValue("maxEp")?.toInt() ?: 0)).toList()
                Season("season-${number ?: 0}", number, episodeNumbers.map { ep -> EpisodeSummary(EpisodeRef(actualRef, "${nativeId}:$number:$ep", number, ep), null, number, ep) })
            }
            MediaDetails(actualRef, subject.stringValue("title") ?: nativeId, year(subject), subject.stringValue("description", "intro"), subject.obj("cover")?.stringValue("url") ?: subject.stringValue("coverUrl"), seasons = seasons, runtimeMinutes = subject.longValue("duration")?.takeIf { it > 0 }?.div(60)?.toInt())
        }
    }

    override suspend fun streams(ref: EpisodeRef): ProviderResult<List<Release>> {
        if (ref.media.provider != instanceId) return ProviderResult.Failure(ProviderFailure.NotFound())
        val episodeQuery = if (ref.media.type == MediaType.SERIES) "&se=${ref.season ?: 1}&ep=${ref.episode ?: 1}" else ""
        return call("GET", "/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=${ref.media.providerMediaId}$episodeQuery").map { root ->
            val data = root.obj("data") ?: root
            data["streams"]?.arrayOrNull().orEmpty().mapNotNull { streamValue -> release(ref, data, streamValue.objOrNull() ?: return@mapNotNull null) }
        }
    }

    override suspend fun resolve(ref: ReleaseRef, purpose: ResolvePurpose): ProviderResult<ResolvedStream> =
        resolved[ref]?.let { ProviderResult.Ok(it) } ?: ProviderResult.Failure(ProviderFailure.NotFound("Release must be refreshed"))

    private fun release(episode: EpisodeRef, data: JsonObject, stream: JsonObject): Release? {
        val cookie = stream.stringValue("signCookie").orEmpty()
        val uri = policyManifest(cookie) ?: stream.stringValue("url")?.takeIf { validDirect(it) && !isNotice(it) } ?: return null
        val resolutions = (stream.stringValue("resolutions") ?: data.stringValue("displayResolutions") ?: "1080").split(',').mapNotNull { it.trim().toIntOrNull() }
        val isDash = uri.endsWith(".mpd", true) || stream.stringValue("format").equals("DASH", true)
        val height = resolutions.maxOrNull()
        val id = stream.stringValue("id") ?: sha256(uri).take(24)
        val ref = ReleaseRef(episode, id)
        val headers = buildMap { put("Referer", "https://sportslive.wine"); put("User-Agent", userAgent); if (cookie.isNotBlank()) put("Cookie", cookie.trimEnd(';').split(';').joinToString("; ") { it.trim() }) }
        resolved[ref] = ResolvedStream(ref, uri, if (isDash) "application/dash+xml" else null, headers, setOfNotNull(runCatching { URI(uri).host }.getOrNull()), sourceLabel = "MovieBox")
        val codec = stream.stringValue("codecName", "codec")
        return Release(ref, listOfNotNull(if (isDash || resolutions.size > 1) "Multi-Res" else height?.let { "${it}p" }, codec).joinToString(" ").ifBlank { "MovieBox" }, if (isDash || resolutions.size > 1) null else height, container = stream.stringValue("format"), sizeBytes = stream.longValue("size"), downloadable = true)
    }

    private suspend fun ensureSession(): ProviderResult<String> = loginMutex.withLock {
        session?.takeIf { it.isValid(nowMs() / 1000) }?.let { return ProviderResult.Ok(it.token) }
        val body = "{}"
        when (val login = rawCall("POST", "/wefeed-mobile-bff/user-api/visitor-login", body, null)) {
            is ProviderResult.Failure -> login
            is ProviderResult.Ok -> {
                val data = login.value.obj("data") ?: login.value
                val token = data.stringValue("token")
                    ?: return ProviderResult.Failure(ProviderFailure.AuthExpired("MovieBox login returned no token"))
                ProviderResult.Ok(MovieBoxSession.fromToken(token, data.stringValue("uid", "userId"), nowMs() / 1000).also { session = it }.token)
            }
        }
    }

    private suspend fun call(method: String, path: String, body: String? = null): ProviderResult<JsonObject> {
        val token = when (val auth = ensureSession()) { is ProviderResult.Ok -> auth.value; is ProviderResult.Failure -> return auth }
        var result = rawCall(method, path, body, token)
        if (result is ProviderResult.Failure && result.failure is ProviderFailure.AuthExpired) {
            session = null
            val fresh = ensureSession()
            if (fresh is ProviderResult.Ok) result = rawCall(method, path, body, fresh.value)
        }
        return result
    }

    private suspend fun rawCall(method: String, path: String, body: String?, token: String?): ProviderResult<JsonObject> = withContext(Dispatchers.IO) {
        var last: ProviderFailure = ProviderFailure.SourceUnavailable("All MovieBox hosts failed")
        var authFailures = 0
        for (offset in hosts.indices) {
            val index = (activeHost + offset) % hosts.size
            val url = hosts[index].trimEnd('/') + path
            try {
                val timestamp = nowMs()
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = method
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", JSON)
                conn.setRequestProperty("Content-Type", JSON)
                conn.setRequestProperty("X-Client-Token", MovieBoxCrypto.clientToken(timestamp))
                conn.setRequestProperty("X-TR-Signature", MovieBoxCrypto.signature(method, JSON, JSON, url, body, timestamp))
                conn.setRequestProperty("X-Client-Info", clientInfo)
                conn.setRequestProperty("X-Client-Status", "0")
                conn.setRequestProperty("X-Forwarded-For", spoofedIp)
                token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                if (method == "POST") {
                    conn.doOutput = true
                    conn.outputStream.use { it.write((body ?: "{}").toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                if (code == 401 || code == 403) {
                    // A single geo-blocked or rate-limited host must not condemn the other six.
                    // Record it and keep trying; only report expired credentials if every host refused.
                    conn.errorStream?.use { it.readAtMost(1024) }
                    conn.disconnect(); authFailures++; last = ProviderFailure.AuthExpired(); continue
                }
                if (code == 429) { last = ProviderFailure.RateLimited(conn.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000)); conn.disconnect(); continue }
                if (code !in 200..299) {
                    conn.errorStream?.use { it.readAtMost(1024) }
                    last = ProviderFailure.SourceUnavailable("MovieBox returned HTTP $code"); conn.disconnect(); continue
                }
                val bytes = conn.inputStream.use { it.readAtMost(MAX_RESPONSE + 1) }
                conn.disconnect()
                if (bytes.size > MAX_RESPONSE) { last = ProviderFailure.ParseFailure("Response is too large"); continue }
                val parsed = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                activeHost = index
                return@withContext ProviderResult.Ok(parsed)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: java.io.IOException) { last = ProviderFailure.NetworkUnavailable() }
            catch (e: Exception) { last = ProviderFailure.ParseFailure() }
        }
        ProviderResult.Failure(if (authFailures >= hosts.size) ProviderFailure.AuthExpired() else last)
    }

    private fun summary(value: JsonElement): MediaSummary? {
        val item = value.objOrNull() ?: return null
        val id = item.stringValue("subjectId", "id") ?: return null
        val type = if (item.longValue("subjectType", "stype") == 2L) MediaType.SERIES else MediaType.MOVIE
        return MediaSummary(MediaRef(instanceId, id, type), item.stringValue("title", "name") ?: id, year(item), item.obj("cover")?.stringValue("url") ?: item.stringValue("coverUrl", "poster", "pic"))
    }

    private fun policyManifest(cookie: String): String? = runCatching {
        val encoded = cookie.split(';').firstNotNullOfOrNull { it.trim().removePrefix("CloudFront-Policy=").takeIf { value -> it.trim().startsWith("CloudFront-Policy=") && value.isNotBlank() } } ?: return null
        val normalized = encoded.map { when (it) { '-' -> '+'; '_' -> '='; '~' -> '/'; else -> it } }.joinToString("").let { it.padEnd((it.length + 3) / 4 * 4, '=') }
        val policy = json.parseToJsonElement(Base64.getDecoder().decode(normalized).toString(Charsets.UTF_8)).jsonObject
        val resource = policy["Statement"]!!.jsonArray.first().jsonObject["Resource"]!!.jsonPrimitive.content.trimEnd('*', '/')
        "$resource/index.mpd".takeIf(::validDirect)
    }.getOrNull()

    private fun validDirect(url: String) = runCatching { URI(url) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrBlank() } == true
    private fun isNotice(url: String) = url.lowercase().let { "notice" in it || "1c7de0bd3393702d9191801f15f88f8d" in it || "9a0461bc39da389663bf3dbb17091d3f" in it || ("macdn.aoneroom.com" in it && "/other/" in it) }
    private fun year(item: JsonObject) = Regex("(19|20)\\d{2}").find(item.stringValue("releaseDate", "year", "releaseInfo").orEmpty())?.value?.toIntOrNull()
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val JSON = "application/json"
        private const val MAX_RESPONSE = 5 * 1_048_576
        val DEFAULT_HOSTS = listOf("https://api6.aoneroom.com", "https://api5.aoneroom.com", "https://api4.aoneroom.com", "https://api4sg.aoneroom.com", "https://api3.aoneroom.com", "https://api6sg.aoneroom.com", "https://api.inmoviebox.com")
        private const val DEFAULT_USER_AGENT = "com.community.oneroom/50020121 (Linux; U; Android 13; en_US; 22101316G; Cronet/135.0.7012.3)"
        private fun defaultClientInfo(deviceId: String) = "{\"package_name\":\"com.community.oneroom\",\"version_name\":\"4.0.01.0813.03\",\"version_code\":50020121,\"os\":\"android\",\"os_version\":\"13\",\"device_id\":\"$deviceId\",\"install_ch\":\"ps\",\"install_store\":\"ps\",\"gaid\":\"\",\"brand\":\"Redmi\",\"model\":\"22101316G\",\"system_language\":\"en\",\"net\":\"wifi\",\"region\":\"US\",\"timezone\":\"Asia/Kolkata\",\"sp_code\":\"40401\",\"X-Play-Mode\":\"2\"}"
    }
}

private fun JsonObject.obj(key: String) = get(key)?.objOrNull()
private fun JsonElement.objOrNull() = this as? JsonObject
private fun JsonElement.arrayOrNull() = this as? JsonArray
private fun JsonElement.primitiveOrNull() = this as? JsonPrimitive
private fun JsonObject.stringValue(vararg keys: String): String? = keys.firstNotNullOfOrNull { (get(it) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
private fun JsonObject.longValue(vararg keys: String): Long? = keys.firstNotNullOfOrNull { (get(it) as? JsonPrimitive)?.longOrNull ?: (get(it) as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }
