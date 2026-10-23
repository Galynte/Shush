// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackReducerTest {
    private val now = 1_700_000_000_000L

    @Test
    fun playWithPresetArmsTheTimer() {
        val started = PlaybackReducer.play(ShushState(timerMode = TimerMode.MIN_30), now)
        assertTrue(started.isPlaying)
        assertEquals(now + 30 * 60_000L, started.timerEndEpochMs)
    }

    @Test
    fun openEndedPlayHasNoEnd() {
        val started = PlaybackReducer.play(ShushState(timerMode = TimerMode.OFF), now)
        assertTrue(started.isPlaying)
        assertNull(started.timerEndEpochMs)
    }

    @Test
    fun pauseFreezesRemainingAndResumeContinuesIt() {
        val playing = PlaybackReducer.play(ShushState(timerMode = TimerMode.MIN_15), now)
        val paused = PlaybackReducer.pause(playing, now + 60_000L)
        assertTrue(paused.isPaused)
        assertFalse(paused.isPlaying)
        assertEquals(14 * 60_000L, paused.pausedRemainingMs)
        val resumed = PlaybackReducer.play(paused, now + 90_000L)
        assertEquals(now + 90_000L + 14 * 60_000L, resumed.timerEndEpochMs)
        assertFalse(resumed.isPaused)
    }

    @Test
    fun changingTheTimerWhilePlayingRestartsTheCountdown() {
        val playing = PlaybackReducer.play(ShushState(timerMode = TimerMode.MIN_15), now)
        val changed = PlaybackReducer.selectTimer(playing, TimerMode.HOUR_1, now + 5_000L)
        assertEquals(now + 5_000L + 60 * 60_000L, changed.timerEndEpochMs)
    }

    @Test
    fun turningTheTimerOffKeepsTheSoundGoing() {
        val playing = PlaybackReducer.play(ShushState(timerMode = TimerMode.MIN_15), now)
        val off = PlaybackReducer.selectTimer(playing, TimerMode.OFF, now + 1_000L)
        assertTrue(off.isPlaying)
        assertNull(off.timerEndEpochMs)
    }

    @Test
    fun customMinutesAreClampedAndSelected() {
        val state = PlaybackReducer.setCustomMinutes(ShushState(), 24 * 60, now)
        assertEquals(TimerMode.CUSTOM, state.timerMode)
        assertEquals(MAX_CUSTOM_MINUTES, state.customMinutes)
        val low = PlaybackReducer.setCustomMinutes(ShushState(), 0, now)
        assertEquals(MIN_CUSTOM_MINUTES, low.customMinutes)
    }

    @Test
    fun stopClearsTheSession() {
        val playing = PlaybackReducer.play(ShushState(timerMode = TimerMode.MIN_30), now)
        val stopped = PlaybackReducer.stop(playing)
        assertFalse(stopped.isPlaying)
        assertFalse(stopped.isPaused)
        assertNull(stopped.timerEndEpochMs)
        assertEquals(TimerMode.MIN_30, stopped.timerMode)
    }

    @Test
    fun fadeAndStatusFollowTheRemainingTime() {
        assertEquals(1f, fadeFactor(null))
        assertEquals(1f, fadeFactor(FADE_MS))
        assertEquals(0.5f, fadeFactor(FADE_MS / 2))
        assertEquals(0f, fadeFactor(0))
        val playing = ShushState(
            sound = NoiseColor.BROWN,
            isPlaying = true,
            timerMode = TimerMode.MIN_30,
            timerEndEpochMs = now + 5_000L,
        )
        val ui = PlaybackReducer.toUi(playing, now)
        assertTrue(ui.isFading)
        assertEquals("Brown · Fading out", PlaybackReducer.statusLine(playing, now))
        val paused = PlaybackReducer.pause(playing, now)
        assertFalse(PlaybackReducer.toUi(paused, now).isFading)
    }

    @Test
    fun volumeRejectsNanAndFormatsDurations() {
        assertEquals(0f, clampVolume(Float.NaN))
        assertEquals(1f, clampVolume(4f))
        assertEquals("45m", formatMinutes(45))
        assertEquals("1h", formatMinutes(60))
        assertEquals("1h 30m", formatMinutes(90))
        assertEquals("28:14", formatRemaining((28 * 60 + 14) * 1000L))
        assertEquals("1:01:01", formatRemaining((3600 + 61) * 1000L))
    }
}
