// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.galynte.shush.playback.NoisePlaybackService
import com.galynte.shush.playback.PlaybackStore

class ShushApp : Application() {
    lateinit var store: PlaybackStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = PlaybackStore(this)
        val channel = NotificationChannel(
            NoisePlaybackService.CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
