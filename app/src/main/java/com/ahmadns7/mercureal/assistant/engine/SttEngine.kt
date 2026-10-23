package com.ahmadns7.mercureal.assistant.engine

import java.io.File

/** Kotlin side of whisper.cpp via JNI. */
class SttEngine {

    private var ctx: Long = 0L

    fun load(modelFile: File) {
        require(modelFile.exists()) { "missing whisper model: ${modelFile.name}" }
        close()
        ctx = nativeInit(modelFile.absolutePath)
        check(ctx != 0L) { "whisper failed to load ${modelFile.name}" }
    }

    fun transcribe(pcm: FloatArray): String {
        if (ctx == 0L || pcm.isEmpty()) return ""
        return nativeTranscribe(ctx, pcm).trim()
    }

    fun close() {
        if (ctx != 0L) {
            nativeFree(ctx)
            ctx = 0L
        }
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(ctx: Long, pcm: FloatArray): String
    private external fun nativeFree(ctx: Long)

    companion object {
        init { System.loadLibrary("mercureal_native") }
    }
}
