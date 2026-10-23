// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sound, volume, and timer choice live in [settings]. The in-progress session lives
 * in a second file that is excluded from backup, and is only restored by the service
 * after the process is recreated while playback was running.
 */
class PlaybackStore(context: Context) {
    private val settings = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
    private val session = context.getSharedPreferences(SESSION, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readSettings())
    val state: StateFlow<ShushState> = _state.asStateFlow()

    fun selectSound(sound: NoiseColor) = update { PlaybackReducer.selectSound(it, sound) }

    fun setVolume(volume: Float) = update { PlaybackReducer.setVolume(it, volume) }

    fun selectTimer(mode: TimerMode, nowEpochMs: Long = System.currentTimeMillis()) =
        update { PlaybackReducer.selectTimer(it, mode, nowEpochMs) }

    fun setCustomMinutes(minutes: Int, nowEpochMs: Long = System.currentTimeMillis()) =
        update { PlaybackReducer.setCustomMinutes(it, minutes, nowEpochMs) }

    fun play(nowEpochMs: Long = System.currentTimeMillis()) =
        update { PlaybackReducer.play(it, nowEpochMs) }

    fun pause(nowEpochMs: Long = System.currentTimeMillis()) =
        update { PlaybackReducer.pause(it, nowEpochMs) }

    fun stop() = update { PlaybackReducer.stop(it) }

    fun restoreSession(nowEpochMs: Long = System.currentTimeMillis()) {
        val playing = session.getBoolean(KEY_PLAYING, false)
        val paused = session.getBoolean(KEY_PAUSED, false)
        if (!playing && !paused) return
        val end = session.getLong(KEY_END, -1L).takeIf { it >= 0L }
        if (playing && end != null && nowEpochMs >= end) {
            stop()
            return
        }
        val remaining = session.getLong(KEY_REMAINING, -1L).takeIf { it >= 0L }
        update { current ->
            current.copy(
                isPlaying = playing,
                isPaused = paused,
                timerEndEpochMs = end,
                pausedRemainingMs = remaining,
            )
        }
    }

    private fun update(transform: (ShushState) -> ShushState) {
        synchronized(this) {
            val previous = _state.value
            val next = transform(previous)
            if (next == previous) return
            _state.value = next
            persist(previous, next)
        }
    }

    private fun readSettings(): ShushState {
        val sound = enumOr(settings.getString(KEY_SOUND, null), NoiseColor.BROWN)
        val mode = enumOr(settings.getString(KEY_TIMER, null), TimerMode.OFF)
        val custom = clampCustomMinutes(settings.getInt(KEY_CUSTOM, 45))
        val volume = clampVolume(settings.getFloat(KEY_VOLUME, 0.22f))
        return ShushState(
            sound = sound,
            volume = volume,
            timerMode = mode,
            customMinutes = custom,
        )
    }

    private fun persist(previous: ShushState, state: ShushState) {
        val settingsChanged = previous.sound != state.sound ||
            previous.volume != state.volume ||
            previous.timerMode != state.timerMode ||
            previous.customMinutes != state.customMinutes
        val sessionChanged = previous.isPlaying != state.isPlaying ||
            previous.isPaused != state.isPaused ||
            previous.timerEndEpochMs != state.timerEndEpochMs ||
            previous.pausedRemainingMs != state.pausedRemainingMs
        if (settingsChanged) {
            settings.edit()
                .putString(KEY_SOUND, state.sound.name)
                .putFloat(KEY_VOLUME, state.volume)
                .putString(KEY_TIMER, state.timerMode.name)
                .putInt(KEY_CUSTOM, state.customMinutes)
                .apply()
        }
        if (sessionChanged) {
            session.edit()
                .putBoolean(KEY_PLAYING, state.isPlaying)
                .putBoolean(KEY_PAUSED, state.isPaused)
                .putLong(KEY_END, state.timerEndEpochMs ?: -1L)
                .putLong(KEY_REMAINING, state.pausedRemainingMs ?: -1L)
                .commit()
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T {
        if (name == null) return default
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }

    private companion object {
        const val SETTINGS = "shush_settings"
        const val SESSION = "shush_session"
        const val KEY_SOUND = "sound"
        const val KEY_VOLUME = "volume"
        const val KEY_TIMER = "timer_mode"
        const val KEY_CUSTOM = "custom_minutes"
        const val KEY_PLAYING = "playing"
        const val KEY_PAUSED = "paused"
        const val KEY_END = "timer_end"
        const val KEY_REMAINING = "paused_remaining"
    }
}
