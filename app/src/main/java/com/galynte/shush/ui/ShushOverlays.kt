// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galynte.shush.playback.FADE_MS
import com.galynte.shush.playback.MAX_CUSTOM_MINUTES
import com.galynte.shush.playback.formatMinutes
import com.galynte.shush.ui.theme.Card
import com.galynte.shush.ui.theme.Lavender
import com.galynte.shush.ui.theme.Night
import com.galynte.shush.ui.theme.NightRaised
import com.galynte.shush.ui.theme.Scrim
import com.galynte.shush.ui.theme.TextMuted
import com.galynte.shush.ui.theme.TextPrimary

@Composable
fun CustomTimerOverlay(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val start = initialMinutes.coerceIn(0, MAX_CUSTOM_MINUTES)
    var hours by remember { mutableIntStateOf(start / 60) }
    var minutes by remember { mutableIntStateOf(start % 60) }
    val total = hours * 60 + minutes
    Box(Modifier.fillMaxSize()) {
        Scrim(onDismiss)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(NightRaised)
                .consumeClicks()
                .padding(24.dp),
        ) {
            Text(
                text = "CUSTOM TIMER",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatMinutes(total),
                color = TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Fades out over the last ${FADE_MS / 1000} seconds.",
                color = TextMuted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stepper(
                    label = "Hours",
                    value = hours.toString(),
                    minusEnabled = hours > 0,
                    plusEnabled = hours < 23,
                    onMinus = { if (hours > 0) hours -= 1 },
                    onPlus = { if (hours < 23) hours += 1 },
                )
                Stepper(
                    label = "Minutes",
                    value = minutes.toString().padStart(2, '0'),
                    minusEnabled = minutes > 0,
                    plusEnabled = minutes < 59,
                    onMinus = { if (minutes > 0) minutes -= 1 },
                    onPlus = { if (minutes < 59) minutes += 1 },
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverlayButton(
                    label = "Cancel",
                    filled = false,
                    enabled = true,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                OverlayButton(
                    label = "Set timer",
                    filled = true,
                    enabled = total >= 1,
                    onClick = { if (total >= 1) onSave(total) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun AboutOverlay(versionName: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Scrim(onDismiss)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(NightRaised)
                .consumeClicks()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Night),
                contentAlignment = Alignment.Center,
            ) {
                ShushMark(Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Shush",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "A hush for the night.",
                color = Lavender,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "White, pink, and brown noise, generated on this phone. " +
                    "A timer eases the sound out instead of cutting it off.",
                color = TextMuted,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "No account, no ads, and no network.",
                color = TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "$versionName  ·  Apache License 2.0",
                color = TextMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(18.dp))
            OverlayButton(
                label = "Done",
                filled = true,
                enabled = true,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Scrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onDismiss,
            ),
    )
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", minusEnabled, onMinus)
            Text(
                text = value,
                modifier = Modifier.width(52.dp),
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            StepButton("+", plusEnabled, onPlus)
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Card.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Lavender else TextMuted,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun OverlayButton(
    label: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        !enabled -> Lavender.copy(alpha = 0.35f)
        filled -> Lavender
        else -> Card
    }
    val foreground = when {
        !enabled -> Night.copy(alpha = 0.6f)
        filled -> Night
        else -> TextPrimary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Modifier.consumeClicks(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        }
    }
}
