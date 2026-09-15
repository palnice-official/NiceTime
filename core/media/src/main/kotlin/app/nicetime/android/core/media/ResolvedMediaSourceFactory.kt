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
