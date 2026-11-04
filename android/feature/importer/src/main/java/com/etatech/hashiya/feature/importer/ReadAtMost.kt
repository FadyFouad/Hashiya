package com.etatech.hashiya.feature.importer

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads at most [limit] bytes: enough to tell a file is too large without reading all of it. */
internal fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
