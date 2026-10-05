package com.etatech.hashiya.core.network.quota

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Search and filter-list responses on disk: kept 24 hours, at most 5 MB, least recently used removed first. Each file
 * is named after a hash of the request without its key and starts with the time it was saved; its modification time is
 * when it was last used. Lives in the cache directory, so the system may clear it and it is never backed up. Any
 * failure to read or write counts as a miss.
 */
class SearchCache(
    private val directory: File,
    private val maxBytes: Long = 5_000_000,
    private val lifetimeMillis: Long = 86_400_000,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** The stored body, if it is younger than the lifetime; marks it used. */
    @Synchronized
    fun read(key: String): String? = try {
        val file = fileFor(key)
        val contents = if (file.isFile) file.readBytes() else null
        if (contents == null || contents.size < HEADER_BYTES) {
            if (contents != null) file.delete()
            null
        } else {
            val time = now()
            val age = time - ByteBuffer.wrap(contents, 0, HEADER_BYTES).long
            if (age < 0 || age >= lifetimeMillis) {
                file.delete()
                null
            } else {
                file.setLastModified(time)
                String(contents, HEADER_BYTES, contents.size - HEADER_BYTES, Charsets.UTF_8)
            }
        }
    } catch (_: Exception) {
        null
    }

    @Synchronized
    fun write(key: String, body: String) {
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return
            val time = now()
            val bytes = body.toByteArray(Charsets.UTF_8)
            val contents = ByteBuffer.allocate(HEADER_BYTES + bytes.size).putLong(time).put(bytes).array()
            val file = fileFor(key)
            val temporary = File(directory, "${file.name}$TEMPORARY_SUFFIX")
            try {
                temporary.writeBytes(contents)
                if (!temporary.renameTo(file)) return
            } finally {
                temporary.delete()
            }
            file.setLastModified(time)
            trim()
        } catch (_: Exception) {
            // A cache that can't be written is just empty.
        }
    }

    /** Removes the least recently used files until the total fits. */
    private fun trim() {
        val files = directory.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    private fun fileFor(key: String) = File(directory, sha256(key))

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val HEADER_BYTES = 8
        private const val TEMPORARY_SUFFIX = ".tmp"

        /** The request without `api_key`, parameters sorted, so the same search hits whichever route sent it. */
        fun key(path: String, query: List<Pair<String, String>>): String {
            val parameters = query
                .filter { it.first != "api_key" }
                .sortedWith(compareBy({ it.first }, { it.second }))
                .joinToString("&") { "${it.first}=${it.second}" }
            return "$path?$parameters"
        }
    }
}
