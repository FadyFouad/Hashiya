package com.etatech.hashiya.core.data.mapping

import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.database.model.searchEntityFor
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus

internal data class PaperEntities(val paper: PaperEntity, val authors: List<PaperAuthorEntity>, val search: PaperSearchEntity)

internal fun Paper.asEntities(localId: String, savedAt: Long, status: ReadingStatus, notes: PaperNotes? = null): PaperEntities =
    PaperEntities(
        paper = PaperEntity(
            id = localId,
            openAlexId = openAlexId,
            doi = doi,
            title = title,
            year = year,
            venue = venue,
            abstract = abstract,
            citationCount = citationCount,
            isOpenAccess = isOpenAccess,
            oaPdfUrl = openAccessPdfUrl,
            savedAt = savedAt,
            readingStatus = status.storedValue
        ),
        authors = authors.mapIndexed { index, author ->
            PaperAuthorEntity(paperId = localId, position = index, name = author.name, openAlexAuthorId = author.openAlexId)
        },
        search = searchEntityFor(localId, title, authors.map { it.name }, abstract, venue, notes)
    )

internal fun PaperWithAuthors.asPaper(): Paper = Paper(
    openAlexId = requireNotNull(paper.openAlexId) { "Papers without an OpenAlex ID are not supported yet" },
    doi = paper.doi,
    title = paper.title,
    authors = authors.sortedBy { it.position }.map { Author(it.name, it.openAlexAuthorId) },
    year = paper.year,
    venue = paper.venue,
    abstract = paper.abstract,
    citationCount = paper.citationCount,
    isOpenAccess = paper.isOpenAccess,
    openAccessPdfUrl = paper.oaPdfUrl
)
