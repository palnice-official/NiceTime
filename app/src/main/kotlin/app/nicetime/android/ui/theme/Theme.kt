package app.nicetime.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    primaryContainer = AccentDark,
    onPrimaryContainer = TextPrimary,
    secondary = AccentDark,
    onSecondary = Background,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentPressed,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = Surface,
    surfaceContainer = SurfaceElevated,
    surfaceContainerHigh = SurfaceContainer,
    surfaceContainerHighest = SurfaceContainer,
    error = Error,
    onError = Color.White,
    outline = TextDisabled,
    outlineVariant = ProgressTrack,
)

private val LightColorScheme = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    background = Color(0xFFF5F3FA),
    onBackground = Color(0xFF1A1625),
    surface = Color.White,
    onSurface = Color(0xFF1A1625),
    surfaceVariant = Color(0xFFE8E4F0),
    onSurfaceVariant = Color(0xFF6B6580),
    error = Error,
)

@Composable
fun NiceTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NiceTimeTypography,
        shapes = NiceTimeShapes,
        content = content,
    )
}
