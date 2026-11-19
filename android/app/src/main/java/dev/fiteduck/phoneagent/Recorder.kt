package dev.fiteduck.phoneagent

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

const val SAMPLE_RATE = 16000

/** One utterance of microphone audio, from start() until stop() hands back a WAV. */
class Recorder {
    private val pcm = ByteArrayOutputStream()
    private var reader: Thread? = null

    @Volatile
    private var recording = false

    /** The caller must already hold RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    fun start() {
        pcm.reset()
        val chunkBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val microphone = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            chunkBytes * 2,
        )
        recording = true
        microphone.startRecording()
        reader = Thread { readUntilStopped(microphone, chunkBytes) }.also { it.start() }
    }

    fun stop(): Recording {
        recording = false
        reader?.join()
        reader = null
        return Recording(pcmToWav(pcm.toByteArray(), SAMPLE_RATE), pcm.size())
    }

    private fun readUntilStopped(microphone: AudioRecord, chunkBytes: Int) {
        val chunk = ByteArray(chunkBytes)
        while (recording) {
            val read = microphone.read(chunk, 0, chunk.size)
            if (read > 0) pcm.write(chunk, 0, read)
        }
        microphone.stop()
        microphone.release()
    }
}

class Recording(val wav: ByteArray, private val pcmBytes: Int) {
    val seconds: Double get() = pcmBytes / (SAMPLE_RATE * 2.0)
}
