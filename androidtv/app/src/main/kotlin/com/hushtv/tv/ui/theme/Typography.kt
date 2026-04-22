package com.hushtv.tv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hushtv.tv.R

/** Inter variable font embedded in res/font. Exposes 400 / 500 / 600 / 700 / 900. */
val Inter: FontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
    Font(R.font.inter, FontWeight.Black),
)

/** Typography scale from the HushTV design spec. */
val HushTypography = Typography(
    // Display / logo
    displayLarge  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black,    fontSize = 72.sp, letterSpacing = (-2.2).sp),
    displayMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black,    fontSize = 56.sp, letterSpacing = (-1.7).sp),

    // Page / section titles
    headlineLarge  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold,     fontSize = 40.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold,     fontSize = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),

    titleLarge  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleSmall  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),

    bodyLarge  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp),

    // Metadata / labels
    labelLarge  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 1.5.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.4.sp),
    labelSmall  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.3.sp),
)
