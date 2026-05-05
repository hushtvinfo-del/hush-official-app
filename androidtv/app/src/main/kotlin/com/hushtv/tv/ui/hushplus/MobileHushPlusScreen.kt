package com.hushtv.tv.ui.hushplus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextPrimary
import com.hushtv.tv.ui.theme.TextSecondary

/**
 * Hush+ section for Mobile. Lives inside the [MobileShell] tab
 * switcher (no nav navigation required) — the top of the screen has
 * a horizontal LazyRow of pills (Overview / VOD / Books / Arcade /
 * Tube), the rest is a vertically-scrollable LazyColumn for the
 * selected sub-page.
 *
 * The vertical-scroll choice is intentional even though the user
 * required no overlap. Phones are inherently scroll-friendly — what
 * matters here is that NOTHING wraps awkwardly or collapses, NOT
 * that everything fits in one frame (which is impossible on a
 * portrait phone for a hero + 6-pillar grid).
 *
 * Each rendered card uses fixed proportions and bounded heights so
 * the layout never breaks at narrow widths.
 */
@Composable
fun MobileHushPlusScreen(nav: androidx.navigation.NavController? = null) {
    var selectedKey by rememberSaveable {
        mutableStateOf(HushPlusContent.OVERVIEW_KEY)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05080F))
            .padding(top = 16.dp),
    ) {
        // ── Eyebrow + title ──
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
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

        Spacer(Modifier.height(14.dp))

        // ── Tab strip (horizontal, scrollable so it never overflows) ──
        TabStrip(
            selectedKey = selectedKey,
            onSelect = { selectedKey = it },
        )

        Spacer(Modifier.height(14.dp))

        // ── Content ──
        if (selectedKey == HushPlusContent.OVERVIEW_KEY) {
            OverviewContentMobile()
        } else {
            val addon = HushPlusContent.findAddon(selectedKey)
            if (addon != null) {
                AddonDetailMobile(addon = addon)
            }
        }
    }

    // HushXXX is its own full-screen experience — same pattern as TV.
    LaunchedEffect(selectedKey) {
        if (selectedKey == "xxx" && nav != null) {
            selectedKey = HushPlusContent.OVERVIEW_KEY
            nav.navigate("mhushxxx/${HushPlusContent.OVERVIEW_KEY}")
        }
    }
}

/* ───────────────────────── Tab strip ───────────────────────── */

@Composable
private fun TabStrip(
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    val items = listOf(
        TabItem(HushPlusContent.OVERVIEW_KEY, "Overview", Cyan),
    ) + HushPlusContent.addons.map {
        TabItem(it.key, it.name, it.accent)
    }
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 18.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(items, key = { it.key }) { item ->
            TabPill(
                label = item.label,
                accent = item.accent,
                selected = selectedKey == item.key,
                onClick = { onSelect(item.key) },
            )
        }
    }
}

private data class TabItem(val key: String, val label: String, val accent: Color)

@Composable
private fun TabPill(
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.18f) else Color(0x14FFFFFF))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else Color(0x22FFFFFF),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) accent else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

/* ───────────────────────── Overview content ───────────────────────── */

@Composable
private fun OverviewContentMobile() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 18.dp,
            vertical = 4.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Introducing Hush+",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 30.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "The ultimate HushTV add-on suite — premium streaming, " +
                    "millions of books & audiobooks, and thousands of " +
                    "retro games. All in one.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
        }
        items(HushPlusContent.pillars) { (title, body) ->
            PillarCardMobile(title = title, body = body)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun PillarCardMobile(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x10FFFFFF))
            .border(
                width = 1.dp,
                color = Color(0x22FFFFFF),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

/* ───────────────────────── Addon detail ───────────────────────── */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddonDetailMobile(addon: HushAddon) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 18.dp,
            vertical = 4.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                addon.eyebrow,
                color = addon.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                addon.name,
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 36.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                addon.tagline,
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
            )
        }

        // Hero — 16:9 image OR icon-only fallback panel.
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .border(
                        width = 1.dp,
                        color = addon.accent.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!addon.heroImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = addon.heroImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = null,
                        tint = addon.accent,
                        modifier = Modifier.size(72.dp),
                    )
                }
            }
        }

        item {
            Text(
                addon.description,
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }

        item {
            Text(
                "WHAT'S INCLUDED",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                addon.features.forEach { f ->
                    FeatureChipMobile(label = f, accent = addon.accent)
                }
            }
        }

        item {
            ComingSoonCtaMobile(accent = addon.accent)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FeatureChipMobile(label: String, accent: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.45f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ComingSoonCtaMobile(accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            Icons.Outlined.Star,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "Coming soon",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            "Hush+ launches with the next platform update.",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
