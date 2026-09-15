package app.nicetime.android.core.media

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

/** Runs on every network hop, including redirects and adaptive child requests. */
internal fun scopedMediaClient(uri: String, headers: Map<String, String>, allowedHosts: Set<String>): OkHttpClient {
    val origin = uri.toHttpUrl()
    val allowed = allowedHosts.map { it.lowercase() }.toSet().ifEmpty { setOf(origin.host) }
    require(origin.host in allowed) { "Media origin is outside the source request scope" }
    return OkHttpClient.Builder()
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (request.url.host !in allowed || (origin.isHttps && !request.url.isHttps)) {
                throw IOException("Source redirected outside its permitted media hosts")
            }
            chain.proceed(request.newBuilder().apply {
                headers.forEach { (name, value) -> header(name, value) }
            }.build())
        }.build()
}
