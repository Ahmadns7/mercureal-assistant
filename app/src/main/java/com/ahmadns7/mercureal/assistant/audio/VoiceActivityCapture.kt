package com.ahmadns7.mercureal.assistant.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Energy-based voice activity detection for conversation turns (no extra model, no permission).
 * Waits for speech, records it, and stops after [END_SILENCE_MS] of quiet. The noise floor adapts
 * while nothing is being said. This is a simple energy gate, not a learned VAD: it will misfire in
 * loud rooms and on TV or music.
 */
class VoiceActivityCapture {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val FRAME = 320          // 20 ms at 16 kHz
        private const val START_FRAMES = 3     // 60 ms above threshold to count as speech
        private const val PREROLL_FRAMES = 10  // keep 200 ms before speech onset
        private const val END_SILENCE_MS = 900
        private const val MAX_UTTERANCE_MS = 20_000
        private const val MIN_SPEECH_RMS = 450.0
    }

    /**
     * Returns the captured utterance as float PCM, or null when no speech started within [waitMs],
     * when [cancelled] turned true, or when the microphone could not be opened.
     */
    @SuppressLint("MissingPermission")
    fun capture(waitMs: Long, cancelled: () -> Boolean): FloatArray? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            },
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuf, FRAME * 2 * 4),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null
        }
        try {
            rec.startRecording()
            val frame = ShortArray(FRAME)
            val preroll = ArrayDeque<FloatArray>()
            val chunks = ArrayList<FloatArray>()
            var noise = 300.0
            var speechFrames = 0
            var started = false
            var silentFrames = 0
            var framesRecorded = 0
            val maxFrames = MAX_UTTERANCE_MS / 20
            val t0 = System.currentTimeMillis()

            while (true) {
                if (cancelled()) return null
                val n = rec.read(frame, 0, FRAME)
                if (n < 0) return null
                if (n == 0) continue
                val level = rms(frame, n)
                val threshold = max(MIN_SPEECH_RMS, noise * 3.0)
                val above = level > threshold
                val samples = FloatArray(n) { frame[it] / 32768f }

                if (!started) {
                    if (above) {
                        speechFrames++
                    } else {
                        speechFrames = 0
                        noise = noise * 0.95 + level * 0.05
                    }
                    preroll.addLast(samples)
                    if (preroll.size > PREROLL_FRAMES) preroll.removeFirst()
                    if (speechFrames >= START_FRAMES) {
                        started = true
                        chunks.addAll(preroll)
                        preroll.clear()
                        silentFrames = 0
                    } else if (System.currentTimeMillis() - t0 > waitMs) {
                        return null
                    }
                } else {
                    chunks.add(samples)
                    framesRecorded++
                    if (above) silentFrames = 0 else silentFrames++
                    if (silentFrames * 20 >= END_SILENCE_MS) break
                    if (framesRecorded >= maxFrames) break
                }
            }
            val total = chunks.sumOf { it.size }
            val out = FloatArray(total)
            var off = 0
            for (c in chunks) {
                c.copyInto(out, off)
                off += c.size
            }
            return out
        } finally {
            rec.stop()
            rec.release()
        }
    }

    private fun rms(frame: ShortArray, n: Int): Double {
        var sum = 0.0
        for (i in 0 until n) {
            val s = frame[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / max(n, 1))
    }
}
