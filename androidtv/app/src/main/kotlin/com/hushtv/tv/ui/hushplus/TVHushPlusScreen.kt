package com.hushtv.tv.ui.hushplus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Top-level Hush+ screen for TV — v1.43.99 "Coming Soon" edition.
 *
 * Per user request (2026-05-06): Hush+ is being temporarily rolled
 * back to a teaser-only page — users can scroll through and see what
 * the suite will offer, but cannot actually launch any addon
 * (HushVOD+, HushBooks, HushArcade, HushTube, HushXXX). The previous
 * full Hush+ navigation (sidebar + addon detail panes + the route
 * out to HushXxxScreen) is intentionally removed from this build.
 *
 * Layout:
 *   ┌────────────────────────────────────────────────────────────┐
 *   │  [‹ Home]    HUSH+                                          │  ← header
 *   ├────────────────────────────────────────────────────────────┤
 *   │  ┌─────────────────────────────────────────────────────┐   │
 *   │  │            ✦ COMING SOON ✦                           │   │  ← hero
 *   │  │      The premium add-on suite, rebuilt.              │   │
 *   │  └─────────────────────────────────────────────────────┘   │
 *   │                                                            │
 *   │  WHAT'S COMING                                             │
 *   │  ◉ Massive Content Library                                 │
 *   │     30,000+ live channels in stunning 4K, plus 250,000…    │
 *   │  ◉ No Buffering, Netflix-Style Tech                        │
 *   │  ◉ Sleek, User-Friendly App                                │
 *   │  ◉ Global Access, No VPN Needed                            │
 *   │  ◉ Backup for HushTV                                       │
 *   │  ◉ Exclusive to HushTV Members                             │
 *   │                                                            │
 *   │  THE ADD-ONS (PREVIEW)                                     │
 *   │  HushVOD+   — every movie & TV show on the internet        │
 *   │  HushBooks  — audiobooks library                           │
 *   │  HushArcade — retro & modern games                         │
 *   │  HushTube   — ad-free YouTube                              │
 *   │                                                            │
 *   └────────────────────────────────────────────────────────────┘
 *
 * The whole screen is one LazyColumn. The ONLY focusables are the
 * [BackToHomeChip] and a single bottom-of-page focusable spacer so
 * D-pad navigation works cleanly. Addon entries are NOT focusable
 * (read-only teaser). The route to HushXxxScreen is also disabled at
 * the MainActivity nav-graph level — see MainActivity.kt for the
 * route guard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TVHushPlusScreen(nav: NavController, playlistId: String) {
    val backHomeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { backHomeFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F)),
    ) {
        // Subtle dodger-blue radial wash for atmosphere.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to Color(0xFF1E90FF).copy(alpha = 0.12f),
                        0.6f to Color.Transparent,
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // ── Header strip ──
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.hushtv.tv.ui.screens.home.BackToHomeChip(
                        nav = nav,
                        playlistId = playlistId,
                        focusRequester = backHomeFocus,
                    )
                    Spacer(Modifier.width(24.dp))
                    Box(
                        Modifier
                            .size(width = 3.dp, height = 22.dp)
                            .background(Cyan, RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "HUSH+",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                    )
                }
            }

            // ── Coming Soon hero ──
            item { ComingSoonHero() }

            // ── What's coming ──
            item {
                SectionHeader("WHAT'S COMING")
            }
            items(items = HushPlusContent.pillars, key = { it.first }) { (title, body) ->
                PillarRow(title = title, body = body)
            }

            // ── Addons preview ──
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("THE ADD-ONS (PREVIEW)")
            }
            items(items = HushPlusContent.addons, key = { it.key }) { addon ->
                AddonTeaserRow(
                    name = addon.name,
                    tagline = addon.tagline,
                    accent = addon.accent,
                )
            }

            // ── Footer reassurance ──
            item {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x141E90FF))
                        .border(
                            1.dp,
                            Color(0xFF1E90FF).copy(alpha = 0.3f),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(20.dp),
                ) {
                    Text(
                        "Hush+ is being rebuilt for the next major release. " +
                            "Existing HushTV members will get access automatically " +
                            "the moment it goes live — no extra setup required.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComingSoonHero() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0B1220),
                        Color(0xFF05080F),
                    )
                )
            )
            .border(
                1.5.dp,
                Color(0xFF1E90FF).copy(alpha = 0.4f),
                RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpinDot()
                Spacer(Modifier.width(10.dp))
                Text(
                    "✦  COMING SOON  ✦",
                    color = Cyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.width(10.dp))
                SpinDot()
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "The premium add-on suite, rebuilt.",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "HushVOD+  ·  HushBooks  ·  HushArcade  ·  HushTube",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

@Composable
private fun SpinDot() {
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(Cyan),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 3.dp, height = 16.dp)
                .background(Cyan, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
        )
    }
}

@Composable
private fun PillarRow(title: String, body: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(Cyan),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun AddonTeaserRow(name: String, tagline: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x10FFFFFF))
            .border(
                1.dp,
                accent.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 36.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.fillMaxHeight().fillMaxWidth(0.78f)) {
            Text(
                name,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tagline,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accent.copy(alpha = 0.18f))
                .border(
                    1.dp,
                    accent.copy(alpha = 0.5f),
                    RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                "SOON",
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
        }
    }
}
