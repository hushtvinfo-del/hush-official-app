@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hushtv.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter

/**
 * Generic row in the category sidebar. Caller provides an ID, label,
 * and a click handler.
 */
data class SidebarItem(
    val id: String,
    val label: String,
)

/**
 * Classic left-hand vertical category rail. Drop in beside any
 * content screen as:
 *
 *     Row(Modifier.fillMaxSize().padding(top = 72.dp)) {
 *         CategorySidebar(items, selectedId, ...)
 *         // Right pane
 *     }
 *
 * Width is fixed at 240 dp. Auto-scrolls the selected row into view
 * on every selection change. Top row lifts focus up to
 * [topRowUpTarget] when provided so the parent top-nav remains
 * reachable even in sidebar mode.
 */
@Composable
fun CategorySidebar(
    items: List<SidebarItem>,
    selectedId: String?,
    title: String,
    firstItemFocus: FocusRequester,
    onFocus: (SidebarItem) -> Unit,
    onEnter: (SidebarItem) -> Unit,
    topRowUpTarget: FocusRequester? = null,
    rightTarget: FocusRequester? = null,
    selectedItemFocus: FocusRequester? = null,
    width: androidx.compose.ui.unit.Dp = 240.dp,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to keep the selected item comfortably in view.
    LaunchedEffect(selectedId, items) {
        val idx = items.indexOfFirst { it.id == selectedId }
        if (idx >= 0) runCatching {
            listState.animateScrollToItem(
                idx.coerceAtLeast(0),
                scrollOffset = -140,
            )
        }
    }

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(Color(0xFF05080F)),
    ) {
        // Header.
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 18.dp)
                        .background(Cyan, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title.uppercase(),
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.5.sp,
                    fontFamily = Inter,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${items.size} categories",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontFamily = Inter,
            )
        }

        // Divider.
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))

        // List.
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 10.dp, vertical = 10.dp,
            ),
        ) {
            items(
                count = items.size,
                key = { idx -> items[idx].id },
            ) { idx ->
                val item = items[idx]
                val isSelected = item.id == selectedId
                val isFirst = idx == 0
                // Focus modifier — attaches the first/selected requesters.
                // Built INLINE (no remember) because the keys are simple
                // booleans and the cost of recomposing the chain on flip
                // is trivial; using `remember` here previously kept stale
                // references in the focus tree and caused crashes on
                // selection change.
                val combinedFocusMod = run {
                    var m: Modifier = Modifier
                    if (isFirst) m = m.focusRequester(firstItemFocus)
                    if (isSelected && selectedItemFocus != null) {
                        m = m.focusRequester(selectedItemFocus)
                    }
                    m
                }
                SidebarRow(
                    label = item.label,
                    selected = isSelected,
                    focusMod = combinedFocusMod,
                    topRowUpTarget = if (isFirst) topRowUpTarget else null,
                    rightTarget = rightTarget,
                    onFocus = { onFocus(item) },
                    onEnter = { onEnter(item) },
                )
            }
        }
    }
}

@Composable
private fun SidebarRow(
    label: String,
    selected: Boolean,
    focusMod: Modifier,
    topRowUpTarget: FocusRequester?,
    rightTarget: FocusRequester?,
    onFocus: () -> Unit,
    onEnter: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = focusMod
            .fillMaxWidth()
            .height(40.dp)
            .background(
                when {
                    focused -> Cyan.copy(alpha = 0.18f)
                    selected -> Color(0x331F2937)
                    else -> Color.Transparent
                },
                shape,
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Cyan else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusProperties {
                if (topRowUpTarget != null) up = topRowUpTarget
                if (rightTarget != null) right = rightTarget
            }
            .focusable()
            .clickableWithEnter(onEnter)
            .padding(horizontal = 12.dp),
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 18.dp)
                    .background(Cyan, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(10.dp))
        } else {
            Spacer(Modifier.width(13.dp))
        }
        Text(
            label,
            color = when {
                selected || focused -> Color.White
                else -> Color(0xFFCBD5E1)
            },
            fontSize = 13.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            fontFamily = Inter,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
