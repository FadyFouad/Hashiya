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

internal sealed interface StageResult {
    /** A checked PDF in a `.part` file in the store's folder; [PdfFileStore.commit] moves it into place, or the caller deletes it. */
    data class Staged(val file: File, val size: Long) : StageResult

    data object NotPdf : StageResult

    data object TooLarge : StageResult
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
     * Copies [input] to a `.part` file named after [prefix] and checks it is a PDF of at most [maxBytes]. A rejected or failed copy
     * leaves no file. Read failures from [input] are thrown as they are; write failures throw [PdfWriteException]. A staged file that
     * is never committed or deleted is removed by the next [sweep]. [onProgress] gets the bytes copied so far.
     */
    fun stage(prefix: String, input: InputStream, maxBytes: Long, onProgress: (Long) -> Unit): StageResult {
        val temp = try {
            dir.mkdirs()
            File.createTempFile("$prefix-", TEMP_SUFFIX, dir)
        } catch (e: IOException) {
            throw PdfWriteException(e)
        }
        var staged = false
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
                    if (total > maxBytes) return StageResult.TooLarge
                    if (headSize < HEADER_WINDOW) {
                        val take = minOf(read, HEADER_WINDOW - headSize)
                        buffer.copyInto(head, destinationOffset = headSize, startIndex = 0, endIndex = take)
                        headSize += take
                        // A web page is usually small, but a large non-PDF shouldn't be read to the end before it is rejected.
                        if (headSize == HEADER_WINDOW && !head.containsPdfHeader(headSize)) return StageResult.NotPdf
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
            if (!head.containsPdfHeader(headSize)) return StageResult.NotPdf
            staged = true
            return StageResult.Staged(temp, total)
        } finally {
            if (!staged) temp.delete()
        }
    }

    /** Moves a staged file over `<paperId>.pdf`; rename(2) replaces the target atomically within one folder. */
    fun commit(staged: File, paperId: String) {
        if (!staged.renameTo(file(paperId))) throw PdfWriteException(IOException("Couldn't move the PDF into place"))
    }

    /**
     * Stages [input] and moves it over `<paperId>.pdf`. A rejected or failed store leaves the current file, if any, as it was, and no
     * temporary file.
     */
    fun store(paperId: String, input: InputStream, maxBytes: Long, onProgress: (Long) -> Unit): StoreResult =
        when (val result = stage(paperId, input, maxBytes, onProgress)) {
            is StageResult.Staged -> {
                try {
                    commit(result.file, paperId)
                } catch (e: PdfWriteException) {
                    result.file.delete()
                    throw e
                }
                StoreResult.Stored(result.size)
            }

            StageResult.NotPdf -> StoreResult.NotPdf

            StageResult.TooLarge -> StoreResult.TooLarge
        }

    /** Bytes free where the PDFs are kept. */
    fun usableSpace(): Long {
        dir.mkdirs()
        return dir.usableSpace
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
