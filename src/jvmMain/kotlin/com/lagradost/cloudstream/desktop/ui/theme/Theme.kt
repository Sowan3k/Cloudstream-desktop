package com.lagradost.cloudstream.desktop.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE50914),       // CloudStream Netflix-red accent
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB20710),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF1F2937),
    onSecondary = Color.White,
    background = Color(0xFF121212),    // Sleek dark background
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),       // Surface cards
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFCCCCCC)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE50914),
    onPrimary = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF121212),
    surface = Color.White,
    onSurface = Color(0xFF121212)
)

@Composable
fun CloudStreamDesktopTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
