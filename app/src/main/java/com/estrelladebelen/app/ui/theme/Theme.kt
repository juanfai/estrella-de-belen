package com.estrelladebelen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary              = Lilac,
    onPrimary            = Midnight,
    primaryContainer     = MidnightVariant,
    onPrimaryContainer   = Cream,
    secondary            = MintGlow,
    onSecondary          = Midnight,
    secondaryContainer   = Color(0xFF3E5048),
    onSecondaryContainer = MintGlow,
    tertiary             = Moonbeam,
    onTertiary           = Midnight,
    tertiaryContainer    = Color(0xFF4A4228),
    onTertiaryContainer  = Moonbeam,
    background           = Midnight,
    onBackground         = Cream,
    surface              = MidnightSurface,
    onSurface            = Cream,
    surfaceVariant       = MidnightVariant,
    onSurfaceVariant     = Lavender,
    outline              = Fog,
    outlineVariant       = AppDivider,
    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
)

@Composable
fun EstrellaDeBelénTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = AppTypography,
        content     = content
    )
}
