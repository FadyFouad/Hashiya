package com.etatech.hashiya.core.bibtex

import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PublicationDetails
import org.junit.Assert.assertEquals
import org.junit.Test

/** The BibTeX fixtures. The iOS port copies this file's cases one to one. */
class BibTeXTest {
    private fun paper(
        title: String = "Deep learning",
        authors: List<String> = listOf("Yann LeCun", "Yoshua Bengio"),
        year: Int? = 2015,
        venue: String? = "Nature",
        doi: String? = "10.1038/nature14539",
        pdf: String? = null,
        details: PublicationDetails = PublicationDetails(workType = "article", sourceType = "journal")
    ) = Paper(
        openAlexId = "W1",
        doi = doi,
        title = title,
        authors = authors.map { Author(it, null) },
        year = year,
        venue = venue,
        abstract = null,
        citationCount = 0,
        isOpenAccess = pdf != null,
        openAccessPdfUrl = pdf,
        publication = details
    )

    @Test
    fun journalArticleWithEveryField() {
        val entry = BibTeX.entry(
            CitablePaper(
                paper(
                    details = PublicationDetails(
                        workType = "article",
                        sourceType = "journal",
                        publisher = "Springer Nature",
                        volume = "521",
                        issue = "7553",
                        firstPage = "436",
                        lastPage = "444"
                    )
                ),
                "lecun2015deep"
            )
        )
        assertEquals(
            """
            @article{lecun2015deep,
              author = {Yann LeCun and Yoshua Bengio},
              title = {Deep learning},
              year = {2015},
              journal = {Nature},
              volume = {521},
              number = {7553},
              pages = {436--444},
              doi = {10.1038/nature14539}
            }

            """.trimIndent(),
            entry
        )
    }

    @Test
    fun conferencePaperIsInproceedingsWithBooktitle() {
        val entry = BibTeX.entry(
            CitablePaper(
                paper(
                    title = "BERT: Pre-training of Deep Bidirectional Transformers",
                    authors = listOf("Jacob Devlin"),
                    year = 2019,
                    venue = "North American Chapter of the Association for Computational Linguistics",
                    doi = "10.18653/v1/n19-1423",
                    details = PublicationDetails(workType = "article", sourceType = "conference", firstPage = "4171", lastPage = "4186")
                ),
                "devlin2019bert"
            )
        )
        assertEquals(
            """
            @inproceedings{devlin2019bert,
              author = {Jacob Devlin},
              title = {{BERT:} Pre-training of Deep Bidirectional Transformers},
              year = {2019},
              booktitle = {North American Chapter of the Association for Computational Linguistics},
              pages = {4171--4186},
              doi = {10.18653/v1/n19-1423}
            }

            """.trimIndent(),
            entry
        )
    }

    @Test
    fun arxivPreprintIsMiscWithEprint() {
        val entry = BibTeX.entry(
            CitablePaper(
                paper(
                    title = "Attention Is All You Need",
                    authors = listOf("Ashish Vaswani"),
                    year = 2017,
                    venue = "arXiv (Cornell University)",
                    doi = "10.48550/arxiv.1706.03762",
                    pdf = "https://arxiv.org/pdf/1706.03762",
                    details = PublicationDetails(workType = "preprint", sourceType = "repository", publisher = "Cornell University")
                ),
                "vaswani2017attention"
            )
        )
        assertEquals(
            """
            @misc{vaswani2017attention,
              author = {Ashish Vaswani},
              title = {Attention Is All You Need},
              year = {2017},
              howpublished = {arXiv (Cornell University)},
              publisher = {Cornell University},
              doi = {10.48550/arxiv.1706.03762},
              eprint = {1706.03762},
              archivePrefix = {arXiv}
            }

            """.trimIndent(),
            entry
        )
    }

    @Test
    fun venueFieldPerEntryType() {
        fun firstVenueLine(work: String, source: String?) = BibTeX.entry(
            CitablePaper(paper(venue = "Venue", details = PublicationDetails(workType = work, sourceType = source)), "k")
        ).lines().firstOrNull { it.contains("{Venue}") }
        assertEquals("  booktitle = {Venue},", firstVenueLine("book-chapter", "book series"))
        assertEquals(null, firstVenueLine("book", null))
        assertEquals("  school = {Venue},", firstVenueLine("dissertation", null))
        assertEquals("  institution = {Venue},", firstVenueLine("report", null))
        assertEquals("  howpublished = {Venue},", firstVenueLine("dataset", null))
    }

    @Test
    fun entryTypeLines() {
        fun header(work: String, source: String?) =
            BibTeX.entry(CitablePaper(paper(details = PublicationDetails(workType = work, sourceType = source)), "k")).lines().first()
        assertEquals("@incollection{k,", header("book-chapter", null))
        assertEquals("@book{k,", header("book", null))
        assertEquals("@phdthesis{k,", header("dissertation", null))
        assertEquals("@techreport{k,", header("report", null))
    }

    @Test
    fun publisherOnlyOnTypesThatTakeOne() {
        fun hasPublisher(work: String, source: String?) = BibTeX.entry(
            CitablePaper(paper(details = PublicationDetails(workType = work, sourceType = source, publisher = "P")), "k")
        ).contains("publisher = {P}")
        assertEquals(false, hasPublisher("article", "journal"))
        assertEquals(false, hasPublisher("article", "conference"))
        assertEquals(true, hasPublisher("book", null))
        assertEquals(true, hasPublisher("book-chapter", null))
        assertEquals(true, hasPublisher("report", null))
        assertEquals(true, hasPublisher("preprint", null))
    }

    @Test
    fun missingFieldsAreLeftOutAndUrlOnlyWithoutDoi() {
        val entry = BibTeX.entry(
            CitablePaper(
                paper(
                    title = "Notes",
                    authors = emptyList(),
                    year = null,
                    venue = null,
                    doi = null,
                    pdf = "https://example.org/a_b.pdf",
                    details = PublicationDetails()
                ),
                "papernd"
            )
        )
        assertEquals(
            """
            @misc{papernd,
              title = {Notes},
              url = {https://example.org/a_b.pdf}
            }

            """.trimIndent(),
            entry
        )
        val withDoi = BibTeX.entry(CitablePaper(paper(pdf = "https://example.org/x.pdf"), "k"))
        assertEquals(false, withDoi.contains("url ="))
    }

    @Test
    fun singlePageAndEqualPages() {
        fun pages(first: String?, last: String?) = BibTeX.entry(
            CitablePaper(paper(details = PublicationDetails("article", "journal", firstPage = first, lastPage = last)), "k")
        ).lines().firstOrNull { it.trimStart().startsWith("pages") }
        assertEquals("  pages = {e12},", pages("e12", null))
        assertEquals("  pages = {7},", pages("7", "7"))
        assertEquals(null, pages(null, "9"))
    }

    @Test
    fun escapesValuesButNotDoiOrUrl() {
        val entry = BibTeX.entry(
            CitablePaper(
                paper(
                    title = "R&D at 50%: the {x}_y   case",
                    authors = listOf("A. O'Brien & Co"),
                    venue = "J. Stuff & Things",
                    doi = "10.1000/a_b%c"
                ),
                "k"
            )
        )
        assertEquals(true, entry.contains("  author = {A. O'Brien \\& Co},"))
        // "R&D" has a capital after its first character, so it is protected like an acronym.
        assertEquals(true, entry.contains("  title = {{R\\&D} at 50\\%: the \\textbraceleft{}x\\textbraceright{}\\_y case},"))
        assertEquals(true, entry.contains("  journal = {J. Stuff \\& Things},"))
        assertEquals(true, entry.contains("  doi = {10.1000/a_b%c}"))
    }

    @Test
    fun protectsCapitalsInTitleAndVenueButNotAuthors() {
        val entry = BibTeX.entry(
            CitablePaper(paper(title = "ImageNet on iPhone", authors = listOf("DeWitt McDonald"), venue = "IEEE TPAMI"), "k")
        )
        assertEquals(true, entry.contains("  title = {{ImageNet} on {iPhone}},"))
        assertEquals(true, entry.contains("  journal = {{IEEE} {TPAMI}},"))
        assertEquals(true, entry.contains("  author = {DeWitt McDonald},"))
        // A word starting with an escaped character gets double braces, so BibTeX doesn't treat it as one special character.
        val hashtag = BibTeX.entry(CitablePaper(paper(title = "The #MeToo movement"), "k"))
        assertEquals(true, hashtag.contains("  title = {The {{\\#MeToo}} movement},"))
    }

    @Test
    fun arabicTextStaysUtf8() {
        val entry = BibTeX.entry(CitablePaper(paper(title = "تطبيقات التعلم العميق", authors = listOf("محمد علي")), "paper2015"))
        assertEquals(true, entry.contains("  author = {محمد علي},"))
        assertEquals(true, entry.contains("  title = {تطبيقات التعلم العميق},"))
    }

    @Test
    fun fileSortsByKeyAndSeparatesWithOneBlankLine() {
        val b = CitablePaper(paper(title = "B"), "bkey")
        val a = CitablePaper(paper(title = "A"), "akey")
        val file = BibTeX.file(listOf(b, a))
        assertEquals(BibTeX.entry(a) + "\n" + BibTeX.entry(b), file)
        assertEquals(true, file.endsWith("}\n"))
        assertEquals("", BibTeX.file(emptyList()))
    }
}
