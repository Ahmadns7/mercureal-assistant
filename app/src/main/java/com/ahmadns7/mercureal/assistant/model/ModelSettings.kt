package com.ahmadns7.mercureal.assistant.model

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.ahmadns7.mercureal.assistant.translate.Languages

private val Context.modelSettingsStore by preferencesDataStore(name = "model_settings")

/** The user's active STT and LLM choice, persisted in DataStore. */
class ModelSettings(private val context: Context) {

    private val sttKey = stringPreferencesKey("active_stt")
    private val llmKey = stringPreferencesKey("active_llm")
    private val ttsKey = stringPreferencesKey("active_tts")
    private val wakeKey = booleanPreferencesKey("wake_word_on")
    private val conversationKey = booleanPreferencesKey("conversation_on")
    private val followUpKey = intPreferencesKey("follow_up_seconds")
    private val sttLangKey = stringPreferencesKey("stt_language")
    private val translationKey = booleanPreferencesKey("translation_on")
    private val targetKey = stringPreferencesKey("translation_target")

    /** Speech language for multilingual whisper models: "auto" (detect) or a language code. Default auto. */
    val sttLanguage: Flow<String> = context.modelSettingsStore.data.map {
        (it[sttLangKey] ?: "auto").takeIf { c -> c in Languages.sttCodes } ?: "auto"
    }

    suspend fun setSttLanguage(code: String) {
        require(code in Languages.sttCodes)
        context.modelSettingsStore.edit { it[sttLangKey] = code }
    }

    /** "say X in <language>" and "translate X" intents. Off by default. */
    val translationOn: Flow<Boolean> = context.modelSettingsStore.data.map { it[translationKey] ?: false }

    suspend fun setTranslationOn(on: Boolean) {
        context.modelSettingsStore.edit { it[translationKey] = on }
    }

    /** Default translation target when the request names none. */
    val translationTarget: Flow<String> = context.modelSettingsStore.data.map {
        (it[targetKey] ?: Languages.DEFAULT_TARGET).takeIf { c -> Languages.byCode(c) != null } ?: Languages.DEFAULT_TARGET
    }

    suspend fun setTranslationTarget(code: String) {
        require(Languages.byCode(code) != null)
        context.modelSettingsStore.edit { it[targetKey] = code }
    }

    /** Whether the always-on wake word service should run. Off by default. */
    val wakeOn: Flow<Boolean> = context.modelSettingsStore.data.map { it[wakeKey] ?: false }

    suspend fun setWakeOn(on: Boolean) {
        context.modelSettingsStore.edit { it[wakeKey] = on }
    }

    /** Continuous conversation: keep listening for follow-ups after each reply. On by default. */
    val conversationOn: Flow<Boolean> = context.modelSettingsStore.data.map { it[conversationKey] ?: true }

    suspend fun setConversationOn(on: Boolean) {
        context.modelSettingsStore.edit { it[conversationKey] = on }
    }

    /** Silence (seconds) that ends a follow-up window. Allowed: 30, 60, 120. Default 60. */
    val followUpSeconds: Flow<Int> = context.modelSettingsStore.data.map {
        (it[followUpKey] ?: 60).takeIf { s -> s in FOLLOW_UP_OPTIONS } ?: 60
    }

    suspend fun setFollowUpSeconds(seconds: Int) {
        require(seconds in FOLLOW_UP_OPTIONS)
        context.modelSettingsStore.edit { it[followUpKey] = seconds }
    }

    val activeStt: Flow<String> = context.modelSettingsStore.data.map {
        resolve(it[sttKey], ModelKind.STT, ModelCatalog.DEFAULT_STT)
    }

    val activeLlm: Flow<String> = context.modelSettingsStore.data.map {
        resolve(it[llmKey], ModelKind.LLM, ModelCatalog.DEFAULT_LLM)
    }

    val activeTts: Flow<String> = context.modelSettingsStore.data.map {
        resolve(it[ttsKey], ModelKind.TTS, ModelCatalog.DEFAULT_TTS)
    }

    suspend fun setActiveTts(id: String) {
        context.modelSettingsStore.edit { it[ttsKey] = id }
    }

    suspend fun setActiveStt(id: String) {
        context.modelSettingsStore.edit { it[sttKey] = id }
    }

    suspend fun setActiveLlm(id: String) {
        context.modelSettingsStore.edit { it[llmKey] = id }
    }

    private fun resolve(saved: String?, kind: ModelKind, default: String): String =
        if (saved != null && ModelCatalog.byId(saved)?.kind == kind) saved else default

    companion object {
        val FOLLOW_UP_OPTIONS = listOf(30, 60, 120)
    }
}
