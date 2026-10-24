// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.galynte.shush.playback.NoiseColor
import com.galynte.shush.playback.ShushUiState
import com.galynte.shush.playback.TimerMode
import com.galynte.shush.ui.ShushScreen
import com.galynte.shush.ui.theme.ShushTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the README from the real Compose UI.
 * Images land in app/build/screenshots and are copied to docs/assets/images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-420dpi")
class ReadmeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home() = shot(idle().copy(sound = NoiseColor.BROWN), "home.png")

    @Test
    fun playing() = shot(
        idle().copy(
            sound = NoiseColor.WHITE,
            volume = 0.62f,
            timerMode = TimerMode.MIN_30,
            isPlaying = true,
            remainingMs = (28 * 60 + 14) * 1000L,
        ),
        "playing.png",
        phase = 0.18f,
    )

    @Test
    fun customTimer() = shot(
        idle().copy(customMinutes = 90),
        "custom_timer.png",
        showCustomTimer = true,
    )

    @Test
    fun about() = shot(idle(), "about.png", showAbout = true)

    private fun idle() = ShushUiState(
        sound = NoiseColor.BROWN,
        volume = 0.22f,
        timerMode = TimerMode.OFF,
        customMinutes = 45,
        isPlaying = false,
        isPaused = false,
        remainingMs = null,
        isFading = false,
    )

    private fun shot(
        state: ShushUiState,
        name: String,
        phase: Float = 0f,
        showCustomTimer: Boolean = false,
        showAbout: Boolean = false,
    ) {
        composeRule.setContent {
            ShushTheme {
                ShushScreen(
                    state = state,
                    waveformPhase = phase,
                    showCustomTimer = showCustomTimer,
                    showAbout = showAbout,
                    versionName = "0.0.2",
                    onSelectSound = {},
                    onVolume = {},
                    onPlayPause = {},
                    onSelectTimer = {},
                    onOpenCustomTimer = {},
                    onDismissCustomTimer = {},
                    onSaveCustomTimer = {},
                    onOpenAbout = {},
                    onDismissAbout = {},
                )
            }
        }
        composeRule.onRoot().save(name)
    }

    private fun file(name: String) = java.io.File("build/screenshots", name).apply {
        parentFile?.mkdirs()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun SemanticsNodeInteraction.save(name: String) {
        captureRoboImage(file(name), RoborazziOptions(taskType = RoborazziTaskType.Record))
    }
}
