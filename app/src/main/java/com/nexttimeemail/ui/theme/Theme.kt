package com.nexttimeemail.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Fixed minimalist scheme — intentionally no dynamic color and no dark variant, so
// the app always reads as light-gray surfaces, black text and dark-gray separators.
private val AppColors = lightColorScheme(
    primary = Ink,
    onPrimary = OnInk,
    secondary = Ink,
    onSecondary = OnInk,
    tertiary = OnSurfaceMuted,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = OnSurfaceMuted,
    secondaryContainer = SurfaceCard,
    onSecondaryContainer = OnSurface,
    outline = Separator,
    outlineVariant = Separator,
)

@Composable
fun NextTimeEmailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = Typography,
        content = content,
    )
}
