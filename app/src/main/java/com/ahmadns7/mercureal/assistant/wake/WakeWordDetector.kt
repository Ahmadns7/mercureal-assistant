package com.ahmadns7.mercureal.assistant.wake

import android.content.Context
import com.ahmadns7.mercureal.assistant.model.ModelCatalog
import com.ahmadns7.mercureal.assistant.model.ModelStore
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/**
 * Streaming keyword spotter (sherpa-onnx KWS zipformer, int8 encoder, chunk-16).
 * Fail closed: [create] throws unless every model file and the keywords file is present.
 * Keywords are phoneme lines in assets/wake/keywords.txt, copied to private storage on start,
 * so tuning is a file swap.
 */
class WakeWordDetector private constructor(
    private val spotter: KeywordSpotter,
    private val keywordsFile: File,
) {
    private val stream = spotter.createStream()

    /** Feeds 16 kHz mono float PCM. Returns true when a keyword fired on this chunk. */
    fun accept(samples: FloatArray): Boolean {
        stream.acceptWaveform(samples, SAMPLE_RATE)
        var hit = false
        while (spotter.isReady(stream)) {
            spotter.decode(stream)
            val r = spotter.getResult(stream)
            if (r.keyword.isNotEmpty()) {
                hit = true
                spotter.reset(stream)
            }
        }
        return hit
    }

    fun release() {
        stream.release()
        spotter.release()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val KEYWORDS_ASSET = "wake/keywords.txt"

        /** Throws IllegalStateException when the model or keywords are missing (fail closed). */
        fun create(context: Context, store: ModelStore): WakeWordDetector {
            val spec = ModelCatalog.byId(ModelCatalog.KWS_ID)
                ?: error("wake word model is not in the catalog")
            check(store.isInstalled(spec)) { "wake word model is not downloaded. Get it in Settings > Models." }

            val keywordsFile = store.dirFile("kws-keywords.txt")
            val kwBytes = try {
                context.assets.open(KEYWORDS_ASSET).use { it.readBytes() }
            } catch (e: java.io.IOException) {
                error("wake word keywords file is missing from the app")
            }
            check(kwBytes.isNotEmpty()) { "wake word keywords file is empty" }
            keywordsFile.writeBytes(kwBytes)

            fun f(name: String) = store.dirFile(name).absolutePath
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = f("kws-encoder.int8.onnx"),
                        decoder = f("kws-decoder.onnx"),
                        joiner = f("kws-joiner.int8.onnx"),
                    ),
                    tokens = f("kws-tokens.txt"),
                    numThreads = 1,
                    provider = "cpu",
                    modelType = "zipformer2",
                ),
                maxActivePaths = 4,
                keywordsFile = keywordsFile.absolutePath,
                keywordsScore = 1.5f,
                keywordsThreshold = 0.1f,
                numTrailingBlanks = 1,
            )
            val spotter = KeywordSpotter(assetManager = null, config = config)
            return WakeWordDetector(spotter, keywordsFile)
        }
    }
}
