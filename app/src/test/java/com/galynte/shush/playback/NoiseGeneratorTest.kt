// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.playback

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class NoiseGeneratorTest {
    @Test
    fun colorsStayInRangeAndNearTheSameLoudness() {
        NoiseColor.entries.forEach { color ->
            val samples = collect(color)
            assertTrue(samples.all { it.isFinite() && abs(it) <= 1f })
            val clips = samples.count { abs(it) >= 0.999f }
            assertTrue("$color clipped $clips samples", clips < samples.size * 0.02)
            val level = rms(samples)
            assertTrue("$color rms $level", level in 0.08..0.45)
        }
    }

    @Test
    fun brownIsSmootherThanPinkAndPinkIsSmootherThanWhite() {
        val white = lag1(collect(NoiseColor.WHITE))
        val pink = lag1(collect(NoiseColor.PINK))
        val brown = lag1(collect(NoiseColor.BROWN))
        assertTrue("white lag1 $white", abs(white) < 0.15)
        assertTrue("pink lag1 $pink vs white $white", pink > white + 0.2)
        assertTrue("brown lag1 $brown vs pink $pink", brown > pink + 0.1)
        assertTrue("brown lag1 $brown", brown > 0.85)
    }

    private fun collect(color: NoiseColor, count: Int = 24_000): FloatArray {
        val generator = NoiseGenerator(seed = 42L)
        repeat(4_000) { generator.next(color) }
        return FloatArray(count) { generator.next(color) }
    }

    private fun rms(samples: FloatArray): Double {
        var sum = 0.0
        for (sample in samples) sum += sample * sample
        return sqrt(sum / samples.size)
    }

    private fun lag1(samples: FloatArray): Double {
        var num = 0.0
        var den = 0.0
        for (index in 1 until samples.size) {
            num += samples[index] * samples[index - 1]
            den += samples[index] * samples[index]
        }
        return if (den == 0.0) 0.0 else num / den
    }
}
