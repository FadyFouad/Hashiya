package com.etatech.hashiya.core.citation

/** Plain text, HTML for the rich clipboard, and RTF for exported reference lists. */
object Rendering {
    fun plain(citation: StyledCitation): String = citation.runs.joinToString("") { it.text }

    fun html(citation: StyledCitation): String = citation.runs.joinToString("") { run ->
        val escaped = escapeHtml(run.text)
        if (run.italic) "<i>$escaped</i>" else escaped
    }

    /** One paragraph per entry; APA entries get a 0.5-inch hanging indent. Readable by Word and Pages. */
    fun rtf(entries: List<StyledCitation>, hangingIndent: Boolean): String = buildString {
        append("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n")
        for (entry in entries) {
            append(if (hangingIndent) "{\\pard\\fi-720\\li720 " else "{\\pard ")
            for (run in entry.runs) {
                if (run.italic) append("{\\i ").append(escapeRtf(run.text)).append('}') else append(escapeRtf(run.text))
            }
            append("\\par}\n")
        }
        append('}')
    }

    private fun escapeHtml(text: String) = buildString {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    /** RTF is 7-bit: everything above U+007F is written as \uN? per UTF-16 unit (N signed 16-bit). */
    private fun escapeRtf(text: String) = buildString {
        for (c in text) {
            when {
                c == '\\' || c == '{' || c == '}' -> append('\\').append(c)
                c.code > 0x7F -> append("\\u").append(c.code.toShort().toInt()).append('?')
                else -> append(c)
            }
        }
    }
}
