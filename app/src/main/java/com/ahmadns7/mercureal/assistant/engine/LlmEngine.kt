package com.ahmadns7.mercureal.assistant.engine

import java.io.File

/** Kotlin side of llama.cpp via JNI. */
class LlmEngine {

    private var ctx: Long = 0L

    fun load(modelFile: File) {
        require(modelFile.exists()) { "missing llm model: ${modelFile.name}" }
        close()
        ctx = nativeInit(modelFile.absolutePath, THREADS, CONTEXT_TOKENS)
        check(ctx != 0L) { "llama failed to load ${modelFile.name}" }
    }

    fun respond(userText: String): String {
        check(ctx != 0L) { "llm not loaded" }
        return nativeGenerate(ctx, buildPrompt(userText), MAX_REPLY_TOKENS).trim()
    }

    fun close() {
        if (ctx != 0L) {
            nativeFree(ctx)
            ctx = 0L
        }
    }

    // ChatML template used by Qwen2.5 instruct models.
    private fun buildPrompt(userText: String): String {
        val s = "\u003C|im_start|\u003E"
        val e = "\u003C|im_end|\u003E"
        return s + "system\n" +
            "You are a concise, private voice assistant. Answer in one or two short sentences." + e + "\n" +
            s + "user\n" + userText + e + "\n" +
            s + "assistant\n"
    }

    private external fun nativeInit(modelPath: String, threads: Int, nCtx: Int): Long
    private external fun nativeGenerate(ctx: Long, prompt: String, maxTokens: Int): String
    private external fun nativeFree(ctx: Long)

    companion object {
        init { System.loadLibrary("mercureal_native") }
        const val THREADS = 4
        const val CONTEXT_TOKENS = 1024
        const val MAX_REPLY_TOKENS = 96
    }
}
