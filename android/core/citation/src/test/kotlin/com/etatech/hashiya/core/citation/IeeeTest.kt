package com.etatech.hashiya.core.citation

import org.junit.Assert.assertEquals
import org.junit.Test

class IeeeTest {
    private fun ieee(p: com.etatech.hashiya.core.model.Paper) = Ieee.format(p)

    @Test
    fun journalArticle() = assertEquals(
        listOf(
            Run("A. Vaswani and N. Shazeer, \"Attention is all you need,\" "),
            Run("Nature", italic = true),
            Run(", vol. 521, no. 7553, pp. 436–444, 2017, doi: 10.1038/nature14539.")
        ),
        ieee(
            paper(
                venue = "Nature",
                doi = "https://doi.org/10.1038/nature14539",
                volume = "521",
                issue = "7553",
                first = "436",
                last = "444"
            )
        ).runs
    )

    @Test
    fun conferencePaper() = assertEquals(
        "A. Vaswani and N. Shazeer, \"Attention is all you need,\" in Advances in Neural Information Processing Systems, 2017, pp. 5998–6008, doi: 10.5555/3295222.3295349.",
        ieee(paper(source = "conference", first = "5998", last = "6008")).plain
    )

    @Test
    fun bookChapter() = assertEquals(
        "A. Vaswani and N. Shazeer, \"Attention is all you need,\" in Deep Learning. MIT Press, 2017, p. 12, doi: 10.5555/3295222.3295349.",
        ieee(paper(work = "book-chapter", venue = "Deep Learning", publisher = "MIT Press", first = "12")).plain
    )

    @Test
    fun book() = assertEquals(
        listOf(
            Run("A. Vaswani and N. Shazeer, "),
            Run("Attention is all you need", italic = true),
            Run(". MIT Press, 2017, doi: 10.5555/3295222.3295349.")
        ),
        ieee(paper(work = "book", source = null, publisher = "MIT Press")).runs
    )

    @Test
    fun thesisReportPreprintOther() {
        assertEquals(
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" Thesis, University of Toronto, 2017.",
            ieee(paper(work = "dissertation", source = null, venue = "University of Toronto", doi = null)).plain
        )
        assertEquals(
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" Google Research, Tech. Rep., 2017.",
            ieee(paper(work = "report", source = null, venue = "Google Research", doi = null)).plain
        )
        assertEquals(
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" arXiv, 2017, doi: 10.48550/arXiv.1706.03762.",
            ieee(paper(work = "preprint", source = "repository", venue = "arXiv", doi = "10.48550/arXiv.1706.03762")).plain
        )
        assertEquals(
            "A. Vaswani and N. Shazeer, \"Attention is all you need,\" Zenodo, 2017.",
            ieee(paper(work = "dataset", source = null, venue = "Zenodo", doi = null)).plain
        )
    }

    @Test
    fun noDoiUsesTheOpenAccessLink() = assertEquals(
        "A. Vaswani and N. Shazeer, \"Attention is all you need,\" Zenodo, 2017. [Online]. Available: https://example.org/a.pdf",
        ieee(paper(work = "dataset", source = null, venue = "Zenodo", doi = null, pdf = "https://example.org/a.pdf")).plain
    )

    @Test
    fun noAuthorsNoYearNoVenue() {
        assertEquals(
            "\"Attention is all you need.\"",
            ieee(paper(authors = emptyList(), year = null, work = null, source = null, venue = null, doi = null)).plain
        )
        assertEquals(
            "\"Untitled,\" Zenodo.",
            ieee(paper(title = "", authors = emptyList(), year = null, work = null, source = null, venue = "Zenodo", doi = null)).plain
        )
    }

    @Test
    fun authorLists() {
        fun names(n: Int) = (1..n).map { "Given$it Family$it" }
        fun text(n: Int) = Rendering.plain(StyledCitation(Ieee.authors(names(n))))
        assertEquals("G. Family1", text(1))
        assertEquals("G. Family1 and G. Family2", text(2))
        assertEquals("G. Family1, G. Family2, and G. Family3", text(3))
        assertEquals(6, text(6).split("Family").size - 1)
        assertEquals(listOf(Run("G. Family1 "), Run("et al.", italic = true)), Ieee.authors(names(7)))
        assertEquals(
            "J.-P. Sartre and محمد عبد الله",
            Rendering.plain(StyledCitation(Ieee.authors(listOf("Jean-Paul Sartre", "محمد عبد الله"))))
        )
    }

    @Test
    fun listNumbersInTheOrderGiven() = assertEquals(
        listOf("[1] \"B,\" Zenodo.", "[2] \"A,\" Zenodo."),
        Ieee.list(
            listOf("B", "A").map {
                paper(title = it, authors = emptyList(), year = null, work = null, source = null, venue = "Zenodo", doi = null)
            }
        ).map { it.plain }
    )

    @Test
    fun finalFullStopDoesNotDouble() = assertEquals(
        "A. Vaswani and N. Shazeer, \"Attention is all you need,\" Springer-Verlag Inc.",
        ieee(paper(work = "dataset", source = null, venue = "Springer-Verlag Inc.", year = null, doi = null)).plain
    )

    @Test
    fun bookTitleEndingInAQuestionMarkTakesNoFullStop() {
        val c = ieee(paper(title = "Is attention all you need?", work = "book", source = null, publisher = "MIT Press", doi = null))
        assertEquals("A. Vaswani and N. Shazeer, Is attention all you need? MIT Press, 2017.", c.plain)
        assertEquals(Run("Is attention all you need?", italic = true), c.runs[1])
    }

    @Test
    fun organisationAuthorIsKeptWhole() = assertEquals(
        true,
        ieee(paper(authors = listOf("OpenAI"))).plain.startsWith("OpenAI, \"Attention")
    )

    @Test
    fun quotedTitleEndingInAMarkTakesNoExtraPunctuation() {
        val q = "Is attention all you need?"
        assertEquals(
            "A. Vaswani and N. Shazeer, \"$q\" Zenodo, 2017.",
            ieee(paper(title = q, work = "dataset", source = null, venue = "Zenodo", doi = null)).plain
        )
        assertEquals(
            "A. Vaswani and N. Shazeer, \"$q\"",
            ieee(paper(title = q, work = "dataset", source = null, venue = null, year = null, doi = null)).plain
        )
        assertEquals(
            "A. Vaswani and N. Shazeer, \"Deep learning.\" Zenodo, 2017.",
            ieee(paper(title = "Deep learning.", work = "dataset", source = null, venue = "Zenodo", doi = null)).plain
        )
    }

    @Test
    fun chapterVenueEndingInAFullStopIsFollowedByThePublisherDirectly() {
        val c = ieee(paper(work = "book-chapter", venue = "Proc. Int. Conf.", publisher = "Springer", first = "12", doi = null))
        assertEquals("A. Vaswani and N. Shazeer, \"Attention is all you need,\" in Proc. Int. Conf. Springer, 2017, p. 12.", c.plain)
        assertEquals(Run("Proc. Int. Conf.", italic = true), c.runs[1])
    }

    @Test
    fun chapterWithoutAVenueKeepsThePublisher() = assertEquals(
        "A. Vaswani and N. Shazeer, \"Attention is all you need,\" MIT Press, 2017, p. 12.",
        ieee(paper(work = "book-chapter", venue = null, publisher = "MIT Press", first = "12", doi = null)).plain
    )
}
