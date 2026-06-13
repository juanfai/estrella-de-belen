package com.estrelladebelen.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand palette ─────────────────────────────────────────────────────────────
val Moonbeam  = Color(0xFFC9B97A)  // warm gold
val Cream     = Color(0xFFE8DFB8)  // primary text
val MintGlow  = Color(0xFFB2D4BA)  // secondary accent
val Sage      = Color(0xFFB4BCA8)  // muted green-gray
val Lavender  = Color(0xFFB8B4CC)  // soft lavender
val Lilac     = Color(0xFF9890B8)  // primary interactive
val Fog       = Color(0xFF7880A0)  // secondary text / borders
val Midnight  = Color(0xFF3A3F68)  // base background

// ── Derived surfaces ──────────────────────────────────────────────────────────
val MidnightSurface  = Color(0xFF464B74)  // card / sheet background
val MidnightVariant  = Color(0xFF404568)  // subtle containers

// ── Semantic aliases (used throughout the UI) ─────────────────────────────────
val AppBackground    = Midnight
val AppSurface       = MidnightSurface
val AppSurfaceVar    = MidnightVariant
val AppPrimary       = Lilac
val AppPrimaryDark   = Fog
val AppContainer     = MidnightVariant
val TextPrimary      = Cream
val TextSecondary    = Fog
val AppDivider       = Color(0xFF505580)

// Auth (unified with app — same dark background)
val AuthBackground   = Midnight
val AuthPrimary      = Lilac

// Player background (now Midnight instead of black)
val PlayerBackground = Midnight

// Legacy aliases — keep old names pointing to new values so nothing breaks
val LavenderBackground  = Midnight
val LavenderSurface     = MidnightSurface
val LavenderPrimary     = Lilac
val LavenderPrimaryDark = Lilac
val LavenderContainer   = MidnightVariant
val LavenderTextSecond  = Fog
val LavenderDivider     = AppDivider
val LavenderSurfaceVar  = MidnightVariant
val PurpleTextPrimary   = Cream
val SkyAccent           = MintGlow
