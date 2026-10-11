package app.nicetime.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import app.nicetime.android.DisplayTitle
import app.nicetime.android.core.model.*
import app.nicetime.android.ui.components.*
import app.nicetime.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoDetailsScreen(
    title: DisplayTitle,
    isDownloaded: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Hero image
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(SurfaceContainer)
                    .clickable { onPlay() }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(title.streamUrl.replace(Regex("\\.(m3u8|mpd|mp4)$"), ".jpg"))
                        .crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Background)
                            )
                        )
                )
                Box(Modifier.align(Alignment.Center)) {
                    Icon(
                        Icons.Default.PlayArrow, null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(12.dp),
                    )
                }
            }

            // Info
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    title.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${title.year} • ${title.kind}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(16.dp))

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Play", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = onDownload,
                        enabled = !isDownloaded,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDownloaded) Success else Color.White,
                        ),
                    ) {
                        Icon(
                            if (isDownloaded) Icons.Default.Check else Icons.Default.Add,
                            null,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isDownloaded) "Downloaded" else "My List")
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = SurfaceContainer)
                Spacer(Modifier.height(16.dp))

                Text("Description", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    title.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )

                Spacer(Modifier.height(16.dp))

                // Action row
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = {}) { Icon(Icons.Default.Share, "Share", tint = TextSecondary) }
                        Text("Share", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onDownload) { Icon(Icons.Default.Add, "Download", tint = if (isDownloaded) Success else TextSecondary) }
                        Text("Download", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More", tint = TextSecondary) }
                        Text("More", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
