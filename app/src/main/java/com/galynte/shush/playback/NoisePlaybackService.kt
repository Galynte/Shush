// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

@file:Suppress("DEPRECATION") // MediaSessionCompat is the lock-screen session for a raw AudioTrack.

package com.galynte.shush.playback

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media.app.NotificationCompat.MediaStyle
import com.galynte.shush.MainActivity
import com.galynte.shush.R
import com.galynte.shush.ShushApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays in the foreground so a locked screen does not stop the noise.
 * The notification is the only control surface outside the app, and the channel is silent.
 */
class NoisePlaybackService : android.app.Service() {
    private val store by lazy { (application as ShushApp).store }
    private val engine by lazy {
        NoiseEngine(store) {
            // Context.getMainExecutor() is API 28. This runs on the audio thread, and minSdk is 26.
            ContextCompat.getMainExecutor(this).execute { finishFromEngine() }
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var postingShade = false
    private lateinit var session: MediaSessionCompat
    private lateinit var focusRequest: AudioFocusRequest
    private var resumeAfterTransientLoss = false

    private val wakeLock: PowerManager.WakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shush:playback")
            .apply { setReferenceCounted(false) }
    }

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private val brandArt: Bitmap by lazy {
        val drawable = requireNotNull(ContextCompat.getDrawable(this, R.mipmap.ic_launcher))
        val size = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            .coerceAtLeast(1)
        drawable.toBitmap(width = size, height = size)
    }

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, "shush").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    store.play()
                    render()
                }

                override fun onPause() {
                    store.pause()
                    render()
                }

                override fun onStop() {
                    store.stop()
                    render()
                }
            })
        }
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(NoiseEngine.mediaAttributes())
            .setOnAudioFocusChangeListener { change -> onAudioFocus(change) }
            .setWillPauseWhenDucked(false)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> store.play()
            ACTION_PAUSE -> store.pause()
            ACTION_STOP -> store.stop()
            ACTION_DISMISS -> {
                // A swipe while nothing is playing clears the shade card.
                // A paused timer stays in the store and the card returns on play.
                if (!store.state.value.isPlaying) {
                    clearShade()
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            null -> store.restoreSession()
            ACTION_SYNC -> Unit
        }
        return render()
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        ticker?.cancel()
        scope.coroutineContext[Job]?.cancel()
        engine.stop()
        abandonFocus()
        releaseWake()
        if (::session.isInitialized) {
            session.isActive = false
            session.release()
        }
        super.onDestroy()
    }

    private fun render(): Int {
        val state = store.state.value
        if (!state.isPlaying && !state.isPaused) {
            clearShade()
            stopSelf()
            return START_NOT_STICKY
        }
        // Stay foreground through a transient focus loss. Dropping it makes the
        // later AUDIOFOCUS_GAIN promote the service from the background, and
        // Android 12+ throws ForegroundServiceStartNotAllowedException.
        val stayForeground = shouldHoldForeground(state)
        val card = buildNotification(state, ongoing = true)
        try {
            // startForegroundService has to reach startForeground even when the
            // sound is only paused. The foreground card must stay ongoing for that
            // call; detach and replace it so a stopped card can be swiped.
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                card,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } catch (security: SecurityException) {
            Log.e(TAG, "Could not start foreground playback", security)
            store.stop()
            clearShade()
            stopSelf()
            return START_NOT_STICKY
        } catch (blocked: IllegalStateException) {
            if (!isForegroundStartNotAllowed(blocked)) throw blocked
            // The process is still alive. Keep the sound going so a short
            // interruption resumes instead of crashing the service.
            Log.w(TAG, "Could not start foreground playback from the background", blocked)
            notifications.notify(
                NOTIFICATION_ID,
                buildNotification(state, ongoing = stayForeground),
            )
            return trackPlayback(state)
        }
        if (!stayForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            notifications.notify(NOTIFICATION_ID, buildNotification(state, ongoing = false))
        }
        return trackPlayback(state)
    }

    private fun shouldHoldForeground(state: ShushState): Boolean =
        state.isPlaying || resumeAfterTransientLoss

    // ForegroundServiceStartNotAllowedException extends IllegalStateException, not
    // SecurityException, and the class does not exist before Android 12.
    private fun isForegroundStartNotAllowed(error: IllegalStateException): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            error is ForegroundServiceStartNotAllowedException

    private fun trackPlayback(state: ShushState): Int {
        updateMediaSession(state)
        session.isActive = true
        if (state.isPlaying) {
            requestFocus()
            acquireWake()
            engine.ducked = false
        } else {
            releaseWake()
        }
        engine.start()
        ensureTicker()
        return START_STICKY
    }

    private fun clearShade() {
        postingShade = false
        ticker?.cancel()
        ticker = null
        engine.stop()
        abandonFocus()
        releaseWake()
        if (::session.isInitialized) {
            session.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(
                        PlaybackStateCompat.STATE_STOPPED,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        0f,
                    )
                    .build(),
            )
            session.isActive = false
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notifications.cancel(NOTIFICATION_ID)
    }

    private fun finishFromEngine() {
        if (!store.state.value.isPlaying) return
        store.stop()
        render()
    }

    private fun onAudioFocus(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterTransientLoss = false
                engine.ducked = false
                if (store.state.value.isPlaying) store.pause()
                render()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (store.state.value.isPlaying) {
                    // render() keeps the foreground service while this flag is set.
                    resumeAfterTransientLoss = true
                    store.pause()
                    render()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> engine.ducked = true
            AudioManager.AUDIOFOCUS_GAIN -> {
                engine.ducked = false
                if (resumeAfterTransientLoss) {
                    resumeAfterTransientLoss = false
                    store.play()
                    render()
                }
            }
        }
    }

    private fun requestFocus() {
        audioManager.requestAudioFocus(focusRequest)
    }

    private fun abandonFocus() {
        resumeAfterTransientLoss = false
        engine.ducked = false
        if (::focusRequest.isInitialized) {
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun acquireWake() {
        if (!wakeLock.isHeld) wakeLock.acquire(WAKE_TIMEOUT_MS)
    }

    private fun refreshWake() {
        if (store.state.value.isPlaying) wakeLock.acquire(WAKE_TIMEOUT_MS)
    }

    private fun releaseWake() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun ensureTicker() {
        postingShade = true
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(1_000)
                if (!postingShade) continue
                val state = store.state.value
                // Paused remaining time is stored, not counted, so that status line
                // does not change. notify() while the card is being swiped resets the
                // shade item, and a post after the system removes the card but before
                // ACTION_DISMISS clears postingShade puts the notification back.
                if (!state.isPlaying) continue
                updateMediaSession(state)
                // A second notify() after stopForeground(REMOVE) is what leaves an
                // ongoing card that the shade will not swipe away. A transient
                // focus loss stays foreground, so that card has to stay ongoing.
                if (!postingShade || !store.state.value.isPlaying) continue
                notifications.notify(
                    NOTIFICATION_ID,
                    buildNotification(state, ongoing = shouldHoldForeground(state)),
                )
                refreshWake()
            }
        }
    }

    private val notifications: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    private fun updateMediaSession(state: ShushState) {
        val playback = when {
            state.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            state.isPaused -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP,
                )
                .setState(playback, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (state.isPlaying) 1f else 0f)
                .build(),
        )
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, noiseTitle(state.sound))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Shush")
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, brandArt)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, brandArt)
                .build(),
        )
    }

    private fun buildNotification(state: ShushState, ongoing: Boolean = state.isPlaying): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val playPauseAction = if (state.isPlaying) {
            action(1, R.drawable.ic_action_pause, "Pause", ACTION_PAUSE)
        } else {
            action(1, R.drawable.ic_action_play, "Play", ACTION_PLAY)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shush)
            .setLargeIcon(brandArt)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(PlaybackReducer.statusLine(state, System.currentTimeMillis()))
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(playPauseAction)
            .addAction(action(2, R.drawable.ic_action_stop, "Stop", ACTION_STOP))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1),
            )
        if (!ongoing) builder.setDeleteIntent(dismissIntent())
        return builder.build()
    }

    private fun dismissIntent(): PendingIntent = PendingIntent.getService(
        this,
        3,
        Intent(this, NoisePlaybackService::class.java).setAction(ACTION_DISMISS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(requestCode: Int, icon: Int, label: String, action: String): NotificationCompat.Action {
        val pending = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, NoisePlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(icon, label, pending).build()
    }

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 7
        const val ACTION_SYNC = "com.galynte.shush.SYNC"
        const val ACTION_PLAY = "com.galynte.shush.PLAY"
        const val ACTION_PAUSE = "com.galynte.shush.PAUSE"
        const val ACTION_STOP = "com.galynte.shush.STOP"
        const val ACTION_DISMISS = "com.galynte.shush.DISMISS"
        private const val TAG = "Shush"
        private const val WAKE_TIMEOUT_MS = 10L * 60L * 60L * 1000L
    }
}
