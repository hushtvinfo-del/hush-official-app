package com.hushtv.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HushDarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = Color.Black,
    secondary = Blue,
    background = BgBlack,
    onBackground = TextPrimary,
    surface = SurfaceNavy,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElev,
    onSurfaceVariant = TextSecondary,
    outline = BorderSlate,
    error = Red,
    onError = Color.Black,
)

@Composable
fun HushTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HushDarkColors,
        typography = HushTypography,
        content = content,
    )
}
