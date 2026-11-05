package com.etatech.hashiya.core.data.importer

import java.io.File
import java.io.IOException

/** The decoded text of each job's file, in [directory] (filesDir/imports in the app): too large for a database row. */
internal class ImportFileStore(private val directory: File) {
    private fun file(jobId: Long) = File(directory, "$jobId.txt")

    /** @throws IOException when the text can't be written. */
    fun write(jobId: Long, text: String) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Can't create $directory")
        file(jobId).writeText(text, Charsets.UTF_8)
    }

    /** Null when the file is missing or unreadable. */
    fun read(jobId: Long): String? = try {
        file(jobId).takeIf { it.isFile }?.readText(Charsets.UTF_8)
    } catch (e: IOException) {
        null
    }

    fun delete(jobId: Long) {
        file(jobId).delete()
    }
}
