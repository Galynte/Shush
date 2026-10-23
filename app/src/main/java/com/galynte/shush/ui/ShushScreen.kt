// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import com.galynte.shush.playback.NoiseColor
import com.galynte.shush.playback.ShushUiState
import com.galynte.shush.playback.TimerMode
import com.galynte.shush.playback.formatMinutes
import com.galynte.shush.playback.formatRemaining
import com.galynte.shush.ui.theme.Card
import com.galynte.shush.ui.theme.CardSelected
import com.galynte.shush.ui.theme.DotIdle
import com.galynte.shush.ui.theme.Lavender
import com.galynte.shush.ui.theme.LavenderBright
import com.galynte.shush.ui.theme.LavenderDeep
import com.galynte.shush.ui.theme.Night
import com.galynte.shush.ui.theme.PlayBottom
import com.galynte.shush.ui.theme.PlayTop
import com.galynte.shush.ui.theme.TextMuted
import com.galynte.shush.ui.theme.TextPrimary
import kotlin.math.roundToInt

@Composable
fun ShushScreen(
    state: ShushUiState,
    waveformPhase: Float,
    showCustomTimer: Boolean,
    showAbout: Boolean,
    versionName: String,
    onSelectSound: (NoiseColor) -> Unit,
    onVolume: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onSelectTimer: (TimerMode) -> Unit,
    onOpenCustomTimer: () -> Unit,
    onDismissCustomTimer: () -> Unit,
    onSaveCustomTimer: (Int) -> Unit,
    onOpenAbout: () -> Unit,
    onDismissAbout: () -> Unit,
) {
    BackHandler(enabled = showCustomTimer || showAbout) {
        if (showCustomTimer) onDismissCustomTimer() else onDismissAbout()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fontScale = LocalDensity.current.fontScale
            val roomy = maxHeight >= 700.dp && fontScale <= 1.15f
            Column(
                modifier = Modifier
                    .then(
                        if (roomy) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.verticalScroll(rememberScrollState())
                        },
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp),
            ) {
                Header(onOpenAbout)
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "SELECT A SOUND",
                    modifier = Modifier.fillMaxWidth(),
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.6.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                SoundRow(state.sound, waveformPhase, onSelectSound)
                if (roomy) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(36.dp))
                PlayControl(state, onPlayPause)
                if (roomy) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(36.dp))
                SectionLabel("VOLUME")
                Spacer(Modifier.height(6.dp))
                VolumeSlider(state.volume, onVolume)
                Spacer(Modifier.height(22.dp))
                SectionLabel("SLEEP TIMER")
                Spacer(Modifier.height(10.dp))
                TimerRow(state, onSelectTimer, onOpenCustomTimer)
                Spacer(Modifier.height(12.dp))
            }
        }
        if (showCustomTimer) {
            CustomTimerOverlay(
                initialMinutes = state.customMinutes,
                onDismiss = onDismissCustomTimer,
                onSave = onSaveCustomTimer,
            )
        }
        if (showAbout) {
            AboutOverlay(versionName = versionName, onDismiss = onDismissAbout)
        }
    }
}

@Composable
private fun Header(onOpenAbout: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ShushMark(
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Shush" },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "SHUSH",
                color = TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.2.sp,
            )
            Text(
                text = "White Noise",
                color = TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
            )
        }
        Box {
            var open by remember { mutableStateOf(false) }
            IconButton(onClick = { open = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = TextMuted)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                containerColor = Card,
            ) {
                DropdownMenuItem(
                    text = { Text("About", color = TextPrimary) },
                    onClick = {
                        open = false
                        onOpenAbout()
                    },
                )
            }
        }
    }
}

@Composable
private fun SoundRow(
    selected: NoiseColor,
    phase: Float,
    onSelect: (NoiseColor) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NoiseColor.entries.forEach { color ->
            SoundCard(
                color = color,
                selected = color == selected,
                phase = phase,
                onClick = { onSelect(color) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SoundCard(
    color: NoiseColor,
    selected: Boolean,
    phase: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .border(1.5.dp, if (selected) LavenderDeep else LavenderDeep.copy(alpha = 0.0f), shape)
            .background(if (selected) CardSelected else Card, shape)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SoundWave(
                color = color,
                phase = if (selected) phase else 0f,
                selected = selected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = color.label,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(Lavender, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Night,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayControl(state: ShushUiState, onPlayPause: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            if (state.isPlaying) {
                Box(
                    modifier = Modifier
                        .size(184.dp)
                        .border(1.5.dp, Lavender.copy(alpha = 0.4f), CircleShape),
                )
            }
            Box(
                modifier = Modifier
                    .size(152.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = CircleShape,
                        ambientColor = Night.copy(alpha = 0.8f),
                        spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                    )
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(PlayTop, PlayBottom)))
                    .semantics { contentDescription = if (state.isPlaying) "Pause" else "Play" }
                    .clickable(onClick = onPlayPause, role = Role.Button),
                contentAlignment = Alignment.Center,
            ) {
                PlayGlyph(playing = state.isPlaying, modifier = Modifier.size(64.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = caption(state),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 20.dp),
            color = if (state.isFading) Lavender else TextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun caption(state: ShushUiState): String = when {
    state.isFading -> "Fading out"
    state.isPlaying && state.remainingMs != null -> "${formatRemaining(state.remainingMs)} left"
    state.isPaused && state.remainingMs != null -> "${formatRemaining(state.remainingMs)} paused"
    state.isPaused -> "Paused"
    else -> ""
}

@Composable
private fun PlayGlyph(playing: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (playing) {
            val barW = size.width * 0.22f
            val gap = size.width * 0.16f
            val barH = size.height * 0.62f
            val top = (size.height - barH) / 2f
            val left = (size.width - (barW * 2f + gap)) / 2f
            val radius = CornerRadius(barW / 3f, barW / 3f)
            drawRoundRect(LavenderBright, Offset(left, top), Size(barW, barH), radius)
            drawRoundRect(LavenderBright, Offset(left + barW + gap, top), Size(barW, barH), radius)
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.36f, size.height * 0.22f)
                lineTo(size.width * 0.78f, size.height * 0.5f)
                lineTo(size.width * 0.36f, size.height * 0.78f)
                close()
            }
            drawPath(path, LavenderBright)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.6.sp,
    )
}

@Composable
private fun VolumeSlider(volume: Float, onVolume: (Float) -> Unit) {
    val percent = (volume * 100).roundToInt()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                contentDescription = "Volume $percent percent"
                progressBarRangeInfo = ProgressBarRangeInfo(volume, 0f..1f)
                setProgress { next ->
                    onVolume(next)
                    true
                }
            }
            .pointerInput(Unit) {
                fun at(x: Float): Float {
                    val thumb = 6.dp.toPx()
                    val usable = (size.width - thumb).coerceAtLeast(1f)
                    return ((x - thumb / 2f) / usable).coerceIn(0f, 1f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onVolume(at(down.position.x))
                    drag(down.id) { change ->
                        onVolume(at(change.position.x))
                        change.consume()
                    }
                }
            },
    ) {
        val thumbW = 6.dp.toPx()
        val usable = size.width - thumbW
        val thumbX = thumbW / 2f + volume.coerceIn(0f, 1f) * usable
        val count = 26
        val midY = size.height / 2f
        for (index in 0 until count) {
            val cx = if (count == 1) size.width / 2f else index * (size.width - 1f) / (count - 1f)
            drawCircle(
                color = if (cx <= thumbX) Lavender else DotIdle,
                radius = 2.5.dp.toPx(),
                center = Offset(cx, midY),
            )
        }
        drawRoundRect(
            color = LavenderBright,
            topLeft = Offset(thumbX - thumbW / 2f, midY - 14.dp.toPx()),
            size = Size(thumbW, 28.dp.toPx()),
            cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f),
        )
    }
}

@Composable
private fun TimerRow(
    state: ShushUiState,
    onSelectTimer: (TimerMode) -> Unit,
    onOpenCustom: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimerChip("Off", state.timerMode == TimerMode.OFF) { onSelectTimer(TimerMode.OFF) }
        TimerChip("15m", state.timerMode == TimerMode.MIN_15) { onSelectTimer(TimerMode.MIN_15) }
        TimerChip("30m", state.timerMode == TimerMode.MIN_30) { onSelectTimer(TimerMode.MIN_30) }
        TimerChip("1h", state.timerMode == TimerMode.HOUR_1) { onSelectTimer(TimerMode.HOUR_1) }
        val customLabel = if (state.timerMode == TimerMode.CUSTOM) {
            formatMinutes(state.customMinutes)
        } else {
            "Custom"
        }
        TimerChip(customLabel, state.timerMode == TimerMode.CUSTOM, onOpenCustom)
    }
}

@Composable
private fun TimerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Lavender else Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Night else TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
