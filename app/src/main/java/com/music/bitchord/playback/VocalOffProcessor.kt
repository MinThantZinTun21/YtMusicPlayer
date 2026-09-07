package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteOrder

/**
 * Live karaoke / vocal-off inside ExoPlayer's sink, same slot as
 * [SpatialAudioProcessor].
 *
 * 16-bit PCM, one channel or more. Each channel is band-rejected on its own
 * — see [VocalOffDsp] — so a panned vocal is not spared the way a mid-only
 * cut would spare it. Unhandled encodings return
 * [AudioProcessor.AudioFormat.NOT_SET] so the sink routes around this
 * processor instead of killing the renderer.
 */
@UnstableApi
class VocalOffProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    private var dsp: VocalOffDsp? = null
    private var channelCount = 0
    private var scratch = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            dsp = null
            channelCount = 0
            scratch = FloatArray(0)
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        scratch = FloatArray(channelCount)
        dsp = VocalOffDsp(inputAudioFormat.sampleRate, channelCount)
        return inputAudioFormat
    }

    override fun onFlush() {
        dsp?.reset()
    }

    override fun onReset() {
        dsp = null
        channelCount = 0
        scratch = FloatArray(0)
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val bytesPerFrame = BYTES_PER_SAMPLE * channelCount
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        val active = dsp
        if (!enabled || active == null) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())
        val frame = scratch

        repeat(frameCount) {
            for (channel in 0 until channelCount) {
                frame[channel] = inputBuffer.short.toFloat()
            }
            active.processFrame(frame, STRENGTH)
            for (channel in 0 until channelCount) {
                outputBuffer.putShort(clampToShort(frame[channel]))
            }
        }
        outputBuffer.flip()
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val STRENGTH = 1f
    }
}
