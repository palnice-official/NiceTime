package app.nicetime.android.core.media

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.nicetime.android.core.model.ResolvedStream
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Owns the process-wide player/session used by UI, notification, lock-screen and PiP controls. */
@UnstableApi
class NiceTimePlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            playWhenReady = true
        }
        session = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: ControllerInfo): MediaSession.ConnectionResult {
                if (controller.packageName != packageName) return MediaSession.ConnectionResult.reject()
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(PlaybackCommands.PLAY_RESOLVED).add(PlaybackCommands.PLAY_OFFLINE).build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
            }

            override fun onCustomCommand(session: MediaSession, controller: ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (customCommand.customAction == PlaybackCommands.PLAY_OFFLINE.customAction) {
                    val id = args.getString(PlaybackCommands.MEDIA_ID) ?: return refused()
                    return try {
                        player.setMediaSource(DownloadRuntime.offlineSource(this@NiceTimePlaybackService, id))
                        player.prepare(); player.play()
                        accepted()
                    } catch (_: Exception) {
                        // Refuse rather than start a partially-available download.
                        refused()
                    }
                }
                if (customCommand.customAction != PlaybackCommands.PLAY_RESOLVED.customAction) {
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
                val uri = args.getString(PlaybackCommands.URI) ?: return refused()
                val keys = args.getStringArrayList(PlaybackCommands.HEADER_KEYS).orEmpty()
                val values = args.getStringArrayList(PlaybackCommands.HEADER_VALUES).orEmpty()
                val headers = keys.zip(values).toMap()
                val source = try {
                    ResolvedMediaSourceFactory.create(
                        this@NiceTimePlaybackService, uri, args.getString(PlaybackCommands.MIME), headers,
                        args.getStringArrayList(PlaybackCommands.HOSTS).orEmpty().toSet(),
                        args.getString(PlaybackCommands.MEDIA_ID).orEmpty(),
                    )
                } catch (_: Exception) {
                    return refused()
                }
                player.setMediaSource(source, args.getLong(PlaybackCommands.POSITION).coerceAtLeast(0)); player.prepare(); player.play()
                return accepted()
            }
        }).build()
    }

    private fun accepted() = Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))

    /** The only refusal the session protocol can carry is an error code, so the UI validates first. */
    private fun refused() = Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

object PlaybackCommands {
    val PLAY_RESOLVED = SessionCommand("app.nicetime.PLAY_RESOLVED", Bundle.EMPTY)
    val PLAY_OFFLINE = SessionCommand("app.nicetime.PLAY_OFFLINE", Bundle.EMPTY)
    internal const val POSITION = "position"
    internal const val URI = "uri"
    internal const val MIME = "mime"
    internal const val MEDIA_ID = "media_id"
    internal const val HEADER_KEYS = "header_keys"
    internal const val HEADER_VALUES = "header_values"
    internal const val HOSTS = "hosts"

    /**
     * Starts a completed download. Returns null on success, or a user-facing reason to refuse.
     * Validation happens here rather than in the service so the caller gets an actionable
     * message: a service-side refusal is only ever an opaque error code.
     */
    suspend fun playOffline(controller: MediaController, id: String): String? = await(
        controller.sendCustomCommand(PLAY_OFFLINE, Bundle().apply { putString(MEDIA_ID, id) }),
        refused = "This download is not ready to play. Remove it and download again.",
    )

    /** Starts a resolved stream. Returns null on success, or a user-facing reason to refuse. */
    suspend fun play(controller: MediaController, stream: ResolvedStream, positionMs: Long = 0): String? {
        val invalid = PlaybackError.describeInvalid(stream)
        if (invalid != null) return invalid
        val args = Bundle().apply {
            putString(URI, stream.uri); putString(MIME, stream.mimeType); putString(MEDIA_ID, stream.release.providerReleaseId)
            putLong(POSITION, positionMs)
            putStringArrayList(HEADER_KEYS, ArrayList(stream.requestHeaders.keys)); putStringArrayList(HEADER_VALUES, ArrayList(stream.requestHeaders.values))
            putStringArrayList(HOSTS, ArrayList(stream.allowedHosts))
        }
        return await(
            controller.sendCustomCommand(PLAY_RESOLVED, args),
            refused = "The source refused to start playback. Retry or choose another release.",
        )
    }

    /**
     * Waits for the service to accept or refuse a command. Without this the UI could not tell
     * "refused" apart from "playing", which is why failed starts used to look like a silent no-op.
     */
    private suspend fun await(future: ListenableFuture<SessionResult>, refused: String): String? =
        suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    val accepted = runCatching { future.get() }.getOrNull()
                    continuation.resume(
                        when {
                            accepted == null -> "The playback service is unavailable. Try again."
                            accepted.resultCode >= 0 -> null
                            else -> refused
                        },
                    )
                },
                Executor { it.run() },
            )
            continuation.invokeOnCancellation { future.cancel(false) }
        }
}
