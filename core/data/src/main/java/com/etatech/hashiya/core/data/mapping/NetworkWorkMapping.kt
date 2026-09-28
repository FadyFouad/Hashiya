package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
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
    openAccessPdfUrl = bestOaLocation?.pdfUrl
)
