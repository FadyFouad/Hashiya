package com.etatech.hashiya.core.importer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object ImportParser {
    /** Null when the text is neither BibTeX nor RIS. */
    fun parse(text: String): ImportParseResult? {
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val firstLine = normalized.lineSequence().firstOrNull { collapse(it) != null }
        return when {
            firstLine != null && RisParser.tagLine(firstLine) != null -> RisParser.parse(normalized)
            BIBTEX_ENTRY.containsMatchIn(normalized) -> BibTeXParser.parse(normalized)
            else -> null
        }
    }

    /** UTF-8 with any BOM dropped; Windows-1252 when the bytes aren't valid UTF-8. */
    fun decode(bytes: ByteArray): String {
        val hasBom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val body = if (hasBom) ByteBuffer.wrap(bytes, 3, bytes.size - 3) else ByteBuffer.wrap(bytes)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(body)
                .toString()
        } catch (e: CharacterCodingException) {
            // Old EndNote exports. Bytes Windows-1252 leaves undefined become U+FFFD instead of failing.
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    private val BIBTEX_ENTRY = Regex("""@[A-Za-z]+[ \t]*[{(]""")
}
