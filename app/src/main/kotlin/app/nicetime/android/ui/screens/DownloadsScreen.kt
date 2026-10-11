package app.nicetime.android.ui.screens

import android.content.Context
import android.os.StatFs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nicetime.android.core.data.WatchProgressEntity
import app.nicetime.android.core.media.DownloadRuntime
import app.nicetime.android.core.media.DownloadSnapshot
import app.nicetime.android.ui.components.*
import app.nicetime.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloads: List<DownloadSnapshot>,
    context: Context,
    onPlay: (DownloadSnapshot) -> Unit,
    onError: (String) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Movies", "Series")

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Downloads", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Accent,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            color = if (selectedTab == index) Accent else TextSecondary,
                        )
                    },
                )
            }
        }

        if (downloads.isEmpty()) {
            EmptyState(
                "No downloads yet",
                "Downloaded videos will be available here for offline viewing.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(downloads, key = { it.id }) { snapshot ->
                    val progressPercent = snapshot.percent?.let { it / 100f }
                    DownloadItemCard(
                        title = snapshot.title,
                        subtitle = snapshot.state,
                        progressPercent = progressPercent,
                        state = if (snapshot.state == "Offline ready") "Downloaded" else "",
                        posterUrl = null,
                        onPlay = if (snapshot.state == "Offline ready") {
                            { onPlay(snapshot) }
                        } else null,
                        onResume = if (snapshot.state in listOf("Paused", "Insufficient storage")) {
                            {
                                try {
                                    DownloadRuntime.resume(context, snapshot.id)
                                } catch (_: Exception) {
                                    onError("Free storage before resuming")
                                }
                            }
                        } else null,
                        onPause = if (snapshot.state in listOf("Downloading", "Queued", "Waiting for network")) {
                            { DownloadRuntime.pause(context, snapshot.id) }
                        } else null,
                        onRemove = { DownloadRuntime.remove(context, snapshot.id) },
                    )
                }

                // Storage info
                item {
                    Spacer(Modifier.height(8.dp))
                    val totalBytes = StatFs(context.filesDir.path).totalBytes
                    val freeBytes = StatFs(context.filesDir.path).availableBytes
                    val usedGB = String.format("%.1f", (totalBytes - freeBytes) / (1024.0 * 1024 * 1024))
                    val totalGB = String.format("%.0f", totalBytes / (1024.0 * 1024 * 1024))
                    val usedFraction = ((totalBytes - freeBytes).toFloat() / totalBytes).coerceIn(0f, 1f)

                    Column {
                        Text("Storage used", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { usedFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Accent,
                            trackColor = ProgressTrack,
                        )
                        Text(
                            "$usedGB GB / $totalGB GB",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
