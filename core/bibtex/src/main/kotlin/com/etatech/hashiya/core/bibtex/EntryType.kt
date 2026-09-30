package com.etatech.hashiya.core.bibtex

import com.etatech.hashiya.core.model.PublicationDetails
import java.util.Locale

/** A BibTeX entry type, the field that holds the paper's venue, and whether a publisher field belongs in it. */
internal enum class EntryType(val bibName: String, val venueField: String?, val hasPublisher: Boolean) {
    Article("article", "journal", hasPublisher = false),
    InProceedings("inproceedings", "booktitle", hasPublisher = false),
    InCollection("incollection", "booktitle", hasPublisher = true),
    Book("book", null, hasPublisher = true),
    PhdThesis("phdthesis", "school", hasPublisher = false),
    TechReport("techreport", "institution", hasPublisher = true),
    Misc("misc", "howpublished", hasPublisher = true)
}

private val JOURNAL_WORK_TYPES = setOf("article", "review", "letter", "editorial")

/** The spec's table, first match wins: a conference article is @inproceedings, a repository article @misc. */
internal fun entryType(details: PublicationDetails): EntryType {
    val work = details.workType?.lowercase(Locale.ROOT)
    val source = details.sourceType?.lowercase(Locale.ROOT)
    return when {
        source == "conference" -> EntryType.InProceedings
        work == "book-chapter" -> EntryType.InCollection
        work == "book" -> EntryType.Book
        work == "dissertation" -> EntryType.PhdThesis
        work == "report" -> EntryType.TechReport
        work == "preprint" || source == "repository" -> EntryType.Misc
        work in JOURNAL_WORK_TYPES && source == "journal" -> EntryType.Article
        else -> EntryType.Misc
    }
}
