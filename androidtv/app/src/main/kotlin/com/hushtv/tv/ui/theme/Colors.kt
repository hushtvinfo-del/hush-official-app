package com.hushtv.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * HushTV design-spec colour tokens.
 * ──────────────────────────────────
 * Reference: https://play.hushtvwebplayer.com/design-spec
 *
 * ⚠ CYAN (#06B6D4) is the single brand accent. Every focus, active, logo-dot,
 *   progress-bar and CTA fill must use it — no exceptions.
 */

// Surfaces
val BgBlack        = Color(0xFF000000)  // app background — pure black
val SurfaceNavy    = Color(0xFF0F172A)  // cards, sidebars
val SurfaceElev    = Color(0xFF1E293B)  // inputs, elevated surfaces
val BorderSlate    = Color(0xFF334155)  // dividers, subtle borders

// Brand accent (primary)
val Cyan           = Color(0xFF06B6D4)
val CyanFocusBg    = Color(0x2606B6D4)  // rgba(6,182,212,0.15)
val CyanRing       = Color(0x5906B6D4)  // rgba(6,182,212,0.35) — outer glow
val CyanGlow08     = Color(0x1406B6D4)  // rgba(6,182,212,0.08) — soft radial glow
val CyanDim        = Color(0x6606B6D4)

// Secondary accents
val Blue           = Color(0xFF3B82F6)
val Green          = Color(0xFF22C55E)
val Red            = Color(0xFFEF4444)
val Amber          = Color(0xFFF59E0B)

// Text scale
val TextPrimary    = Color(0xFFFFFFFF)
val TextSecondary  = Color(0xFF94A3B8)
val TextMuted      = Color(0xFF475569)
val TextDim        = Color(0xFF64748B)

// Unfocused card state (spec)
val UnfocusedBorder = Color(0x14FFFFFF) // rgba(255,255,255,0.08)
val UnfocusedBg     = Color(0x0AFFFFFF) // rgba(255,255,255,0.04)
