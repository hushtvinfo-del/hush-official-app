package com.hushtv.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.ui.diagnostics.SpeedTestPane
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan

/** Standalone TV screen wrapping [SpeedTestPane] with a back chip. */
@Composable
fun TVSpeedTestScreen(nav: NavController) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(horizontal = 64.dp, vertical = 32.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header with back chip.
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChip(onClick = { nav.popBackStack() })
                Spacer(Modifier.width(20.dp))
                Text(
                    "Speed Test",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(20.dp))
            SpeedTestPane(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BackChip(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .background(
                if (focused) Cyan.copy(alpha = 0.22f) else Color(0x14FFFFFF),
                RoundedCornerShape(20.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.ArrowBack, null,
            tint = if (focused) Cyan else Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Back",
            color = if (focused) Cyan else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
