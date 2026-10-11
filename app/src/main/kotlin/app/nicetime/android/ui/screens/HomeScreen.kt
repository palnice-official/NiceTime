package app.nicetime.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.nicetime.android.DisplayTitle
import app.nicetime.android.ProviderTitle
import app.nicetime.android.core.data.*
import app.nicetime.android.core.model.*
import app.nicetime.android.ui.components.*
import app.nicetime.android.ui.theme.*

@Composable
fun HomeScreen(
    watchProgress: List<WatchProgressEntity>,
    sources: List<SourceConfiguration>,
    nativeProviders: List<Pair<ContentProvider, String>>,
    demoTitles: List<DisplayTitle>,
    onDemoSelect: (DisplayTitle) -> Unit,
    onProviderSelect: (ProviderTitle) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToYou: () -> Unit = {},
) {
    var providerItems by remember { mutableStateOf<List<ProviderTitle>>(emptyList()) }
    var providerError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val providerFactory = remember { ProviderFactory() }
    var selectedProviders by remember { mutableStateOf<Set<String>>(emptySet()) }
    val availableProviders = remember(sources, nativeProviders) {
        buildList {
            nativeProviders.forEach { (_, name) -> add(name) }
            sources.filter { it.enabled }.forEach { add(it.displayName) }
        }.distinct()
    }

    LaunchedEffect(availableProviders) {
        if (selectedProviders.isEmpty() && availableProviders.isNotEmpty())
            selectedProviders = availableProviders.toSet()
    }

    LaunchedEffect(sources, nativeProviders, selectedProviders) {
        loading = true
        val providers = mutableListOf<Pair<ContentProvider, String>>()
        nativeProviders.filter { it.second in selectedProviders }.forEach { providers += it }
        sources.filter { it.enabled && it.displayName in selectedProviders }.forEach { source ->
            when (val created = providerFactory.create(source)) {
                is ProviderResult.Ok -> providers += created.value to source.displayName
                is ProviderResult.Failure -> {}
            }
        }
        val registry = ProviderRegistry(providers.filter { it.first.capabilities.catalog }.map { it.first })
        val names = providers.associate { it.first.instanceId to it.second }
        val results = registry.catalogAll()
        val errors = mutableListOf<String>()
        providerItems = results.flatMap { result ->
            when (val value = result.result) {
                is ProviderResult.Ok -> value.value.items.mapNotNull { item ->
                    registry.provider(result.provider)?.let { ProviderTitle(item, it, names[result.provider] ?: "Addon") }
                }
                is ProviderResult.Failure -> {
                    errors += "${names[result.provider] ?: "Source"}: ${value.failure.message}"
                    emptyList()
                }
            }
        }.distinctBy { it.summary.ref }.take(20)
        providerError = errors.joinToString("\n").ifBlank { null }
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // Hero — first demo title
        if (demoTitles.isNotEmpty()) {
            val hero = demoTitles.first()
            item {
                HeroBanner(
                    title = hero.title,
                    year = hero.year.toString(),
                    kind = hero.kind,
                    imageUrl = hero.streamUrl.replace(Regex("\\.(m3u8|mpd|mp4)$"), ".jpg"),
                    badge = "Popular",
                    onPlay = { onDemoSelect(hero) },
                    onMyList = { onDemoSelect(hero) },
                )
            }
        }

        // Trending Now — provider items as horizontal poster rail
        if (providerItems.isNotEmpty()) {
            item {
                SectionHeader("Trending Now")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(providerItems, key = { "${it.summary.ref.provider}:${it.summary.ref.providerMediaId}" }) { item ->
                        MoviePosterCard(
                            title = item.summary.title,
                            year = item.summary.year?.toString(),
                            kind = if (item.summary.ref.type == MediaType.SERIES) "Series" else "Movie",
                            posterUrl = item.summary.posterUrl,
                            onClick = { onProviderSelect(item) },
                        )
                    }
                }
            }
        }

        // Continue Watching
        if (watchProgress.isNotEmpty()) {
            item { SectionHeader("Continue Watching") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(watchProgress.take(10)) { progress ->
                        val durMs = progress.durationMs
                        val fraction = if (durMs != null && durMs > 0)
                            (progress.positionMs.toFloat() / durMs).coerceIn(0f, 1f) else null
                        val remainingMs = (durMs ?: 0L) - progress.positionMs
                        val remainingMin = (remainingMs / 60_000).coerceAtLeast(0)

                        val matchedDemo = demoTitles.find { it.id == progress.mediaId }
                        LandscapeCard(
                            title = progress.title,
                            subtitle = if (matchedDemo != null) "${matchedDemo.year} • ${matchedDemo.kind}" else null,
                            progressFraction = fraction,
                            remainingText = "${remainingMin}m left",
                            posterUrl = matchedDemo?.streamUrl?.replace(Regex("\\.(m3u8|mpd|mp4)$"), ".jpg"),
                            onClick = { matchedDemo?.let(onDemoSelect) },
                        )
                    }
                }
            }
        }

        // Popular Movies — demo titles as poster rail
        if (demoTitles.isNotEmpty()) {
            item { SectionHeader("Popular Movies") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(demoTitles, key = { it.id }) { title ->
                        MoviePosterCard(
                            title = title.title,
                            year = title.year.toString(),
                            kind = title.kind,
                            posterUrl = title.streamUrl.replace(Regex("\\.(m3u8|mpd|mp4)$"), ".jpg"),
                            onClick = { onDemoSelect(title) },
                        )
                    }
                }
            }
        }

        // Recently Added — provider items
        if (providerItems.size > 5) {
            item { SectionHeader("Recently Added") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(providerItems.drop(5).take(10), key = { "recent_${it.summary.ref.provider}:${it.summary.ref.providerMediaId}" }) { item ->
                        MoviePosterCard(
                            title = item.summary.title,
                            year = item.summary.year?.toString(),
                            kind = if (item.summary.ref.type == MediaType.SERIES) "Series" else "Movie",
                            posterUrl = item.summary.posterUrl,
                            onClick = { onProviderSelect(item) },
                        )
                    }
                }
            }
        }

        if (loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Accent) }
        }
        providerError?.let { error ->
            item {
                AssistChip(
                    onClick = {},
                    label = { Text(error, maxLines = 3, color = Error) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
