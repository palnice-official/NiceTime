package app.nicetime.android.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import app.nicetime.android.ui.theme.*

@OptIn(UnstableApi::class)
@Composable
fun PlayerDialog(
    player: Player,
    title: String,
    onDismiss: () -> Unit,
) {
    var isFullscreen by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    val context = LocalContext.current
    val activity = remember { context as? Activity }

    // Lock/unlock orientation based on fullscreen state
    DisposableEffect(isFullscreen) {
        activity?.let {
            val original = it.requestedOrientation
            it.requestedOrientation = if (isFullscreen)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            onDispose { it.requestedOrientation = original }
        } ?: onDispose {}
    }

    // Handle back press in fullscreen — exit fullscreen first
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = "Playback failed. Retry or choose another source."
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = if (isFullscreen) Arrangement.Center else Arrangement.Top,
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFullscreen) Modifier.fillMaxHeight()
                            else Modifier.aspectRatio(16f / 9f)
                        ),
                )

                // Controls — only show in windowed mode
                if (!isFullscreen) {
                    Surface(color = Surface, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    title,
                                    Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = TextPrimary,
                                )
                            }
                            playbackError?.let {
                                Text(
                                    it,
                                    Modifier.padding(12.dp),
                                    color = Error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) },
                                    modifier = Modifier.semantics { contentDescription = "Rewind 10 seconds" },
                                ) { Text("-10s", color = Accent) }
                                IconButton(
                                    onClick = { player.playWhenReady = !player.playWhenReady },
                                    modifier = Modifier.semantics { contentDescription = if (playing) "Pause" else "Play" },
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        null,
                                        tint = Accent,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                                TextButton(
                                    onClick = { player.seekTo((player.currentPosition + 10_000).coerceAtLeast(0)) },
                                    modifier = Modifier.semantics { contentDescription = "Forward 10 seconds" },
                                ) { Text("+10s", color = Accent) }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = {
                                        val groups = player.currentTracks.groups
                                        val videoGroup = groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
                                        if (videoGroup != null && videoGroup.length > 1) {
                                            val current = (0 until videoGroup.length).firstOrNull { videoGroup.isTrackSelected(it) } ?: 0
                                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                                .setOverrideForType(
                                                    androidx.media3.common.TrackSelectionOverride(
                                                        videoGroup.mediaTrackGroup,
                                                        listOf((current + 1) % videoGroup.length),
                                                    )
                                                ).build()
                                        }
                                    },
                                    modifier = Modifier.semantics { contentDescription = "Cycle video quality" },
                                ) { Text("Quality", color = TextSecondary) }
                                TextButton(
                                    onClick = {
                                        val groups = player.currentTracks.groups
                                        val audioGroup = groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
                                        if (audioGroup != null && audioGroup.length > 1) {
                                            val current = (0 until audioGroup.length).firstOrNull { audioGroup.isTrackSelected(it) } ?: 0
                                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                                .setOverrideForType(
                                                    androidx.media3.common.TrackSelectionOverride(
                                                        audioGroup.mediaTrackGroup,
                                                        listOf((current + 1) % audioGroup.length),
                                                    )
                                                ).build()
                                        }
                                    },
                                    modifier = Modifier.semantics { contentDescription = "Cycle audio track" },
                                ) { Text("Audio", color = TextSecondary) }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { isFullscreen = true }) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Fullscreen", color = TextSecondary)
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.semantics { contentDescription = "Close player" },
                                ) { Text("Close", color = TextSecondary) }
                            }
                        }
                    }
                }
            }

            // Fullscreen controls overlay — semi-transparent top bar with back button
            if (isFullscreen) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            Modifier.weight(1f),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { isFullscreen = false }) {
                            Icon(
                                Icons.Default.PlayArrow,
                                "Exit fullscreen",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }

            // Error in fullscreen
            if (isFullscreen && playbackError != null) {
                Text(
                    playbackError!!,
                    Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    color = Error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private object Brush {
    fun verticalGradient(
        colors: List<Color>,
        startY: Float = 0f,
        endY: Float = Float.POSITIVE_INFINITY,
    ) = androidx.compose.ui.graphics.Brush.verticalGradient(colors, startY, endY)
}
