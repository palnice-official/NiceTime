package app.nicetime.android.ui.screens

import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nicetime.android.core.data.*
import app.nicetime.android.ui.components.*
import app.nicetime.android.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun YouScreen(
    settings: AppSettings,
    settingsRepository: SettingsRepository?,
    sources: List<SourceConfiguration>,
    sourceRepository: SourceRepository?,
    diagnosticLog: DiagnosticLog,
    historyRepo: HistoryRepository,
    onNavigateToSourceSettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Profile header
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        tint = Accent,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("NiceTime", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentSubtle,
                ) {
                    Text(
                        "Premium",
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Accent,
                    )
                }
            }
        }

        // Quick links
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.List, null, tint = Accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("My List", Modifier.weight(1f), color = TextPrimary)
                        Icon(Icons.Default.ArrowForward, null, tint = TextSecondary)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Continue Watching", Modifier.weight(1f), color = TextPrimary)
                        Icon(Icons.Default.ArrowForward, null, tint = TextSecondary)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Downloads", Modifier.weight(1f), color = TextPrimary)
                        Icon(Icons.Default.ArrowForward, null, tint = TextSecondary)
                    }
                }
            }
        }

        // Preferences section
        item {
            Spacer(Modifier.height(12.dp))
            Text(
                "Preferences",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
        }
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    PreferenceSwitch(
                        "Dark theme",
                        "Use a dark color scheme",
                        settings.darkTheme,
                        onChecked = { settingsRepository?.let { store -> scope.launch { store.setDarkTheme(it) } } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    PreferenceSwitch(
                        "Wi-Fi downloads only",
                        "Avoid mobile data for downloads",
                        settings.wifiOnlyDownloads,
                        onChecked = { settingsRepository?.let { store -> scope.launch { store.setWifiOnlyDownloads(it) } } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    PreferenceSwitch(
                        "Watch history",
                        "Save progress and continue watching",
                        settings.historyEnabled,
                        onChecked = { settingsRepository?.let { store -> scope.launch { store.setHistoryEnabled(it) } } },
                    )
                }
            }
        }

        // History
        if (settings.historyEnabled) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "History",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                )
            }
            item {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { scope.launch { historyRepo.clearHistory() } },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Error, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Clear history", color = TextPrimary)
                    }
                }
            }
        }

        // App Settings section
        item {
            Spacer(Modifier.height(12.dp))
            Text(
                "App Settings",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
        }
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToSourceSettings)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Settings, null, tint = Accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Source Settings", Modifier.weight(1f), color = TextPrimary)
                        Icon(Icons.Default.ArrowForward, null, tint = TextSecondary)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Info, null, tint = Accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Help & Support", Modifier.weight(1f), color = TextPrimary)
                        Icon(Icons.Default.ArrowForward, null, tint = TextSecondary)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Info, null, tint = Accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("About", Modifier.weight(1f), color = TextPrimary)
                        Icon(Icons.Default.ArrowForward, null, tint = TextSecondary)
                    }
                }
            }
        }

        // Diagnostics
        item {
            Spacer(Modifier.height(12.dp))
            Text(
                "Diagnostics",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
        }
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(16.dp)) {
                    val logText = diagnosticLog.export()
                    Text(
                        if (logText.isBlank()) "No events recorded"
                        else "Recent events (${logText.lines().size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                val redacted = redactSensitiveText(diagnosticLog.exportWithContext())
                                context.startActivity(
                                    android.content.Intent.createChooser(
                                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            putExtra(android.content.Intent.EXTRA_TEXT, redacted)
                                            type = "text/plain"
                                        },
                                        "Export diagnostics",
                                    )
                                )
                            },
                            enabled = logText.isNotBlank(),
                        ) { Text("Export") }
                        TextButton(
                            onClick = { diagnosticLog.clear() },
                            enabled = logText.isNotBlank(),
                        ) { Text("Clear") }
                    }
                }
            }
        }
    }
}
