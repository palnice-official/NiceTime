package app.nicetime.android.core.media

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.nicetime.android.core.model.ResolvedStream
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

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
                    val id = args.getString(PlaybackCommands.MEDIA_ID) ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    return try {
                        player.setMediaSource(DownloadRuntime.offlineSource(this@NiceTimePlaybackService, id))
                        player.prepare(); player.play()
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    } catch (_: Exception) { Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE)) }
                }
                if (customCommand.customAction != PlaybackCommands.PLAY_RESOLVED.customAction) return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                val uri = args.getString(PlaybackCommands.URI) ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                val keys = args.getStringArrayList(PlaybackCommands.HEADER_KEYS).orEmpty()
                val values = args.getStringArrayList(PlaybackCommands.HEADER_VALUES).orEmpty()
                val headers = keys.zip(values).toMap()
                val source = try {
                    ResolvedMediaSourceFactory.create(this@NiceTimePlaybackService, uri, args.getString(PlaybackCommands.MIME), headers, args.getStringArrayList(PlaybackCommands.HOSTS).orEmpty().toSet(), args.getString(PlaybackCommands.MEDIA_ID).orEmpty())
                } catch (_: Exception) { return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE)) }
                player.setMediaSource(source, args.getLong(PlaybackCommands.POSITION).coerceAtLeast(0)); player.prepare(); player.play()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).build()
    }

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

    fun playOffline(controller: androidx.media3.session.MediaController, id: String) =
        controller.sendCustomCommand(PLAY_OFFLINE, Bundle().apply { putString(MEDIA_ID, id) })

    fun play(controller: androidx.media3.session.MediaController, stream: ResolvedStream, positionMs: Long = 0) {
        val args = Bundle().apply {
            putString(URI, stream.uri); putString(MIME, stream.mimeType); putString(MEDIA_ID, stream.release.providerReleaseId)
            putLong(POSITION, positionMs)
            putStringArrayList(HEADER_KEYS, ArrayList(stream.requestHeaders.keys)); putStringArrayList(HEADER_VALUES, ArrayList(stream.requestHeaders.values))
            putStringArrayList(HOSTS, ArrayList(stream.allowedHosts))
        }
        controller.sendCustomCommand(PLAY_RESOLVED, args)
    }
}
