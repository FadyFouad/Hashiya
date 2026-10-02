package com.etatech.hashiya.core.model

/** A research paper. [title] is empty when the source has none; the UI shows a localized "Untitled". */
data class Paper(
    val openAlexId: String,
    val doi: String?,
    val title: String,
    val authors: List<Author>,
    val year: Int?,
    val venue: String?,
    val abstract: String?,
    val citationCount: Int,
    val isOpenAccess: Boolean,
    val openAccessPdfUrl: String?,
    val publication: PublicationDetails = PublicationDetails()
)

data class Author(val name: String, val openAlexId: String?)
