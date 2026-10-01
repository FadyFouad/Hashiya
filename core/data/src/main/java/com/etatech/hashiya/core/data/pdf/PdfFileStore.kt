package com.etatech.hashiya.core.data.pdf

import java.io.File
import java.io.IOException
import java.io.InputStream

/** The largest PDF the app stores; a download or attach past it stops and keeps nothing. */
internal const val MAX_PDF_BYTES = 100L * 1024 * 1024

/** Suffix of the files a store writes before renaming them into place; the sweep removes any left behind. */
internal const val TEMP_SUFFIX = ".part"

/** PDF readers accept the `%PDF-` header anywhere in the first 1024 bytes, after a short preamble. */
private const val HEADER_WINDOW = 1024
private val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)
private const val BUFFER_SIZE = 64 * 1024

internal sealed interface StoreResult {
    data class Stored(val size: Long) : StoreResult

    data object NotPdf : StoreResult

    data object TooLarge : StoreResult
}

/**
 * Writing the file failed (the disk is full, or the rename didn't happen). Not an [IOException], so callers never mistake it for
 * the network or the source failing.
 */
internal class PdfWriteException(cause: Throwable) : Exception(cause)

/** Owns one folder of PDFs named `<paperId>.pdf`. Not thread-safe per paper id; callers run one store per paper at a time. */
internal class PdfFileStore(private val dir: File) {
    fun file(paperId: String): File = File(dir, "$paperId.pdf")

    /**
     * Copies [input] to a temporary file, checks it is a PDF and at most [maxBytes], then renames it over `<paperId>.pdf`. A rejected
     * or failed copy leaves the current file, if any, as it was, and no temporary file. [onProgress] gets the bytes copied so far.
     * Read failures from [input] are thrown as they are; write failures throw [PdfWriteException].
     */
    fun store(paperId: String, input: InputStream, maxBytes: Long, onProgress: (Long) -> Unit): StoreResult {
        val temp = try {
            dir.mkdirs()
            File.createTempFile("$paperId-", TEMP_SUFFIX, dir)
        } catch (e: IOException) {
            throw PdfWriteException(e)
        }
        var stored = false
        try {
            val head = ByteArray(HEADER_WINDOW)
            var headSize = 0
            var total = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            val output = try {
                temp.outputStream()
            } catch (e: IOException) {
                throw PdfWriteException(e)
            }
            output.use { out ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) return StoreResult.TooLarge
                    if (headSize < HEADER_WINDOW) {
                        val take = minOf(read, HEADER_WINDOW - headSize)
                        buffer.copyInto(head, destinationOffset = headSize, startIndex = 0, endIndex = take)
                        headSize += take
                        // A web page is usually small, but a large non-PDF shouldn't be read to the end before it is rejected.
                        if (headSize == HEADER_WINDOW && !head.containsPdfHeader(headSize)) return StoreResult.NotPdf
                    }
                    try {
                        out.write(buffer, 0, read)
                    } catch (e: IOException) {
                        throw PdfWriteException(e)
                    }
                    onProgress(total)
                }
                // Without this, a power loss just after the rename can leave an empty or partial `<paperId>.pdf`.
                try {
                    out.fd.sync()
                } catch (e: IOException) {
                    throw PdfWriteException(e)
                }
            }
            if (!head.containsPdfHeader(headSize)) return StoreResult.NotPdf
            val target = file(paperId)
            // rename(2) replaces the target atomically within one folder.
            if (!temp.renameTo(target)) throw PdfWriteException(IOException("Couldn't move the PDF into place"))
            stored = true
            return StoreResult.Stored(total)
        } finally {
            if (!stored) temp.delete()
        }
    }

    fun delete(paperId: String) {
        file(paperId).delete()
    }

    /** Deletes every PDF whose paper id is not in [keep], and every temporary file left by a store that never finished. */
    fun sweep(keep: Set<String>) {
        dir.listFiles().orEmpty().forEach { file ->
            val orphan = when {
                file.name.endsWith(TEMP_SUFFIX) -> true
                file.name.endsWith(".pdf") -> file.name.removeSuffix(".pdf") !in keep
                else -> false
            }
            if (orphan) file.delete()
        }
    }

    private fun ByteArray.containsPdfHeader(size: Int): Boolean =
        (0..size - PDF_HEADER.size).any { start -> PDF_HEADER.indices.all { this[start + it] == PDF_HEADER[it] } }
}
