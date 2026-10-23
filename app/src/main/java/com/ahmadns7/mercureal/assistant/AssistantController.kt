package com.ahmadns7.mercureal.assistant

import android.content.Context
import com.ahmadns7.mercureal.assistant.audio.MicRecorder
import com.ahmadns7.mercureal.assistant.engine.LlmEngine
import com.ahmadns7.mercureal.assistant.engine.SttEngine
import com.ahmadns7.mercureal.assistant.engine.Tts
import com.ahmadns7.mercureal.assistant.model.ModelStore
import com.ahmadns7.mercureal.assistant.ui.AssistantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates the fully on-device pipeline:
 * mic PCM -> whisper.cpp (STT) -> llama.cpp (LLM) -> Android TTS.
 * No network code exists anywhere in this app.
 */
class AssistantController(context: Context) {

    val state = AssistantState()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val models = ModelStore(context)
    private val recorder = MicRecorder()
    private val stt = SttEngine()
    private val llm = LlmEngine()
    private val tts = Tts(context)

    private var loaded = false

    fun startListening() {
        state.label = "listening"
        state.reply = ""
        recorder.start()
    }

    fun stopListeningAndAnswer() {
        val pcm = recorder.stop()
        scope.launch {
            try {
                ensureModelsLoaded()
                state.label = "transcribing"
                val text = withContext(Dispatchers.Default) { stt.transcribe(pcm) }
                state.transcript = text
                if (text.isBlank()) {
                    state.label = "didn't catch that"
                    return@launch
                }
                state.label = "thinking"
                val reply = withContext(Dispatchers.Default) { llm.respond(text) }
                state.reply = reply
                state.label = "speaking"
                tts.speak(reply)
                state.label = "idle"
            } catch (e: Exception) {
                state.label = "error: ${e.message}"
            }
        }
    }

    private fun ensureModelsLoaded() {
        if (loaded) return
        stt.load(models.whisperModelFile())
        llm.load(models.llmModelFile())
        loaded = true
    }

    fun release() {
        recorder.stop()
        stt.close()
        llm.close()
        tts.shutdown()
        scope.coroutineContext[SupervisorJob]?.cancel()
    }
}
