package ru.topacademy.omni.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BlueAccent,
    onPrimary = TextPrimaryDark,
    primaryContainer = CardDark,
    onPrimaryContainer = AmberPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    background = BgDark,
    onBackground = TextPrimaryDark,
    outline = BorderDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = BlueAccent,
    onPrimary = Color.White,
    primaryContainer = CardLight,
    onPrimaryContainer = BlueAccent,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    background = BgLight,
    onBackground = TextPrimaryLight,
    outline = BorderLight,
    surfaceVariant = Color(0xFFF1F5F9), // slate-100
    onSurfaceVariant = TextSecondaryLight
)

@Composable
fun OmniTheme(isDark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        content = content
    )
}
