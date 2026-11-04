package com.etatech.hashiya.core.importer

enum class ImportFormat { BibTeX, Ris }

/**
 * One reference read from an import file, with the fields Hashiya uses. Text is decoded (no LaTeX), whitespace is collapsed and
 * empty values are null. [workType] and [sourceType] use OpenAlex's vocabulary, so core/bibtex exports the same entry type back.
 */
data class ImportedEntry(
    /** The 1-based line where the entry starts: its `@` in BibTeX, its `TY` in RIS. */
    val line: Int,
    /** The BibTeX type lowercased ("article"), or the RIS `TY` code ("JOUR"). */
    val entryType: String,
    /** The BibTeX key as written; always null for RIS. */
    val citeKey: String? = null,
    /** "" when the entry has none. */
    val title: String = "",
    /** Display names ("Given Family"), in the file's order. */
    val authors: List<String> = emptyList(),
    val year: Int? = null,
    val venue: String? = null,
    /** In normalizeDoi form: lowercase, no prefix. */
    val doi: String? = null,
    val url: String? = null,
    val abstract: String? = null,
    val volume: String? = null,
    val issue: String? = null,
    val firstPage: String? = null,
    val lastPage: String? = null,
    val publisher: String? = null,
    val workType: String? = null,
    val sourceType: String? = null
)

enum class ImportIssueKind {
    /** A BibTeX entry with a syntax error, or one that never closes. */
    Malformed,

    /** RIS tag lines outside a record, with no `TY` before them. */
    MissingType,

    /** An entry with neither a title nor a DOI: nothing to import. */
    Empty
}

/** An entry that was skipped, and where it starts. */
data class ImportIssue(val line: Int, val kind: ImportIssueKind)

data class ImportParseResult(val format: ImportFormat, val entries: List<ImportedEntry>, val issues: List<ImportIssue>)
