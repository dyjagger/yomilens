package app.yomilens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F51B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E4FF),
    onPrimaryContainer = Color(0xFF17206B),
    secondary = Color(0xFFB84A62),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E0),
    onSecondaryContainer = Color(0xFF6D1731),
    background = Color(0xFFF8F6F1),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE4E1E9),
    onSurfaceVariant = Color(0xFF46464F),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBC3FF),
    onPrimary = Color(0xFF10206F),
    primaryContainer = Color(0xFF29378D),
    onPrimaryContainer = Color(0xFFE1E4FF),
    secondary = Color(0xFFFFB1C1),
    onSecondary = Color(0xFF68172F),
    secondaryContainer = Color(0xFF8F2F47),
    onSecondaryContainer = Color(0xFFFFD9E0),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE5E1E6),
    surface = Color(0xFF1A1B1F),
    onSurface = Color(0xFFE5E1E6),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    error = Color(0xFFFFB4AB),
)

@Composable
fun YomiLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
