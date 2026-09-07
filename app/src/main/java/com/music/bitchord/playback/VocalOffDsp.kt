package com.music.bitchord.playback

import kotlin.math.PI
import kotlin.math.tan

/**
 * Real-time karaoke / vocal-off on **every** PCM channel.
 *
 * The first version only attenuated the stereo mid, with a one-pole split that
 * left most of a 1 kHz voice intact (~−13 dB). Lead vocals that were doubled,
 * reverbed, or panned still came through. This pass:
 *
 *  1. Band-rejects the singing range on **each channel independently**, so a
 *     hard-left or hard-right vocal is cut the same as a centered one.
 *  2. Uses the same 24 dB/octave Butterworth SVF as
 *     [TransitionFilterProcessor], not a 6 dB/octave one-pole.
 *
 * Bass below [LOW_HZ] and air above [HIGH_HZ] are kept so the track does not
 * collapse to silence. Instruments that live in the vocal band (guitars, piano
 * mids) are thinned — that is the cost of hiding a voice that is not centered.
 */
class VocalOffDsp(
    sampleRate: Int,
    channelCount: Int = 2,
) {
    private val channels = Array(channelCount.coerceAtLeast(1)) { ChannelReject(sampleRate) }

    val channelCount: Int get() = channels.size

    fun reset() {
        channels.forEach { it.reset() }
    }

    /**
     * In-place: [samples] is one frame, `samples.size >= channelCount`.
     *
     * @param strength 0 = dry, 1 = full vocal-band reject on every channel.
     */
    fun processFrame(samples: FloatArray, strength: Float) {
        val amount = strength.coerceIn(0f, 1f)
        for (index in channels.indices) {
            samples[index] = channels[index].process(samples[index], amount)
        }
    }

    /** Stereo convenience for tests and the 2-channel processor path. */
    fun process(left: Float, right: Float, strength: Float): Pair<Float, Float> {
        if (channels.size == 1) {
            val sample = floatArrayOf(left)
            processFrame(sample, strength)
            return sample[0] to sample[0]
        }
        val frame = floatArrayOf(left, right)
        processFrame(frame, strength)
        return frame[0] to frame[1]
    }

    companion object {
        /** Keep kick / bass; chest voice sitting on top of them is accepted. */
        const val LOW_HZ = 160f

        /**
         * Upper edge of the reject. Past 4 kHz on purpose: presence and
         * sibilance of a lead vocal live here, and leaving them in is what
         * made the first toggle still sound like singing.
         */
        const val HIGH_HZ = 7000f
    }
}

/**
 * Parallel low-pass + high-pass = band-reject. Two cascaded trapezoidal SVF
 * stages per half, matching [TransitionFilterProcessor].
 */
private class ChannelReject(sampleRate: Int) {
    private val low = Butterworth4(VocalOffDsp.LOW_HZ, sampleRate, highPass = false)
    private val high = Butterworth4(VocalOffDsp.HIGH_HZ, sampleRate, highPass = true)

    fun process(input: Float, amount: Float): Float {
        if (amount <= 0f) return input
        val wet = low.process(input) + high.process(input)
        return if (amount >= 1f) wet else input * (1f - amount) + wet * amount
    }

    fun reset() {
        low.reset()
        high.reset()
    }
}

private class Butterworth4(
    cutoffHz: Float,
    sampleRate: Int,
    private val highPass: Boolean,
) {
    private val a1 = FloatArray(STAGES)
    private val a2 = FloatArray(STAGES)
    private val a3 = FloatArray(STAGES)
    private val k = FloatArray(STAGES)
    private val state = FloatArray(STAGES * 2)

    init {
        val nyquistLimit = sampleRate * MAX_CUTOFF_FRACTION
        val fc = cutoffHz.coerceIn(MIN_HZ, nyquistLimit)
        val g = tan(PI * fc / sampleRate).toFloat()
        for (stage in 0 until STAGES) {
            val qk = 1f / BUTTERWORTH_Q[stage]
            val aa1 = 1f / (1f + g * (g + qk))
            a1[stage] = aa1
            a2[stage] = g * aa1
            a3[stage] = g * (g * aa1)
            k[stage] = qk
        }
    }

    fun process(input: Float): Float {
        var value = input
        for (stage in 0 until STAGES) {
            val i = stage * 2
            val ic1 = state[i]
            val ic2 = state[i + 1]
            val v3 = value - ic2
            val v1 = a1[stage] * ic1 + a2[stage] * v3
            val v2 = ic2 + a2[stage] * ic1 + a3[stage] * v3
            state[i] = 2f * v1 - ic1
            state[i + 1] = 2f * v2 - ic2
            value = if (highPass) value - k[stage] * v1 - v2 else v2
        }
        return value
    }

    fun reset() {
        state.fill(0f)
    }

    private companion object {
        const val STAGES = 2
        const val MIN_HZ = 10f
        const val MAX_CUTOFF_FRACTION = 0.45f
        val BUTTERWORTH_Q = floatArrayOf(0.54120f, 1.30656f)
    }
}
