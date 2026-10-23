package com.ahmadns7.mercureal.assistant.model

import android.content.Context
import java.io.File

/**
 * Models are sideloaded, never downloaded. Put them in the app's external files dir:
 *   adb push ggml-tiny.en.bin /sdcard/Android/data/com.ahmadns7.mercureal.assistant/files/
 *   adb push qwen2.5-0.5b-instruct-q4_k_m.gguf /sdcard/Android/data/com.ahmadns7.mercureal.assistant/files/
 * See scripts/fetch_models.sh for the exact files.
 */
class ModelStore(private val context: Context) {

    private val dir: File
        get() = context.getExternalFilesDir(null) ?: context.filesDir

    fun whisperModelFile(): File = File(dir, WHISPER_MODEL)
    fun llmModelFile(): File = File(dir, LLM_MODEL)

    companion object {
        const val WHISPER_MODEL = "ggml-tiny.en.bin"
        const val LLM_MODEL = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    }
}
