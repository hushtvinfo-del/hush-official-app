package com.hushtv.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF06B6D4)
val CyanDim = Color(0x6606B6D4)
val Blue = Color(0xFF3B82F6)
val Purple = Color(0xFF8B5CF6)
val Pink = Color(0xFFEC4899)
val Amber = Color(0xFFF59E0B)
val Green = Color(0xFF10B981)
val Red = Color(0xFFEF4444)
val Background = Color.Black
val Surface = Color(0xFF0A0A0A)
val CardBg = Color(0x14FFFFFF)    // rgba(255,255,255,0.08)
val CardBorder = Color(0x26FFFFFF) // rgba(255,255,255,0.15)
val TextSecondary = Color(0xFF9CA3AF)

private val DarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = Color.Black,
    secondary = Blue,
    background = Background,
    onBackground = Color.White,
    surface = Surface,
    onSurface = Color.White
)

@Composable
fun HushTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
