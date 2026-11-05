import Foundation
@testable import HashiyaImport
import HashiyaModel
import Testing

/// Ported from Android's `ImportPlannerTest.kt`, `ImportCiteKeysTest.kt` and `LocalPaperTest.kt`.
struct ImportPlannerTests {
    private func entry(_ line: Int, title: String? = nil, doi: String? = nil, year: Int? = 2020) -> ImportedEntry {
        ImportedEntry(line: line, entryType: "article", title: title ?? "T\(line)", year: year, doi: doi)
    }

    private func kinds(_ plan: [PlannedEntry]) -> [String] {
        plan.map {
            switch $0 {
            case let .toImport(entry): "toImport \(entry.line)"
            case let .inLibrary(entry): "inLibrary \(entry.line)"
            case let .alreadyImported(entry): "alreadyImported \(entry.line)"
            case let .repeated(entry): "repeated \(entry.line)"
            }
        }
    }

    @Test func titleKeysIgnoreCaseAccentsTashkeelAndPunctuation() {
        #expect(titleKey("Über: Deep-Learning!", year: 2020) == titleKey("uber deep learning", year: 2020))
        #expect(titleKey("تَحليل  المشاعر", year: nil) == titleKey("تحليل المشاعر", year: nil))
        #expect(titleKey("  ?! ", year: 2020) == nil)
    }

    @Test func repeatsInTheFileAreImportedOnce() {
        let plan = ImportPlanner.plan(
            [entry(1, doi: "10.1/a"), entry(2, title: "Other", doi: "10.1/a"), entry(3, title: "Same Title"), entry(4, title: "same title."),
             entry(5, title: "Same Title", year: 2021)],
            existing: ExistingLibrary()
        )
        #expect(kinds(plan) == ["toImport 1", "repeated 2", "toImport 3", "repeated 4", "toImport 5"])
    }

    @Test func savedAndAlreadyImportedPapers() {
        let existing = ExistingLibrary(
            dois: ["10.1/mine", "10.1/theirs"],
            titleKeys: [titleKey("Saved Paper", year: 2019)!],
            importedDOIs: ["10.1/mine"]
        )
        let plan = ImportPlanner.plan(
            [entry(1, doi: "10.1/mine"), entry(2, doi: "10.1/theirs"), entry(3, title: "Saved paper", year: 2019), entry(4, title: "Saved paper")],
            existing: existing
        )
        #expect(kinds(plan) == ["alreadyImported 1", "inLibrary 2", "inLibrary 3", "toImport 4"])
    }

    @Test func citeKeys() {
        #expect(ImportCiteKeys.sanitize("ronneberger_u-net_2015") == "ronneberger_u-net_2015")
        #expect(ImportCiteKeys.sanitize("bad#key") == nil)
        #expect(ImportCiteKeys.sanitize("a b") == nil)
        #expect(ImportCiteKeys.resolve("smith2020", taken: ["smith2020", "smith2020a"]) == "smith2020b")
        #expect(ImportCiteKeys.resolve("k", taken: Set(["k"] + "abcdefghijklmnopqrstuvwxyz".map { "k\($0)" })) == "kaa")
    }

    @Test func aLocalPaperCarriesTheFilesFieldsUnderALocalID() {
        let paper = ImportedEntry(line: 1, entryType: "misc", title: "T", authors: ["Jane Doe"], year: 2005, venue: "V", doi: "10.1/x",
                                  url: "https://example.org/report.PDF?download=1").localPaper(uuid: "1234")
        #expect(paper.openAlexID == "local-1234")
        #expect(paper.authors == [Author(name: "Jane Doe")])
        #expect(paper.openAccessPDFURL == "https://example.org/report.PDF?download=1")
        #expect(ImportedEntry(line: 1, entryType: "misc", url: "https://x.org/page").localPaper(uuid: "1").openAccessPDFURL == nil)
    }

    @Test func aLocalPaperKeepsItsPublicationDetails() {
        let paper = ImportedEntry(line: 1, entryType: "article", title: "T", volume: "12", issue: "3", firstPage: "4", lastPage: "9",
                                  publisher: "P", workType: "article", sourceType: "journal").localPaper(uuid: "1")
        #expect(paper.publication == PublicationDetails(workType: "article", sourceType: "journal", publisher: "P",
                                                        volume: "12", issue: "3", firstPage: "4", lastPage: "9"))
    }
}
