package com.ahmadns7.mercureal.assistant.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Speech output via the platform TextToSpeech engine.
 * TODO: verify the installed TTS voice is fully offline on target devices
 * (Pico TTS is offline; some cloud-backed voices are not). Ship a bundled
 * Piper/Sherpa-ONNX voice if the platform voice can't be guaranteed offline.
 */
class Tts(context: Context) {

    private var ready = false
    private val engine: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) engine.language = Locale.US
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    fun shutdown() {
        engine.stop()
        engine.shutdown()
    }
}
