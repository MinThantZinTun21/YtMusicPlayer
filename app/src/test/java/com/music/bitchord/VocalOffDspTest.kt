package com.music.bitchord

import com.music.bitchord.playback.VocalOffDsp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class VocalOffDspTest {

    @Test
    fun strengthZeroIsAPassthrough() {
        val dsp = VocalOffDsp(SAMPLE_RATE)
        val (left, right) = dsp.process(1200f, -800f, strength = 0f)
        assertEquals(1200f, left, 0.01f)
        assertEquals(-800f, right, 0.01f)
    }

    @Test
    fun centeredVocalBandIsAttenuated() {
        val (inRms, outRms) = energy(hz = VOCAL_HZ, leftGain = 1f, rightGain = 1f)
        assertTrue(
            "centered 1 kHz should drop, in=$inRms out=$outRms",
            outRms < inRms * 0.08,
        )
    }

    @Test
    fun pannedVocalBandIsAttenuated() {
        val (inRms, outRms) = energy(hz = VOCAL_HZ, leftGain = 1f, rightGain = 0f)
        assertTrue(
            "left-only 1 kHz should drop, in=$inRms out=$outRms",
            outRms < inRms * 0.08,
        )
    }

    @Test
    fun sideOnlyVocalBandIsAttenuated() {
        val (inRms, outRms) = energy(hz = VOCAL_HZ, leftGain = 1f, rightGain = -1f)
        assertTrue(
            "side-only 1 kHz should drop, in=$inRms out=$outRms",
            outRms < inRms * 0.08,
        )
    }

    @Test
    fun centeredBassIsKept() {
        val (inRms, outRms) = energy(hz = BASS_HZ, leftGain = 1f, rightGain = 1f)
        assertEquals(inRms, outRms, inRms * 0.20)
    }

    @Test
    fun everyChannelOfAFrameIsProcessed() {
        val dsp = VocalOffDsp(SAMPLE_RATE, channelCount = 4)
        val frame = FloatArray(4)
        var inEnergy = 0.0
        var outEnergy = 0.0
        var count = 0
        for (i in 0 until FRAMES) {
            val sample = sine(VOCAL_HZ, i)
            frame.fill(sample)
            dsp.processFrame(frame, strength = 1f)
            if (i < SETTLE) continue
            inEnergy += sample * sample * 4
            outEnergy += frame.sumOf { (it * it).toDouble() }
            count++
        }
        val inRms = sqrt(inEnergy / (count * 4))
        val outRms = sqrt(outEnergy / (count * 4))
        assertTrue("all 4 channels should drop, in=$inRms out=$outRms", outRms < inRms * 0.08)
    }

    private fun energy(hz: Int, leftGain: Float, rightGain: Float): Pair<Double, Double> {
        val dsp = VocalOffDsp(SAMPLE_RATE)
        var inEnergy = 0.0
        var outEnergy = 0.0
        var count = 0
        for (i in 0 until FRAMES) {
            val sample = sine(hz, i)
            val leftIn = sample * leftGain
            val rightIn = sample * rightGain
            val (left, right) = dsp.process(leftIn, rightIn, strength = 1f)
            if (i < SETTLE) continue
            inEnergy += leftIn * leftIn + rightIn * rightIn
            outEnergy += left * left + right * right
            count++
        }
        return sqrt(inEnergy / (count * 2)) to sqrt(outEnergy / (count * 2))
    }

    private fun sine(hz: Int, index: Int): Float =
        (sin(2.0 * PI * hz * index / SAMPLE_RATE) * 8000.0).toFloat()

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val FRAMES = 8_192
        const val SETTLE = 2_048
        const val VOCAL_HZ = 1_000
        const val BASS_HZ = 80
    }
}
