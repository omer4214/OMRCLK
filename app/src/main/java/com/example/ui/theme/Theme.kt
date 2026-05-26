package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrilliantLavender,
    secondary = BrilliantLavenderDim,
    tertiary = ElegantGreen,
    background = SophisticatedBg,
    surface = SophisticatedSurface,
    surfaceVariant = SophisticatedOutline,
    outline = SophisticatedOutline,
    outlineVariant = SophisticatedAltOutline,
    onPrimary = IntenseViolet,
    onSecondary = TextPrimaryLight,
    onTertiary = DeepMidnight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryMuted,
    error = CoralRose,
    primaryContainer = IntenseViolet,
    onPrimaryContainer = BrilliantLavenderDim,
    secondaryContainer = SophisticatedOutline,
    onSecondaryContainer = TextPrimaryLight
)

private val LightColorScheme = DarkColorScheme // Force sophisticated dark across both modes to satisfy theme goal completely


@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
