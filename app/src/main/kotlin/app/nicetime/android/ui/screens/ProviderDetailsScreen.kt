package app.nicetime.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import app.nicetime.android.core.model.*
import app.nicetime.android.ui.components.*
import app.nicetime.android.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailsScreen(
    titleName: String,
    sourceName: String,
    provider: ContentProvider,
    onBack: () -> Unit,
    onPlay: (ResolvedStream) -> Unit,
    onDownload: (ResolvedStream) -> Unit,
) {
    var details by remember { mutableStateOf<MediaDetails?>(null) }
    var selectedEpisode by remember { mutableStateOf<EpisodeRef?>(null) }
    var releases by remember { mutableStateOf<List<Release>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(titleName) {
        loading = true; error = null
        when (val result = provider.search(SearchRequest(titleName))) {
            is ProviderResult.Ok -> {
                val item = result.value.items.firstOrNull()
                if (item == null) { error = "Not found"; loading = false; return@LaunchedEffect }
                when (val det = provider.details(item.ref)) {
                    is ProviderResult.Ok -> {
                        details = det.value
                        selectedEpisode = if (det.value.ref.type == MediaType.MOVIE)
                            EpisodeRef(det.value.ref, det.value.ref.providerMediaId, null, null)
                        else det.value.seasons.firstOrNull()?.episodes?.firstOrNull()?.ref
                    }
                    is ProviderResult.Failure -> error = det.failure.message
                }
            }
            is ProviderResult.Failure -> error = result.failure.message
        }
        loading = false
    }

    LaunchedEffect(selectedEpisode) {
        releases = emptyList(); val chosen = selectedEpisode ?: return@LaunchedEffect
        loading = true; error = null
        when (val streams = provider.streams(chosen)) {
            is ProviderResult.Ok -> {
                releases = streams.value.filter { it.ref.episode == chosen }
                if (releases.isEmpty()) error = "No supported releases"
            }
            is ProviderResult.Failure -> error = streams.failure.message
        }
        loading = false
    }

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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Hero
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(SurfaceContainer)
            ) {
                val imageUrl = details?.backdropUrl ?: details?.posterUrl
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl).crossfade(true).build(),
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
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        titleName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    details?.let {
                        Text(
                            "$sourceName • ${it.year ?: ""}",
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            // Synopsis
            details?.synopsis?.let {
                Text(
                    it,
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            // Seasons / Episodes
            details?.seasons?.forEach { season ->
                Text(
                    "Season ${season.number ?: season.providerSeasonId}",
                    Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                season.episodes.filter { it.ref.media == details?.ref }.forEach { candidate ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable {
                                selectedEpisode = candidate.ref
                                releases = emptyList()
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (candidate.stillUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(candidate.stillUrl).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 100.dp, height = 56.dp)
                                        .background(SurfaceContainer, RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            RadioButton(
                                selected = selectedEpisode == candidate.ref,
                                onClick = {
                                    selectedEpisode = candidate.ref
                                    releases = emptyList()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Accent),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "E${candidate.episode ?: ""} ${candidate.title.orEmpty()}",
                                maxLines = 1,
                                color = TextPrimary,
                            )
                        }
                    }
                }
            }

            if (loading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = Accent,
                )
            }

            error?.let {
                Text(
                    it,
                    Modifier.padding(horizontal = 16.dp),
                    color = Error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Source releases
            if (releases.isNotEmpty()) {
                Text(
                    "Sources",
                    Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                releases.forEach { release ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(release.label, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text(
                                    listOfNotNull(
                                        release.resolutionHeight?.let { "${it}p" },
                                        release.audioLanguage,
                                        release.container,
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        when (val stream = provider.resolve(release.ref, ResolvePurpose.STREAM)) {
                                            is ProviderResult.Ok -> if (stream.value.release == release.ref) onPlay(stream.value)
                                            is ProviderResult.Failure -> error = stream.failure.message
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                shape = RoundedCornerShape(8.dp),
                            ) { Text("Play", color = Color.White) }
                            if (release.downloadable) {
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            when (val stream = provider.resolve(release.ref, ResolvePurpose.DOWNLOAD)) {
                                                is ProviderResult.Ok -> if (stream.value.release == release.ref) onDownload(stream.value)
                                                is ProviderResult.Failure -> error = stream.failure.message
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                ) { Text("Download") }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
