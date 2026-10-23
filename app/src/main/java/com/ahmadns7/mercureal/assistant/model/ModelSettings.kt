package com.ahmadns7.mercureal.assistant.model

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.modelSettingsStore by preferencesDataStore(name = "model_settings")

/** The user's active STT and LLM choice, persisted in DataStore. */
class ModelSettings(private val context: Context) {

    private val sttKey = stringPreferencesKey("active_stt")
    private val llmKey = stringPreferencesKey("active_llm")

    val activeStt: Flow<String> = context.modelSettingsStore.data.map {
        resolve(it[sttKey], ModelKind.STT, ModelCatalog.DEFAULT_STT)
    }

    val activeLlm: Flow<String> = context.modelSettingsStore.data.map {
        resolve(it[llmKey], ModelKind.LLM, ModelCatalog.DEFAULT_LLM)
    }

    suspend fun setActiveStt(id: String) {
        context.modelSettingsStore.edit { it[sttKey] = id }
    }

    suspend fun setActiveLlm(id: String) {
        context.modelSettingsStore.edit { it[llmKey] = id }
    }

    private fun resolve(saved: String?, kind: ModelKind, default: String): String =
        if (saved != null && ModelCatalog.byId(saved)?.kind == kind) saved else default
}
