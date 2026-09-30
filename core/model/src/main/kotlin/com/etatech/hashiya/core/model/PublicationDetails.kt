package com.etatech.hashiya.core.model

/**
 * Bibliographic details used for citations, as OpenAlex reports them. Every field is null when the source has none.
 * The strings are kept as-is; core/bibtex interprets them, so a new OpenAlex type needs no migration.
 */
data class PublicationDetails(
    /** OpenAlex's work type, such as "article", "preprint", "book-chapter". */
    val workType: String? = null,
    /** OpenAlex's source type, such as "journal", "conference", "repository". */
    val sourceType: String? = null,
    val publisher: String? = null,
    val volume: String? = null,
    val issue: String? = null,
    val firstPage: String? = null,
    val lastPage: String? = null
)
