package com.ahmadns7.mercureal.assistant.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

/**
 * Records 16 kHz mono PCM16, the format whisper.cpp expects.
 * Push-to-talk: start() on press, stop() on release returns the raw samples.
 */
class MicRecorder {

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 30
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val buffer = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun start() {
        if (record != null) return
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )
        buffer.reset()
        rec.startRecording()
        record = rec
        thread = Thread {
            val chunk = ByteArray(minBuf)
            val maxBytes = SAMPLE_RATE * 2 * MAX_SECONDS
            while (record === rec && buffer.size() < maxBytes) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n > 0) synchronized(buffer) { buffer.write(chunk, 0, n) }
            }
        }.also { it.start() }
    }

    /** Stops capture and returns mono PCM16 samples as floats in [-1, 1]. */
    fun stop(): FloatArray {
        val rec = record ?: return FloatArray(0)
        record = null
        thread?.join()
        rec.stop()
        rec.release()
        val bytes = synchronized(buffer) { buffer.toByteArray() }
        val out = FloatArray(bytes.size / 2)
        for (i in out.indices) {
            val lo = bytes[2 * i].toInt() and 0xff
            val hi = bytes[2 * i + 1].toInt()
            out[i] = ((hi shl 8) or lo) / 32768f
        }
        return out
    }
}
