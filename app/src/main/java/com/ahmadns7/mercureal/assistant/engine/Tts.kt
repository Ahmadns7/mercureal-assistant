package com.ahmadns7.mercureal.assistant.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.ahmadns7.mercureal.assistant.model.ModelSpec
import com.ahmadns7.mercureal.assistant.model.ModelStore
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.security.MessageDigest

/**
 * Speech output, fully on-device: sherpa-onnx (VITS voice, int8) running on CPU.
 *
 * There is NO platform TextToSpeech and NO fallback. If the voice model is not installed or fails
 * its checks, [load] throws and the assistant reports the error instead of speaking.
 *
 * The voice .onnx comes from Settings > Models (ModelDownloader, pinned URL + sha256). The two
 * small text files the VITS model needs (tokens, lexicon) are bundled in assets and are checked
 * against pinned sha256 values before they are used.
 */
class Tts(private val context: Context, private val store: ModelStore) {

    private var engine: OfflineTts? = null
    private var loadedId: String? = null

    @Volatile
    private var track: AudioTrack? = null

    /** Loads [spec] as the active voice. Throws if it is missing or any file fails its pinned check. */
    fun load(spec: ModelSpec) {
        if (loadedId == spec.id && engine != null) return
        close()
        check(store.isInstalled(spec)) { "${spec.displayName} is not downloaded. Get it in Settings > Models." }

        val aux = store.ttsAuxDir()
        val tokens = stageAsset("tts/vits-ljs/tokens.txt", File(aux, "vits-ljs.tokens.txt"), TOKENS_SHA256)
        val lexicon = stageAsset("tts/vits-ljs/lexicon.txt", File(aux, "vits-ljs.lexicon.txt"), LEXICON_SHA256)

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = store.fileFor(spec).absolutePath,
                    lexicon = lexicon,
                    tokens = tokens,
                ),
                numThreads = 2,
                provider = "cpu",
            ),
        )
        engine = OfflineTts(config = config)
        loadedId = spec.id
    }

    /** Synthesises [text] on the offline voice and plays it. Blocking: call off the main thread. */
    fun speak(text: String) {
        if (text.isBlank()) return
        val voice = checkNotNull(engine) { "no voice loaded. Get a voice in Settings > Models." }
        val audio = voice.generate(text = text, sid = 0, speed = 1.0f)
        play(audio.samples, audio.sampleRate)
    }

    private fun play(samples: FloatArray, sampleRate: Int) {
        if (samples.isEmpty()) return
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, samples.size * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        try {
            t.play()
            var off = 0
            while (off < samples.size && track === t) {
                val n = t.write(samples, off, samples.size - off, AudioTrack.WRITE_BLOCKING)
                if (n <= 0) break
                off += n
            }
            // Wait for the device to play out the audio, bounded by its duration plus a margin.
            val limitMs = samples.size * 1000L / sampleRate + 1000L
            val start = System.currentTimeMillis()
            while (track === t && t.playbackHeadPosition < samples.size &&
                System.currentTimeMillis() - start < limitMs
            ) {
                Thread.sleep(20)
            }
        } finally {
            if (track === t) track = null
            t.stop()
            t.release()
        }
    }

    fun close() {
        track?.let { runCatching { it.stop() } }
        track = null
        engine?.release()
        engine = null
        loadedId = null
    }

    /** Copies a bundled asset into private storage once and re-checks its sha256 before every use. */
    private fun stageAsset(assetPath: String, dest: File, sha256: String): String {
        if (!(dest.isFile && sha256Of(dest).equals(sha256, ignoreCase = true))) {
            val tmp = File(dest.path + ".tmp")
            context.assets.open(assetPath).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            check(sha256Of(tmp).equals(sha256, ignoreCase = true)) {
                "bundled voice file $assetPath failed its sha256 check, not loaded"
            }
            dest.delete()
            check(tmp.renameTo(dest)) { "could not place voice file ${dest.name}" }
        }
        return dest.absolutePath
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TOKENS_SHA256 = "5fee2c6b238d712287f2ecb08f34a8a8b413bcb7390862ef6fb6fd6f0f8d3a17"
        const val LEXICON_SHA256 = "bdccfc6da71c45c48e2e0056fcf0aab760577c5f959f6c1b5eb3e3e916fd5a0e"
    }
}
