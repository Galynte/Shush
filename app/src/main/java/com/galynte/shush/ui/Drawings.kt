// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.galynte.shush.playback.NoiseColor
import com.galynte.shush.ui.theme.Lavender
import com.galynte.shush.ui.theme.LavenderBright
import com.galynte.shush.ui.theme.TextMuted
import kotlin.math.sin

/**
 * Crescent with three short bars in the opening. The same geometry is drawn
 * by tools/render_brand.py for the launcher and the Play listing.
 *
 * Coordinates are in a 108 grid, which is the adaptive-icon viewport.
 * Moon outer (45, 54) r19, cut (55, 50) r15.5.
 * Bars at x 66 / 73.5 / 81, heights 12 / 22 / 14, centered on y 54.
 */
@Composable
fun ShushMark(modifier: Modifier = Modifier, monochrome: Boolean = false) {
    val moon = if (monochrome) Color.White else LavenderBright
    val bars = if (monochrome) Color.White else Lavender
    Canvas(modifier) {
        val side = size.minDimension
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        fun px(v: Float) = left + v / 108f * side
        fun py(v: Float) = top + v / 108f * side
        fun r(v: Float) = v / 108f * side

        if (!monochrome) {
            drawCircle(
                color = Lavender.copy(alpha = 0.16f),
                radius = r(28f),
                center = Offset(px(50f), py(54f)),
            )
        }
        val crescent = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(Offset(px(45f), py(54f)), r(19f)))
            addOval(Rect(Offset(px(55f), py(50f)), r(15.5f)))
        }
        drawPath(crescent, moon)
        drawBar(px(66f), py(48f), r(5f), r(12f), bars)
        drawBar(px(73.5f), py(43f), r(5f), r(22f), bars)
        drawBar(px(81f), py(47f), r(5f), r(14f), bars)
    }
}

private fun DrawScope.drawBar(x: Float, y: Float, w: Float, h: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(w / 2f, w / 2f),
    )
}

@Composable
fun SoundWave(
    color: NoiseColor,
    phase: Float,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) LavenderBright else TextMuted
    Canvas(modifier) {
        when (color) {
            NoiseColor.WHITE -> drawBars(18, tint) { index, _ ->
                val base = WHITE_PATTERN[index % WHITE_PATTERN.size]
                base + 0.05f * sin(index * 0.85 + phase * TWO_PI).toFloat()
            }
            NoiseColor.PINK -> drawBars(18, tint) { index, count ->
                val t = index / (count - 1f)
                val arch = sin(t * Math.PI).toFloat()
                0.24f + arch * 0.58f + 0.06f * sin(t * 8.0 + phase * TWO_PI).toFloat()
            }
            NoiseColor.BROWN -> drawBars(14, tint) { index, count ->
                val t = index / (count - 1f)
                val wave = 0.5f + 0.5f * sin(t * TWO_PI + phase * TWO_PI).toFloat()
                val ends = sin(t * Math.PI).toFloat()
                0.2f + wave * 0.68f * (0.45f + 0.55f * ends)
            }
        }
    }
}

private fun DrawScope.drawBars(count: Int, color: Color, heightFor: (Int, Int) -> Float) {
    val slot = size.width / count
    val barW = slot * 0.42f
    for (index in 0 until count) {
        val fraction = heightFor(index, count).coerceIn(0.12f, 1f)
        val barH = fraction * size.height
        val x = index * slot + (slot - barW) / 2f
        val y = (size.height - barH) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )
    }
}

private const val TWO_PI = Math.PI * 2.0

private val WHITE_PATTERN = floatArrayOf(
    0.42f, 0.78f, 0.55f, 0.92f, 0.48f, 0.70f, 0.86f, 0.38f,
    0.74f, 0.60f, 0.95f, 0.50f, 0.82f, 0.44f, 0.68f, 0.88f,
)
