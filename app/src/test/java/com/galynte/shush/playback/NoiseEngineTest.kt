// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.playback

import android.media.AudioFormat
import android.media.AudioTrack
import androidx.test.core.app.ApplicationProvider
import com.galynte.shush.ShushApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * A dead or invalid [AudioTrack] makes [AudioTrack.write] return a non-positive
 * count. The play loop has to stop and call [NoiseEngine]'s end callback.
 * Continuing while playback is running spins the audio thread, and the
 * service keeps its wake lock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NoiseEngineTest {
    private val ended = AtomicInteger()

    @After
    fun stopSession() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        app.store.stop()
    }

    @Test
    fun deadTrackEndsPlaybackInsteadOfSpinning() {
        assertWriteFailureEnds(AudioTrack.ERROR_DEAD_OBJECT)
    }

    @Test
    fun zeroWriteEndsPlaybackInsteadOfSpinning() {
        assertWriteFailureEnds(0)
    }

    @Test
    fun invalidTrackEndsPlaybackInsteadOfSpinning() {
        assertWriteFailureEnds(AudioTrack.ERROR_INVALID_OPERATION)
    }

    @Test
    fun partialWriteThenFailureStillEnds() {
        val track = ScriptedTrack(listOf(128, AudioTrack.ERROR_DEAD_OBJECT))
        val engine = playingEngine(track)
        try {
            engine.start()
            val thread = audioThread(engine)
            thread.join(2_000)
            assertFalse("audio thread kept running after a failed write", thread.isAlive)
            assertEquals(1, ended.get())
            assertEquals(2, track.writes.get())
        } finally {
            engine.stop()
        }
    }

    @Test
    fun successfulWritesKeepPlayingUntilStopped() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        app.store.play()
        val track = HealthyTrack()
        val engine = NoiseEngine(app.store, { track }) { ended.incrementAndGet() }
        try {
            engine.start()
            val thread = audioThread(engine)
            assertTrue(waitForWrite(track))
            assertTrue(thread.isAlive)
            assertEquals(0, ended.get())
            engine.stop()
            thread.join(2_000)
            assertFalse(thread.isAlive)
            assertEquals(0, ended.get())
        } finally {
            engine.stop()
        }
    }

    @Test
    fun deadTrackWhilePausedEndsInsteadOfRetrying() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        app.store.stop()
        val track = ScriptedTrack(listOf(AudioTrack.ERROR_DEAD_OBJECT))
        val engine = NoiseEngine(app.store, { track }) { ended.incrementAndGet() }
        try {
            engine.start()
            val thread = audioThread(engine)
            thread.join(2_000)
            assertFalse(thread.isAlive)
            assertEquals(1, ended.get())
            assertEquals(1, track.writes.get())
        } finally {
            engine.stop()
        }
    }

    @Test
    fun pausedTrackStillWaitsBetweenBuffers() {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        app.store.stop()
        val track = HealthyTrack()
        val engine = NoiseEngine(app.store, { track }) { ended.incrementAndGet() }
        try {
            engine.start()
            val thread = audioThread(engine)
            assertTrue(waitForWrite(track))
            Thread.sleep(50)
            assertTrue(thread.isAlive)
            assertEquals(0, ended.get())
        } finally {
            engine.stop()
        }
    }

    private fun assertWriteFailureEnds(result: Int) {
        val track = ScriptedTrack(listOf(result))
        val engine = playingEngine(track)
        try {
            engine.start()
            val thread = audioThread(engine)
            thread.join(2_000)
            assertFalse("audio thread kept running after write returned $result", thread.isAlive)
            assertEquals("onEnded calls for write $result", 1, ended.get())
            assertEquals("writes for result $result", 1, track.writes.get())
        } finally {
            engine.stop()
        }
    }

    private fun playingEngine(track: AudioTrack): NoiseEngine {
        val app = ApplicationProvider.getApplicationContext<ShushApp>()
        app.store.play()
        ended.set(0)
        return NoiseEngine(app.store, { track }) { ended.incrementAndGet() }
    }

    private fun waitForWrite(track: HealthyTrack): Boolean {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (track.writes.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        return track.writes.get() > 0
    }

    private fun audioThread(engine: NoiseEngine): Thread {
        val field = NoiseEngine::class.java.getDeclaredField("thread")
        field.isAccessible = true
        return field.get(engine) as Thread
    }
}

@Suppress("DEPRECATION")
private open class FakeTrack : AudioTrack(
    NoiseEngine.mediaAttributes(),
    AudioFormat.Builder()
        .setSampleRate(NoiseEngine.SAMPLE_RATE)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build(),
    8_192,
    MODE_STREAM,
    0,
) {
    override fun play() = Unit

    override fun pause() = Unit

    override fun flush() = Unit

    override fun release() = Unit

    override fun getPlayState(): Int = PLAYSTATE_PLAYING
}

private class ScriptedTrack(private val results: List<Int>) : FakeTrack() {
    val writes = AtomicInteger()

    override fun write(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
        val index = writes.getAndIncrement()
        return results[index.coerceAtMost(results.lastIndex)]
    }
}

private class HealthyTrack : FakeTrack() {
    val writes = AtomicInteger()

    override fun write(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
        writes.incrementAndGet()
        return sizeInShorts
    }
}
