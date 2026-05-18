package com.hushtv.tv.ui.canada

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders a QR code at the requested size by drawing the underlying
 * BitMatrix directly onto a Compose Canvas. No Bitmap allocation, no
 * Android-Graphics dependency surface — works in TV + mobile + preview.
 *
 * @param content the payload to encode. Keep it under ~300 chars or the
 *   resulting QR matrix gets too dense for cheap phone cameras to read.
 * @param size edge length of the rendered square.
 * @param fg foreground (dark module) colour — default pure black for max
 *   contrast against the white background.
 * @param bg background (light module) colour — default white. A coloured
 *   background here breaks scan-ability on most banking apps.
 */
@Composable
fun QrCode(
    content: String,
    size: Dp,
    fg: Color = Color.Black,
    bg: Color = Color.White,
) {
    val matrix = remember(content) {
        runCatching {
            QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
        }.getOrNull()
    }
    Canvas(modifier = Modifier.size(size).background(bg)) {
        val m = matrix ?: return@Canvas
        val w = m.width
        val h = m.height
        if (w <= 0 || h <= 0) return@Canvas
        val cellW = this.size.width / w
        val cellH = this.size.height / h
        // Slight overdraw (+0.5 px) on each cell eliminates the hairline
        // gaps that show up when the canvas size doesn't divide evenly
        // into the matrix grid — those hairlines kill scan reliability
        // on phones with low-res rear cameras.
        val cellSize = Size(cellW + 0.5f, cellH + 0.5f)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (m.get(x, y)) {
                    drawRect(
                        color = fg,
                        topLeft = Offset(x * cellW, y * cellH),
                        size = cellSize,
                    )
                }
            }
        }
    }
}
