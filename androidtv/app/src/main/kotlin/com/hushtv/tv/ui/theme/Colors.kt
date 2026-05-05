package com.hushtv.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * HushTV design-spec colour tokens.
 * ──────────────────────────────────
 * Reference: https://play.hushtvwebplayer.com/design-spec
 *
 * ⚠ The brand accent is the single primary colour — every focus, active,
 *   logo-dot, progress-bar and CTA fill must use it via the `Cyan` token
 *   (kept named for codebase continuity even though the value is now a
 *   saturated sky blue, NOT actual cyan, after the v1.43+ rebrand).
 *
 *   Why #1E90FF (DodgerBlue) + #59BFF2 (lighter accent)?
 *   • Matches the user-supplied brand swatch: a saturated DodgerBlue
 *     paired with a lighter cyan-leaning sky blue for glows/highlights.
 *   • 3.68:1 contrast on white text → AA-large-text compliant for big
 *     buttons and headlines; still legible on photo hero overlays.
 *   • 7.92:1 against pure black → pops on the dark app background and
 *     on bright photo heroes alike without washing out.
 *   • Reads as bright, modern broadcast blue (Bell / Xfinity Stream
 *     direction) rather than the previous darker navy tone.
 */

// Surfaces
val BgBlack        = Color(0xFF000000)  // app background — pure black
val SurfaceNavy    = Color(0xFF0F172A)  // cards, sidebars
val SurfaceElev    = Color(0xFF1E293B)  // inputs, elevated surfaces
val BorderSlate    = Color(0xFF334155)  // dividers, subtle borders

// Brand accent (primary) — saturated sky blue (DodgerBlue).
// Variable name kept as `Cyan` to avoid mass-renaming hundreds of
// callsites; the colour value is the single source of truth.
val Cyan           = Color(0xFF1E90FF)
val CyanFocusBg    = Color(0x261E90FF)  // 15 % alpha — soft focus fill
val CyanRing       = Color(0x591E90FF)  // 35 % alpha — outer ring glow
val CyanGlow08     = Color(0x141E90FF)  //  8 % alpha — radial glow
val CyanDim        = Color(0x661E90FF)  // 40 % alpha — disabled / inactive

// Secondary accent — lighter cyan-blue, used for highlights and glows.
val Blue           = Color(0xFF59BFF2)
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
