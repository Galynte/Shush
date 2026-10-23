// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF141228)
val NightRaised = Color(0xFF1C1834)
val Card = Color(0xFF2A2648)
val CardSelected = Color(0xFF342E5C)
val Lavender = Color(0xFFD5C9FF)
val LavenderBright = Color(0xFFE6DEFF)
val LavenderDeep = Color(0xFFB7A6F2)
val TextPrimary = Color(0xFFF3F0FF)
val TextMuted = Color(0xFFA39BBF)
val DotIdle = Color(0xFF4A4568)
val PlayTop = Color(0xFF34315C)
val PlayBottom = Color(0xFF1E1B38)
val Scrim = Color(0xC0100E22)

private val ShushColors = darkColorScheme(
    primary = Lavender,
    onPrimary = Night,
    secondary = LavenderDeep,
    onSecondary = Night,
    background = Night,
    onBackground = TextPrimary,
    surface = NightRaised,
    onSurface = TextPrimary,
    surfaceContainer = Card,
    surfaceContainerHigh = CardSelected,
)

@Composable
fun ShushTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShushColors,
        typography = Typography(),
        content = content,
    )
}
