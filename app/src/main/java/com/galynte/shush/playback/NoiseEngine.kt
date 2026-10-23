// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import kotlin.math.abs

/**
 * Writes synthesized noise to an [AudioTrack]. Volume, color, and the fade
 * are read from [PlaybackStore] on each buffer, so the screen can change them
 * without restarting the stream.
 */
class NoiseEngine(
    private val store: PlaybackStore,
    private val onEnded: () -> Unit,
) {
    @Volatile var ducked: Boolean = false

    @Volatile private var stopRequested = false
    private var thread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (thread?.isAlive == true) return
        stopRequested = false
        thread = Thread(::runLoop, "shush-noise").also { it.start() }
    }

    fun stop() {
        stopRequested = true
        val current = thread
        if (current != null && current !== Thread.currentThread()) {
            current.join(2_000)
        }
        if (thread === current) thread = null
        releaseTrack()
    }

    private fun runLoop() {
        var notify = false
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            loop()
        } catch (error: Throwable) {
            Log.e(TAG, "Playback stopped", error)
            notify = true
        }
        if (notify) onEnded()
    }

    private fun loop() {
        val track = createTrack()
        synchronized(this) { audioTrack = track }
        val generator = NoiseGenerator()
        val buffer = ShortArray(BUFFER_SAMPLES)
        var applied = 0f
        var notify = false
        try {
            track.play()
            while (!stopRequested) {
                val session = store.state.value
                val now = System.currentTimeMillis()
                val end = session.timerEndEpochMs
                if (session.isPlaying && end != null && now >= end) {
                    notify = true
                    break
                }
                val remaining = if (session.isPlaying && end != null) end - now else null
                val fade = if (session.isPlaying) fadeFactor(remaining) else 0f
                val duck = if (ducked) 0.28f else 1f
                val target = if (session.isPlaying) shapedGain(session.volume, fade) * duck else 0f
                for (i in buffer.indices) {
                    val delta = (target - applied).coerceIn(-GAIN_STEP, GAIN_STEP)
                    applied += delta
                    val sample = generator.next(session.sound) * applied
                    buffer[i] = (sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
                writeAll(track, buffer)
                if (!session.isPlaying && abs(applied) < 0.001f) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                        track.flush()
                    }
                    Thread.sleep(40)
                }
            }
        } finally {
            releaseTrack()
        }
        if (notify) onEnded()
    }

    private fun writeAll(track: AudioTrack, buffer: ShortArray) {
        var offset = 0
        while (offset < buffer.size && !stopRequested) {
            val wrote = track.write(buffer, offset, buffer.size - offset)
            if (wrote <= 0) break
            offset += wrote
        }
    }

    private fun createTrack(): AudioTrack {
        val min = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = (if (min > 0) min else BUFFER_SAMPLES * 2) * 2
        return AudioTrack.Builder()
            .setAudioAttributes(mediaAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()
    }

    private fun releaseTrack() {
        val track = synchronized(this) {
            val current = audioTrack
            audioTrack = null
            current
        } ?: return
        try {
            track.pause()
        } catch (_: IllegalStateException) {
        }
        try {
            track.flush()
        } catch (_: IllegalStateException) {
        }
        try {
            track.release()
        } catch (_: IllegalStateException) {
        }
    }

    companion object {
        private const val TAG = "Shush"
        const val SAMPLE_RATE = 48_000
        private const val BUFFER_SAMPLES = 2048
        private const val GAIN_STEP = 0.0015f

        fun mediaAttributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
