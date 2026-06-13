package com.estrelladebelen.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

private val baseline = Typography()

val AppTypography = Typography(
    displayLarge   = baseline.displayLarge.copy(fontFamily   = FontFamily.Default),
    displayMedium  = baseline.displayMedium.copy(fontFamily  = FontFamily.Default),
    displaySmall   = baseline.displaySmall.copy(fontFamily   = FontFamily.Default),
    headlineLarge  = baseline.headlineLarge.copy(fontFamily  = FontFamily.Default),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = FontFamily.Default),
    headlineSmall  = baseline.headlineSmall.copy(fontFamily  = FontFamily.Default),
    titleLarge     = baseline.titleLarge.copy(fontFamily     = FontFamily.Default),
    titleMedium    = baseline.titleMedium.copy(fontFamily    = FontFamily.Default),
    titleSmall     = baseline.titleSmall.copy(fontFamily     = FontFamily.Default),
    bodyLarge      = baseline.bodyLarge.copy(fontFamily      = FontFamily.SansSerif),
    bodyMedium     = baseline.bodyMedium.copy(fontFamily     = FontFamily.SansSerif),
    bodySmall      = baseline.bodySmall.copy(fontFamily      = FontFamily.SansSerif),
    labelLarge     = baseline.labelLarge.copy(fontFamily     = FontFamily.SansSerif),
    labelMedium    = baseline.labelMedium.copy(fontFamily    = FontFamily.SansSerif),
    labelSmall     = baseline.labelSmall.copy(fontFamily     = FontFamily.SansSerif),
)
