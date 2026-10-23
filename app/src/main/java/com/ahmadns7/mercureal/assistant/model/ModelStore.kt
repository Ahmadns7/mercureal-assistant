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

    /**
     * Installed means the final file exists at the exact pinned size. The sha256 was checked
     * before the file was moved into place (see ModelDownloader), so only verified files get this name.
     */
    fun isInstalled(spec: ModelSpec): Boolean {
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
    }

    fun bytesUsed(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
}
