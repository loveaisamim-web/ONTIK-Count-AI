package com.ontik.countai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = OntikGreenPrimary,
    onPrimary = WhiteBackground,
    primaryContainer = OntikGreenLight,
    onPrimaryContainer = OntikGreenDark,
    secondary = OntikGreenDark,
    onSecondary = WhiteBackground,
    background = WhiteBackground,
    onBackground = TextPrimaryDark,
    surface = WhiteBackground,
    onSurface = TextPrimaryDark,
    surfaceVariant = SoftGraySurface,
    onSurfaceVariant = TextSecondarySlate,
    outline = CardBorderGray,
    error = DangerRed,
    onError = WhiteBackground
)

@Composable
fun OntikCountAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = OntikTypography,
        content = content
    )
}
