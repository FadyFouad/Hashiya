package com.etatech.hashiya.core.importer

/** BibTeX and BibLaTeX (spec §4.2). [parse] never throws: a broken entry becomes an [ImportIssue] and the rest go on. */
internal object BibTeXParser {
    private val MONTHS = listOf(
        "jan" to "January", "feb" to "February", "mar" to "March", "apr" to "April", "may" to "May", "jun" to "June",
        "jul" to "July", "aug" to "August", "sep" to "September", "oct" to "October", "nov" to "November", "dec" to "December"
    ).toMap()

    private val EXTENDED_NAME_KEYS = setOf("family", "given", "prefix", "suffix", "useprefix")

    fun parse(text: String): ImportParseResult {
        val macros = MONTHS.toMutableMap()
        val entries = mutableListOf<ImportedEntry>()
        val issues = mutableListOf<ImportIssue>()
        for (chunk in chunks(text)) {
            val reader = Reader(chunk.text, macros)
            val raw = try {
                reader.entry()
            } catch (e: Exception) {
                // Malformed, or any slip on input this parser didn't foresee: the entry is lost, never the file.
                issues += ImportIssue(chunk.line, ImportIssueKind.Malformed)
                continue
            } ?: continue
            val entry = build(raw, chunk.line)
            if (entry.title.isEmpty() && entry.doi == null) {
                issues += ImportIssue(chunk.line, ImportIssueKind.Empty)
            } else {
                entries += entry
            }
        }
        return ImportParseResult(ImportFormat.BibTeX, entries, issues)
    }

    private class Chunk(val text: String, val line: Int)

    /** Splits at every line that starts (after spaces) with `@`, letters, optional spaces and `{` or `(`. */
    private fun chunks(text: String): List<Chunk> {
        val starts = mutableListOf<Pair<Int, Int>>()
        var offset = 0
        var line = 1
        while (offset <= text.length) {
            val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
            if (startsEntry(text, offset, lineEnd)) starts += offset to line
            offset = lineEnd + 1
            line++
        }
        return starts.mapIndexed { index, (start, startLine) ->
            val end = starts.getOrNull(index + 1)?.first ?: text.length
            Chunk(text.substring(start, end), startLine)
        }
    }

    private fun startsEntry(text: String, from: Int, to: Int): Boolean {
        var i = from
        while (i < to && (text[i] == ' ' || text[i] == '\t')) i++
        if (i >= to || text[i] != '@') return false
        i++
        val lettersStart = i
        while (i < to && text[i].isAsciiLetter()) i++
        if (i == lettersStart) return false
        while (i < to && (text[i] == ' ' || text[i] == '\t')) i++
        return i < to && (text[i] == '{' || text[i] == '(')
    }

    private class Malformed : Exception()

    private class RawEntry(val type: String, val key: String?, val fields: Map<String, String>)

    /** Reads one chunk: `@type{key, name = value, …}`. Returns null for @comment, @preamble and @string. */
    private class Reader(private val s: String, private val macros: MutableMap<String, String>) {
        private var p = 0

        fun entry(): RawEntry? {
            while (p < s.length && s[p] != '@') p++
            p++
            val type = word().lowercase()
            skipSpaces()
            val close = when (peek()) {
                '{' -> '}'
                '(' -> ')'
                else -> throw Malformed()
            }
            p++
            when (type) {
                "comment", "preamble" -> return null

                "string" -> {
                    fields(close).forEach { (name, value) -> macros[name] = value }
                    return null
                }
            }
            val key = key(close)
            val fields = if (key.second) fields(close) else emptyMap()
            return RawEntry(type, key.first, fields)
        }

        /** The key, and whether fields follow. Rewinds when the entry has no key and starts with a field. */
        private fun key(close: Char): Pair<String?, Boolean> {
            skipSpaces()
            val start = p
            while (p < s.length && s[p] != ',' && s[p] != '=' && s[p] != close && !isSpace(s[p])) p++
            val key = s.substring(start, p).ifEmpty { null }
            skipSpaces()
            return when (peek()) {
                ',' -> {
                    p++
                    key to true
                }

                close -> {
                    p++
                    key to false
                }

                '=' -> {
                    p = start
                    null to true
                }

                else -> throw Malformed()
            }
        }

        /** `name = value` pairs up to [close]; field names lowercased, the first of a repeated name kept. */
        private fun fields(close: Char): Map<String, String> {
            val fields = LinkedHashMap<String, String>()
            while (true) {
                skipSpaces()
                when (peek()) {
                    close -> {
                        p++
                        return fields
                    }

                    ',' -> {
                        p++
                        continue
                    }
                }
                val start = p
                while (p < s.length && !isSpace(s[p]) && s[p] !in "=,{}\"#()") p++
                if (p == start) throw Malformed()
                val name = s.substring(start, p).lowercase()
                skipSpaces()
                if (peek() != '=') throw Malformed()
                p++
                val value = value()
                fields.putIfAbsent(name, value)
                skipSpaces()
                when (peek()) {
                    ',' -> p++
                    close -> Unit
                    else -> throw Malformed()
                }
            }
        }

        /** Parts joined by `#`: braced, quoted, a number or a macro. */
        private fun value(): String {
            val out = StringBuilder()
            while (true) {
                skipSpaces()
                val c = peek() ?: throw Malformed()
                when {
                    c == '{' -> out.append(braced())
                    c == '"' -> out.append(quoted())
                    c in '0'..'9' -> out.append(digits())
                    c.isAsciiLetter() -> out.append(macros[macroName().lowercase()].orEmpty())
                    else -> throw Malformed()
                }
                skipSpaces()
                if (peek() == '#') p++ else return out.toString()
            }
        }

        private fun braced(): String {
            val start = p + 1
            var depth = 0
            while (p < s.length) {
                when (s[p]) {
                    '{' -> depth++

                    '}' -> if (--depth == 0) {
                        p++
                        return s.substring(start, p - 1)
                    }
                }
                p++
            }
            throw Malformed()
        }

        private fun quoted(): String {
            p++
            val start = p
            var depth = 0
            while (p < s.length) {
                when (s[p]) {
                    '{' -> depth++

                    '}' -> depth--

                    '"' -> if (depth == 0) {
                        p++
                        return s.substring(start, p - 1)
                    }
                }
                p++
            }
            throw Malformed()
        }

        private fun digits(): String {
            val start = p
            while (p < s.length && s[p] in '0'..'9') p++
            return s.substring(start, p)
        }

        private fun macroName(): String {
            val start = p
            while (p < s.length && (s[p].isAsciiLetter() || s[p] in '0'..'9' || s[p] in "_-:.+/")) p++
            return s.substring(start, p)
        }

        private fun word(): String {
            val start = p
            while (p < s.length && s[p].isAsciiLetter()) p++
            return s.substring(start, p)
        }

        private fun peek(): Char? = s.getOrNull(p)

        private fun skipSpaces() {
            while (p < s.length && isSpace(s[p])) p++
        }
    }

    private fun build(raw: RawEntry, line: Int): ImportedEntry {
        val fields = raw.fields
        fun text(name: String): String? = fields[name]?.let { collapse(LatexDecoder.decode(it)) }
        fun rawText(name: String): String? = fields[name]?.let { collapse(cleanRaw(it)) }

        val journal = text("journal") ?: text("journaltitle")
        val howPublished = fields["howpublished"]
        val howPublishedIsUrl = howPublished != null &&
            (howPublished.contains("\\url", ignoreCase = true) || howPublished.contains("http", ignoreCase = true))
        val url = rawText("url") ?: howPublished?.takeIf { howPublishedIsUrl }?.let(::urlIn)
        val eprintType = (text("archiveprefix") ?: text("eprinttype"))?.lowercase()
        val doi = doiFrom(rawText("doi"))
            ?: (if (eprintType == "arxiv") arxivDoi(rawText("eprint")) else null)
            ?: arxivDoi(rawText("arxivid"))
            ?: doiFrom(url)
            ?: scholarArxivDoi(journal)
        val (firstPage, lastPage) = splitPages(fields["pages"]?.let(::cleanRaw))
        val kind = bibTeXKind(raw.type, doi, journal)
        return ImportedEntry(
            line = line,
            entryType = raw.type,
            citeKey = raw.key,
            title = text("title").orEmpty(),
            authors = names(fields["author"]).ifEmpty { names(fields["editor"]) },
            year = firstYear(fields["year"]) ?: firstYear(fields["date"]),
            venue = journal ?: text("booktitle") ?: text("school") ?: text("institution") ?: howPublished
                ?.takeUnless { howPublishedIsUrl }
                ?.let { collapse(LatexDecoder.decode(it)) },
            doi = doi,
            url = url,
            abstract = text("abstract"),
            volume = text("volume"),
            issue = text("number") ?: text("issue"),
            firstPage = firstPage,
            lastPage = lastPage,
            publisher = text("publisher"),
            workType = kind.workType,
            sourceType = kind.sourceType
        )
    }

    /** The URL in `\url{…}`, or the whole value when it is a bare link. */
    private fun urlIn(value: String): String? {
        val start = value.indexOf("\\url{", ignoreCase = true)
        if (start >= 0) {
            val end = value.indexOf('}', start)
            return collapse(cleanRaw(value.substring(start + 5, if (end < 0) value.length else end)))
        }
        return collapse(cleanRaw(value))?.takeIf { it.startsWith("http", ignoreCase = true) }
    }

    /** Display names from a BibTeX name list (spec §4.2, Names). */
    private fun names(raw: String?): List<String> {
        if (raw == null) return emptyList()
        return splitOutsideBraces(raw).mapNotNull(::displayName)
    }

    /** Splits on the word "and" (any case) between whitespace, outside braces. */
    private fun splitOutsideBraces(raw: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '{' -> depth++

                c == '}' -> depth--

                depth == 0 && isSpace(c) -> {
                    var j = i
                    while (j < raw.length && isSpace(raw[j])) j++
                    if (raw.regionMatches(j, "and", 0, 3, ignoreCase = true) && j + 3 < raw.length && isSpace(raw[j + 3])) {
                        parts += raw.substring(start, i)
                        start = j + 3
                        i = start
                        continue
                    }
                }
            }
            i++
        }
        parts += raw.substring(start)
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun displayName(raw: String): String? {
        if (raw.equals("others", ignoreCase = true)) return null
        if (raw.startsWith('{') && closingBrace(raw, 0) == raw.length - 1) return decoded(raw)
        val parts = splitTopLevelCommas(raw)
        val extended = parts.size > 1 && parts.all { part -> part.substringBefore('=', "").trim().lowercase() in EXTENDED_NAME_KEYS }
        if (extended) {
            val values = parts.associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=').trim() }
            return joinNames(listOf(values["given"], values["prefix"], values["family"], values["suffix"]))
        }
        val pieces = parts.filter { it.isNotEmpty() }
        return when (pieces.size) {
            0 -> null
            1 -> decoded(pieces[0])
            2 -> joinNames(listOf(pieces[1], pieces[0]))
            else -> joinNames(listOf(pieces[2], pieces[0], pieces[1]))
        }
    }

    private fun joinNames(parts: List<String?>): String? = collapse(parts.mapNotNull { it?.let(::decoded) }.joinToString(" "))

    private fun decoded(raw: String): String = collapse(LatexDecoder.decode(raw)).orEmpty()

    private fun splitTopLevelCommas(raw: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        raw.forEachIndexed { i, c ->
            when {
                c == '{' -> depth++

                c == '}' -> depth--

                c == ',' && depth == 0 -> {
                    parts += raw.substring(start, i).trim()
                    start = i + 1
                }
            }
        }
        parts += raw.substring(start).trim()
        return parts
    }

    private fun closingBrace(s: String, open: Int): Int {
        var depth = 0
        for (i in open until s.length) {
            when (s[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return i
            }
        }
        return -1
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
}
