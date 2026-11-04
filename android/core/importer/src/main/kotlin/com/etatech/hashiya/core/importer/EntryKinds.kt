package com.etatech.hashiya.core.importer

/** OpenAlex's (workType, sourceType) for an entry, so core/bibtex exports the same entry type back (spec §4.2 and §4.3). */
internal data class EntryKind(val workType: String?, val sourceType: String?)

private val NONE = EntryKind(null, null)
private val JOURNAL_ARTICLE = EntryKind("article", "journal")
private val CONFERENCE_PAPER = EntryKind("article", "conference")
private val BOOK = EntryKind("book", null)
private val CHAPTER = EntryKind("book-chapter", null)
private val THESIS = EntryKind("dissertation", null)
private val REPORT = EntryKind("report", null)
private val PREPRINT = EntryKind("preprint", "repository")

internal fun bibTeXKind(type: String, doi: String?, journal: String?): EntryKind {
    val kind = when (type) {
        "article" -> JOURNAL_ARTICLE
        "inproceedings", "conference" -> CONFERENCE_PAPER
        "book", "mvbook", "proceedings" -> BOOK
        "incollection", "inbook", "bookinbook" -> CHAPTER
        "phdthesis", "mastersthesis", "thesis" -> THESIS
        "techreport", "report" -> REPORT
        else -> NONE
    }
    val preprint = isArxivDoi(doi) && (kind.workType == null || isScholarArxivJournal(journal))
    return if (preprint) PREPRINT else kind
}

internal val RIS_BOOK_TYPES = setOf("BOOK", "EBOOK", "EDBOOK")

internal fun risKind(type: String, doi: String?): EntryKind {
    val kind = when (type) {
        "JOUR", "JFULL", "EJOUR" -> JOURNAL_ARTICLE
        "MGZN", "NEWS" -> EntryKind("article", null)
        "CONF", "CPAPER" -> CONFERENCE_PAPER
        in RIS_BOOK_TYPES -> BOOK
        "CHAP", "ECHAP" -> CHAPTER
        "THES" -> THESIS
        "RPRT" -> REPORT
        else -> NONE
    }
    return if (kind.workType == null && isArxivDoi(doi)) PREPRINT else kind
}
