package com.etatech.hashiya.share

import com.etatech.hashiya.feature.search.SearchNote
import com.etatech.hashiya.feature.search.navigation.SearchRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareToSearchRouteTest {
    @Test
    fun arxivLinkOpensTheArxivLookup() {
        assertEquals(
            SearchRoute(query = "arXiv:1706.03762", pageTitle = "Attention Is All You Need"),
            shareToSearchRoute("https://arxiv.org/abs/1706.03762", "Attention Is All You Need")
        )
    }

    @Test
    fun doiLinkOpensTheDoiLookup() {
        assertEquals(
            SearchRoute(query = "10.1038/nature14539", pageTitle = "Deep learning | Nature"),
            shareToSearchRoute("https://doi.org/10.1038/nature14539", "Deep learning | Nature")
        )
    }

    @Test
    fun publisherPageWithDoiOpensTheDoiLookup() {
        assertEquals(
            SearchRoute(query = "10.1145/3292500.3330701"),
            shareToSearchRoute("https://dl.acm.org/doi/10.1145/3292500.3330701", null)
        )
    }

    @Test
    fun idInsideSharedTextIsFound() {
        assertEquals(
            SearchRoute(query = "arXiv:2401.00001"),
            shareToSearchRoute("Check this out: https://arxiv.org/abs/2401.00001v2", "  ")
        )
    }

    @Test
    fun pageWithoutIdSearchesItsTitle() {
        assertEquals(
            SearchRoute(query = "Deep learning", note = SearchNote.NoIdInShare.name),
            shareToSearchRoute("https://www.nature.com/articles/nature14539", " Deep learning ")
        )
    }

    @Test
    fun missingTextButTitleSearchesTheTitle() {
        assertEquals(
            SearchRoute(query = "Deep learning", note = SearchNote.NoIdInShare.name),
            shareToSearchRoute(null, "Deep learning")
        )
    }

    @Test
    fun nothingUsableShowsTheNote() {
        assertEquals(SearchRoute(note = SearchNote.NothingInShare.name), shareToSearchRoute("just some words", null))
        assertEquals(SearchRoute(note = SearchNote.NothingInShare.name), shareToSearchRoute(null, "   "))
    }

    @Test
    fun veryLongSharedTitleIsShortened() {
        val longTitle = "A".repeat(400)
        val shortenedTitle = "A".repeat(300)

        assertEquals(
            SearchRoute(query = shortenedTitle, note = SearchNote.NoIdInShare.name),
            shareToSearchRoute(null, longTitle)
        )
        assertEquals(
            SearchRoute(query = "arXiv:1706.03762", pageTitle = shortenedTitle),
            shareToSearchRoute("https://arxiv.org/abs/1706.03762", longTitle)
        )
    }
}
