// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

@file:Suppress("DEPRECATION") // The playback service still exposes a MediaSessionCompat.

package com.galynte.shush.playback

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import com.galynte.shush.ShushApp
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
            .get()
        // onStartCommand directly, not ServiceController.startCommand. The controller
        // idles the main looper, and the audio thread can post "playback ended" before
        // that idle runs, which clears the notification this test just created.
        service.onStartCommand(intent, 0, 1)
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
            shutdown(service, app)
        }
    }

    @Test
    fun stoppedPlaybackClearsTheNotification() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        val service = start(app, NoisePlaybackService.ACTION_PLAY)
        try {
            assertTrue(shadowOf(service).isLastForegroundNotificationAttached)
            assertTrue(posted(service).flags and Notification.FLAG_ONGOING_EVENT != 0)

            deliver(service, NoisePlaybackService.ACTION_STOP)
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(shadowOf(service).isForegroundStopped)
            assertNull(postedOrNull(service))
            assertFalse(app.store.state.value.isPlaying)
            assertFalse(app.store.state.value.isPaused)
        } finally {
            shutdown(service, app)
        }
    }

    @Test
    fun pausedNotificationCanBeSwipedAndReturnsOnPlay() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        val service = start(app, NoisePlaybackService.ACTION_PLAY)
        try {
            deliver(service, NoisePlaybackService.ACTION_PAUSE)
            val paused = posted(service)
            assertEquals(0, paused.flags and Notification.FLAG_ONGOING_EVENT)
            assertNotNull(paused.deleteIntent)
            assertFalse(shadowOf(service).isLastForegroundNotificationAttached)

            // The shade sends the delete intent when the card is swiped.
            deliver(service, NoisePlaybackService.ACTION_DISMISS)
            shadowOf(Looper.getMainLooper()).idle()

            assertNull(postedOrNull(service))
            assertTrue(app.store.state.value.isPaused)
            assertFalse(app.store.state.value.isPlaying)
        } finally {
            shutdown(service, app)
        }

        val resumed = start(app, NoisePlaybackService.ACTION_PLAY)
        try {
            assertTrue(posted(resumed).flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertTrue(app.store.state.value.isPlaying)
            assertTrue(shadowOf(resumed).isLastForegroundNotificationAttached)
        } finally {
            shutdown(resumed, app)
        }
    }

    @Test
    fun transientFocusLossKeepsTheForegroundServiceAndResumes() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        val service = start(app, NoisePlaybackService.ACTION_PLAY)
        try {
            audioFocus(service, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

            assertTrue(app.store.state.value.isPaused)
            assertFalse(app.store.state.value.isPlaying)
            assertTrue(shadowOf(service).isLastForegroundNotificationAttached)
            val paused = posted(service)
            assertTrue(paused.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertNull(paused.deleteIntent)

            audioFocus(service, AudioManager.AUDIOFOCUS_GAIN)

            assertTrue(app.store.state.value.isPlaying)
            assertFalse(app.store.state.value.isPaused)
            assertTrue(shadowOf(service).isLastForegroundNotificationAttached)
            assertTrue(posted(service).flags and Notification.FLAG_ONGOING_EVENT != 0)
        } finally {
            shutdown(service, app)
        }
    }

    @Test
    fun permanentFocusLossReleasesTheForegroundService() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        val service = start(app, NoisePlaybackService.ACTION_PLAY)
        try {
            audioFocus(service, AudioManager.AUDIOFOCUS_LOSS)

            assertTrue(app.store.state.value.isPaused)
            assertFalse(app.store.state.value.isPlaying)
            assertFalse(shadowOf(service).isLastForegroundNotificationAttached)
            val paused = posted(service)
            assertEquals(0, paused.flags and Notification.FLAG_ONGOING_EVENT)
            assertNotNull(paused.deleteIntent)

            audioFocus(service, AudioManager.AUDIOFOCUS_GAIN)

            assertFalse(app.store.state.value.isPlaying)
            assertTrue(app.store.state.value.isPaused)
            assertFalse(shadowOf(service).isLastForegroundNotificationAttached)
        } finally {
            shutdown(service, app)
        }
    }

    @Test
    @Config(sdk = [31])
    fun resumeAfterTransientLossSurvivesBackgroundForegroundRestriction() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        val service = start(app, NoisePlaybackService.ACTION_PLAY)
        try {
            audioFocus(service, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            assertFalse(app.store.state.value.isPlaying)
            shadowOf(service).setThrowInStartForeground(
                ForegroundServiceStartNotAllowedException("background"),
            )
            audioFocus(service, AudioManager.AUDIOFOCUS_GAIN)
            assertTrue(app.store.state.value.isPlaying)
            assertFalse(app.store.state.value.isPaused)
            assertFalse(shadowOf(service).isStoppedBySelf)
            assertNotNull(postedOrNull(service))
        } finally {
            shutdown(service, app)
        }
    }

    @Test
    fun missingForegroundPermissionStillStopsPlayback() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        val service = start(app, NoisePlaybackService.ACTION_PLAY)
        try {
            shadowOf(service).setThrowInStartForeground(SecurityException("denied"))
            deliver(service, NoisePlaybackService.ACTION_SYNC)
            assertFalse(app.store.state.value.isPlaying)
            assertFalse(app.store.state.value.isPaused)
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally {
            shutdown(service, app)
        }
    }

    @Test
    fun otherForegroundFailuresStillPropagate() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        val service = start(app, NoisePlaybackService.ACTION_PLAY)
        try {
            audioFocus(service, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            shadowOf(service).setThrowInStartForeground(IllegalStateException("notification missing"))
            try {
                audioFocus(service, AudioManager.AUDIOFOCUS_GAIN)
                fail("unrelated foreground failure should propagate")
            } catch (thrown: InvocationTargetException) {
                assertEquals("notification missing", thrown.cause?.message)
            }
            assertTrue(app.store.state.value.isPlaying)
        } finally {
            shutdown(service, app)
        }
    }

    private fun start(app: ShushApp, action: String): NoisePlaybackService {
        val intent = Intent(app, NoisePlaybackService::class.java).setAction(action)
        val service = Robolectric.buildService(NoisePlaybackService::class.java, intent)
            .create()
            .get()
        // Skip ServiceController.startCommand so its looper idle cannot run the
        // audio thread's ended callback before the test reads the notification.
        service.onStartCommand(intent, 0, 1)
        return service
    }

    private fun deliver(service: NoisePlaybackService, action: String) {
        service.onStartCommand(Intent(service, NoisePlaybackService::class.java).setAction(action), 0, 2)
    }

    private fun audioFocus(service: NoisePlaybackService, change: Int) {
        val method = NoisePlaybackService::class.java.getDeclaredMethod(
            "onAudioFocus",
            Int::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(service, change)
    }

    private fun posted(service: NoisePlaybackService): Notification =
        requireNotNull(postedOrNull(service))

    private fun postedOrNull(service: NoisePlaybackService): Notification? =
        service.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .firstOrNull { it.id == NoisePlaybackService.NOTIFICATION_ID }
            ?.notification

    private fun shutdown(service: NoisePlaybackService, app: ShushApp) {
        if (!shadowOf(service).isStoppedBySelf) service.onDestroy()
        app.store.stop()
        // The audio thread posts the ended callback here. Drain it while playback
        // is already stopped so it cannot clear the next test's notification.
        shadowOf(Looper.getMainLooper()).idle()
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
