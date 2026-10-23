package com.ahmadns7.mercureal.assistant.engine

import com.ahmadns7.mercureal.assistant.model.PromptFormat
import java.io.File

/** Kotlin side of llama.cpp via JNI. */
class LlmEngine {

    private var ctx: Long = 0L
    private var format: PromptFormat = PromptFormat.CHATML

    fun load(modelFile: File, promptFormat: PromptFormat) {
        require(modelFile.exists()) { "missing llm model: ${modelFile.name}" }
        close()
        ctx = nativeInit(modelFile.absolutePath, THREADS, CONTEXT_TOKENS)
        check(ctx != 0L) { "llama failed to load ${modelFile.name}" }
        format = promptFormat
    }

    /** [memory] is extra system context (the user's saved notes), or null for a plain turn. */
    fun respond(userText: String, memory: String? = null): String {
        check(ctx != 0L) { "llm not loaded" }
        return nativeGenerate(ctx, buildPrompt(userText, memory), MAX_REPLY_TOKENS).trim()
    }

    fun close() {
        if (ctx != 0L) {
            nativeFree(ctx)
            ctx = 0L
        }
    }

    // Chat templates for the curated instruct models.
    // Special tokens are written as unicode escapes so they survive source handling.
    private fun buildPrompt(userText: String, memory: String?): String {
        val system = if (memory.isNullOrBlank()) SYSTEM else "$SYSTEM\n\n$memory"
        return buildPromptFor(system, userText)
    }

    private fun buildPromptFor(system: String, userText: String): String = when (format) {
        PromptFormat.CHATML ->
            "\u003C|im_start|\u003Esystem\n$system\u003C|im_end|\u003E\n" +
                "\u003C|im_start|\u003Euser\n$userText\u003C|im_end|\u003E\n" +
                "\u003C|im_start|\u003Eassistant\n"
        PromptFormat.LLAMA3 ->
            "\u003C|begin_of_text|\u003E" +
                "\u003C|start_header_id|\u003Esystem\u003C|end_header_id|\u003E\n\n$system\u003C|eot_id|\u003E" +
                "\u003C|start_header_id|\u003Euser\u003C|end_header_id|\u003E\n\n$userText\u003C|eot_id|\u003E" +
                "\u003C|start_header_id|\u003Eassistant\u003C|end_header_id|\u003E\n\n"
    }

    private external fun nativeInit(modelPath: String, threads: Int, nCtx: Int): Long
    private external fun nativeGenerate(ctx: Long, prompt: String, maxTokens: Int): String
    private external fun nativeFree(ctx: Long)

    companion object {
        init { System.loadLibrary("mercureal_native") }
        const val THREADS = 4
        /** KV-cache size in tokens. Turns are stateless, so this only needs to fit one prompt plus reply. */
        const val CONTEXT_TOKENS = 2048
        /** Upper bound on generated tokens per reply (replies are one or two sentences). */
        const val MAX_REPLY_TOKENS = 160
        private const val SYSTEM = "You are a concise, private voice assistant. Answer in one or two short sentences."
    }
}
