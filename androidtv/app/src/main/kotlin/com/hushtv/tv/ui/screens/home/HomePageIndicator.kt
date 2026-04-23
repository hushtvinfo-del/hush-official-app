package com.hushtv.tv.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter

/**
 * A page for the Home pager — what the indicator should label it as.
 */
data class HomePage(val key: String, val label: String)

/**
 * Mini vertical page indicator pinned to the right edge of the screen.
 *
 *   ⌃          ← up chevron (only shown if a page exists above)
 *   • ◉ • •    ← dots, active is bigger + cyan with a small label
 *   ⌄          ← down chevron (only shown if a page exists below)
 *
 * The active dot expands into a pill that reveals the page's label so
 * users instantly know where they are without cluttering the screen.
 * Inactive pages are small translucent dots. Chevron glyphs hint that
 * more content exists above/below → discoverable.
 */
@Composable
fun HomePageIndicator(
    pages: List<HomePage>,
    currentPage: String,
    modifier: Modifier = Modifier,
) {
    if (pages.size < 2) return
    val currentIdx = pages.indexOfFirst { it.key == currentPage }.coerceAtLeast(0)
    val hasAbove = currentIdx > 0
    val hasBelow = currentIdx < pages.lastIndex

    Column(
        modifier = modifier.padding(end = 20.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Up chevron — "there's something above".
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (hasAbove) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = Color(0x80FFFFFF),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        pages.forEachIndexed { idx, page ->
            PageDot(
                label = page.label,
                active = idx == currentIdx,
            )
        }

        // Down chevron — "there's something below".
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (hasBelow) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0x80FFFFFF),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun PageDot(label: String, active: Boolean) {
    // Active dot grows to a bigger filled circle; inactive dots stay
    // small + translucent. No pill, no label — clean minimal indicator.
    val size by animateDpAsState(
        targetValue = if (active) 14.dp else 8.dp,
        animationSpec = tween(220),
        label = "page-dot-size",
    )
    val bg by animateColorAsState(
        targetValue = if (active) Cyan else Color(0x66FFFFFF),
        animationSpec = tween(220),
        label = "page-dot-bg",
    )

    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
    )
    @Suppress("UNUSED_EXPRESSION") label  // label kept in API for future tooltip/aria
}
