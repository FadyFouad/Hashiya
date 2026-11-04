package com.etatech.hashiya.core.importer

/** RIS (spec §4.3). Lines are already `\n`-separated. [parse] never throws. */
internal object RisParser {
    private val VENUE_TAGS = listOf("T2", "JF", "JO", "BT", "JA", "J2")
    private val DOI_TAGS = listOf("DO", "UR", "L3", "M3", "N1")

    /** A tag line split into its tag and value, or null for any other line. */
    internal fun tagLine(line: String): Pair<String, String>? {
        if (line.length < 4) return null
        val a = line[0]
        val b = line[1]
        if (a !in 'A'..'Z' || (b !in 'A'..'Z' && b !in '0'..'9')) return null
        var i = 2
        var spaces = 0
        while (i < line.length && line[i] == ' ' && spaces < 2) {
            i++
            spaces++
        }
        if (spaces == 0 || i >= line.length || line[i] != '-') return null
        i++
        if (i < line.length && line[i] == ' ') i++
        return line.substring(0, 2) to line.substring(i)
    }

    private class Record(val line: Int) {
        val tags = mutableListOf<Pair<String, StringBuilder>>()

        fun values(tag: String): List<String> = tags.filter { it.first == tag }.mapNotNull { collapse(it.second.toString()) }

        fun first(tag: String): String? = values(tag).firstOrNull()
    }

    fun parse(text: String): ImportParseResult {
        val entries = mutableListOf<ImportedEntry>()
        val issues = mutableListOf<ImportIssue>()
        var record: Record? = null
        var strayLine: Int? = null
        var lastValue: StringBuilder? = null

        fun closeRecord() {
            record?.let { open ->
                val entry = build(open)
                if (entry.title.isEmpty() &&
                    entry.doi == null
                ) {
                    issues += ImportIssue(open.line, ImportIssueKind.Empty)
                } else {
                    entries += entry
                }
            }
            record = null
        }

        fun closeStray() {
            strayLine?.let { issues += ImportIssue(it, ImportIssueKind.MissingType) }
            strayLine = null
        }

        text.split('\n').forEachIndexed { index, line ->
            val lineNumber = index + 1
            val tag = tagLine(line)
            when {
                tag == null -> {
                    // A wrapped value continues; blank lines and text outside a record are ignored.
                    if (collapse(line) != null) lastValue?.append(' ')?.append(line)
                }

                tag.first == "TY" -> {
                    closeRecord()
                    closeStray()
                    record = Record(lineNumber).also { it.tags += "TY" to StringBuilder(tag.second) }
                    lastValue = null
                }

                tag.first == "ER" -> {
                    closeRecord()
                    closeStray()
                    lastValue = null
                }

                record != null -> {
                    val value = StringBuilder(tag.second)
                    record!!.tags += tag.first to value
                    lastValue = value
                }

                else -> {
                    if (strayLine == null) strayLine = lineNumber
                    lastValue = null
                }
            }
        }
        closeRecord()
        closeStray()
        return ImportParseResult(ImportFormat.Ris, entries, issues)
    }

    private fun build(record: Record): ImportedEntry {
        val type = record.first("TY").orEmpty()
        val doi = DOI_TAGS.asSequence().flatMap { record.values(it).asSequence() }.firstNotNullOfOrNull(::doiFrom)
        val authors = (record.tags.filter { it.first == "AU" || it.first == "A1" }.mapNotNull { collapse(it.second.toString()) })
            .ifEmpty { record.values("A2") }
            .mapNotNull(::displayName)
        val venue = when (type) {
            in RIS_BOOK_TYPES -> null
            "THES", "RPRT" -> VENUE_TAGS.firstNotNullOfOrNull(record::first) ?: record.first("PB")
            else -> VENUE_TAGS.firstNotNullOfOrNull(record::first)
        }
        val start = record.first("SP")
        val end = record.first("EP")
        val (firstPage, lastPage) = if (end == null) splitPages(start) else start to end
        val kind = risKind(type, doi)
        return ImportedEntry(
            line = record.line,
            entryType = type,
            title = record.first("TI") ?: record.first("T1").orEmpty(),
            authors = authors,
            year = firstYear(record.first("PY")) ?: firstYear(record.first("Y1")) ?: firstYear(record.first("DA")),
            venue = venue,
            doi = doi,
            url = record.first("UR"),
            abstract = record.first("AB") ?: record.first("N2"),
            volume = record.first("VL"),
            issue = record.first("IS"),
            firstPage = firstPage,
            lastPage = lastPage,
            publisher = record.first("PB"),
            workType = kind.workType,
            sourceType = kind.sourceType
        )
    }

    /** "Last, First" → "First Last"; "Last, First, Suffix" → "First Last Suffix"; no comma → as written. */
    private fun displayName(raw: String): String? {
        val parts = raw.split(',').mapNotNull { collapse(it) }
        return when (parts.size) {
            0 -> null
            1 -> parts[0]
            2 -> "${parts[1]} ${parts[0]}"
            else -> "${parts[1]} ${parts[0]} ${parts.drop(2).joinToString(" ")}"
        }
    }
}
