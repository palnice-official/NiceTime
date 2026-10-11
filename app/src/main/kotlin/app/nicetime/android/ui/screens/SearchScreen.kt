package app.nicetime.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nicetime.android.DisplayTitle
import app.nicetime.android.ProviderTitle
import app.nicetime.android.core.data.*
import app.nicetime.android.core.model.*
import app.nicetime.android.ui.components.*
import app.nicetime.android.ui.theme.*
import kotlinx.coroutines.delay

private val trendingSearches = listOf("Action", "Comedy", "Drama", "Thriller", "Hindi", "Bollywood", "Web Series")
private val genres = listOf("Action", "Comedy", "Drama", "Thriller", "Romance", "Sci-Fi", "Horror", "Animation")

@Composable
fun SearchScreen(
    sources: List<SourceConfiguration>,
    nativeProviders: List<Pair<ContentProvider, String>>,
    demoTitles: List<DisplayTitle>,
    onDemoSelect: (DisplayTitle) -> Unit,
    onProviderSelect: (ProviderTitle) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val providerFactory = remember { ProviderFactory() }
    var sourceResults by remember { mutableStateOf<List<ProviderTitle>>(emptyList()) }
    var sourceErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selectedProviders by remember { mutableStateOf<Set<String>>(emptySet()) }
    val availableProviders = remember(sources, nativeProviders) {
        buildList {
            nativeProviders.forEach { (_, name) -> add(name) }
            sources.filter { it.enabled }.forEach { add(it.displayName) }
        }.distinct()
    }

    val isSearching = query.isNotBlank()
    val filteredDemo = remember(query) {
        demoTitles.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }

    LaunchedEffect(availableProviders) {
        if (selectedProviders.isEmpty() && availableProviders.isNotEmpty())
            selectedProviders = availableProviders.toSet()
    }

    LaunchedEffect(query, sources, nativeProviders, selectedProviders) {
        sourceResults = emptyList(); sourceErrors = emptyList(); delay(300)
        if (query.isBlank()) { loading = false; return@LaunchedEffect }
        loading = true
        val providers = mutableListOf<Pair<ContentProvider, String>>()
        val failures = mutableListOf<String>()
        nativeProviders.filter { it.second in selectedProviders }.forEach { providers += it }
        sources.filter { it.enabled && it.displayName in selectedProviders }.forEach { source ->
            when (val created = providerFactory.create(source)) {
                is ProviderResult.Ok -> providers += created.value to source.displayName
                is ProviderResult.Failure -> failures += "${source.displayName}: ${created.failure.message}"
            }
        }
        val registry = ProviderRegistry(providers.map { it.first })
        val names = providers.associate { it.first.instanceId to it.second }
        registry.searchResults(SearchRequest(query.trim())).collect { result ->
            when (val value = result.result) {
                is ProviderResult.Ok -> {
                    sourceResults = (sourceResults + value.value.items.mapNotNull { item ->
                        registry.provider(result.provider)?.let {
                            ProviderTitle(item, it, names[result.provider] ?: "Addon")
                        }
                    }).distinctBy { it.summary.ref }
                }
                is ProviderResult.Failure -> failures += "${names[result.provider] ?: "Addon"}: ${value.failure.message}"
            }
            sourceErrors = failures.toList()
        }
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Search bar
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search movies and series" },
                placeholder = { Text("Search movies and series...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = SurfaceContainer,
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
        }

        // Source filter chips
        if (availableProviders.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableProviders) { provider ->
                        FilterChip(
                            selected = provider in selectedProviders,
                            onClick = {
                                selectedProviders = if (provider in selectedProviders)
                                    selectedProviders - provider else selectedProviders + provider
                            },
                            label = { Text(provider) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentSubtle,
                                selectedLabelColor = Accent,
                            ),
                        )
                    }
                }
            }
        }

        if (loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent) }
        }

        // Results
        if (isSearching) {
            // Tabs: All, Movies, Series
            if (sourceResults.isNotEmpty() || filteredDemo.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = AccentSubtle,
                        ) {
                            Text("All", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Accent, style = MaterialTheme.typography.labelLarge)
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = SurfaceContainer,
                        ) {
                            Text("Movies", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = SurfaceContainer,
                        ) {
                            Text("Series", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Provider results
            items(sourceResults, key = { "${it.summary.ref.provider}:${it.summary.ref.providerMediaId}" }) { item ->
                SearchResultCard(
                    title = item.summary.title,
                    year = item.summary.year?.toString(),
                    kind = if (item.summary.ref.type == MediaType.SERIES) "Series" else "Movie",
                    posterUrl = item.summary.posterUrl,
                    onPlayClick = { onProviderSelect(item) },
                    onClick = { onProviderSelect(item) },
                )
            }

            // Demo results
            items(filteredDemo, key = { it.id }) { title ->
                SearchResultCard(
                    title = title.title,
                    year = title.year.toString(),
                    kind = title.kind,
                    posterUrl = title.streamUrl.replace(Regex("\\.(m3u8|mpd|mp4)$"), ".jpg"),
                    onPlayClick = { onDemoSelect(title) },
                    onClick = { onDemoSelect(title) },
                )
            }

            // Errors
            items(sourceErrors) { error ->
                AssistChip(
                    onClick = {},
                    label = { Text(error, maxLines = 2, color = Error) },
                )
            }

            // No results
            if (sourceResults.isEmpty() && filteredDemo.isEmpty() && sourceErrors.isEmpty() && !loading) {
                item {
                    EmptyState(
                        "No results found",
                        "Try searching with different keywords or check your spelling.",
                    )
                }
            }
        } else {
            // Empty state — show trending & genres
            item {
                Text(
                    "Trending Searches",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(trendingSearches) { search ->
                        GenreChip(label = search, onClick = { query = search })
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Browse by Genre",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(genres) { genre ->
                        GenreChip(label = genre, onClick = { query = genre })
                    }
                }
            }

            // Top Picks
            if (demoTitles.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Top Picks for You",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        }
    }
}
