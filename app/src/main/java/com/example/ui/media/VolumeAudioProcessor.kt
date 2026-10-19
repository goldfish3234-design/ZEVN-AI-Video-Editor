package com.example.ui.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Scales 16-bit PCM audio by [volume] during export (0 = silent, 1 = unchanged).
 */
class VolumeAudioProcessor(private val volume: Float) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val out = replaceOutputBuffer(inputBuffer.remaining())
        while (inputBuffer.remaining() >= 2) {
            val lo = inputBuffer.get().toInt() and 0xFF
            val hi = inputBuffer.get().toInt()
            val sample = (hi shl 8) or lo
            val scaled = (sample * volume).toInt().coerceIn(-32768, 32767)
            out.put((scaled and 0xFF).toByte())
            out.put(((scaled shr 8) and 0xFF).toByte())
        }
        out.flip()
    }
}
