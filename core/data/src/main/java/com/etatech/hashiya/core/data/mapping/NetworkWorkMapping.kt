package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PublicationDetails
import com.etatech.hashiya.core.model.normalizeDoi
import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.rebuildAbstract

private const val OPENALEX_ID_PREFIX = "https://openalex.org/"

internal fun NetworkWork.asPaper(): Paper = Paper(
    openAlexId = id.removePrefix(OPENALEX_ID_PREFIX),
    doi = doi?.let(::normalizeDoi),
    title = displayName.orEmpty().trim(),
    authors = authorships.mapNotNull { authorship ->
        authorship.author.displayName?.takeIf { it.isNotBlank() }?.let { name ->
            Author(name = name, openAlexId = authorship.author.id?.removePrefix(OPENALEX_ID_PREFIX))
        }
    },
    year = publicationYear,
    venue = primaryLocation?.source?.displayName,
    abstract = rebuildAbstract(abstractInvertedIndex),
    citationCount = citedByCount,
    isOpenAccess = openAccess?.isOa ?: false,
    openAccessPdfUrl = bestOaLocation?.pdfUrl,
    publication = asPublicationDetails()
)

/** The citation details OpenAlex reports for this work; blank strings become null. */
internal fun NetworkWork.asPublicationDetails(): PublicationDetails {
    val source = primaryLocation?.source
    return PublicationDetails(
        workType = type.orNullIfBlank(),
        sourceType = source?.type.orNullIfBlank(),
        publisher = source?.hostOrganizationName.orNullIfBlank(),
        volume = biblio?.volume.orNullIfBlank(),
        issue = biblio?.issue.orNullIfBlank(),
        firstPage = biblio?.firstPage.orNullIfBlank(),
        lastPage = biblio?.lastPage.orNullIfBlank()
    )
}

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
