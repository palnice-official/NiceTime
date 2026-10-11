package app.nicetime.android

import android.os.Bundle
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.nicetime.android.core.data.*
import app.nicetime.android.core.media.DownloadRuntime
import app.nicetime.android.core.media.DownloadSnapshot
import app.nicetime.android.core.media.NiceTimePlaybackService
import app.nicetime.android.core.media.PlaybackCommands
import app.nicetime.android.core.model.*
import app.nicetime.android.ui.components.*
import app.nicetime.android.ui.screens.*
import app.nicetime.android.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

private enum class MainTab(val label: String, val icon: ImageVector, val route: String) {
    HOME("Home", Icons.Default.Home, "home"),
    SEARCH("Search", Icons.Default.Search, "search"),
    DOWNLOADS("Downloads", Icons.Default.Add, "downloads"),
    YOU("You", Icons.Default.Person, "you"),
}

data class DisplayTitle(
    val id: String, val title: String, val year: Int, val kind: String,
    val summary: String, val accent: Color, val streamUrl: String,
)

data class ProviderTitle(val summary: MediaSummary, val provider: ContentProvider, val sourceName: String)

internal val demoTitles = listOf(
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
    val controllerFuture = remember {
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, NiceTimePlaybackService::class.java)),
        ).buildAsync()
    }
    var player by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            { player = runCatching { controllerFuture.get() }.getOrNull() },
            androidx.core.content.ContextCompat.getMainExecutor(context),
        )
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

    LaunchedEffect(settings.wifiOnlyDownloads) {
        DownloadRuntime.setWifiOnly(context, settings.wifiOnlyDownloads)
    }
    LaunchedEffect(player, activeHistory, settings.historyEnabled) {
        val identity = activeHistory ?: return@LaunchedEffect
        val ctrl = player ?: return@LaunchedEffect
        if (!settings.historyEnabled) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(5000)
            historyRepo.saveProgress(
                identity.copy(
                    positionMs = ctrl.currentPosition.coerceAtLeast(0),
                    durationMs = ctrl.duration.takeIf { it > 0 },
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            )
        }
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text("Unable to complete action") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { actionError = null }) { Text("OK") } },
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isOnMainScreen = currentRoute in MainTab.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (isOnMainScreen) {
                Column {
                    nowPlaying?.let { playing ->
                        MiniPlayer(
                            title = playing.title,
                            posterUrl = playing.streamUrl.replace(Regex("\\.(m3u8|mpd|mp4)$"), ".jpg"),
                            onExpand = { showPlayer = true },
                            onDismiss = { player?.stop(); nowPlaying = null; activeHistory = null },
                        )
                    }
                    NavigationBar(
                        containerColor = NavBar,
                        tonalElevation = 0.dp,
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != tab.route) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        tab.icon,
                                        contentDescription = tab.label,
                                        tint = if (selected) Accent else TextSecondary,
                                    )
                                },
                                label = {
                                    Text(
                                        tab.label,
                                        color = if (selected) Accent else TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = AccentSubtle,
                                ),
                            )
                        }
                    }
                }
            }
        },
        containerColor = Background,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(MainTab.HOME.route) {
                HomeScreen(
                    watchProgress = watchProgress,
                    sources = sources,
                    nativeProviders = nativeProviders,
                    demoTitles = demoTitles,
                    onDemoSelect = { navController.navigate("demo_detail/${it.id}") },
                    onProviderSelect = { navController.navigate("provider_detail/${Uri.encode(it.summary.title)}/${Uri.encode(it.sourceName)}") },
                    onNavigateToSearch = { navController.navigate(MainTab.SEARCH.route) },
                    onNavigateToYou = { navController.navigate(MainTab.YOU.route) },
                )
            }
            composable(MainTab.SEARCH.route) {
                SearchScreen(
                    sources = sources,
                    nativeProviders = nativeProviders,
                    demoTitles = demoTitles,
                    onDemoSelect = { navController.navigate("demo_detail/${it.id}") },
                    onProviderSelect = { navController.navigate("provider_detail/${Uri.encode(it.summary.title)}/${Uri.encode(it.sourceName)}") },
                )
            }
            composable(MainTab.DOWNLOADS.route) {
                DownloadsScreen(
                    downloads = downloads,
                    context = context,
                    onPlay = { snapshot ->
                        val controller = player
                        if (controller == null) {
                            actionError = "The player is still starting. Try again in a moment."
                            return@DownloadsScreen
                        }
                        if (!DownloadRuntime.isOfflineReady(context, snapshot.id)) {
                            actionError = "Download is incomplete. Remove and download again."
                            return@DownloadsScreen
                        }
                        scope.launch {
                            val reason = PlaybackCommands.playOffline(controller, snapshot.id)
                            if (reason != null) { actionError = reason; return@launch }
                            activeHistory = null
                            nowPlaying = DisplayTitle(snapshot.id, snapshot.title, 0, "Offline", "", Color(0xFF315B75), "")
                            showPlayer = true
                        }
                    },
                    onError = { actionError = it },
                )
            }
            composable(MainTab.YOU.route) {
                YouScreen(
                    settings = settings,
                    settingsRepository = settingsRepository,
                    sources = sources,
                    sourceRepository = sourceRepository,
                    diagnosticLog = diagnosticLog,
                    historyRepo = historyRepo,
                    onNavigateToSourceSettings = { navController.navigate("source_settings") },
                )
            }
            composable("source_settings") {
                SourceSettingsScreen(
                    settings = settings,
                    settingsRepository = settingsRepository,
                    sources = sources,
                    sourceRepository = sourceRepository,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                "demo_detail/{titleId}",
                arguments = listOf(navArgument("titleId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val titleId = backStackEntry.arguments?.getString("titleId") ?: return@composable
                val title = demoTitles.find { it.id == titleId } ?: return@composable
                DemoDetailsScreen(
                    title = title,
                    isDownloaded = downloads.any { it.id == title.id },
                    onBack = { navController.popBackStack() },
                    onPlay = {
                        player?.setMediaItem(MediaItem.fromUri(title.streamUrl))
                        val previous = watchProgress.find { it.providerInstanceId == "demo" && it.mediaId == title.id }
                        player?.seekTo(previous?.positionMs ?: 0)
                        player?.prepare()
                        player?.playWhenReady = true
                        nowPlaying = title
                        showPlayer = true
                        activeHistory = WatchProgressEntity("demo", title.id, title.id, title.title, 0, null, System.currentTimeMillis())
                        if (settings.historyEnabled) {
                            scope.launch {
                                historyRepo.saveProgress(
                                    WatchProgressEntity("demo", title.id, title.id, title.title, previous?.positionMs ?: 0L, null, System.currentTimeMillis())
                                )
                            }
                        }
                    },
                    onDownload = {
                        val ref = ReleaseRef(
                            EpisodeRef(
                                MediaRef(ProviderInstanceId(java.util.UUID.nameUUIDFromBytes("nicetime-demo".toByteArray())), title.id, MediaType.MOVIE),
                                title.id, null, null,
                            ),
                            title.id,
                        )
                        val stream = ResolvedStream(ref, title.streamUrl, sourceLabel = "Demo")
                        if (!DownloadRuntime.hasStorage(context)) {
                            diagnosticLog.record("Download rejected: low storage for ${title.title}")
                        } else {
                            scope.launch {
                                try {
                                    DownloadRuntime.enqueue(context, stream, title.title, settings.wifiOnlyDownloads)
                                } catch (_: Exception) {
                                    actionError = "Download could not be queued."
                                }
                            }
                            diagnosticLog.record("Download queued: ${title.title}")
                        }
                    },
                )
            }
            composable(
                "provider_detail/{titleName}/{sourceName}",
            ) { backStackEntry ->
                val titleName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("titleName") ?: "", "UTF-8")
                val sourceName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("sourceName") ?: "", "UTF-8")
                val provider = nativeProviders.find { it.second == sourceName }?.first
                if (provider != null) {
                    ProviderDetailsScreen(
                        titleName = titleName,
                        sourceName = sourceName,
                        provider = provider,
                        onBack = { navController.popBackStack() },
                        onPlay = { stream ->
                            val controller = player
                            if (controller == null) {
                                actionError = "The player is still starting. Try again in a moment."
                                return@ProviderDetailsScreen
                            }
                            val title = DisplayTitle(
                                stream.release.episode.media.providerMediaId, titleName, 0, "Movie",
                                "From $sourceName", Color(0xFF315B75), stream.uri,
                            )
                            val identity = stream.release.episode
                            val previous = watchProgress.find {
                                it.providerInstanceId == identity.media.provider.toString() &&
                                        it.mediaId == identity.media.providerMediaId &&
                                        it.episodeId == identity.providerEpisodeId
                            }
                            val positionMs = previous?.positionMs ?: 0
                            scope.launch {
                                val reason = PlaybackCommands.play(controller, stream, positionMs)
                                if (reason != null) { actionError = reason; return@launch }
                                val progress = WatchProgressEntity(
                                    identity.media.provider.toString(), identity.media.providerMediaId,
                                    identity.providerEpisodeId, titleName, positionMs,
                                    null, System.currentTimeMillis(),
                                )
                                activeHistory = progress
                                if (settings.historyEnabled) historyRepo.saveProgress(progress)
                                nowPlaying = title
                                showPlayer = true
                            }
                        },
                        onDownload = { stream ->
                            scope.launch {
                                try {
                                    DownloadRuntime.enqueue(context, stream, titleName, settings.wifiOnlyDownloads)
                                } catch (_: Exception) {
                                    actionError = "Download could not be queued."
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showPlayer && nowPlaying != null) {
        player?.let {
            PlayerDialog(
                player = it,
                title = nowPlaying!!.title,
                onDismiss = { showPlayer = false },
            )
        }
    }
}

private object Uri {
    fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
    fun decode(value: String): String = java.net.URLDecoder.decode(value, "UTF-8")
}
