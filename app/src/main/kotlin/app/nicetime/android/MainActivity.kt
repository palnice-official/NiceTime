package app.nicetime.android

import android.os.Bundle
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.request.ImageRequest
import app.nicetime.android.core.data.*
import app.nicetime.android.core.media.DownloadRuntime
import app.nicetime.android.core.media.DownloadSnapshot
import app.nicetime.android.core.media.NiceTimePlaybackService
import app.nicetime.android.core.media.PlaybackCommands
import app.nicetime.android.core.model.*
import app.nicetime.android.ui.theme.NiceTimeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = SettingsRepository(applicationContext)
        val sourceRepository = SourceRepository(applicationContext)
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            val sources by sourceRepository.sources.collectAsState(initial = emptyList())
            NiceTimeTheme(darkTheme = settings.darkTheme) {
                NiceTimeApp(settings, settingsRepository, sources, sourceRepository)
            }
        }
    }
}

private enum class MainTab(val label: String, val symbol: String, val route: String) {
    HOME("Home", "⌂", "home"), EXPLORE("Explore", "⌕", "explore"),
    DOWNLOADS("Downloads", "↓", "downloads"), YOU("You", "●", "you")
}

internal data class DisplayTitle(
    val id: String, val title: String, val year: Int, val kind: String,
    val summary: String, val accent: Color, val streamUrl: String,
)

internal data class ProviderTitle(val summary: MediaSummary, val provider: ContentProvider, val sourceName: String)

private val demoTitles = listOf(
    DisplayTitle("bbb", "Big Buck Bunny", 2008, "Movie", "A cheerful open movie from the Blender Foundation.", Color(0xFF2A7F62), "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
    DisplayTitle("sintel", "Sintel", 2010, "Movie", "A young traveler searches for a dragon she once cared for.", Color(0xFF8B4B62), "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"),
    DisplayTitle("tears", "Tears of Steel", 2012, "Movie", "Scientists and warriors face a destructive robot invasion.", Color(0xFF3E5F8A), "https://devstreaming-cdn.apple.com/videos/streaming/examples/adv_dv_atmos/main.m3u8"),
    DisplayTitle("cosmos", "Cosmos Laundromat", 2015, "Movie", "A sheep meets a mysterious salesman with an unusual offer.", Color(0xFF8A663E), "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"),
)

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun NiceTimeApp(
    settings: AppSettings = AppSettings(),
    settingsRepository: SettingsRepository? = null,
    sources: List<SourceConfiguration> = emptyList(),
    sourceRepository: SourceRepository? = null,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val controllerFuture = remember { MediaController.Builder(context, SessionToken(context, ComponentName(context, NiceTimePlaybackService::class.java))).buildAsync() }
    var player by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(controllerFuture) {
        controllerFuture.addListener({ player = runCatching { controllerFuture.get() }.getOrNull() }, androidx.core.content.ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(controllerFuture) }
    }
    var nowPlaying by remember { mutableStateOf<DisplayTitle?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    val downloads by DownloadRuntime.observe(context).collectAsState(initial = emptyList())
    val historyRepo = remember { HistoryRepository(context) }
    val watchProgress by historyRepo.observeProgress().collectAsState(initial = emptyList())
    val diagnosticLog = remember { DiagnosticLog() }
    val scope = rememberCoroutineScope()
    val nativeProviders = remember(settings.movieBoxEnabled, settings.fourKHDHubEnabled) {
        buildList<Pair<ContentProvider, String>> {
            if (settings.movieBoxEnabled) add(NativeProviders.movieBox to "MovieBox")
            if (settings.fourKHDHubEnabled) add(NativeProviders.fourKHDHub to "4KHDHub")
        }
    }
    var actionError by remember { mutableStateOf<String?>(null) }
    var activeHistory by remember { mutableStateOf<WatchProgressEntity?>(null) }
    LaunchedEffect(settings.wifiOnlyDownloads) { DownloadRuntime.setWifiOnly(context, settings.wifiOnlyDownloads) }
    LaunchedEffect(player, activeHistory, settings.historyEnabled) {
        val identity = activeHistory ?: return@LaunchedEffect
        val controller = player ?: return@LaunchedEffect
        if (!settings.historyEnabled) return@LaunchedEffect
        while (true) {
            delay(5000)
            historyRepo.saveProgress(identity.copy(positionMs = controller.currentPosition.coerceAtLeast(0),
                durationMs = controller.duration.takeIf { it > 0 }, updatedAtEpochMs = System.currentTimeMillis()))
        }
    }
    actionError?.let { message ->
        AlertDialog(onDismissRequest = { actionError = null }, title = { Text("Unable to complete action") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { actionError = null }) { Text("OK") } })
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isOnMainScreen = currentRoute in MainTab.entries.map { it.route }

    Scaffold(
        topBar = {
            if (isOnMainScreen) {
                TopAppBar(title = {
                    Text(if (currentRoute == MainTab.HOME.route) "NiceTime" else MainTab.entries.find { it.route == currentRoute }?.label ?: "", fontWeight = FontWeight.Bold)
                })
            }
        },
        bottomBar = {
            if (isOnMainScreen) {
                Column {
                    nowPlaying?.let {
                        MiniPlayer(title = it, onExpand = { showPlayer = true },
                            onDismiss = { player?.stop(); nowPlaying = null; activeHistory = null })
                    }
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            NavigationBarItem(selected = currentRoute == tab.route,
                                onClick = { if (currentRoute != tab.route) { navController.navigate(tab.route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } } },
                                icon = { Text(tab.symbol) }, label = { Text(tab.label) })
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = MainTab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(MainTab.HOME.route) {
                HomeScreen(watchProgress, sources, nativeProviders,
                    onDemoSelect = { navController.navigate("demo_detail/${it.id}") },
                    onProviderSelect = { navController.navigate("provider_detail/${Uri.encode(it.summary.title)}/${Uri.encode(it.sourceName)}") })
            }
            composable(MainTab.EXPLORE.route) {
                ExploreScreen(sources, nativeProviders,
                    onDemoSelect = { navController.navigate("demo_detail/${it.id}") },
                    onProviderSelect = { navController.navigate("provider_detail/${Uri.encode(it.summary.title)}/${Uri.encode(it.sourceName)}") })
            }
            composable(MainTab.DOWNLOADS.route) {
                DownloadsScreen(downloads, context, onPlay = { snapshot ->
                    if (DownloadRuntime.isOfflineReady(context, snapshot.id)) {
                        player?.let { PlaybackCommands.playOffline(it, snapshot.id) }
                        activeHistory = null
                        nowPlaying = DisplayTitle(snapshot.id, snapshot.title, 0, "Offline", "", Color(0xFF315B75), "")
                        showPlayer = true
                    } else { actionError = "Download is incomplete. Remove and download again." }
                }, onError = { actionError = it })
            }
            composable(MainTab.YOU.route) {
                SettingsScreen(settings, settingsRepository, sources, sourceRepository, diagnosticLog, historyRepo)
            }
            composable("demo_detail/{titleId}", arguments = listOf(navArgument("titleId") { type = NavType.StringType })) { backStackEntry ->
                val titleId = backStackEntry.arguments?.getString("titleId") ?: return@composable
                val title = demoTitles.find { it.id == titleId } ?: return@composable
                DemoDetailsScreen(title = title, isDownloaded = downloads.any { it.id == title.id }, onBack = { navController.popBackStack() },
                    onPlay = {
                        player?.setMediaItem(MediaItem.fromUri(title.streamUrl))
                        val previous = watchProgress.find { it.providerInstanceId == "demo" && it.mediaId == title.id }
                        player?.seekTo(previous?.positionMs ?: 0); player?.prepare(); player?.playWhenReady = true
                        nowPlaying = title; showPlayer = true
                        activeHistory = WatchProgressEntity("demo", title.id, title.id, title.title, 0, null, System.currentTimeMillis())
                        if (settings.historyEnabled) scope.launch { historyRepo.saveProgress(WatchProgressEntity("demo", title.id, title.id, title.title, previous?.positionMs ?: 0L, null, System.currentTimeMillis())) }
                    },
                    onDownload = {
                        val ref = ReleaseRef(EpisodeRef(MediaRef(ProviderInstanceId(java.util.UUID.nameUUIDFromBytes("nicetime-demo".toByteArray())), title.id, MediaType.MOVIE), title.id, null, null), title.id)
                        val stream = ResolvedStream(ref, title.streamUrl, sourceLabel = "Demo")
                        if (!DownloadRuntime.hasStorage(context)) diagnosticLog.record("Download rejected: low storage for ${title.title}")
                        else { scope.launch { try { DownloadRuntime.enqueue(context, stream, title.title, settings.wifiOnlyDownloads) } catch (_: Exception) { actionError = "Download could not be queued." } }; diagnosticLog.record("Download queued: ${title.title}") }
                    })
            }
            composable("provider_detail/{titleName}/{sourceName}") { backStackEntry ->
                val titleName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("titleName") ?: "", Charsets.UTF_8)
                val sourceName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("sourceName") ?: "", Charsets.UTF_8)
                val provider = nativeProviders.find { it.second == sourceName }?.first
                if (provider != null) {
                    ProviderDetailsScreen(titleName = titleName, sourceName = sourceName, provider = provider, onBack = { navController.popBackStack() },
                        onPlay = { stream ->
                            val title = DisplayTitle(stream.release.episode.media.providerMediaId, titleName, 0, "Movie", "From $sourceName", Color(0xFF315B75), stream.uri)
                            val identity = stream.release.episode
                            val previous = watchProgress.find { it.providerInstanceId == identity.media.provider.toString() && it.mediaId == identity.media.providerMediaId && it.episodeId == identity.providerEpisodeId }
                            player?.let { PlaybackCommands.play(it, stream, previous?.positionMs ?: 0) }
                            activeHistory = WatchProgressEntity(identity.media.provider.toString(), identity.media.providerMediaId, identity.providerEpisodeId, titleName, previous?.positionMs ?: 0, null, System.currentTimeMillis())
                            nowPlaying = title; showPlayer = true
                            if (settings.historyEnabled) scope.launch { historyRepo.saveProgress(WatchProgressEntity(identity.media.provider.toString(), identity.media.providerMediaId, identity.providerEpisodeId, titleName, previous?.positionMs ?: 0L, null, System.currentTimeMillis())) }
                        },
                        onDownload = { stream -> scope.launch { try { DownloadRuntime.enqueue(context, stream, titleName, settings.wifiOnlyDownloads) } catch (_: Exception) { actionError = "Download could not be queued." } } })
                }
            }
        }
    }

    if (showPlayer && nowPlaying != null) {
        player?.let { PlayerDialog(player = it, title = nowPlaying!!.title, onDismiss = { showPlayer = false }) }
    }
}

// ============ FULL-SCREEN DETAILS ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DemoDetailsScreen(title: DisplayTitle, isDownloaded: Boolean, onBack: () -> Unit, onPlay: () -> Unit, onDownload: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title.title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(title.accent).clickable { onPlay() }) {
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(title.streamUrl.replace(Regex("\\.(m3u8|mpd|mp4)$"), ".jpg")).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))))
                Box(Modifier.align(Alignment.Center)) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape).padding(12.dp))
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${title.year} • ${title.kind}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPlay, modifier = Modifier.weight(1f).height(48.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Play") }
                    OutlinedButton(onClick = onDownload, enabled = !isDownloaded, modifier = Modifier.height(48.dp)) { Text(if (isDownloaded) "Downloaded" else "Download") }
                }
                HorizontalDivider()
                Text("Description", fontWeight = FontWeight.Medium)
                Text(title.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun ProviderDetailsScreen(titleName: String, sourceName: String, provider: ContentProvider, onBack: () -> Unit, onPlay: (ResolvedStream) -> Unit, onDownload: (ResolvedStream) -> Unit) {
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
                        selectedEpisode = if (det.value.ref.type == MediaType.MOVIE) EpisodeRef(det.value.ref, det.value.ref.providerMediaId, null, null)
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
            is ProviderResult.Ok -> { releases = streams.value.filter { it.ref.episode == chosen }; if (releases.isEmpty()) error = "No supported releases" }
            is ProviderResult.Failure -> error = streams.failure.message
        }
        loading = false
    }

    Scaffold(topBar = { TopAppBar(title = { Text(titleName) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(280.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                val imageUrl = details?.backdropUrl ?: details?.posterUrl
                if (imageUrl != null) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(titleName, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    details?.let { Text("$sourceName • ${it.year ?: ""}", color = Color.White.copy(alpha = 0.9f)) }
                }
            }
            details?.synopsis?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            details?.seasons?.forEach { season ->
                Text("Season ${season.number ?: season.providerSeasonId}", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Medium)
                season.episodes.filter { it.ref.media == details?.ref }.forEach { candidate ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { selectedEpisode = candidate.ref; releases = emptyList() }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (candidate.stillUrl != null) { AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(candidate.stillUrl).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 100.dp, height = 56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)); Spacer(Modifier.width(12.dp)) }
                            RadioButton(selected = selectedEpisode == candidate.ref, onClick = { selectedEpisode = candidate.ref; releases = emptyList() })
                            Spacer(Modifier.width(8.dp))
                            Text("E${candidate.episode ?: ""} ${candidate.title.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
            if (releases.isNotEmpty()) {
                Text("Sources", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Medium)
                releases.forEach { release ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(release.label, fontWeight = FontWeight.Medium)
                                Text(listOfNotNull(release.resolutionHeight?.let { "${it}p" }, release.audioLanguage, release.container).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = {
                                scope.launch {
                                    when (val stream = provider.resolve(release.ref, ResolvePurpose.STREAM)) {
                                        is ProviderResult.Ok -> if (stream.value.release == release.ref) onPlay(stream.value)
                                        is ProviderResult.Failure -> error = stream.failure.message
                                    }
                                }
                            }) { Text("Play") }
                            if (release.downloadable) {
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        when (val stream = provider.resolve(release.ref, ResolvePurpose.DOWNLOAD)) {
                                            is ProviderResult.Ok -> if (stream.value.release == release.ref) onDownload(stream.value)
                                            is ProviderResult.Failure -> error = stream.failure.message
                                        }
                                    }
                                }) { Text("Download") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============ MAIN SCREENS ============

@Composable
private fun HomeScreen(watchProgress: List<WatchProgressEntity>, sources: List<SourceConfiguration>, nativeProviders: List<Pair<ContentProvider, String>>, onDemoSelect: (DisplayTitle) -> Unit, onProviderSelect: (ProviderTitle) -> Unit) {
    var providerItems by remember { mutableStateOf<List<ProviderTitle>>(emptyList()) }
    var providerError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val providerFactory = remember { ProviderFactory() }
    var selectedProviders by remember { mutableStateOf<Set<String>>(emptySet()) }
    val availableProviders = remember(sources, nativeProviders) { buildList { nativeProviders.forEach { (_, name) -> add(name) }; sources.filter { it.enabled }.forEach { add(it.displayName) } }.distinct() }
    LaunchedEffect(availableProviders) { if (selectedProviders.isEmpty() && availableProviders.isNotEmpty()) selectedProviders = availableProviders.toSet() }
    LaunchedEffect(sources, nativeProviders, selectedProviders) {
        loading = true; providerError = null
        val providers = mutableListOf<Pair<ContentProvider, String>>()
        nativeProviders.filter { it.second in selectedProviders }.forEach { providers += it }
        sources.filter { it.enabled && it.displayName in selectedProviders }.forEach { source -> when (val created = providerFactory.create(source)) { is ProviderResult.Ok -> providers += created.value to source.displayName; is ProviderResult.Failure -> {} } }
        val registry = ProviderRegistry(providers.map { it.first }); val names = providers.associate { it.first.instanceId to it.second }
        val results = registry.catalogAll(); val errors = mutableListOf<String>()
        providerItems = results.flatMap { result -> when (val value = result.result) { is ProviderResult.Ok -> value.value.items.mapNotNull { item -> registry.provider(result.provider)?.let { ProviderTitle(item, it, names[result.provider] ?: "Addon") } }; is ProviderResult.Failure -> { errors += "${names[result.provider] ?: "Source"}: ${value.failure.message}"; emptyList() } } }.distinctBy { it.summary.ref }.take(20)
        providerError = errors.joinToString("\n").ifBlank { null }; loading = false
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { FeaturedTitle(demoTitles.first(), onDemoSelect) }
        if (watchProgress.isNotEmpty()) {
            item { SectionHeading("Continue watching") }
            items(watchProgress.take(5)) { progress ->
                ContinueWatchingCard(progress) { demoTitles.find { it.id == progress.mediaId }?.let(onDemoSelect) }
            }
        }
        if (availableProviders.isNotEmpty()) {
            item { SectionHeading("Browse by source") }
            item { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(availableProviders) { provider -> FilterChip(selected = provider in selectedProviders, onClick = { selectedProviders = if (provider in selectedProviders) selectedProviders - provider else selectedProviders + provider }, label = { Text(provider) }) } } }
        }
        if (providerItems.isNotEmpty()) {
            item { SectionHeading("Trending") }
            items(providerItems, key = { "${it.summary.ref.provider}:${it.summary.ref.providerMediaId}" }) { item ->
                ProviderTitleCard(item) { onProviderSelect(item) }
            }
        }
        if (providerError != null) item { Text(providerError!!, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        item { SectionHeading("Movies & Shows") }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(((demoTitles.size / 2 + demoTitles.size % 2) * 260).dp),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(demoTitles, key = { it.id }) { title ->
                    MovieCard(title) { onDemoSelect(title) }
                }
            }
        }
    }
}

@Composable
private fun ExploreScreen(sources: List<SourceConfiguration>, nativeProviders: List<Pair<ContentProvider, String>>, onDemoSelect: (DisplayTitle) -> Unit, onProviderSelect: (ProviderTitle) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query) { demoTitles.filter { it.title.contains(query.trim(), ignoreCase = true) } }
    val providerFactory = remember { ProviderFactory() }
    var sourceResults by remember { mutableStateOf<List<ProviderTitle>>(emptyList()) }
    var sourceErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selectedProviders by remember { mutableStateOf<Set<String>>(emptySet()) }
    val availableProviders = remember(sources, nativeProviders) { buildList { nativeProviders.forEach { (_, name) -> add(name) }; sources.filter { it.enabled }.forEach { add(it.displayName) } }.distinct() }
    LaunchedEffect(availableProviders) { if (selectedProviders.isEmpty() && availableProviders.isNotEmpty()) selectedProviders = availableProviders.toSet() }
    LaunchedEffect(query, sources, nativeProviders, selectedProviders) {
        sourceResults = emptyList(); sourceErrors = emptyList(); delay(300)
        if (query.isBlank()) { loading = false; return@LaunchedEffect }
        loading = true; val providers = mutableListOf<Pair<ContentProvider, String>>(); val failures = mutableListOf<String>()
        nativeProviders.filter { it.second in selectedProviders }.forEach { providers += it }
        sources.filter { it.enabled && it.displayName in selectedProviders }.forEach { source -> when (val created = providerFactory.create(source)) { is ProviderResult.Ok -> providers += created.value to source.displayName; is ProviderResult.Failure -> failures += "${source.displayName}: ${created.failure.message}" } }
        val registry = ProviderRegistry(providers.map { it.first }); val names = providers.associate { it.first.instanceId to it.second }
        registry.searchResults(SearchRequest(query.trim())).collect { result -> when (val value = result.result) { is ProviderResult.Ok -> sourceResults = (sourceResults + value.value.items.mapNotNull { item -> registry.provider(result.provider)?.let { ProviderTitle(item, it, names[result.provider] ?: "Addon") } }).distinctBy { it.summary.ref }; is ProviderResult.Failure -> failures += "${names[result.provider] ?: "Addon"}: ${value.failure.message}" }; sourceErrors = failures.toList() }
        loading = false
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search movies and series" }, singleLine = true, label = { Text("Search movies and series") }) }
        if (availableProviders.isNotEmpty()) item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(availableProviders) { provider -> FilterChip(selected = provider in selectedProviders, onClick = { selectedProviders = if (provider in selectedProviders) selectedProviders - provider else selectedProviders + provider }, label = { Text(provider) }) } } }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        items(sourceErrors) { error -> AssistChip(onClick = {}, label = { Text(error, maxLines = 2) }) }
        if (sourceResults.isNotEmpty()) { item { Text("Results from ${selectedProviders.size} source(s)", style = MaterialTheme.typography.titleMedium) }; items(sourceResults, key = { "${it.summary.ref.provider}:${it.summary.ref.providerMediaId}" }) { item -> ProviderTitleCard(item) { onProviderSelect(item) } } }
        if (filtered.isNotEmpty()) { item { Text("Open demo catalog", style = MaterialTheme.typography.titleMedium) }; items(filtered, key = { it.id }) { MovieCard(it, onDemoSelect) } }
        else if (sourceResults.isEmpty() && !loading) item { EmptyState("No results", "Try another title or add an addon source in You.") }
    }
}

@Composable
private fun DownloadsScreen(downloads: List<DownloadSnapshot>, context: android.content.Context, onPlay: (DownloadSnapshot) -> Unit, onError: (String) -> Unit) {
    if (downloads.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState("No downloads yet", "Downloaded videos will be available here for offline viewing.") }
    else LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Downloads", style = MaterialTheme.typography.titleLarge) }
        items(downloads, key = { it.id }) { snapshot -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(snapshot.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(snapshot.state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (snapshot.state == "Offline ready") TextButton(onClick = { onPlay(snapshot) }) { Text("Play") }
                if (snapshot.state in listOf("Paused", "Insufficient storage")) TextButton(onClick = { try { DownloadRuntime.resume(context, snapshot.id) } catch (_: Exception) { onError("Free storage before resuming") } }, modifier = Modifier.semantics { contentDescription = "Resume download: ${snapshot.title}" }) { Text("Resume") }
                if (snapshot.state in listOf("Downloading", "Queued", "Waiting for network")) TextButton(onClick = { DownloadRuntime.pause(context, snapshot.id) }, modifier = Modifier.semantics { contentDescription = "Pause download: ${snapshot.title}" }) { Text("Pause") }
                if (snapshot.state != "Deleting") TextButton(onClick = { DownloadRuntime.remove(context, snapshot.id) }, modifier = Modifier.semantics { contentDescription = "Remove download: ${snapshot.title}" }) { Text("Remove") }
            }
            if (snapshot.percent != null) { LinearProgressIndicator(progress = { snapshot.percent!! / 100f }, Modifier.fillMaxWidth()); Text("${snapshot.percent!!.toInt()}%", style = MaterialTheme.typography.bodySmall) }
            else if (snapshot.bytesDownloaded > 0) Text("${snapshot.bytesDownloaded / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, repository: SettingsRepository?, sources: List<SourceConfiguration>, sourceRepository: SourceRepository?, diagnosticLog: DiagnosticLog, historyRepo: HistoryRepository) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var showAddSource by rememberSaveable { mutableStateOf(false) }; var sourceUrl by rememberSaveable { mutableStateOf("") }; var sourceError by rememberSaveable { mutableStateOf<String?>(null) }; var installing by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Preferences", style = MaterialTheme.typography.titleLarge) }
        item { SettingSwitch("Dark theme", "Use a dark color scheme", settings.darkTheme) { repository?.let { store -> scope.launch { store.setDarkTheme(it) } } } }
        item { SettingSwitch("Wi-Fi downloads only", "Avoid mobile data for downloads", settings.wifiOnlyDownloads) { repository?.let { store -> scope.launch { store.setWifiOnlyDownloads(it) } } } }
        item { SettingSwitch("Watch history", "Save progress and continue watching", settings.historyEnabled) { repository?.let { store -> scope.launch { store.setHistoryEnabled(it) } } } }
        if (settings.historyEnabled) item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = { scope.launch { historyRepo.clearHistory() } }) { Text("Clear history") } } }
        item { Spacer(Modifier.height(16.dp)); Text("Sources", style = MaterialTheme.typography.titleLarge) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { SettingSwitch("MovieBox", "Candidate source; availability varies by region", settings.movieBoxEnabled) { enabled -> scope.launch { repository?.setMovieBoxEnabled(enabled) } } } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { SettingSwitch("4KHDHub", "Candidate source; availability varies by region", settings.fourKHDHubEnabled) { enabled -> scope.launch { repository?.setFourKHDHubEnabled(enabled) } } } } }
        item { Text("Addon sources", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)); Text("Add sources from any region.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(sources, key = { it.instanceId }) { source -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(source.displayName, fontWeight = FontWeight.Medium); Text(if (source.enabled) "Enabled" else "Disabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(source.enabled, onCheckedChange = { enabled -> sourceRepository?.let { store -> scope.launch { store.setEnabled(source.instanceId, enabled) } } }) }; TextButton(onClick = { sourceRepository?.let { store -> scope.launch { store.remove(source.instanceId) } } }) { Text("Remove") } } } }
        item { Button(onClick = { showAddSource = true }, enabled = sourceRepository != null) { Text("Add addon source") } }
        item { Spacer(Modifier.height(16.dp)); Text("Storage", style = MaterialTheme.typography.titleLarge); Text("${android.os.StatFs(context.filesDir.path).availableBytes / (1024 * 1024)} MiB free", style = MaterialTheme.typography.bodyMedium) }
        item { Spacer(Modifier.height(16.dp)); Text("Diagnostics", style = MaterialTheme.typography.titleLarge); val logText = diagnosticLog.export(); if (logText.isBlank()) Text("No events recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) else Text("Recent events (${logText.lines().size})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = { val redacted = redactSensitiveText(diagnosticLog.exportWithContext()); context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { putExtra(android.content.Intent.EXTRA_TEXT, redacted); type = "text/plain" }, "Export diagnostics")) }, enabled = logText.isNotBlank()) { Text("Export") }; TextButton(onClick = { diagnosticLog.clear() }, enabled = logText.isNotBlank()) { Text("Clear") } } }
    }
    if (showAddSource) AlertDialog(onDismissRequest = { if (!installing) showAddSource = false }, title = { Text("Add addon source") }, text = { Column { Text("Enter an HTTPS addon URL."); Spacer(Modifier.height(12.dp)); OutlinedTextField(sourceUrl, { sourceUrl = it; sourceError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Addon URL") }, singleLine = true, enabled = !installing, isError = sourceError != null); sourceError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } } },
        confirmButton = { Button(onClick = { installing = true; scope.launch { when (val result = AddonClient.create().fetchManifest(sourceUrl)) { is ProviderResult.Ok -> { sourceRepository?.save(SourceConfiguration(manifestUrl = AddonClient.normalizeManifestUrl(sourceUrl), displayName = result.value.name)); sourceUrl = ""; sourceError = null; showAddSource = false }; is ProviderResult.Failure -> sourceError = result.failure.message }; installing = false } }, enabled = sourceUrl.isNotBlank() && !installing) { Text("Add") } },
        dismissButton = { TextButton(onClick = { showAddSource = false }, enabled = !installing) { Text("Cancel") } })
}

// ============ SHARED COMPONENTS ============

@Composable private fun FeaturedTitle(title: DisplayTitle, onSelect: (DisplayTitle) -> Unit) {
    Box(Modifier.fillMaxWidth().height(250.dp).background(title.accent).semantics { contentDescription = "${title.title}, ${title.year}, ${title.kind}. ${title.summary}" }.clickable { onSelect(title) }) {
        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(title.streamUrl.replace(Regex("\\.(m3u8|mpd|mp4)$"), ".jpg")).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) { Text(title.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold); Text("${title.year} • ${title.kind}", color = Color.White); Spacer(Modifier.height(12.dp)); Button(onClick = { onSelect(title) }, modifier = Modifier.semantics { contentDescription = "View details for ${title.title}" }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Play") } }
    }
}
@Composable private fun SectionHeading(text: String) { Text(text, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp), style = MaterialTheme.typography.titleLarge) }
@Composable private fun MovieCard(title: DisplayTitle, onSelect: (DisplayTitle) -> Unit) {
    Card(
        Modifier.fillMaxWidth().semantics { contentDescription = "${title.title}, ${title.year}, ${title.kind}" }.clickable { onSelect(title) },
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(title.accent)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(title.streamUrl.replace(Regex("\\.(m3u8|mpd|mp4)$"), ".jpg")).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))))
                Box(Modifier.align(Alignment.Center)) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape).padding(8.dp))
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(title.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("${title.year} • ${title.kind}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun ProviderTitleCard(item: ProviderTitle, onSelect: () -> Unit) { Card(Modifier.fillMaxWidth().semantics { contentDescription = "${item.summary.title}, ${item.sourceName}" }.clickable(onClick = onSelect)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { if (item.summary.posterUrl != null) { AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(item.summary.posterUrl).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 80.dp, height = 110.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) }; Column(Modifier.padding(start = if (item.summary.posterUrl != null) 12.dp else 0.dp).weight(1f)) { Text(item.summary.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(listOfNotNull(item.summary.year?.toString(), if (item.summary.ref.type == MediaType.SERIES) "Series" else "Movie", item.sourceName).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
@Composable private fun MiniPlayer(title: DisplayTitle, onExpand: () -> Unit, onDismiss: () -> Unit) { Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).semantics { contentDescription = "Now playing: ${title.title}. Tap to expand." }.clickable(onClick = onExpand).padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(width = 64.dp, height = 40.dp).background(title.accent, RoundedCornerShape(4.dp))) { AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(title.streamUrl.replace(Regex("\\.(m3u8|mpd|mp4)$"), ".jpg")).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))) }; Spacer(Modifier.width(12.dp)); Text(title.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); TextButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "Stop playback" }) { Text("Close") } } }
@Composable private fun SettingSwitch(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { stateDescription = if (checked) "$title enabled" else "$title disabled" }, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, onChecked) } }
@Composable private fun EmptyState(title: String, body: String) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun ContinueWatchingCard(progress: WatchProgressEntity, onClick: () -> Unit) { val durMs = progress.durationMs; val fraction = if (durMs != null && durMs > 0) (progress.positionMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f; val remainingMs = (durMs ?: 0L) - progress.positionMs; val remainingMin = (remainingMs / 60_000).coerceAtLeast(0); Card(Modifier.fillMaxWidth().semantics { contentDescription = "${progress.title}, $remainingMin minutes remaining" }.clickable(onClick = onClick)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(progress.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(4.dp)); LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth()); Text("${remainingMin}m left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@Composable
private fun PlayerDialog(player: Player, title: String, onDismiss: () -> Unit) {
    var isFullscreen by remember { mutableStateOf(false) }; var playbackError by remember { mutableStateOf<String?>(null) }; var playing by remember { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) { val listener = object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }; override fun onPlayerError(error: androidx.media3.common.PlaybackException) { playbackError = "Playback failed. Retry or choose another source." } }; player.addListener(listener); onDispose { player.removeListener(listener) } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = !isFullscreen)) {
        Card(Modifier.fillMaxWidth()) { Column {
            AndroidView(factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } }, update = { it.player = player }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            playbackError?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }, modifier = Modifier.semantics { contentDescription = "Rewind 10 seconds" }) { Text("-10s") }
                TextButton(onClick = { player.playWhenReady = !player.playWhenReady }, modifier = Modifier.semantics { contentDescription = if (playing) "Pause" else "Play" }) { Text(if (playing) "Pause" else "Play") }
                TextButton(onClick = { player.seekTo((player.currentPosition + 10_000).coerceAtLeast(0)) }, modifier = Modifier.semantics { contentDescription = "Forward 10 seconds" }) { Text("+10s") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { val groups = player.currentTracks.groups; val videoGroup = groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }; if (videoGroup != null && videoGroup.length > 1) { val current = (0 until videoGroup.length).firstOrNull { videoGroup.isTrackSelected(it) } ?: 0; player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(androidx.media3.common.TrackSelectionOverride(videoGroup.mediaTrackGroup, listOf((current + 1) % videoGroup.length))).build() } }, modifier = Modifier.semantics { contentDescription = "Cycle video quality" }) { Text("Quality") }
                TextButton(onClick = { val groups = player.currentTracks.groups; val audioGroup = groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }; if (audioGroup != null && audioGroup.length > 1) { val current = (0 until audioGroup.length).firstOrNull { audioGroup.isTrackSelected(it) } ?: 0; player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(androidx.media3.common.TrackSelectionOverride(audioGroup.mediaTrackGroup, listOf((current + 1) % audioGroup.length))).build() } }, modifier = Modifier.semantics { contentDescription = "Cycle audio track" }) { Text("Audio") }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { isFullscreen = !isFullscreen }) { Text(if (isFullscreen) "Window" else "Expand") }; TextButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "Minimize player" }) { Text("Minimize") } }
        } }
    }
}

private object Uri {
    fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
    fun decode(value: String): String = java.net.URLDecoder.decode(value, "UTF-8")
}

@Preview(showBackground = true) @OptIn(UnstableApi::class) @Composable private fun NiceTimeAppPreview() { NiceTimeTheme { NiceTimeApp() } }
