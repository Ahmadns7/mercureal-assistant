package com.ahmadns7.mercureal.assistant.model

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.modelSettingsStore by preferencesDataStore(name = "model_settings")

/** The user's active STT and LLM choice, persisted in DataStore. */
class ModelSettings(private val context: Context) {

    private val sttKey = stringPreferencesKey("active_stt")
    private val llmKey = stringPreferencesKey("active_llm")
    private val ttsKey = stringPreferencesKey("active_tts")
    private val wakeKey = booleanPreferencesKey("wake_word_on")
    private val conversationKey = booleanPreferencesKey("conversation_on")
    private val followUpKey = intPreferencesKey("follow_up_seconds")

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
