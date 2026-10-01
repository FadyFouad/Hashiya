@testable import HashiyaBibTeX
import HashiyaModel
import Testing

/// Mirrors Android's core/bibtex BibTeXTest, case for case, and the on-device BibTeXOnDeviceTest. The output must match
/// Android's byte for byte.
struct BibTeXTests {
    private func paper(
        title: String = "Deep learning",
        authors: [String] = ["Yann LeCun", "Yoshua Bengio"],
        year: Int? = 2015,
        venue: String? = "Nature",
        doi: String? = "10.1038/nature14539",
        pdf: String? = nil,
        details: PublicationDetails = PublicationDetails(workType: "article", sourceType: "journal")
    ) -> Paper {
        Paper(
            openAlexID: "W1",
            doi: doi,
            title: title,
            authors: authors.map { Author(name: $0) },
            year: year,
            venue: venue,
            abstract: nil,
            citationCount: 0,
            isOpenAccess: pdf != nil,
            openAccessPDFURL: pdf,
            publication: details
        )
    }

    private func lines(_ text: String) -> [String] {
        text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
    }

    @Test func journalArticleWithEveryField() {
        let entry = BibTeX.entry(
            CitablePaper(
                paper: paper(
                    details: PublicationDetails(
                        workType: "article",
                        sourceType: "journal",
                        publisher: "Springer Nature",
                        volume: "521",
                        issue: "7553",
                        firstPage: "436",
                        lastPage: "444"
                    )
                ),
                citeKey: "lecun2015deep"
            )
        )
        #expect(entry == """
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

            """)
    }

    @Test func conferencePaperIsInproceedingsWithBooktitle() {
        let entry = BibTeX.entry(
            CitablePaper(
                paper: paper(
                    title: "BERT: Pre-training of Deep Bidirectional Transformers",
                    authors: ["Jacob Devlin"],
                    year: 2019,
                    venue: "North American Chapter of the Association for Computational Linguistics",
                    doi: "10.18653/v1/n19-1423",
                    details: PublicationDetails(workType: "article", sourceType: "conference", firstPage: "4171", lastPage: "4186")
                ),
                citeKey: "devlin2019bert"
            )
        )
        #expect(entry == """
            @inproceedings{devlin2019bert,
              author = {Jacob Devlin},
              title = {{BERT:} Pre-training of Deep Bidirectional Transformers},
              year = {2019},
              booktitle = {North American Chapter of the Association for Computational Linguistics},
              pages = {4171--4186},
              doi = {10.18653/v1/n19-1423}
            }

            """)
    }

    @Test func arxivPreprintIsMiscWithEprint() {
        let entry = BibTeX.entry(
            CitablePaper(
                paper: paper(
                    title: "Attention Is All You Need",
                    authors: ["Ashish Vaswani"],
                    year: 2017,
                    venue: "arXiv (Cornell University)",
                    doi: "10.48550/arxiv.1706.03762",
                    pdf: "https://arxiv.org/pdf/1706.03762",
                    details: PublicationDetails(workType: "preprint", sourceType: "repository", publisher: "Cornell University")
                ),
                citeKey: "vaswani2017attention"
            )
        )
        #expect(entry == """
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

            """)
    }

    @Test func venueFieldPerEntryType() {
        func firstVenueLine(_ work: String, _ source: String?) -> String? {
            lines(BibTeX.entry(CitablePaper(paper: paper(venue: "Venue", details: PublicationDetails(workType: work, sourceType: source)), citeKey: "k")))
                .first { $0.contains("{Venue}") }
        }
        #expect(firstVenueLine("book-chapter", "book series") == "  booktitle = {Venue},")
        #expect(firstVenueLine("book", nil) == nil)
        #expect(firstVenueLine("dissertation", nil) == "  school = {Venue},")
        #expect(firstVenueLine("report", nil) == "  institution = {Venue},")
        #expect(firstVenueLine("dataset", nil) == "  howpublished = {Venue},")
    }

    @Test func entryTypeLines() {
        func header(_ work: String, _ source: String?) -> String? {
            lines(BibTeX.entry(CitablePaper(paper: paper(details: PublicationDetails(workType: work, sourceType: source)), citeKey: "k"))).first
        }
        #expect(header("book-chapter", nil) == "@incollection{k,")
        #expect(header("book", nil) == "@book{k,")
        #expect(header("dissertation", nil) == "@phdthesis{k,")
        #expect(header("report", nil) == "@techreport{k,")
    }

    @Test func publisherOnlyOnTypesThatTakeOne() {
        func hasPublisher(_ work: String, _ source: String?) -> Bool {
            BibTeX.entry(
                CitablePaper(paper: paper(details: PublicationDetails(workType: work, sourceType: source, publisher: "P")), citeKey: "k")
            ).contains("publisher = {P}")
        }
        #expect(hasPublisher("article", "journal") == false)
        #expect(hasPublisher("article", "conference") == false)
        #expect(hasPublisher("book", nil) == true)
        #expect(hasPublisher("book-chapter", nil) == true)
        #expect(hasPublisher("report", nil) == true)
        #expect(hasPublisher("preprint", nil) == true)
    }

    @Test func missingFieldsAreLeftOutAndUrlOnlyWithoutDoi() {
        let entry = BibTeX.entry(
            CitablePaper(
                paper: paper(
                    title: "Notes",
                    authors: [],
                    year: nil,
                    venue: nil,
                    doi: nil,
                    pdf: "https://example.org/a_b.pdf",
                    details: PublicationDetails()
                ),
                citeKey: "papernd"
            )
        )
        #expect(entry == """
            @misc{papernd,
              title = {Notes},
              url = {https://example.org/a_b.pdf}
            }

            """)
        let withDoi = BibTeX.entry(CitablePaper(paper: paper(pdf: "https://example.org/x.pdf"), citeKey: "k"))
        #expect(withDoi.contains("url =") == false)
    }

    @Test func singlePageAndEqualPages() {
        func pages(_ first: String?, _ last: String?) -> String? {
            lines(
                BibTeX.entry(
                    CitablePaper(
                        paper: paper(details: PublicationDetails(workType: "article", sourceType: "journal", firstPage: first, lastPage: last)),
                        citeKey: "k"
                    )
                )
            ).first { line in line.drop { $0 == " " }.hasPrefix("pages") }
        }
        #expect(pages("e12", nil) == "  pages = {e12},")
        #expect(pages("7", "7") == "  pages = {7},")
        #expect(pages(nil, "9") == nil)
    }

    @Test func escapesValuesButNotDoiOrUrl() {
        let entry = BibTeX.entry(
            CitablePaper(
                paper: paper(
                    title: "R&D at 50%: the {x}_y   case",
                    authors: ["A. O'Brien & Co"],
                    venue: "J. Stuff & Things",
                    doi: "10.1000/a_b%c"
                ),
                citeKey: "k"
            )
        )
        #expect(entry.contains(#"  author = {A. O'Brien \& Co},"#))
        // "R&D" has a capital after its first character, so it is protected like an acronym.
        #expect(entry.contains(#"  title = {{R\&D} at 50\%: the \textbraceleft{}x\textbraceright{}\_y case},"#))
        #expect(entry.contains(#"  journal = {J. Stuff \& Things},"#))
        #expect(entry.contains("  doi = {10.1000/a_b%c}"))
    }

    @Test func protectsCapitalsInTitleAndVenueButNotAuthors() {
        let entry = BibTeX.entry(
            CitablePaper(paper: paper(title: "ImageNet on iPhone", authors: ["DeWitt McDonald"], venue: "IEEE TPAMI"), citeKey: "k")
        )
        #expect(entry.contains("  title = {{ImageNet} on {iPhone}},"))
        #expect(entry.contains("  journal = {{IEEE} {TPAMI}},"))
        #expect(entry.contains("  author = {DeWitt McDonald},"))
        // A word starting with an escaped character gets double braces, so BibTeX doesn't treat it as one special character.
        let hashtag = BibTeX.entry(CitablePaper(paper: paper(title: "The #MeToo movement"), citeKey: "k"))
        #expect(hashtag.contains(##"  title = {The {{\#MeToo}} movement},"##))
    }

    @Test func arabicTextStaysUtf8() {
        let entry = BibTeX.entry(CitablePaper(paper: paper(title: "تطبيقات التعلم العميق", authors: ["محمد علي"]), citeKey: "paper2015"))
        #expect(entry.contains("  author = {محمد علي},"))
        #expect(entry.contains("  title = {تطبيقات التعلم العميق},"))
    }

    @Test func fileSortsByKeyAndSeparatesWithOneBlankLine() {
        let b = CitablePaper(paper: paper(title: "B"), citeKey: "bkey")
        let a = CitablePaper(paper: paper(title: "A"), citeKey: "akey")
        let file = BibTeX.file([b, a])
        #expect(file == BibTeX.entry(a) + "\n" + BibTeX.entry(b))
        #expect(file.hasSuffix("}\n"))
        #expect(BibTeX.file([]) == "")
    }

    @Test func organisationAndCommaAuthorsStayWhole() {
        let entry = BibTeX.entry(
            CitablePaper(paper: paper(authors: ["Bill and Melinda Gates Foundation", "Smith, Jane", "Anand Kumar"]), citeKey: "k")
        )
        #expect(entry.contains("  author = {{Bill and Melinda Gates Foundation} and {Smith, Jane} and Anand Kumar},"))
    }

    @Test func bareArxivPrefixHasNoEprint() {
        let entry = BibTeX.entry(CitablePaper(paper: paper(doi: "10.48550/arXiv."), citeKey: "k"))
        #expect(entry.contains("eprint") == false)
        #expect(entry.contains("archivePrefix") == false)
    }

    @Test func urlBracesArePercentEncoded() {
        let entry = BibTeX.entry(CitablePaper(paper: paper(doi: nil, pdf: "https://x.org/a{b}.pdf"), citeKey: "k"))
        #expect(entry.contains("  url = {https://x.org/a%7Bb%7D.pdf}"))
    }

    /// The cases of Android's on-device BibTeXOnDeviceTest: Unicode spaces that crashed Android's regex engine on copy.
    @Test func unicodeWhitespaceMatchesAndroid() {
        let unicode = paper(
            title: "\u{0085}Deep\u{2028}learning\u{00A0}for\u{3000}graphs",
            authors: ["José\u{00A0}Müller", "محمد علي"],
            year: 2015
        )
        #expect(CiteKeys.base(unicode) == "muller2015deep")
        #expect(CiteKeys.assign([unicode, unicode], taken: []) == ["muller2015deep", "muller2015deepa"])
        let file = BibTeX.file([CitablePaper(paper: unicode, citeKey: "muller2015deep")])
        #expect(file.contains("  title = {Deep learning for graphs},"))
        #expect(file.contains("  author = {José Müller and محمد علي},"))
    }

    // Swift-only.

    @Test func anUppercaseArxivPrefixStillGivesAnEprint() {
        let entry = BibTeX.entry(CitablePaper(paper: paper(doi: "10.48550/ARXIV.2101.00001"), citeKey: "k"))
        #expect(entry.contains("  eprint = {2101.00001},"))
        #expect(entry.contains("  archivePrefix = {arXiv}"))
    }

    @Test func anEntryWithNoFieldsHasAnEmptyBody() {
        let entry = BibTeX.entry(
            CitablePaper(paper: paper(title: " ", authors: [], year: nil, venue: nil, doi: nil, details: PublicationDetails()), citeKey: "papernd")
        )
        #expect(entry == "@misc{papernd,\n}\n")
    }
}
