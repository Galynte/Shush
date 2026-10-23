// Copyright 2026 Galynte. Licensed under the Apache License, Version 2.0.

package com.galynte.shush.playback

import kotlin.random.Random

/**
 * Streams white, pink, and brown noise in the range -1..1.
 *
 * Pink noise uses Paul Kellet's economy filter. Brown noise is a leaky integrator
 * so the walk does not drift into silence or a rail. Levels are trimmed so the
 * three colors sit in a similar loudness range before the volume slider.
 */
class NoiseGenerator(seed: Long = 0x5EEDL) {
    private val random = Random(seed)
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var b3 = 0.0
    private var b4 = 0.0
    private var b5 = 0.0
    private var b6 = 0.0
    private var brown = 0.0

    fun next(color: NoiseColor): Float {
        val sample = when (color) {
            NoiseColor.WHITE -> white()
            NoiseColor.PINK -> pink()
            NoiseColor.BROWN -> this.brown()
        }
        return if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
    }

    private fun unit(): Double = random.nextDouble() * 2.0 - 1.0

    private fun white(): Float = (unit() * WHITE_SCALE).toFloat()

    private fun pink(): Float {
        val white = unit()
        b0 = finite(0.99886 * b0 + white * 0.0555179)
        b1 = finite(0.99332 * b1 + white * 0.0750759)
        b2 = finite(0.96900 * b2 + white * 0.1538520)
        b3 = finite(0.86650 * b3 + white * 0.3104856)
        b4 = finite(0.55000 * b4 + white * 0.5329522)
        b5 = finite(-0.7616 * b5 - white * 0.0168980)
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
        b6 = finite(white * 0.115926)
        return (pink * PINK_SCALE).toFloat()
    }

    private fun brown(): Float {
        brown = finite((brown + unit() * BROWN_MIX) * BROWN_LEAK)
        return (brown * BROWN_SCALE).toFloat()
    }

    private fun finite(value: Double): Double = if (value.isFinite()) value else 0.0

    private companion object {
        const val WHITE_SCALE = 0.62
        const val PINK_SCALE = 0.20
        const val BROWN_MIX = 0.035
        const val BROWN_LEAK = 0.996
        const val BROWN_SCALE = 1.05
    }
}
