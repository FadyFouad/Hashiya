package com.etatech.hashiya.core.bibtex

import com.etatech.hashiya.core.model.PublicationDetails
import com.etatech.hashiya.core.model.WorkKind
import com.etatech.hashiya.core.model.workKind

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

/** The BibTeX type for a kind of work: preprints and anything else are @misc. */
internal fun entryType(details: PublicationDetails): EntryType = when (details.workKind()) {
    WorkKind.Conference -> EntryType.InProceedings
    WorkKind.Chapter -> EntryType.InCollection
    WorkKind.Book -> EntryType.Book
    WorkKind.Thesis -> EntryType.PhdThesis
    WorkKind.Report -> EntryType.TechReport
    WorkKind.Article -> EntryType.Article
    WorkKind.Preprint, WorkKind.Other -> EntryType.Misc
}
