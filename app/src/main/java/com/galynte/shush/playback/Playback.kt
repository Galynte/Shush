// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.playback

/** How long the sound takes to reach silence at the end of a timer. */
const val FADE_MS = 12_000L

const val MIN_CUSTOM_MINUTES = 1
const val MAX_CUSTOM_MINUTES = 8 * 60

enum class NoiseColor(val label: String, val description: String) {
    WHITE("White", "Steady, full spectrum hush"),
    PINK("Pink", "Softer, like rain on a roof"),
    BROWN("Brown", "Deep, like a distant ocean"),
}

enum class TimerMode {
    OFF,
    MIN_15,
    MIN_30,
    HOUR_1,
    CUSTOM,
    ;

    fun durationMinutes(customMinutes: Int): Int? = when (this) {
        OFF -> null
        MIN_15 -> 15
        MIN_30 -> 30
        HOUR_1 -> 60
        CUSTOM -> customMinutes
    }
}

data class ShushState(
    val sound: NoiseColor = NoiseColor.BROWN,
    val volume: Float = 0.22f,
    val timerMode: TimerMode = TimerMode.OFF,
    val customMinutes: Int = 45,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val timerEndEpochMs: Long? = null,
    val pausedRemainingMs: Long? = null,
)

data class ShushUiState(
    val sound: NoiseColor,
    val volume: Float,
    val timerMode: TimerMode,
    val customMinutes: Int,
    val isPlaying: Boolean,
    val isPaused: Boolean,
    val remainingMs: Long?,
    val isFading: Boolean,
)

fun clampVolume(volume: Float): Float = if (volume.isNaN()) 0f else volume.coerceIn(0f, 1f)

fun clampCustomMinutes(minutes: Int): Int = minutes.coerceIn(MIN_CUSTOM_MINUTES, MAX_CUSTOM_MINUTES)

/** Linear fade across the last [FADE_MS]. Open-ended playback stays at full level. */
fun fadeFactor(remainingMs: Long?): Float {
    if (remainingMs == null) return 1f
    if (remainingMs <= 0L) return 0f
    if (remainingMs >= FADE_MS) return 1f
    return remainingMs.toFloat() / FADE_MS.toFloat()
}

/**
 * Quiet at the bottom of the slider, full level at the top.
 * A straight line makes the low settings used at night too loud.
 */
fun shapedGain(volume: Float, fade: Float): Float {
    val v = clampVolume(volume)
    val shaped = v * (0.35f + 0.65f * v)
    return (shaped * fade.coerceIn(0f, 1f)).coerceIn(0f, 1f)
}

fun formatMinutes(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val mins = safe % 60
    return when {
        hours <= 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}

fun formatRemaining(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

object PlaybackReducer {
    fun selectSound(state: ShushState, sound: NoiseColor): ShushState = state.copy(sound = sound)

    fun setVolume(state: ShushState, volume: Float): ShushState = state.copy(volume = clampVolume(volume))

    fun selectTimer(state: ShushState, mode: TimerMode, nowEpochMs: Long): ShushState =
        retimed(state.copy(timerMode = mode), nowEpochMs)

    fun setCustomMinutes(state: ShushState, minutes: Int, nowEpochMs: Long): ShushState =
        retimed(
            state.copy(timerMode = TimerMode.CUSTOM, customMinutes = clampCustomMinutes(minutes)),
            nowEpochMs,
        )

    fun play(state: ShushState, nowEpochMs: Long): ShushState {
        if (state.isPlaying) return state
        val minutes = state.timerMode.durationMinutes(state.customMinutes)
        val end = when {
            minutes == null -> null
            state.isPaused && state.pausedRemainingMs != null -> nowEpochMs + state.pausedRemainingMs
            else -> nowEpochMs + minutes * 60_000L
        }
        return state.copy(
            isPlaying = true,
            isPaused = false,
            timerEndEpochMs = end,
            pausedRemainingMs = null,
        )
    }

    fun pause(state: ShushState, nowEpochMs: Long): ShushState {
        if (!state.isPlaying) return state
        val remaining = state.timerEndEpochMs?.let { (it - nowEpochMs).coerceAtLeast(0L) }
        return state.copy(
            isPlaying = false,
            isPaused = true,
            timerEndEpochMs = null,
            pausedRemainingMs = remaining,
        )
    }

    fun stop(state: ShushState): ShushState = state.copy(
        isPlaying = false,
        isPaused = false,
        timerEndEpochMs = null,
        pausedRemainingMs = null,
    )

    fun toUi(state: ShushState, nowEpochMs: Long): ShushUiState {
        val remaining = when {
            state.isPlaying && state.timerEndEpochMs != null ->
                (state.timerEndEpochMs - nowEpochMs).coerceAtLeast(0L)
            state.isPaused -> state.pausedRemainingMs
            else -> null
        }
        val fading = state.isPlaying && remaining != null && remaining in 1 until FADE_MS
        return ShushUiState(
            sound = state.sound,
            volume = state.volume,
            timerMode = state.timerMode,
            customMinutes = state.customMinutes,
            isPlaying = state.isPlaying,
            isPaused = state.isPaused,
            remainingMs = remaining,
            isFading = fading,
        )
    }

    fun statusLine(state: ShushState, nowEpochMs: Long): String {
        val ui = toUi(state, nowEpochMs)
        val name = state.sound.label
        return when {
            ui.isFading -> "$name · Fading out"
            state.isPlaying && ui.remainingMs != null -> "$name · ${formatRemaining(ui.remainingMs)} left"
            state.isPlaying -> name
            state.isPaused && ui.remainingMs != null -> "Paused · ${formatRemaining(ui.remainingMs)} left"
            state.isPaused -> "Paused"
            else -> "Stopped"
        }
    }

    private fun retimed(state: ShushState, nowEpochMs: Long): ShushState {
        val minutes = state.timerMode.durationMinutes(state.customMinutes)
            ?: return state.copy(timerEndEpochMs = null, pausedRemainingMs = null)
        val duration = minutes * 60_000L
        return when {
            state.isPlaying -> state.copy(timerEndEpochMs = nowEpochMs + duration, pausedRemainingMs = null)
            state.isPaused -> state.copy(timerEndEpochMs = null, pausedRemainingMs = duration)
            else -> state.copy(timerEndEpochMs = null, pausedRemainingMs = null)
        }
    }
}
