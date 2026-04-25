package com.hushtv.tv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hushtv.tv.ui.diagnostics.SpeedTestPane
import com.hushtv.tv.ui.theme.BgBlack

/** Mobile speed-test screen — touch-friendly. */
@Composable
fun MobileSpeedTestScreen(nav: NavController) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BgBlack)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .clickable { nav.popBackStack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ArrowBack, null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Speed Test",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            SpeedTestPane(modifier = Modifier.weight(1f))
        }
    }
}
