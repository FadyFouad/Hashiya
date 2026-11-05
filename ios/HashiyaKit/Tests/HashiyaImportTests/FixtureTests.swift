import Foundation
@testable import HashiyaImport
import Testing

/// Real-shape exports must parse to exactly the results in `Fixtures/expected`: the same JSON Android's `FixtureTest`
/// reads from `testdata/import/expected`. `Fixtures` is a byte-for-byte copy of `testdata/import`; Android's
/// `IosFixtureCopyTest` fails if the two ever differ.
struct FixtureTests {
    static let names = [
        "zotero.bib", "better-bibtex.bib", "better-biblatex.bib", "mendeley.bib", "google-scholar.bib", "nasty.bib",
        "zotero.ris", "endnote.ris", "mendeley.ris", "nasty.ris", "endnote-cp1252.ris",
    ]

    private static func url(_ path: String) -> URL {
        Bundle.module.resourceURL!.appendingPathComponent("Fixtures").appendingPathComponent(path)
    }

    @Test(arguments: FixtureTests.names)
    func parsesExactly(name: String) throws {
        let data = try Data(contentsOf: Self.url(name))
        let expected = try JSONSerialization.jsonObject(with: Data(contentsOf: Self.url("expected/\(name).json"))) as! NSDictionary
        let result = try #require(ImportParser.parse(ImportParser.decode(data)))
        let actual = result.json() as NSDictionary
        #expect(actual.isEqual(to: expected as! [AnyHashable: Any]), "\(name):\n\(actual)\n≠\n\(expected)")
    }

    @Test func everyFixtureHasAnExpectedResult() throws {
        let files = try FileManager.default.contentsOfDirectory(atPath: Self.url("").path)
            .filter { $0.hasSuffix(".bib") || $0.hasSuffix(".ris") }
        #expect(Set(files) == Set(Self.names))
    }
}

extension ImportParseResult {
    /// The expected files' shape: every field present, nils as NSNull.
    func json() -> [String: Any] {
        [
            "format": format.rawValue,
            "entries": entries.map { $0.json() },
            "issues": issues.map { ["line": $0.line, "kind": $0.kind.rawValue] as [String: Any] },
        ]
    }
}

private extension ImportedEntry {
    func json() -> [String: Any] {
        func value(_ text: String?) -> Any { text ?? NSNull() }
        return [
            "line": line,
            "entryType": entryType,
            "citeKey": value(citeKey),
            "title": title,
            "authors": authors,
            "year": year.map { $0 as Any } ?? NSNull(),
            "venue": value(venue),
            "doi": value(doi),
            "url": value(url),
            "abstract": value(abstract),
            "volume": value(volume),
            "issue": value(issue),
            "firstPage": value(firstPage),
            "lastPage": value(lastPage),
            "publisher": value(publisher),
            "workType": value(workType),
            "sourceType": value(sourceType),
        ]
    }
}
