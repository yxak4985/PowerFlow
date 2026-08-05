package com.powerflow.battery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1677FF),
    onPrimary = Color.White,
    secondary = Color(0xFF00BFA5),
    tertiary = Color(0xFF7C6CFF),
    background = Color(0xFFEAF3FF),
    onBackground = Color(0xFF10233D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10233D),
    surfaceVariant = Color(0xFFDCE9FA),
    onSurfaceVariant = Color(0xFF4A5E78),
    outline = Color(0xFF8FA6C0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF62B4FF),
    onPrimary = Color(0xFF0A2440),
    secondary = Color(0xFF3FE0C4),
    tertiary = Color(0xFF9E8FFF),
    background = Color(0xFF081426),
    onBackground = Color(0xFFEAF3FF),
    surface = Color(0xFF122238),
    onSurface = Color(0xFFEAF3FF),
    surfaceVariant = Color(0xFF1B3150),
    onSurfaceVariant = Color(0xFFAFC4DE),
    outline = Color(0xFF3D5A80)
)

@Composable
fun PowerFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
