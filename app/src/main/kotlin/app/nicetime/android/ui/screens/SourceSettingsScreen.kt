package app.nicetime.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nicetime.android.core.data.*
import app.nicetime.android.core.model.ProviderResult
import app.nicetime.android.ui.components.*
import app.nicetime.android.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSettingsScreen(
    settings: AppSettings,
    settingsRepository: SettingsRepository?,
    sources: List<SourceConfiguration>,
    sourceRepository: SourceRepository?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showAddSource by rememberSaveable { mutableStateOf(false) }
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var sourceError by rememberSaveable { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Source Settings", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Available Sources", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            }

            // Native sources
            item {
                SourceCard(
                    name = "MovieBox",
                    description = "Primary source",
                    enabled = settings.movieBoxEnabled,
                    onToggle = { enabled ->
                        settingsRepository?.let { store ->
                            scope.launch { store.setMovieBoxEnabled(enabled) }
                        }
                    },
                )
            }
            item {
                SourceCard(
                    name = "4KHDHub",
                    description = "Secondary source",
                    enabled = settings.fourKHDHubEnabled,
                    onToggle = { enabled ->
                        settingsRepository?.let { store ->
                            scope.launch { store.setFourKHDHubEnabled(enabled) }
                        }
                    },
                )
            }

            // Addon sources
            if (sources.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Addon Sources", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                }
                items(sources, key = { it.instanceId }) { source ->
                    SourceCard(
                        name = source.displayName,
                        description = if (source.enabled) "Enabled" else "Disabled",
                        enabled = source.enabled,
                        onToggle = { enabled ->
                            sourceRepository?.let { store ->
                                scope.launch { store.setEnabled(source.instanceId, enabled) }
                            }
                        },
                    )
                }
            }

            // Add button
            item {
                Button(
                    onClick = { showAddSource = true },
                    enabled = sourceRepository != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Add addon source", color = Color.White)
                }
            }

            // Info note
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentSubtle),
                ) {
                    Text(
                        "We use multiple sources to provide you with the best streaming experience. You can change sources anytime in the settings.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }

    // Add source dialog
    if (showAddSource) {
        AlertDialog(
            onDismissRequest = { if (!installing) showAddSource = false },
            containerColor = SurfaceElevated,
            title = { Text("Add addon source", color = TextPrimary) },
            text = {
                Column {
                    Text("Enter an HTTPS addon URL.", color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        sourceUrl,
                        { sourceUrl = it; sourceError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Addon URL") },
                        singleLine = true,
                        enabled = !installing,
                        isError = sourceError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = SurfaceContainer,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        ),
                    )
                    sourceError?.let {
                        Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        installing = true
                        scope.launch {
                            when (val result = AddonClient.create().fetchManifest(sourceUrl)) {
                                is ProviderResult.Ok -> {
                                    sourceRepository?.save(
                                        SourceConfiguration(
                                            manifestUrl = AddonClient.normalizeManifestUrl(sourceUrl),
                                            displayName = result.value.name,
                                        )
                                    )
                                    sourceUrl = ""; sourceError = null; showAddSource = false
                                }
                                is ProviderResult.Failure -> sourceError = result.failure.message
                            }
                            installing = false
                        }
                    },
                    enabled = sourceUrl.isNotBlank() && !installing,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("Add", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showAddSource = false }, enabled = !installing) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }
}
