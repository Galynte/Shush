// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

@file:Suppress("DEPRECATION") // The playback service still exposes a MediaSessionCompat.

package com.galynte.shush.playback

import android.app.Notification
import android.content.Intent
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import com.galynte.shush.ShushApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sleep timer ends on the audio thread. [NoisePlaybackService] has to hop
 * back to the main thread on API 26, where [android.content.Context.getMainExecutor]
 * does not exist.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    @Test
    fun notificationNamesTheNoiseAndShowsTheBrandIcon() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        app.store.selectSound(NoiseColor.PINK)
        val intent = Intent(app, NoisePlaybackService::class.java)
            .setAction(NoisePlaybackService.ACTION_PLAY)
        val service = Robolectric.buildService(NoisePlaybackService::class.java, intent)
            .create()
            .startCommand(0, 1)
            .get()
        try {
            val notification = shadowOf(service).lastForegroundNotification
            assertEquals("Shush", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals("Pink Noise", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            val icon = notification.getLargeIcon()
            assertNotNull(icon)
            assertTrue(icon!!.loadDrawable(service)!!.toBitmap().hasVisiblePixel())

            val metadata = sessionMetadata(service)
            assertEquals("Pink Noise", metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
            assertEquals("Shush", metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
            val art = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            assertNotNull(art)
            assertTrue(art!!.hasVisiblePixel())
        } finally {
            service.onDestroy()
            app.store.stop()
        }
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

    // Robolectric does not publish this session's metadata through MediaController.
    private fun sessionMetadata(service: NoisePlaybackService): MediaMetadataCompat {
        val sessionField = NoisePlaybackService::class.java.getDeclaredField("session")
        sessionField.isAccessible = true
        val session = sessionField.get(service) as MediaSessionCompat
        val implField = MediaSessionCompat::class.java.getDeclaredField("mImpl")
        implField.isAccessible = true
        val impl = implField.get(session)
        val metadataField = declaredField(impl.javaClass, "mMetadata")
        return metadataField.get(impl) as MediaMetadataCompat
    }

    private fun declaredField(start: Class<*>, name: String): java.lang.reflect.Field {
        var type: Class<*>? = start
        while (type != null) {
            try {
                return type.getDeclaredField(name).also { it.isAccessible = true }
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw NoSuchFieldException("$name on ${start.name}")
    }
}

private fun android.graphics.Bitmap.hasVisiblePixel(): Boolean {
    if (width <= 0 || height <= 0) return false
    val stepX = (width / 16).coerceAtLeast(1)
    val stepY = (height / 16).coerceAtLeast(1)
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            if (getPixel(x, y) ushr 24 != 0) return true
            x += stepX
        }
        y += stepY
    }
    return false
}
