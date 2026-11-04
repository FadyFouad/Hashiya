package com.etatech.hashiya.core.importer

import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.LOCAL_PAPER_ID_PREFIX
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PublicationDetails

/**
 * The paper to save for an entry OpenAlex didn't match, under the id `local-[uuid]`. Paper has no general URL field, so only a
 * link to a PDF is kept, as the PDF link.
 */
fun ImportedEntry.toLocalPaper(uuid: String): Paper = Paper(
    openAlexId = LOCAL_PAPER_ID_PREFIX + uuid,
    doi = doi,
    title = title,
    authors = authors.map { Author(name = it, openAlexId = null) },
    year = year,
    venue = venue,
    abstract = abstract,
    citationCount = 0,
    isOpenAccess = false,
    openAccessPdfUrl = url?.takeIf(::isPdfLink),
    publication = PublicationDetails(
        workType = workType,
        sourceType = sourceType,
        publisher = publisher,
        volume = volume,
        issue = issue,
        firstPage = firstPage,
        lastPage = lastPage
    )
)

private fun isPdfLink(url: String): Boolean = url.substringBefore('#').substringBefore('?').endsWith(".pdf", ignoreCase = true)
