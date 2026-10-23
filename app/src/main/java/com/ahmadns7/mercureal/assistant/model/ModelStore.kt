package com.ahmadns7.mercureal.assistant.model

import android.content.Context
import java.io.File

/**
 * Model files live in the app's PRIVATE internal storage (filesDir/models).
 * Never external storage: other apps and shared storage cannot read them.
 */
class ModelStore(context: Context) {

    private val dir = File(context.filesDir, "models").apply { mkdirs() }

    fun fileFor(spec: ModelSpec): File = File(dir, spec.fileName)

    fun partFileFor(spec: ModelSpec): File = File(dir, spec.fileName + ".part")

    /** A file inside the models directory by its name (archive members, keyword files). */
    fun dirFile(name: String): File = File(dir, name)

    /**
     * Installed means the final file exists at the exact pinned size. For an archive model the
     * archive is deleted after extraction, so installed means every extracted member is present at
     * its pinned size. Each file was sha256-checked before it was moved into place (see ModelDownloader).
     */
    fun isInstalled(spec: ModelSpec): Boolean {
        if (spec.members.isNotEmpty()) {
            return spec.members.all { m -> dirFile(m.fileName).let { it.isFile && it.length() == m.sizeBytes } }
        }
        val f = fileFor(spec)
        return f.isFile && f.length() == spec.sizeBytes
    }

    /** Bytes of an unfinished download kept in the .part file (0 when there is none). */
    fun partialBytesFor(spec: ModelSpec): Long {
        val f = partFileFor(spec)
        return if (f.isFile) f.length() else 0L
    }

    fun delete(spec: ModelSpec) {
        fileFor(spec).delete()
        partFileFor(spec).delete()
        spec.members.forEach { m ->
            dirFile(m.fileName).delete()
            dirFile(m.fileName + ".part").delete()
        }
    }

    /** Directory for TTS companion files (tokens, lexicon) staged from the app's bundled assets. */
    fun ttsAuxDir(): File = File(dir, "tts").apply { mkdirs() }

    fun bytesUsed(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
