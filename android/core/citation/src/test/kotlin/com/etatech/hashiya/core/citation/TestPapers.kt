package com.etatech.hashiya.core.citation

import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PublicationDetails

internal fun paper(
    title: String = "Attention is all you need",
    authors: List<String> = listOf("Ashish Vaswani", "Noam Shazeer"),
    year: Int? = 2017,
    venue: String? = "Advances in Neural Information Processing Systems",
    doi: String? = "10.5555/3295222.3295349",
    pdf: String? = null,
    work: String? = "article",
    source: String? = "journal",
    publisher: String? = null,
    volume: String? = null,
    issue: String? = null,
    first: String? = null,
    last: String? = null
) = Paper(
    openAlexId = "W1", doi = doi, title = title, authors = authors.map { Author(it, null) }, year = year, venue = venue,
    abstract = null, citationCount = 0, isOpenAccess = pdf != null, openAccessPdfUrl = pdf,
    publication = PublicationDetails(work, source, publisher, volume, issue, first, last)
)
