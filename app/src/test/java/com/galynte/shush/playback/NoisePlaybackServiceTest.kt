// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.playback

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.galynte.shush.ShushApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The sleep timer ends on the audio thread. [NoisePlaybackService] has to hop
 * back to the main thread on API 26, where [android.content.Context.getMainExecutor]
 * does not exist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28])
class NoisePlaybackServiceTest {
    @Test
    fun timerEndStopsTheSessionOnTheMainThread() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        app.store.selectTimer(TimerMode.MIN_15)
        app.store.play()
        assertTrue(app.store.state.value.isPlaying)

        val service = Robolectric.buildService(NoisePlaybackService::class.java).create().get()
        engineEnded(service).invoke()
        shadowOf(Looper.getMainLooper()).idle()

        val state = app.store.state.value
        assertFalse(state.isPlaying)
        assertFalse(state.isPaused)
    }

    private fun engineEnded(service: NoisePlaybackService): () -> Unit {
        val delegate = NoisePlaybackService::class.java.getDeclaredField("engine\$delegate")
        delegate.isAccessible = true
        val engine = (delegate.get(service) as Lazy<*>).value as NoiseEngine
        val onEnded = NoiseEngine::class.java.getDeclaredField("onEnded")
        onEnded.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return onEnded.get(engine) as () -> Unit
    }
}
