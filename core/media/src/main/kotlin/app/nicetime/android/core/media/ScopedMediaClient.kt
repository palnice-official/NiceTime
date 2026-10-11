package app.nicetime.android.core.media

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Headers that are part of a source's *credentials* and must therefore never leave the
 * hosts the stream explicitly declared. Everything else (a CDN edge, a redirect target,
 * an adaptive-segment host) is fetched without them.
 */
private val CREDENTIAL_HEADERS = setOf(
    "cookie",
    "authorization",
    "proxy-authorization",
    "x-api-key",
    "referer",
    "origin",
)

/**
 * Runs on every network hop, including redirects and adaptive child requests.
 *
 * Scoping policy:
 *  - The stream's **declared** hosts ([allowedHosts]) always receive the full request context.
 *  - Redirects and segment/CDN hosts *outside* that scope are still fetched, because real
 *    DASH/HLS manifests routinely live on a different host than their segments, and file hosts
 *    routinely 302 to a download CDN. Refusing those hops is what used to make every movie
 *    fail with "Playback failed". They are fetched **credential-free**, so the source's
 *    cookies/authorization/referer cannot leak to a host it never declared.
 *  - An HTTPS request is never allowed to downgrade to HTTP. Android blocks cleartext on
 *    API 28+ anyway, so the hop could not succeed; failing with a clear reason is better than
 *    feeding a 302 body to the decoder.
 */
internal fun scopedMediaClient(uri: String, headers: Map<String, String>, allowedHosts: Set<String>): OkHttpClient {
    val origin = uri.toHttpUrl()
    val scope = allowedHosts.map { it.lowercase() }.toSet().ifEmpty { setOf(origin.host) }
    if (origin.host !in scope) {
        throw IOException("Media origin ${origin.host} is outside this source's request scope")
    }
    val userAgent = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value

    return OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (origin.isHttps && !request.url.isHttps) {
                throw IOException("Source downgraded a secure media request to HTTP; refusing to send it")
            }
            val builder = request.newBuilder()
            if (request.url.host in scope) {
                headers.forEach { (name, value) -> builder.header(name, value) }
            } else {
                // OkHttp carries previously-applied headers into each follow-up request, so
                // strip rather than simply "not add".
                CREDENTIAL_HEADERS.forEach { builder.removeHeader(it) }
                if (userAgent != null) builder.header("User-Agent", userAgent)
            }
            chain.proceed(builder.build())
        }.build()
}
