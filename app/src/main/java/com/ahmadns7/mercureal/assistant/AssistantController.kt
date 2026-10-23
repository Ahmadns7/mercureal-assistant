package com.ahmadns7.mercureal.assistant

import android.content.Context
import com.ahmadns7.mercureal.assistant.audio.MicRecorder
import com.ahmadns7.mercureal.assistant.commands.DeviceActions
import com.ahmadns7.mercureal.assistant.commands.DeviceCommand
import com.ahmadns7.mercureal.assistant.commands.DeviceCommands
import com.ahmadns7.mercureal.assistant.audio.VoiceActivityCapture
import com.ahmadns7.mercureal.assistant.engine.LlmEngine
import com.ahmadns7.mercureal.assistant.engine.SttEngine
import com.ahmadns7.mercureal.assistant.engine.Tts
import com.ahmadns7.mercureal.assistant.memory.VoiceIntent
import com.ahmadns7.mercureal.assistant.memory.VoiceIntents
import com.ahmadns7.mercureal.assistant.model.ModelCatalog
import com.ahmadns7.mercureal.assistant.model.ModelRepository
import com.ahmadns7.mercureal.assistant.model.ModelSpec
import com.ahmadns7.mercureal.assistant.model.PromptFormat
import com.ahmadns7.mercureal.assistant.translate.Languages
import com.ahmadns7.mercureal.assistant.translate.Translation
import com.ahmadns7.mercureal.assistant.ui.AssistantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Orchestrates the fully on-device pipeline:
 * mic PCM -> whisper.cpp (STT) -> [notes intents] -> llama.cpp (LLM) -> sherpa-onnx (offline TTS).
 *
 * Conversation mode: after a turn is spoken, the controller opens a follow-up window and listens
 * with [VoiceActivityCapture] for the next utterance. The window closes after the configured
 * silence (default 60 s), when the user says an end phrase, or when hold-to-talk takes over.
 * The mic is not captured while the reply is being spoken, and wake-word hits are ignored during
 * speech and for a short echo tail afterwards.
 *
 * Every answered turn is saved to the on-device history (see [com.ahmadns7.mercureal.assistant.history.HistoryStore]).
 * The last few turns are passed to the LLM so follow-ups like "what did I just ask" work.
 */
class AssistantController(context: Context, val repo: ModelRepository) {

    val state = AssistantState()
    private val notes get() = repo.notes

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recorder = MicRecorder()
    private val vad = VoiceActivityCapture()
    private val stt = SttEngine()
    private val llm = LlmEngine()
    private val tts = Tts(context, repo.store)
    private val deviceActions = DeviceActions(context) { perm -> permissionRequester?.invoke(perm) }

    /** Set by the activity: shows a runtime permission prompt for device commands. */
    @Volatile var permissionRequester: ((String) -> Unit)? = null

    /** Held for a whole turn or conversation session, so turns never overlap. */
    private val turnLock = Mutex()
    @Volatile private var sessionCancelled = false
    @Volatile private var quietUntil = 0L

    /** Groups the turns of one conversation session in the history. New id per session. */
    @Volatile private var sessionId: String = java.util.UUID.randomUUID().toString()

    private var loadedStt: String? = null
    private var loadedLlm: String? = null
    private var loadedTts: String? = null

    /**
     * True while the controller owns the microphone (a turn or a conversation session). The wake
     * word service releases its own mic while this is set, so only one AudioRecord is open.
     */
    @Volatile var micInUse = false
        private set

    /** True while a reply is spoken or its echo tail is still playing. */
    fun isSpeakingOrEcho(): Boolean = System.currentTimeMillis() < quietUntil

    /** Ends an open follow-up window. */
    fun endSession() {
        sessionCancelled = true
    }

    fun startListening() {
        // Hold-to-talk interrupts any open follow-up window. Never open a second mic while one is in use.
        sessionCancelled = true
        if (micInUse || turnLock.isLocked) return
        state.session = false
        state.label = "listening"
        state.reply = ""
        recorder.start()
    }

    /**
     * Wake-word hand-off: runs the turn on an utterance captured after the keyword fired.
     * Returns false (and drops the audio) if a turn or session is already running.
     */
    fun answerUtterance(pcm: FloatArray): Boolean {
        if (!turnLock.tryLock()) return false
        scope.launch {
            try {
                runSession(pcm)
            } finally {
                turnLock.unlock()
            }
        }
        return true
    }

    fun stopListeningAndAnswer() {
        val pcm = recorder.stop()
        scope.launch { turnLock.withLock { runSession(pcm) } }
    }

    private suspend fun runSession(firstPcm: FloatArray) {
        sessionCancelled = false
        sessionId = java.util.UUID.randomUUID().toString()
        micInUse = true
        var pcm: FloatArray? = firstPcm
        try {
            while (pcm != null) {
                val keepGoing = handleTurn(pcm, followUp = state.session)
                if (!keepGoing || sessionCancelled) break
                if (!repo.settings.conversationOn.first()) break

                val windowMs = repo.settings.followUpSeconds.first() * 1000L
                state.session = true
                state.label = "listening"
                // Let the room's echo of the reply die down before opening the mic.
                delay(ECHO_TAIL_MS)
                if (sessionCancelled) break
                pcm = withContext(Dispatchers.IO) { vad.capture(windowMs) { sessionCancelled } }
                if (pcm == null) {
                    // Silence ran out the follow-up window, or the mic was unavailable.
                    if (!sessionCancelled) state.label = "idle"
                    break
                }
            }
        } catch (e: Exception) {
            state.label = "error: ${e.message}"
        } finally {
            micInUse = false
            state.session = false
            if (!state.label.startsWith("error")) state.label = "idle"
        }
    }

    /** Returns false when the turn was an end phrase that closes the conversation. */
    private suspend fun handleTurn(pcm: FloatArray, followUp: Boolean): Boolean {
        ensureModelsLoaded()
        state.label = "transcribing"
        val text = withContext(Dispatchers.Default) { stt.transcribe(pcm, sttLanguage()) }
        state.transcript = text
        if (text.isBlank()) {
            state.label = "didn't catch that"
            return true
        }
        if (followUp && END_PHRASE.matches(text.trim())) {
            state.reply = ""
            return false
        }

        val translation = if (repo.settings.translationOn.first()) Translation.parse(text) else null
        if (translation != null) {
            val target = translation.target
                ?: Languages.byCode(repo.settings.translationTarget.first())
                ?: Languages.byCode(Languages.DEFAULT_TARGET)!!
            state.label = "translating"
            val translated = withContext(Dispatchers.Default) { llm.translate(translation.text, target.name) }
            saveTurn(text, translated, "translate")
            state.reply = translated
            state.label = "speaking"
            // The bundled voice is English-only: only speak English output, otherwise show it.
            if (Languages.voiceCanSpeak(target)) {
                try {
                    withContext(Dispatchers.IO) { tts.speak(translated) }
                } finally {
                    quietUntil = System.currentTimeMillis() + ECHO_TAIL_MS
                }
            } else {
                state.reply = "$translated\n(${target.name}: shown on screen only, the voice is English-only)"
            }
            return true
        }

        val device = DeviceCommands.parse(text)
        if (device !is DeviceCommand.None) {
            val reply = deviceActions.run(device) ?: "ok"
            saveTurn(text, reply, "device")
            state.reply = reply
            state.label = "speaking"
            try {
                withContext(Dispatchers.IO) { tts.speak(reply) }
            } finally {
                quietUntil = System.currentTimeMillis() + ECHO_TAIL_MS
            }
            return true
        }

        val (reply, mode) = when (val intent = VoiceIntents.parse(text)) {
            is VoiceIntent.Remember -> {
                notes.add(intent.text)
                "got it, saved." to "note"
            }
            is VoiceIntent.Recall -> {
                state.label = "thinking"
                val memory = VoiceIntents.memoryContext(notes.all(), intent.topic)
                val recent = repo.history.recentContext()
                val combined = listOfNotNull(memory, recent).joinToString("\n\n")
                withContext(Dispatchers.Default) { llm.respond(text, combined) } to "recall"
            }
            VoiceIntent.None -> {
                state.label = "thinking"
                val recent = repo.history.recentContext()
                withContext(Dispatchers.Default) { llm.respond(text, recent) } to "chat"
            }
        }
        saveTurn(text, reply, mode)
        state.reply = reply
        state.label = "speaking"
        try {
            withContext(Dispatchers.IO) { tts.speak(reply) }
        } finally {
            quietUntil = System.currentTimeMillis() + ECHO_TAIL_MS
        }
        return true
    }

    /** Saves an answered turn to history. A failed save never blocks the reply. */
    private suspend fun saveTurn(question: String, answer: String, mode: String) {
        if (answer.isBlank()) return
        runCatching { repo.history.record(sessionId, mode, question, answer) }
    }

    /**
     * Language for whisper: the Settings choice only applies to multilingual models.
     * English-only (.en) models always get "en", since whisper cannot detect language on them.
     */
    private suspend fun sttLanguage(): String {
        val sttId = repo.settings.activeStt.first()
        val multilingual = ModelCatalog.byId(sttId)?.multilingual == true
        return if (multilingual) repo.settings.sttLanguage.first() else "en"
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
        // Fails loudly (no speech, no system voice) if the offline voice is missing or broken.
        val ttsId = repo.settings.activeTts.first()
        if (ttsId != loadedTts) {
            tts.load(installedSpec(ttsId))
            loadedTts = ttsId
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
        tts.close()
        repo.release()
        scope.cancel()
    }

    private companion object {
        const val ECHO_TAIL_MS = 800L
        val END_PHRASE = Regex(
            "^(stop listening|that's all|thats all|that is all|goodbye|bye|stop)[\\s.!?]*$",
            RegexOption.IGNORE_CASE,
        )
    }
}
