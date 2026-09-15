package app.nicetime.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import app.nicetime.android.core.media.DownloadRuntime
import app.nicetime.android.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.util.concurrent.atomic.AtomicInteger

/** Runs only against generated test media and deletes only its own random download. */
@UnstableApi
class MediaAcceptanceInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        try {
            runBlocking { withTimeout(90_000) { verifyOffline() } }
            report.putString("stream", "PASS: scoped authenticated download; persisted user pause; cache-only video frame after server shutdown; deletion\n")
            finish(Activity.RESULT_OK, report)
        } catch (error: Throwable) {
            report.putString("stream", "FAIL: " + generateSequence(error) { it.cause }.joinToString(" -> ") { "${it.javaClass.simpleName}: ${it.message}" } + "\n")
            finish(Activity.RESULT_CANCELED, report)
        }
    }

    private suspend fun verifyOffline() {
        val originalWifiOnly = app.nicetime.android.core.data.SettingsRepository(targetContext).settings.first().wifiOnlyDownloads
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val server = MockWebServer()
        val payload = context.assets.open("sample.mp4").use { it.readBytes() }
        val denied = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Cookie") != "fixture-only") {
                    denied.incrementAndGet()
                    return MockResponse().setResponseCode(403)
                }
                val start = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                return MockResponse().setResponseCode(if (start > 0) 206 else 200)
                    .addHeader("Content-Type", "video/mp4")
                    .addHeader("Accept-Ranges", "bytes")
                    .addHeader("ETag", "\"fixture-v1\"")
                    .apply { if (start > 0) addHeader("Content-Range", "bytes $start-${payload.lastIndex}/${payload.size}") }
                    .setBody(Buffer().write(payload, start, payload.size - start))
                    .throttleBody(16384, 100, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        }
        server.start()
        val ref = ReleaseRef(EpisodeRef(MediaRef(ProviderInstanceId.new(), "fixture", MediaType.MOVIE), "fixture", null, null), "mp4")
        val stream = ResolvedStream(ref, server.url("/sample.mp4").toString(), "video/mp4",
            mapOf("Cookie" to "fixture-only"), setOf("localhost"), sourceLabel = "Controlled fixture")
        val id = DownloadRuntime.stableId(ref)
        var serverStopped = false
        try {
            withContext(Dispatchers.Main) { DownloadRuntime.enqueue(targetContext, stream, "Acceptance fixture", false) }
            eventually { DownloadRuntime.manager(targetContext).downloadIndex.getDownload(id) != null }
            withContext(Dispatchers.Main) { DownloadRuntime.pause(targetContext, id) }
            eventually { DownloadRuntime.manager(targetContext).downloadIndex.getDownload(id)?.stopReason == 1 }
            delay(250)
            check(withContext(Dispatchers.Main) { DownloadRuntime.manager(targetContext).downloadIndex.getDownload(id)!!.stopReason } == 1)
            withContext(Dispatchers.Main) { DownloadRuntime.resume(targetContext, id) }
            eventually { DownloadRuntime.manager(targetContext).downloadIndex.getDownload(id)?.state == Download.STATE_COMPLETED }
            check(denied.get() == 0) { "Request context missing" }
            server.shutdown(); serverStopped = true
            val frame = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) {
                val texture = SurfaceTexture(0)
                val surface = Surface(texture)
                val player = ExoPlayer.Builder(targetContext).build()
                try {
                    player.setVideoSurface(surface)
                    player.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { frame.complete(Unit) }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) { frame.completeExceptionally(error) }
                    })
                    player.setMediaSource(DownloadRuntime.offlineSource(targetContext, id))
                    player.prepare(); player.play()
                    withTimeout(15_000) { frame.await() }
                } finally { player.release(); surface.release(); texture.release() }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { DownloadRuntime.remove(targetContext, id) }
            withContext(NonCancellable + Dispatchers.Main) { DownloadRuntime.setWifiOnly(targetContext, originalWifiOnly) }
            if (!serverStopped) server.shutdown()
            runOnMainSync { activity.finish() }
        }
        eventually { DownloadRuntime.manager(targetContext).downloadIndex.getDownload(id) == null }
    }

    private suspend fun eventually(predicate: () -> Boolean) {
        withTimeout(30_000) { while (!withContext(Dispatchers.Main) { predicate() }) delay(100) }
    }
}
