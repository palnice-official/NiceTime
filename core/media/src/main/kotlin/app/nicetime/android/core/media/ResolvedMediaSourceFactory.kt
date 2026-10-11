package app.nicetime.android.core.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import app.nicetime.android.core.model.ResolvedStream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.Proxy

/** Builds one isolated request stack per resolved stream. Sensitive headers only reach allowed hosts. */
@UnstableApi
object ResolvedMediaSourceFactory {
    fun create(context: Context, stream: ResolvedStream): MediaSource {
        return create(context, stream.uri, stream.mimeType, stream.requestHeaders, stream.allowedHosts, stream.release.providerReleaseId)
    }

    fun create(context: Context, uri: String, mimeType: String?, headers: Map<String, String>, allowedHosts: Set<String>, mediaId: String): MediaSource {
        val client = scopedMediaClient(uri, headers, allowedHosts)
        val upstream = OkHttpDataSource.Factory(client)
        val dataSource = DefaultDataSource.Factory(context.applicationContext, upstream)
        val item = MediaItem.Builder().setUri(uri).setMimeType(mimeType).setMediaId(mediaId).build()
        return DefaultMediaSourceFactory(dataSource).createMediaSource(item)
    }
}

/**
 * Turns a startup failure into something a person can act on. FR-17 requires the error to name
 * the source and offer a retry/alternate action rather than a generic "Playback failed".
 */
object PlaybackError {

    /**
     * Returns a reason the stream can never play, or null if it is worth starting.
     * Checked before the request is dispatched so failures are explained rather than silent.
     */
    fun describeInvalid(stream: app.nicetime.android.core.model.ResolvedStream): String? {
        if (stream.uri.isBlank()) return "The source returned no playable link. Choose another release."
        val origin = stream.uri.toHttpUrlOrNull()
            ?: return "The source returned a link this player cannot open. Choose another release."
        if (origin.scheme != "http" && origin.scheme != "https") {
            return "The source returned an unsupported link type. Choose another release."
        }
        val scope = stream.allowedHosts.map { it.lowercase() }.toSet()
        if (scope.isNotEmpty() && origin.host !in scope) {
            return "This source is outside its own request scope. Choose another release."
        }
        return null
    }

    fun describe(error: Throwable, uri: String): String {
        val host = runCatching { uri.toHttpUrlOrNull()?.host }.getOrNull() ?: "the source"
        val root = generateSequence(error) { it.cause }.last()
        val reason = when (root) {
            is javax.net.ssl.SSLException -> "Secure connection to $host was refused."
            is java.net.UnknownHostException -> "$host could not be reached. Check your connection."
            is java.net.ConnectException, is java.net.SocketTimeoutException -> "$host did not respond in time."
            is java.io.FileNotFoundException -> "$host returned not-found for this media."
            else -> root.message?.takeIf { it.isNotBlank() } ?: "The media could not be loaded."
        }
        val hint = when (root) {
            is javax.net.ssl.SSLException, is java.net.UnknownHostException,
            is java.net.ConnectException, is java.net.SocketTimeoutException,
            -> "Retry, or pick another source."
            else -> "Retry, or pick another release."
        }
        return "$reason $hint"
    }
}
