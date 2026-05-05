package com.hushtv.tv.ui.hushplus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary

/**
 * Hush+ section for Mobile — v1.43.99 "Coming Soon" edition.
 *
 * Per user request: Hush+ is rolled back to a teaser-only page on
 * mobile too. Users can scroll through the pillars and addon list
 * but cannot actually launch any addon (HushVOD+, HushBooks,
 * HushArcade, HushTube, HushXXX). The previous tab-switcher + addon
 * detail panes + the route out to MobileHushXxx is intentionally
 * removed from this build.
 */
@Composable
fun MobileHushPlusScreen(
    @Suppress("UNUSED_PARAMETER")
    nav: androidx.navigation.NavController? = null,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F)),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = 64.dp,
            start = 18.dp,
            end = 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── Eyebrow + title ──
        item {
            Column {
                Text(
                    "PREMIUM ADD-ON",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Hush+",
                    color = TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 36.sp,
                )
            }
        }

        // ── Coming Soon hero ──
        item { ComingSoonHeroMobile() }

        // ── What's coming ──
        item { SectionHeaderMobile("WHAT'S COMING") }
        items(items = HushPlusContent.pillars, key = { it.first }) { (title, body) ->
            PillarRowMobile(title = title, body = body)
        }

        // ── Addons preview ──
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeaderMobile("THE ADD-ONS (PREVIEW)")
        }
        items(items = HushPlusContent.addons, key = { it.key }) { addon ->
            AddonTeaserRowMobile(
                name = addon.name,
                tagline = addon.tagline,
                accent = addon.accent,
            )
        }

        // ── Footer reassurance ──
        item {
            Spacer(Modifier.height(8.dp))
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

@Composable
private fun ComingSoonHeroMobile() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0B1220), Color(0xFF05080F))
                )
            )
            .border(
                1.5.dp,
                Color(0xFF1E90FF).copy(alpha = 0.4f),
                RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Cyan))
                Spacer(Modifier.size(8.dp))
                Text(
                    "✦  COMING SOON  ✦",
                    color = Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.size(8.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(Cyan))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "The premium add-on suite, rebuilt.",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "VOD+ · Books · Arcade · Tube",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun SectionHeaderMobile(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).background(Cyan, RoundedCornerShape(2.dp)))
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.5.sp,
        )
    }
}

@Composable
private fun PillarRowMobile(title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(Cyan),
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun AddonTeaserRowMobile(name: String, tagline: String, accent: Color) {
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 30.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tagline,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp,
            )
        }
        Spacer(Modifier.size(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accent.copy(alpha = 0.18f))
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                "SOON",
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
            )
        }
    }
}
