package com.ontik.countai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = OntikGreenPrimary,
    onPrimary = LightSurface,
    primaryContainer = OntikGreenLight,
    onPrimaryContainer = OntikGreenDark,
    secondary = OntikAmberAccent,
    onSecondary = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = DangerRed
)

private val DarkColors = darkColorScheme(
    primary = OntikGreenBright,
    onPrimary = DarkBackground,
    primaryContainer = OntikGreenDark,
    onPrimaryContainer = OntikGreenLight,
    secondary = OntikAmberAccent,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = DangerRed
)

@Composable
fun OntikCountAITheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OntikTypography,
        content = content
    )
}
