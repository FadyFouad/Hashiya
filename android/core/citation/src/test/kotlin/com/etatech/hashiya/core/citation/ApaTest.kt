package com.etatech.hashiya.core.citation

import org.junit.Assert.assertEquals
import org.junit.Test

class ApaTest {
    private fun apa(p: com.etatech.hashiya.core.model.Paper) = Apa.format(p)

    @Test
    fun journalArticle() = assertEquals(
        listOf(
            Run("Vaswani, A., & Shazeer, N. (2017). Attention is all you need. "),
            Run("Nature", italic = true),
            Run(", "),
            Run("521", italic = true),
            Run("(7553), 436–444. https://doi.org/10.1038/nature14539")
        ),
        apa(paper(venue = "Nature", doi = "10.1038/nature14539", volume = "521", issue = "7553", first = "436", last = "444")).runs
    )

    @Test
    fun conferencePaper() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. In Advances in Neural Information Processing Systems " +
            "(pp. 5998–6008). Curran Associates. https://doi.org/10.5555/3295222.3295349",
        apa(paper(source = "conference", publisher = "Curran Associates", first = "5998", last = "6008")).plain
    )

    @Test
    fun bookChapterSinglePage() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. In Deep Learning (p. 12). MIT Press. https://doi.org/10.5555/3295222.3295349",
        apa(paper(work = "book-chapter", venue = "Deep Learning", publisher = "MIT Press", first = "12", last = "12")).plain
    )

    @Test
    fun bookItalicisesTheTitle() = assertEquals(
        listOf(
            Run("Vaswani, A., & Shazeer, N. (2017). "),
            Run("Attention is all you need", italic = true),
            Run(". MIT Press. https://doi.org/10.5555/3295222.3295349")
        ),
        apa(paper(work = "book", source = null, publisher = "MIT Press")).runs
    )

    @Test
    fun thesis() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need [Thesis, University of Toronto]. https://doi.org/10.5555/3295222.3295349",
        apa(paper(work = "dissertation", source = null, venue = "University of Toronto")).plain
    )

    @Test
    fun reportUsesThePublisherElseTheVenue() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. Google Research. https://doi.org/10.5555/3295222.3295349",
        apa(paper(work = "report", source = null, venue = "Google Research")).plain
    )

    @Test
    fun preprint() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need [Preprint]. arXiv. https://doi.org/10.48550/arXiv.1706.03762",
        apa(paper(work = "preprint", source = "repository", venue = "arXiv", doi = "https://doi.org/10.48550/arXiv.1706.03762")).plain
    )

    @Test
    fun other() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. Zenodo.",
        apa(paper(work = "dataset", source = null, venue = "Zenodo", doi = null)).plain
    )

    @Test
    fun noDoiUsesTheOpenAccessLink() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. Zenodo. https://example.org/a.pdf",
        apa(paper(work = "dataset", source = null, venue = "Zenodo", doi = null, pdf = "https://example.org/a.pdf")).plain
    )

    @Test
    fun noAuthorsNoYearNoTitle() {
        assertEquals(
            "[Untitled]. (n.d.). Zenodo.",
            apa(paper(title = " ", authors = emptyList(), year = null, work = null, source = null, venue = "Zenodo", doi = null)).plain
        )
        assertEquals(
            "Attention is all you need. (n.d.). Zenodo.",
            apa(paper(authors = emptyList(), year = null, work = null, source = null, venue = "Zenodo", doi = null)).plain
        )
    }

    @Test
    fun aTitleEndingInAQuestionMarkGetsNoFullStop() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Is attention all you need? Zenodo.",
        apa(paper(title = "Is attention all you need?", work = null, source = null, venue = "Zenodo", doi = null)).plain
    )

    @Test
    fun authorLists() {
        fun names(n: Int) = (1..n).map { "Given$it Family$it" }
        assertEquals("Family1, G.", Apa.authors(names(1)))
        assertEquals("Family1, G., & Family2, G.", Apa.authors(names(2)))
        assertEquals("Family1, G., Family2, G., & Family3, G.", Apa.authors(names(3)))
        assertEquals(20, Apa.authors(names(20)).split("Family").size - 1)
        val many = Apa.authors(names(21))
        assertEquals(true, many.contains("Family19, G., . . . Family21, G."))
        assertEquals(false, many.contains("Family20"))
        assertEquals("OpenAI, & Family2, G.", Apa.authors(listOf("OpenAI", "Given2 Family2")))
    }

    @Test
    fun listSortsByFamilyIgnoringCaseAndDiacriticsThenYearThenTitle() {
        val list = Apa.list(
            listOf(
                paper(title = "B", authors = listOf("Zoe Zed"), year = 2020),
                paper(title = "C", authors = listOf("Ann Émile"), year = null),
                paper(title = "A", authors = listOf("Ann emile"), year = 2019),
                paper(title = "D", authors = emptyList(), year = 2018)
            )
        ).map { it.plain.substringBefore(" (") }
        assertEquals(listOf("D.", "emile, A.", "Émile, A.", "Zed, Z."), list)
    }

    @Test
    fun aWholeNameGetsAFullStopBeforeTheYear() {
        assertEquals(true, apa(paper(authors = listOf("OpenAI"))).plain.startsWith("OpenAI. (2017). "))
        assertEquals(
            true,
            apa(paper(authors = listOf("Ashish Vaswani", "محمد علي"))).plain.startsWith("Vaswani, A., & محمد علي. (2017). ")
        )
    }

    @Test
    fun anIssueWithoutAVolumeIsSeparatedFromTheJournal() {
        val c = apa(paper(venue = "Nature", doi = null, volume = null, issue = "7553", first = "436", last = "444"))
        assertEquals(true, c.plain.contains("Nature, (7553), 436–444."))
        assertEquals(listOf(Run("Nature", italic = true), Run(", (7553), 436–444.")), c.runs.drop(1).take(2))
    }

    @Test
    fun aPublisherEndingInAFullStopGetsNoSecondOne() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. Springer-Verlag Inc. https://doi.org/10.5555/3295222.3295349",
        apa(paper(work = "book", source = null, publisher = "Springer-Verlag Inc.")).plain
    )

    @Test
    fun aVenueEndingInAFullStopGetsNoSecondOne() = assertEquals(
        "Vaswani, A., & Shazeer, N. (2017). Attention is all you need. In Proc. IEEE Conf. Curran. https://doi.org/10.5555/3295222.3295349",
        apa(paper(source = "conference", venue = "Proc. IEEE Conf.", publisher = "Curran")).plain
    )
}
