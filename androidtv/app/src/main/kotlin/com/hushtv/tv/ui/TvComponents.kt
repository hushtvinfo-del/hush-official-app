package com.hushtv.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hushtv.tv.ui.theme.Cyan

/** Shared modifier that scales-up and cyan-glows on D-pad focus (mirrors .tv-card CSS). */
fun Modifier.tvFocusable(
    scaleOnFocus: Float = 1.06f,
    shape: Shape = RoundedCornerShape(16.dp),
    borderColor: Color = Cyan
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleOnFocus else 1f,
        animationSpec = tween(150),
        label = "focusScale"
    )
    this
        .scale(scale)
        .shadow(
            elevation = if (focused) 24.dp else 0.dp,
            shape = shape,
            ambientColor = Cyan,
            spotColor = Cyan
        )
        .border(
            width = if (focused) 3.dp else 0.dp,
            color = if (focused) borderColor else Color.Transparent,
            shape = shape
        )
        .onFocusChanged { focused = it.isFocused }
        .focusable()
}

/** "hush" + "tv." wordmark from the React code. */
@Composable
fun HushTVLogo(
    fontSize: TextUnit = 48.sp,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("hush", color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Black)
        Text("tv.", color = Cyan, fontSize = fontSize, fontWeight = FontWeight.Black)
    }
}
