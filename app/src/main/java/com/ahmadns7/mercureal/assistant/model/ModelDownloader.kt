package com.ahmadns7.mercureal.assistant.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The ONLY code in the app that touches the network. It fetches one curated model file over HTTPS,
 * streams it into a .part file in private storage, checks the exact size and sha256, and only then
 * renames it to its final name. Any failure deletes the partial file.
 */
object ModelDownloader {

    suspend fun download(
        spec: ModelSpec,
        store: ModelStore,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val part = store.partFileFor(spec)
        part.delete()

        val conn = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "mercureal-assistant")
        }

        try {
            conn.connect()
            if (conn.responseCode !in 200..299) error("server returned http ${conn.responseCode}")

            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        onProgress((done.toFloat() / spec.sizeBytes).coerceAtMost(1f))
                    }
                }
            }

            if (done != spec.sizeBytes) error("size mismatch: got $done bytes, expected ${spec.sizeBytes}")
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hex.equals(spec.sha256, ignoreCase = true)) error("checksum mismatch, file rejected")

            val dest = store.fileFor(spec)
            dest.delete()
            if (!part.renameTo(dest)) error("could not move the verified file into place")
        } catch (e: Throwable) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }
}
