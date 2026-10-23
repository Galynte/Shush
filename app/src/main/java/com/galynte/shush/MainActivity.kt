// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.galynte.shush.playback.NoisePlaybackService
import com.galynte.shush.playback.PlaybackReducer
import com.galynte.shush.playback.PlaybackStore
import com.galynte.shush.playback.TimerMode
import com.galynte.shush.ui.ShushScreen
import com.galynte.shush.ui.theme.ShushTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val store by lazy { (application as ShushApp).store }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ShushRoute(
                store = store,
                onSessionChanged = ::pushService,
                onRequestNotifications = ::ensureNotificationPermission,
            )
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun pushService() {
        val active = store.state.value.let { it.isPlaying || it.isPaused }
        val intent = Intent(this, NoisePlaybackService::class.java)
            .setAction(NoisePlaybackService.ACTION_SYNC)
        if (active) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
private fun ShushRoute(
    store: PlaybackStore,
    onSessionChanged: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val session by store.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.isPlaying) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (session.isPlaying) 250 else 1_000)
        }
    }
    var showCustom by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val ui = PlaybackReducer.toUi(session, now)
    ShushTheme {
        ShushScreen(
            state = ui,
            waveformPhase = if (ui.isPlaying) (now % 4_000L) / 4_000f else 0f,
            showCustomTimer = showCustom,
            showAbout = showAbout,
            versionName = BuildConfig.VERSION_NAME,
            onSelectSound = store::selectSound,
            onVolume = store::setVolume,
            onPlayPause = {
                if (store.state.value.isPlaying) {
                    store.pause()
                } else {
                    onRequestNotifications()
                    store.play()
                }
                onSessionChanged()
            },
            onSelectTimer = { mode ->
                if (mode == TimerMode.CUSTOM) {
                    showAbout = false
                    showCustom = true
                } else {
                    store.selectTimer(mode)
                    if (store.state.value.isPlaying || store.state.value.isPaused) onSessionChanged()
                }
            },
            onOpenCustomTimer = {
                showAbout = false
                showCustom = true
            },
            onDismissCustomTimer = { showCustom = false },
            onSaveCustomTimer = { minutes ->
                store.setCustomMinutes(minutes)
                showCustom = false
                if (store.state.value.isPlaying || store.state.value.isPaused) onSessionChanged()
            },
            onOpenAbout = {
                showCustom = false
                showAbout = true
            },
            onDismissAbout = { showAbout = false },
        )
    }
}
