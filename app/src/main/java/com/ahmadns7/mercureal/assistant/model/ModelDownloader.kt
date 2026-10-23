package com.ahmadns7.mercureal.assistant.model

import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Size or sha256 mismatch. The partial file is deleted, so the next attempt starts from zero.
 * Plain network failures and cancellation are NOT this type: they keep the .part for resume.
 */
class ModelVerificationException(message: String) : IOException(message)

/**
 * The ONLY code in the app that touches the network. It fetches one curated model file over HTTPS
 * into a .part file in private storage, then checks the exact size and sha256 of the FULL file and
 * only then renames it to its final name.
 *
 * Resume: if a .part file exists, the download asks for `Range: bytes=<partSize>-`. A 206 response
 * whose Content-Range starts at that offset is appended to the existing bytes. Anything else (the
 * server ignored Range, or refused it) restarts from byte 0. The sha256 always covers the whole file,
 * including the bytes that were already on disk before this attempt.
 *
 * Failure policy:
 *  - cancel or a network error keeps the .part, so a later download resumes it.
 *  - a size or checksum mismatch is a verification failure and deletes the .part.
 */
object ModelDownloader {

    private const val BUF_SIZE = 64 * 1024

    /**
     * @param onStart called once with the byte offset the transfer actually starts from
     *   (0 for a fresh download or a restart, otherwise the resumed offset).
     * @param onProgress fraction of the full file on disk, 0..1.
     */
    suspend fun download(
        spec: ModelSpec,
        store: ModelStore,
        onStart: (resumedFrom: Long) -> Unit = {},
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val part = store.partFileFor(spec)
        if (part.isFile && part.length() > spec.sizeBytes) part.delete()
        val existing = if (part.isFile) part.length() else 0L

        val digest: MessageDigest = if (existing < spec.sizeBytes) {
            fetchInto(spec, part, existing, onStart, onProgress)
        } else {
            // Complete on disk but never verified (for example the app stopped before the rename).
            onStart(existing)
            hashFile(part, MessageDigest.getInstance("SHA-256"))
        }

        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (!hex.equals(spec.sha256, ignoreCase = true)) {
            part.delete()
            throw ModelVerificationException("checksum mismatch, file rejected")
        }

        val dest = store.fileFor(spec)
        dest.delete()
        if (!part.renameTo(dest)) error("could not move the verified file into place")
        if (spec.archive != null) extractMembers(spec, dest, store)
    }

    private fun openArchive(format: ArchiveFormat, file: File): ArchiveInputStream<*> = when (format) {
        ArchiveFormat.TAR_BZ2 -> TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(FileInputStream(file)))
        )
        ArchiveFormat.ZIP -> ZipArchiveInputStream(BufferedInputStream(FileInputStream(file)))
    }

    /**
     * Stream-extracts the verified archive. Only entries named in spec.members are written, and each
     * one is size- and sha256-checked against its pin before it is renamed into place. Any failure
     * removes every member, and the archive is deleted in all cases so no unverified data is left.
     */
    private fun extractMembers(spec: ModelSpec, archive: File, store: ModelStore) {
        val format = spec.archive ?: return
        val expected = spec.members.associateBy { it.path }
        val written = HashSet<String>()
        try {
            openArchive(format, archive).use { ais ->
                while (true) {
                    val entry = ais.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val member = expected[entry.name] ?: continue
                    val tmp = store.dirFile(member.fileName + ".part")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(BUF_SIZE)
                        while (true) {
                            val n = ais.read(buf)
                            if (n < 0) break
                            size += n
                            if (size > member.sizeBytes) {
                                throw ModelVerificationException("${member.fileName}: larger than pinned size")
                            }
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                        }
                    }
                    if (size != member.sizeBytes) {
                        throw ModelVerificationException("${member.fileName}: size ${size}, expected ${member.sizeBytes}")
                    }
                    val hex = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!hex.equals(member.sha256, ignoreCase = true)) {
                        throw ModelVerificationException("${member.fileName}: checksum mismatch")
                    }
                    val dest = store.dirFile(member.fileName)
                    dest.delete()
                    if (!tmp.renameTo(dest)) error("could not move ${member.fileName} into place")
                    written += member.path
                }
            }
            val missing = expected.keys - written
            if (missing.isNotEmpty()) throw ModelVerificationException("archive is missing ${missing.size} expected file(s)")
        } catch (e: Exception) {
            spec.members.forEach {
                store.dirFile(it.fileName).delete()
                store.dirFile(it.fileName + ".part").delete()
            }
            throw if (e is ModelVerificationException) e
            else ModelVerificationException("archive extraction failed: ${e.message}")
        } finally {
            archive.delete()
        }
    }

    /**
     * Streams the remainder of the file into [part] and returns a digest that already covers every
     * byte on disk. Throws IOException (part kept) on interruption, ModelVerificationException
     * (part deleted) on a size overrun.
     */
    private suspend fun fetchInto(
        spec: ModelSpec,
        part: File,
        existing: Long,
        onStart: (Long) -> Unit,
        onProgress: (Float) -> Unit,
    ): MessageDigest {
        var from = existing
        var conn = open(spec.url, from)
        try {
            conn.connect()
            var code = conn.responseCode
            if (from > 0 && !(code == HttpURLConnection.HTTP_PARTIAL && contentRangeStart(conn) == from)) {
                // Range not honoured (or refused, e.g. 416): throw the partial away and start over.
                conn.disconnect()
                part.delete()
                from = 0L
                conn = open(spec.url, 0L)
                conn.connect()
                code = conn.responseCode
            }
            if (code !in 200..299) throw IOException("server returned http $code")

            onStart(from)
            val digest = MessageDigest.getInstance("SHA-256")
            if (from > 0) hashFile(part, digest)

            var done = from
            conn.inputStream.use { input ->
                FileOutputStream(part, from > 0).use { out ->
                    val buf = ByteArray(BUF_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        onProgress((done.toFloat() / spec.sizeBytes).coerceAtMost(1f))
                    }
                }
            }

            if (done > spec.sizeBytes) {
                part.delete()
                throw ModelVerificationException("size mismatch: got $done bytes, expected ${spec.sizeBytes}")
            }
            if (done < spec.sizeBytes) {
                // The stream ended early: an interruption, not corruption. Keep the .part for resume.
                throw IOException("interrupted at $done of ${spec.sizeBytes} bytes, partial kept for resume")
            }
            return digest
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, from: Long): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "mercureal-assistant")
            setRequestProperty("Accept-Encoding", "identity")
            if (from > 0) setRequestProperty("Range", "bytes=$from-")
        }

    /** Start offset from `Content-Range: bytes <start>-<end>/<total>`, or null if absent. */
    private fun contentRangeStart(conn: HttpURLConnection): Long? =
        conn.getHeaderField("Content-Range")
            ?.let { Regex("""^\s*bytes\s+(\d+)-""").find(it) }
            ?.groupValues?.get(1)?.toLongOrNull()

    private fun hashFile(file: File, digest: MessageDigest): MessageDigest {
        file.inputStream().use { input ->
            val buf = ByteArray(BUF_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest
    }
}
