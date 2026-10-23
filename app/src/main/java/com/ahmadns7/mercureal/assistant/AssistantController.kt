package com.ahmadns7.mercureal.assistant

import android.content.Context
import com.ahmadns7.mercureal.assistant.audio.MicRecorder
import com.ahmadns7.mercureal.assistant.engine.LlmEngine
import com.ahmadns7.mercureal.assistant.engine.SttEngine
import com.ahmadns7.mercureal.assistant.engine.Tts
import com.ahmadns7.mercureal.assistant.model.ModelCatalog
import com.ahmadns7.mercureal.assistant.model.ModelRepository
import com.ahmadns7.mercureal.assistant.model.ModelSpec
import com.ahmadns7.mercureal.assistant.model.PromptFormat
import com.ahmadns7.mercureal.assistant.ui.AssistantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates the fully on-device pipeline:
 * mic PCM -> whisper.cpp (STT) -> llama.cpp (LLM) -> Android TTS.
 * The active STT/LLM are read from settings each turn, so a switch in
 * Settings > Models takes effect on the next utterance.
 */
class AssistantController(context: Context, private val repo: ModelRepository) {

    val state = AssistantState()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recorder = MicRecorder()
    private val stt = SttEngine()
    private val llm = LlmEngine()
    private val tts = Tts(context)

    private var loadedStt: String? = null
    private var loadedLlm: String? = null

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

    private suspend fun ensureModelsLoaded() {
        val sttId = repo.settings.activeStt.first()
        if (sttId != loadedStt) {
            stt.load(repo.store.fileFor(installedSpec(sttId)))
            loadedStt = sttId
        }
        val llmId = repo.settings.activeLlm.first()
        if (llmId != loadedLlm) {
            val spec = installedSpec(llmId)
            llm.load(repo.store.fileFor(spec), spec.promptFormat ?: PromptFormat.CHATML)
            loadedLlm = llmId
        }
    }

    private fun installedSpec(id: String): ModelSpec {
        val spec = ModelCatalog.byId(id) ?: error("unknown model $id")
        check(repo.store.isInstalled(spec)) {
            "${spec.displayName} is not downloaded. Get it in Settings > Models."
        }
        return spec
    }

    fun release() {
        recorder.stop()
        stt.close()
        llm.close()
        tts.shutdown()
        repo.release()
        scope.cancel()
    }
}
