package com.etatech.hashiya.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkKindTest {
    private fun kind(work: String?, source: String?) = PublicationDetails(workType = work, sourceType = source).workKind()

    @Test
    fun followsTheBibTeXTableInOrder() {
        assertEquals(WorkKind.Conference, kind("article", "conference"))
        assertEquals(WorkKind.Chapter, kind("book-chapter", "book"))
        assertEquals(WorkKind.Book, kind("book", null))
        assertEquals(WorkKind.Thesis, kind("dissertation", null))
        assertEquals(WorkKind.Report, kind("report", null))
        assertEquals(WorkKind.Preprint, kind("preprint", null))
        assertEquals(WorkKind.Preprint, kind("article", "repository"))
        assertEquals(WorkKind.Article, kind("Article", "Journal"))
        assertEquals(WorkKind.Article, kind("review", "journal"))
    }

    @Test
    fun anythingElseIsOther() {
        assertEquals(WorkKind.Other, kind(null, null))
        assertEquals(WorkKind.Other, kind("article", null))
        assertEquals(WorkKind.Other, kind("dataset", "journal"))
    }

    @Test
    fun stylesRoundTripTheirIdsAndDefaultToApa() {
        CitationStyle.entries.forEach { assertEquals(it, CitationStyle.fromId(it.id)) }
        assertEquals(CitationStyle.Apa, CitationStyle.fromId(null))
        assertEquals(CitationStyle.Apa, CitationStyle.fromId("harvard"))
    }
}
