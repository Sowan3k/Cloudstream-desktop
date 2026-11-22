package com.lagradost.cloudstream.desktop.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Burgundy Wine: https://21st.dev/@serafimcloud/themes/burgundy-wine
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE63946),       // Burgundy Wine primary accent
    onPrimary = Color(0xFF0F0203),
    primaryContainer = Color(0xFF3D0A0D),
    onPrimaryContainer = Color(0xFFFFDCE0),
    secondary = Color(0xFF3D0A0D),
    onSecondary = Color(0xFFFFDCE0),
    secondaryContainer = Color(0xFF4A151B),
    onSecondaryContainer = Color(0xFFFFDCE0),
    tertiary = Color(0xFFCD182A),
    onTertiary = Color.White,
    background = Color(0xFF0F0203),    // Sleek dark background
    onBackground = Color(0xFFFCEEF0),
    surface = Color(0xFF1A0507),       // Surface cards
    onSurface = Color(0xFFFCEEF0),
    surfaceVariant = Color(0xFF2A0A0D),
    onSurfaceVariant = Color(0xFFD4A1A6),
    outline = Color(0xFF9E6972),
    outlineVariant = Color(0xFF2B0D10),
    error = Color(0xFFFF4D6D),
    onError = Color(0xFF0F0203)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF800020),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4E7EA),
    onPrimaryContainer = Color(0xFF800020),
    secondary = Color(0xFFF4E7EA),
    onSecondary = Color(0xFF800020),
    secondaryContainer = Color(0xFFEEDFE2),
    onSecondaryContainer = Color(0xFF3D0A0D),
    tertiary = Color(0xFF9A1B32),
    onTertiary = Color.White,
    background = Color(0xFFFDF8F9),
    onBackground = Color(0xFF3D0A0D),
    surface = Color.White,
    onSurface = Color(0xFF3D0A0D),
    surfaceVariant = Color(0xFFF4E7EA),
    onSurfaceVariant = Color(0xFF7C5C5F),
    outline = Color(0xFF9F737D),
    outlineVariant = Color(0xFFE5D1D4),
    error = Color(0xFFBE123C),
    onError = Color.White
)

private val LocalDarkTheme = staticCompositionLocalOf { true }

val SurfaceBase: Color
    @Composable get() = MaterialTheme.colorScheme.background
val SurfaceLayer: Color
    @Composable get() = MaterialTheme.colorScheme.surface
val SurfaceCard: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val SurfaceHover: Color
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val BorderSubtle: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant
val BorderDefault: Color
    @Composable get() = MaterialTheme.colorScheme.outline
val TextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextTertiary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Accent: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val AccentText: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFFFF8FA3) else MaterialTheme.colorScheme.primary
val AccentHover: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFFFF8FA3) else MaterialTheme.colorScheme.tertiary
val OnAccent: Color
    @Composable get() = MaterialTheme.colorScheme.onPrimary
val ErrorColor: Color
    @Composable get() = MaterialTheme.colorScheme.error
val WarningColor: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFFFFB300) else Color(0xFF8A4B00)
// Media overlays stay dark in both themes, so their accent needs its own contrast.
val MediaAccent = Color(0xFFFF8FA3)
val SidebarBackground: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF0A0102) else Color(0xFF3D0A0D)
val SidebarForeground: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFFFCEEF0) else Color(0xFFFDF8F9)
val SidebarHover: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF1A0507) else Color(0xFF591116)
val SidebarBorder: Color
    @Composable get() = SidebarHover
val SidebarAccent: Color
    @Composable get() = if (LocalDarkTheme.current) Accent else Color(0xFFEA869A)

@Composable
fun CloudStreamDesktopTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
