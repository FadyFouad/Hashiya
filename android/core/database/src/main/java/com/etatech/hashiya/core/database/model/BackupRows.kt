package com.etatech.hashiya.core.database.model

/** Everything an export writes, read in one transaction so it is consistent. */
data class LibrarySnapshot(
    val papers: List<PaperWithAuthors>,
    val notes: List<PaperNotesEntity>,
    val collections: List<CollectionEntity>,
    val links: List<CollectionPaperEntity>
)

data class PdfTotals(val count: Int, val bytes: Long)

/**
 * A backup paper ready to merge. [paper] has a fresh local id, and its `pdf_*` columns are set only when a staged PDF goes with
 * it; [authors] and [notes] belong to that id.
 */
data class IncomingPaper(val ref: Int, val paper: PaperEntity, val authors: List<PaperAuthorEntity>, val notes: PaperNotesEntity?)

/** [refs] are backup refs; ones that name no paper in the merge are skipped. */
data class IncomingCollection(val name: String, val nameKey: String, val createdAt: Long, val refs: List<Int>)

/** [pdfTargets]: backup ref → the local id whose PDF columns now point at that ref's staged file. */
data class MergeOutcome(
    val added: Int,
    val matched: Int,
    val notesAdded: Int,
    val collectionsCreated: Int,
    val pdfTargets: Map<Int, String>
)
