package dev.fiteduck.phoneagent

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val HEADER_BYTES = 44
private const val BYTES_PER_SAMPLE = 2

/** Wraps mono 16-bit PCM in the 44 byte WAV header the host expects. */
fun pcmToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
    val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray())
    header.putInt(HEADER_BYTES - 8 + pcm.size)
    header.put("WAVE".toByteArray())
    header.put("fmt ".toByteArray())
    header.putInt(16)
    header.putShort(1) // PCM
    header.putShort(1) // mono
    header.putInt(sampleRate)
    header.putInt(sampleRate * BYTES_PER_SAMPLE)
    header.putShort(BYTES_PER_SAMPLE.toShort())
    header.putShort(16)
    header.put("data".toByteArray())
    header.putInt(pcm.size)
    return header.array() + pcm
}
