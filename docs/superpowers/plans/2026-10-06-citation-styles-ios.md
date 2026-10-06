# Citation Styles (iOS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iOS side of APA 7 and IEEE citations (Android: PR #47): copy a formatted citation from Details' ⋯ menu (rich text with a plain fallback), export a reference list (`.rtf`) from the Library, remember the last style.

**Architecture:** `WorkKind` and `CitationStyle` move into `HashiyaModel`; a new SwiftPM target `HashiyaCitation` ports Android's `:core:citation` (runs, names, APA, IEEE, plain/HTML/RTF renderers) **including every Android review fix**. `CitationRepository` gains a style; a small `CitationStyleStore` (UserDefaults) remembers it. Features keep depending only on `HashiyaData`/`HashiyaModel`; only `HashiyaData` imports `HashiyaCitation`.

**Tech Stack:** Swift 6, SwiftUI, GRDB, Swift Testing, swift-snapshot-testing, XcodeGen.

**Spec:** `docs/superpowers/specs/2026-10-06-citation-styles-design.md`

**Reference implementation:** Android `android/core/citation/src/main/kotlin/com/etatech/hashiya/core/citation/*.kt` and its tests `android/core/citation/src/test/kotlin/com/etatech/hashiya/core/citation/*.kt` (`NamesTest`, `RenderingTest`, `ApaTest`, `IeeeTest`). **Their expected strings are binding for iOS: both apps must produce identical citations.**

## Global Constraints

- `CitationStyle`: `apa`, `ieee`, `bibtex` (raw values), default `.apa`.
- Formatting is offline; the only network use is the existing details refetch in `GRDBCitationRepository`.
- BibTeX output must not change; `HashiyaBibTeXTests` stay green and unedited (except renaming `CitationResult(bibtex:` → `CitationResult(text:` wherever it appears).
- Output strings, punctuation, italics, author rules, page rules, DOI rules, RTF bytes and sort/numbering are exactly Android's (see Reference implementation), including: `endSentence` after authors and after every venue/publisher; issue without volume `, (Issue)`; a quoted IEEE title ending in `. ? !` takes no added `,`/`.`; an IEEE chapter venue ending in `. ? !` is followed by ` Publisher`; an IEEE chapter without a venue keeps the publisher as its own part; RTF writes CR/LF/tab as a space; APA RTF paragraphs `{\pard\fi-720\li720\sl480\slmult1 `, IEEE `{\pard `.
- Files: `<name>.bib`, `<name> – APA.rtf`, `<name> – IEEE.rtf` (en dash with spaces), `hashiya-library` when no collection.
- Analytics: `ExportFormat` gains `apa`, `ieee`; no new events; copying logs nothing.
- Every String Catalog key has Arabic (`python3 ios/scripts/check-translations.py`); restore `ios/Hashiya/InfoPlist.xcstrings` and `ios/HashiyaShare/InfoPlist.xcstrings` after local builds if Xcode rewrote them; stage files by name, never `git add -A`.
- Commits authored `Fady <fady.fouad.a@gmail.com>`; no AI attribution or trailers.

Test command (swap the target):
```bash
cd ios && xcodegen generate --spec project.yml >/dev/null && cd ..
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya \
  -destination 'platform=iOS Simulator,id=F857F105-E1D5-449B-BBB0-0FA009FBD574' \
  -only-testing:HashiyaCitationTests -collect-test-diagnostics never 2>&1 | tail -40
```
Snapshot baselines are recorded on CI (Task 8), not locally.

## Review Focus

1. iOS and Android disagree on a citation (a missed review fix in the port) — Tasks 3–4 port every Android test one to one.
2. Rich paste: APA/IEEE copies must put both HTML and plain text on the pasteboard (Task 6 tests the closure payload).
3. Arabic titles and names survive the `.rtf` (Task 2 ports the Unicode RTF test).
4. An empty collection exports a valid empty RTF document (Task 5).
5. The UI tests that tap "Export .bib" keep working with the new export menu (Task 7 updates them).

---

### Task 1: `WorkKind` and `CitationStyle` in `HashiyaModel`

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaModel/WorkKind.swift`, `ios/HashiyaKit/Sources/HashiyaModel/CitationStyle.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaBibTeX/EntryType.swift` (lines 38–52), `ios/HashiyaKit/Sources/HashiyaModel/PublicationDetails.swift` (doc comment: "HashiyaBibTeX and HashiyaCitation interpret them")
- Test: `ios/HashiyaKit/Tests/HashiyaModelTests/WorkKindTests.swift`

**Interfaces:**
- Produces: `public enum WorkKind: Sendable { case article, conference, chapter, book, thesis, report, preprint, other }`; `extension PublicationDetails { public var workKind: WorkKind }`; `public enum CitationStyle: String, Sendable, CaseIterable { case apa, ieee, bibtex; public init(storedValue: String?) }` (unknown/nil → `.apa`).

- [ ] **Step 1: Failing test** — `WorkKindTests.swift`:
```swift
import HashiyaModel
import Testing

struct WorkKindTests {
    private func kind(_ work: String?, _ source: String?) -> WorkKind {
        PublicationDetails(workType: work, sourceType: source).workKind
    }

    @Test func followsTheBibTeXTableInOrder() {
        #expect(kind("article", "conference") == .conference)
        #expect(kind("book-chapter", "book") == .chapter)
        #expect(kind("book", nil) == .book)
        #expect(kind("dissertation", nil) == .thesis)
        #expect(kind("report", nil) == .report)
        #expect(kind("preprint", nil) == .preprint)
        #expect(kind("article", "repository") == .preprint)
        #expect(kind("Article", "Journal") == .article)
        #expect(kind("review", "journal") == .article)
    }

    @Test func anythingElseIsOther() {
        #expect(kind(nil, nil) == .other)
        #expect(kind("article", nil) == .other)
        #expect(kind("dataset", "journal") == .other)
    }

    @Test func stylesRoundTripAndDefaultToAPA() {
        for style in CitationStyle.allCases { #expect(CitationStyle(storedValue: style.rawValue) == style) }
        #expect(CitationStyle(storedValue: nil) == .apa)
        #expect(CitationStyle(storedValue: "harvard") == .apa)
    }
}
```
(Check `PublicationDetails`'s initializer — if it has no `workType:sourceType:` labelled init with defaults for the rest, use its real memberwise init.)
- [ ] **Step 2:** run with `-only-testing:HashiyaModelTests` → build FAILS.
- [ ] **Step 3: Implement.** `WorkKind.swift`:
```swift
/// What kind of work a paper is, from OpenAlex's work and source types; BibTeX and the citation styles share it.
public enum WorkKind: Sendable {
    case article, conference, chapter, book, thesis, report, preprint, other
}

private let journalWorkTypes: Set<String> = ["article", "review", "letter", "editorial"]

extension PublicationDetails {
    /// First match wins: a conference article is a conference paper, a repository article a preprint.
    public var workKind: WorkKind {
        let work = workType?.lowercased()
        let source = sourceType?.lowercased()
        if source == "conference" { return .conference }
        if work == "book-chapter" { return .chapter }
        if work == "book" { return .book }
        if work == "dissertation" { return .thesis }
        if work == "report" { return .report }
        if work == "preprint" || source == "repository" { return .preprint }
        if let work, journalWorkTypes.contains(work), source == "journal" { return .article }
        return .other
    }
}
```
`CitationStyle.swift`:
```swift
/// How a citation or a reference list is written; the raw value is what the preference and analytics store.
public enum CitationStyle: String, Sendable, CaseIterable {
    case apa, ieee, bibtex

    /// APA when nothing, or something unknown, is stored.
    public init(storedValue: String?) {
        self = storedValue.flatMap(CitationStyle.init(rawValue:)) ?? .apa
    }
}
```
In `EntryType.swift`, delete `journalWorkTypes` and replace `entryType` with:
```swift
/// The BibTeX type for a kind of work: preprints and anything else are @misc.
func entryType(_ details: PublicationDetails) -> EntryType {
    switch details.workKind {
    case .conference: .inProceedings
    case .chapter: .inCollection
    case .book: .book
    case .thesis: .phdThesis
    case .report: .techReport
    case .article: .article
    case .preprint, .other: .misc
    }
}
```
- [ ] **Step 4:** run `-only-testing:HashiyaModelTests` and `-only-testing:HashiyaBibTeXTests` → PASS (BibTeX tests unedited).
- [ ] **Step 5:** commit `refactor(ios): share the kind of work between BibTeX and citation styles` (stage the 4 files by name).

---

### Task 2: `HashiyaCitation` — runs, names, renderers

**Files:**
- Modify: `ios/HashiyaKit/Package.swift` (products: `.library(name: "HashiyaCitation", targets: ["HashiyaCitation"])`; targets: `.target(name: "HashiyaCitation", dependencies: ["HashiyaModel"])` after `HashiyaBibTeX`; test target `.testTarget(name: "HashiyaCitationTests", dependencies: ["HashiyaCitation", "HashiyaModel"])` after `HashiyaBibTeXTests`)
- Modify: `ios/project.yml` (test list near line 156: `- package: HashiyaKit/HashiyaCitationTests` after `HashiyaBibTeXTests`)
- Create: `ios/HashiyaKit/Sources/HashiyaCitation/StyledCitation.swift`, `PersonName.swift`, `Rendering.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaCitationTests/PersonNameTests.swift`, `RenderingTests.swift`

**Interfaces:**
- Produces: `public struct Run: Equatable, Sendable { public var text: String; public var italic: Bool; public init(_ text: String, italic: Bool = false) }`; `public struct StyledCitation: Equatable, Sendable { public var runs: [Run]; public init(_ runs: [Run]); public var plain: String }`; `struct CitationBuilder` (`text(_:)`, `italic(_:)`, `append(_ runs:)`, `endSentence()`, `build()`); `struct PersonName: Equatable { let family: String; let initials: String? }`; `func personName(_ name: String) -> PersonName`; `public enum Rendering { static func plain(_:) -> String; static func html(_:) -> String; static func rtf(_ entries: [StyledCitation], hangingIndent: Bool) -> String }`.

- [ ] **Step 1: Package and project.** Edit `Package.swift` and `project.yml` as listed.
- [ ] **Step 2: Failing tests.** Port Android's `NamesTest.kt` (3 tests) to `PersonNameTests.swift` and `RenderingTest.kt` (all tests, including `lineBreaksAndTabsBecomeASingleSpace`, the emoji `\u-10179?\u-8704?` case, the hanging-indent test expecting `{\pard\fi-720\li720\sl480\slmult1 x\par}` for APA and `{\pard x\par}` with no `\sl` for IEEE, and the empty document) to `RenderingTests.swift` — same inputs, same expected strings, Swift Testing `@Test`/`#expect`, `@testable import HashiyaCitation`. Example:
```swift
@testable import HashiyaCitation
import Testing

struct RenderingTests {
    private let citation = StyledCitation([Run("A & B <x> \"q\". "), Run("Journal", italic: true), Run(", 1.")])

    @Test func htmlEscapesAndItalicises() {
        #expect(Rendering.html(citation) == "A &amp; B &lt;x&gt; &quot;q&quot;. <i>Journal</i>, 1.")
    }

    @Test func anEmptyListIsAValidEmptyDocument() {
        #expect(Rendering.rtf([], hangingIndent: true) == "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n}")
    }
}
```
- [ ] **Step 3:** `-only-testing:HashiyaCitationTests` → build FAILS.
- [ ] **Step 4: Implement.** `StyledCitation.swift`:
```swift
/// A piece of a citation: plain or italic text.
public struct Run: Equatable, Sendable {
    public var text: String
    public var italic: Bool

    public init(_ text: String, italic: Bool = false) {
        self.text = text
        self.italic = italic
    }
}

/// A formatted citation as runs, so each output (plain, HTML, RTF) can show the italics its own way.
public struct StyledCitation: Equatable, Sendable {
    public var runs: [Run]

    public init(_ runs: [Run]) {
        self.runs = runs
    }

    public var plain: String { Rendering.plain(self) }
}

/// Builds the runs, merging neighbours of the same kind.
struct CitationBuilder {
    private(set) var runs: [Run] = []

    mutating func text(_ s: String) { add(Run(s)) }

    mutating func italic(_ s: String) { add(Run(s, italic: true)) }

    mutating func append(_ more: [Run]) { more.forEach { add($0) } }

    /// Ends a sentence: a full stop unless the text already ends with . ? or !.
    mutating func endSentence() {
        let last = runs.last?.text.last
        if last != "." && last != "?" && last != "!" { text(".") }
    }

    func build() -> StyledCitation { StyledCitation(runs) }

    private mutating func add(_ run: Run) {
        guard !run.text.isEmpty else { return }
        if let last = runs.last, last.italic == run.italic {
            runs[runs.count - 1].text += run.text
        } else {
            runs.append(run)
        }
    }
}
```
`PersonName.swift`:
```swift
/// A person's family name and initials; `initials` is nil when the name is kept whole.
struct PersonName: Equatable {
    let family: String
    let initials: String?
}

/// "Aidan N. Gomez" → Gomez, A. N. One-word names (organisations) and Arabic-script names are kept whole.
func personName(_ name: String) -> PersonName {
    let parts = name.split(whereSeparator: \.isWhitespace).map(String.init)
    let trimmed = parts.joined(separator: " ")
    let arabic = trimmed.unicodeScalars.contains { (0x0600...0x06FF).contains($0.value) }
    guard parts.count >= 2, !arabic else { return PersonName(family: trimmed, initials: nil) }
    let initials = parts.dropLast().map { given in
        given.split(separator: "-").map { "\($0.first!.uppercased())." }.joined(separator: "-")
    }.joined(separator: " ")
    return PersonName(family: parts.last!, initials: initials)
}
```
`Rendering.swift`:
```swift
/// Plain text, HTML for the rich pasteboard, and RTF for exported reference lists.
public enum Rendering {
    public static func plain(_ citation: StyledCitation) -> String {
        citation.runs.map(\.text).joined()
    }

    public static func html(_ citation: StyledCitation) -> String {
        citation.runs.map { run in
            let escaped = escapeHTML(run.text)
            return run.italic ? "<i>\(escaped)</i>" : escaped
        }.joined()
    }

    /// One paragraph per entry; APA entries get a 0.5-inch hanging indent and double spacing. Readable by Word and Pages.
    public static func rtf(_ entries: [StyledCitation], hangingIndent: Bool) -> String {
        var out = "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n"
        for entry in entries {
            out += hangingIndent ? "{\\pard\\fi-720\\li720\\sl480\\slmult1 " : "{\\pard "
            for run in entry.runs {
                out += run.italic ? "{\\i \(escapeRTF(run.text))}" : escapeRTF(run.text)
            }
            out += "\\par}\n"
        }
        return out + "}"
    }

    private static func escapeHTML(_ text: String) -> String {
        var out = ""
        for c in text {
            switch c {
            case "&": out += "&amp;"
            case "<": out += "&lt;"
            case ">": out += "&gt;"
            case "\"": out += "&quot;"
            default: out.append(c)
            }
        }
        return out
    }

    /// RTF is 7-bit: everything above U+007F is written as \uN? per UTF-16 unit (N signed 16-bit).
    private static func escapeRTF(_ text: String) -> String {
        var out = ""
        for unit in text.utf16 {
            switch unit {
            case 0x5C, 0x7B, 0x7D: out += "\\" + String(UnicodeScalar(UInt8(unit)))
            case 0x0A, 0x0D, 0x09: out += " "
            case 0x80...: out += "\\u\(Int16(bitPattern: unit))?"
            default: out += String(UnicodeScalar(UInt8(unit)))
            }
        }
        return out
    }
}
```
- [ ] **Step 5:** `-only-testing:HashiyaCitationTests` → PASS.
- [ ] **Step 6:** commit `feat(ios): styled citations and their plain, HTML and RTF forms`.

---

### Task 3: APA 7

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaCitation/Common.swift`, `ios/HashiyaKit/Sources/HashiyaCitation/APA.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaCitationTests/TestPapers.swift`, `APATests.swift`

**Interfaces:**
- Produces: `public enum APA { public static func format(_ paper: Paper) -> StyledCitation; public static func list(_ papers: [Paper]) -> [StyledCitation]; static func authors(_ names: [String]) -> String }`; `Common.swift`: `func doiOf(_:) -> String`, `func pageRange(_ first: String?, _ last: String?) -> String?`, `func isSinglePage(_:_:) -> Bool`, `func sortKey(_:) -> String`, `extension Optional where Wrapped == String { var nonBlank: String? }`.

- [ ] **Step 1: Test papers.** `TestPapers.swift` — a `paper(...)` helper with the same parameters and defaults as Android's `TestPapers.kt` (title "Attention is all you need", authors ["Ashish Vaswani", "Noam Shazeer"], year 2017, venue "Advances in Neural Information Processing Systems", doi "10.5555/3295222.3295349", pdf nil, work "article", source "journal", publisher/volume/issue/first/last nil), building `Paper(openAlexID: "W1", doi:, title:, authors: names.map { Author(name: $0, openAlexID: nil) }, year:, venue:, isOpenAccess: pdf != nil, openAccessPDFURL: pdf, publication: PublicationDetails(...))` (use `Paper`'s real init labels).
- [ ] **Step 2: Failing tests.** Port **every** `@Test` in Android's `ApaTest.kt` (17 tests, including the round-1 review tests: whole-name authors `OpenAI. (2017). ` and `…, & محمد علي. (2017). `, issue without volume `Nature, (7553), 436–444.`, `Springer-Verlag Inc.` without `..`, `In Proc. IEEE Conf. Curran.`) to `APATests.swift` with identical inputs and expected strings/runs. Example:
```swift
@testable import HashiyaCitation
import HashiyaModel
import Testing

struct APATests {
    @Test func journalArticle() {
        #expect(APA.format(paper(venue: "Nature", doi: "10.1038/nature14539", volume: "521", issue: "7553", first: "436", last: "444")).runs == [
            Run("Vaswani, A., & Shazeer, N. (2017). Attention is all you need. "),
            Run("Nature", italic: true), Run(", "), Run("521", italic: true), Run("(7553), 436–444. https://doi.org/10.1038/nature14539"),
        ])
    }
}
```
- [ ] **Step 3:** `-only-testing:HashiyaCitationTests` → build FAILS.
- [ ] **Step 4: Implement.** `Common.swift`:
```swift
import Foundation

/// "10.1/x" from "10.1/x", "https://doi.org/10.1/x" or "doi:10.1/x".
func doiOf(_ raw: String) -> String {
    raw.trimmingCharacters(in: .whitespacesAndNewlines)
        .replacingOccurrences(of: #"^(https?://(dx\.)?doi\.org/|doi:)"#, with: "", options: [.regularExpression, .caseInsensitive])
}

/// "436–444", or "12" for a single page; nil when there is no first page.
func pageRange(_ first: String?, _ last: String?) -> String? {
    guard let a = first.nonBlank else { return nil }
    guard let b = last.nonBlank, b != a else { return a }
    return "\(a)–\(b)"
}

func isSinglePage(_ first: String?, _ last: String?) -> Bool {
    guard let b = last.nonBlank else { return true }
    return b == first?.trimmingCharacters(in: .whitespacesAndNewlines)
}

/// Lower case without diacritics, for sorting.
func sortKey(_ text: String) -> String {
    text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US_POSIX")).lowercased()
}

extension Optional where Wrapped == String {
    /// Trimmed, or nil when empty.
    var nonBlank: String? {
        guard let trimmed = self?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
        return trimmed
    }
}
```
`APA.swift` — a line-for-line port of Android's final `Apa.kt` (read it): `format` (authors → `endSentence()` → ` (Year). ` → title; no authors: title → ` (Year).`), `list` (sort by `sortKey(first author's family or title)`, then year present before absent, then year ascending, then `sortKey(title)`; use a stable sort — sort `enumerated()` with the index as the last tie-break), `authors` (1 / ≤20 with `, & ` / 21+ with `, . . . `), `title` (`[Untitled]`; italic for book/thesis/report/preprint/other; ` [Thesis, Venue]` / ` [Preprint]`; `endSentence()`), `source` (article: ` *Journal*`, `, *Vol*`, issue `(N)` or `, (N)` without volume, `, pages`, `endSentence()`; conference/chapter: ` In *Venue*`, ` (p. x)`/` (pp. x–y)`, `endSentence()`, then ` Publisher` + `endSentence()`; book: ` Publisher.`; thesis: nothing; report: ` (publisher ?? venue).`; preprint/other: ` Venue.` — every closing stop via `endSentence()`), `link` (`https://doi.org/` + `doiOf(doi)`, else the open-access URL).
- [ ] **Step 5:** `-only-testing:HashiyaCitationTests` → PASS. If a ported expectation fails, the Swift port differs from Android — fix the port, never the expectation.
- [ ] **Step 6:** commit `feat(ios): APA 7 references`.

---

### Task 4: IEEE

**Files:**
- Create: `ios/HashiyaKit/Sources/HashiyaCitation/IEEE.swift`
- Test: `ios/HashiyaKit/Tests/HashiyaCitationTests/IEEETests.swift`

**Interfaces:**
- Produces: `public enum IEEE { public static func format(_ paper: Paper) -> StyledCitation; public static func list(_ papers: [Paper]) -> [StyledCitation]; static func authors(_ names: [String]) -> [Run] }`.

- [ ] **Step 1: Failing tests.** Port **every** `@Test` in Android's `IeeeTest.kt` (15 tests, including `quotedTitleEndingInAMarkTakesNoExtraPunctuation`, `chapterVenueEndingInAFullStopIsFollowedByThePublisherDirectly`, `chapterWithoutAVenueKeepsThePublisher`, the `Springer-Verlag Inc.` and book-title-with-`?` tests) with identical inputs and expected strings/runs.
- [ ] **Step 2:** run → build FAILS.
- [ ] **Step 3: Implement** `IEEE.swift` — a line-for-line port of Android's final `Ieee.kt` (read it): authors + `, `; book: italic title, `endSentence()`, ` publisher ?? venue, year, doi` + `endSentence()`; other kinds: the tail parts per kind (article: *venue*, `vol. V`, `no. N`, pages, year, doi; conference: `in ` *venue*, year, pages, doi; chapter: `in ` *venue* then ` Publisher` if the venue ends in `. ? !` else `. Publisher`, or the publisher alone when there is no venue, then year, pages, doi; thesis: `Thesis`, venue, year, doi; report: `publisher ?? venue`, `Tech. Rep.`, year, doi; else venue, year, doi); the quoted title closes with nothing if it ends in `. ? !`, `.` with no tail, `,` otherwise; tail joined by `, ` then `endSentence()`; no DOI → ` [Online]. Available: <url>`. `list` prefixes `[n] ` and merges runs. `authors`: 1; 2 with ` and `; 3–6 with `, ` and `, and `; 7+ → `Run("First ")`, `Run("et al.", italic: true)`.
- [ ] **Step 4:** run → PASS (same rule: fix the port, not the expectation).
- [ ] **Step 5:** commit `feat(ios): IEEE references`.

---

### Task 5: The repository and the remembered style

**Files:**
- Modify: `ios/HashiyaKit/Package.swift` (`HashiyaData` depends on `HashiyaCitation`)
- Modify: `ios/HashiyaKit/Sources/HashiyaData/CitationRepository.swift`
- Create: `ios/HashiyaKit/Sources/HashiyaData/CitationStyleStore.swift`
- Modify: `ios/HashiyaKit/Sources/HashiyaTesting/FakeCitationRepository.swift`
- Modify: every `CitationResult(bibtex:` / `.bibtex` reader in main and test code under `ios/` (grep; at least `PaperDetailsViewModel.swift`, `LibraryViewModel.swift`, `FakeCitationRepository.swift`, their tests, `FakeCitationRepositoryTests.swift`, `GRDBCitationRepositoryTests.swift`)
- Test: `ios/HashiyaKit/Tests/HashiyaDataTests/GRDBCitationRepositoryTests.swift`, `ios/HashiyaKit/Tests/HashiyaDataTests/CitationStyleStoreTests.swift`

**Interfaces:**
- Produces:
  - `public struct CitationResult: Equatable, Sendable { public var text: String; public var html: String?; public var rtf: String?; public var complete: Bool; public init(text: String, html: String? = nil, rtf: String? = nil, complete: Bool) }`
  - `protocol CitationRepository { func entry(openAlexID: String, style: CitationStyle) async throws -> CitationResult?; func export(collectionID: Int64?, style: CitationStyle) async throws -> CitationResult }` plus an extension with `entry(openAlexID:)` / `export(collectionID:)` forwarding `.bibtex`.
  - `public final class CitationStyleStore: @unchecked Sendable { public init(defaults: UserDefaults = .standard); public var style: CitationStyle { get }; public func set(_ style: CitationStyle) }` — key `"citationStyle"`.
  - `FakeCitationRepository`: `entryCalls`/`exportCalls` keep their types; new `styles: [CitationStyle]` (every call's style); `entry` returns the scripted result with `html: "<i>\(text)</i>"` for non-BibTeX styles; `export` returns it with `rtf: "{\\rtf1 \(text)}"` for non-BibTeX styles.

- [ ] **Step 1: Failing tests.** `CitationStyleStoreTests` (with `TestDefaults.make()`): default `.apa`; `set(.ieee)` then a new store on the same defaults reads `.ieee`; a stored `"harvard"` reads `.apa`. In `GRDBCitationRepositoryTests`, reusing the file's helpers (`repository`, `paper`, the saving helpers used by `keysAreAssignedInSavedOrderAndNeverChange` and `emptyCollectionExportsAnEmptyCompleteFile`):
  - `anAPAEntryHasPlainAndHTMLText` — `entry(…, style: .apa)` text contains `"(20"`, html contains `"<i>"`, rtf nil.
  - `anIEEEExportNumbersInSavedOrderAndHasRTF` — save "Zebra studies" then "Aardvark studies"; export `.ieee`: text starts with `[1] ` and the `[1]` line contains "Zebra", the `[2]` line "Aardvark"; rtf starts with `{\rtf1`.
  - `anEmptyCollectionExportsAnEmptyRTFDocument` — rtf == `"{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n}"`, text `""`, complete true.
  - `bibtexIsUnchangedAndHasNoRichForms` — default entry has html nil, rtf nil.
- [ ] **Step 2:** `-only-testing:HashiyaDataTests` → build FAILS.
- [ ] **Step 3: Implement.** In `GRDBCitationRepository`:
```swift
    public func entry(openAlexID: String, style: CitationStyle) async throws -> CitationResult? {
        guard let stored = try await store.citablePaper(openAlexID: openAlexID) else { return nil }
        try await refetch([stored])
        try await assignMissingKeys()
        // Nil when the paper was removed while its details were being fetched.
        guard let row = try await store.citablePaper(openAlexID: openAlexID) else { return nil }
        switch style {
        case .bibtex:
            guard let citable = row.citable else { return nil }
            return CitationResult(text: BibTeX.entry(citable), complete: row.hasDetails)
        case .apa, .ieee:
            let citation = style == .apa ? APA.format(row.asPaper()) : IEEE.format(row.asPaper())
            return CitationResult(text: citation.plain, html: Rendering.html(citation), complete: row.hasDetails)
        }
    }

    public func export(collectionID: Int64?, style: CitationStyle) async throws -> CitationResult {
        try await refetch(store.citablePapers(collectionID: collectionID))
        try await assignMissingKeys()
        // Read again: papers removed meanwhile drop out. One saved after the keys were assigned has none yet and is left out too.
        let rows = try await store.citablePapers(collectionID: collectionID).filter { $0.paper.citeKey != nil }
        let complete = rows.allSatisfy(\.hasDetails)
        switch style {
        case .bibtex:
            return CitationResult(text: BibTeX.file(rows.compactMap(\.citable)), complete: complete)
        case .apa, .ieee:
            let papers = rows.map { $0.asPaper() }
            let list = style == .apa ? APA.list(papers) : IEEE.list(papers)
            return CitationResult(
                text: list.map(\.plain).joined(separator: "\n\n"),
                rtf: Rendering.rtf(list, hangingIndent: style == .apa),
                complete: complete
            )
        }
    }
```
(match `asPaper()`'s real spelling). `CitationStyleStore.swift`:
```swift
import Foundation
import HashiyaModel

/// The style Copy citation and Export use first; APA until one is chosen.
public final class CitationStyleStore: @unchecked Sendable {
    private let defaults: UserDefaults
    private static let key = "citationStyle"

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public var style: CitationStyle { CitationStyle(storedValue: defaults.string(forKey: Self.key)) }

    public func set(_ style: CitationStyle) { defaults.set(style.rawValue, forKey: Self.key) }
}
```
Rename `CitationResult(bibtex:` → `CitationResult(text:` and `.bibtex` → `.text` at every reader; update the fake per **Interfaces** (keep its existing scripting API).
- [ ] **Step 4:** `-only-testing:HashiyaDataTests`, `-only-testing:FeaturePaperDetailsTests`, `-only-testing:FeatureLibraryTests` → PASS (features only renamed).
- [ ] **Step 5:** commit `feat(ios): citations in APA and IEEE from the repository, and the remembered style`.

---

### Task 6: Details — Copy citation

**Files:**
- Modify: `ios/HashiyaKit/Sources/FeaturePaperDetails/PaperDetailsViewModel.swift`, `PaperDetailsContent.swift` (`moreOptions` menu, `PaperDetailsActions`), `PaperDetailsScreen.swift` (wiring), `PaperDetailsBanner.swift`, `Resources/Localizable.xcstrings`
- Modify: `ios/Hashiya/AppContainer.swift` (`makePaperDetailsViewModel`: the pasteboard closure and the style store)
- Modify tests constructing `PaperDetailsViewModel` (grep) for the new `copy` payload type; `PaperDetailsStringsTests.swift`
- Test: `ios/HashiyaKit/Tests/FeaturePaperDetailsTests/PaperDetailsViewModelTests.swift` (`// MARK: Copy` section)

**Interfaces:**
- Consumes: `CitationRepository.entry(openAlexID:style:)`, `CitationResult(text, html, …)`, `CitationStyleStore`.
- Produces: `public struct CopiedText: Equatable, Sendable { public let text: String; public let html: String? }`; view model init `copy: @escaping @MainActor (CopiedText) -> Void` (replacing `(String) -> Void`) and `styles: CitationStyleStore = CitationStyleStore()`; `public private(set) var citationStyle: CitationStyle` (initialised from the store); `public func copyCitation(_ style: CitationStyle) async` (replacing `copyBibTeX()`); messages `.apaCopied`, `.ieeeCopied`, `.bibtexCopied`, `.citationIncomplete` (renamed from `.bibtexIncomplete`), `.copyFailed`; `public func orderedStyles(_ remembered: CitationStyle) -> [CitationStyle]` (remembered first, then the rest in `apa, ieee, bibtex` order) in `FeaturePaperDetails`; `PaperDetailsActions.copyCitation: (CitationStyle) -> Void` and `PaperDetailsActions.citationStyle: CitationStyle = .apa`.

- [ ] **Step 1: Strings** (FeaturePaperDetails catalog, en/ar): add `details.copyApa` "Copy APA 7 citation" / «نسخ استشهاد APA 7», `details.copyIeee` "Copy IEEE citation" / «نسخ استشهاد IEEE», `details.apaCopied` "APA citation copied" / «تم نسخ استشهاد APA», `details.ieeeCopied` "IEEE citation copied" / «تم نسخ استشهاد IEEE»; rename `details.bibtexIncomplete` → `details.citationIncomplete` (same texts); change `details.copyFailed` to "Couldn't copy the citation" / «تعذّر نسخ الاستشهاد»; keep `details.copyBibtex` and `details.bibtexCopied`.
- [ ] **Step 2: Failing tests** (view model, using the file's `Clipboard` helper now recording `CopiedText`, and `CitationStyleStore(defaults: TestDefaults.make())`):
  - `copyingAPACopiesRichTextRemembersItAndSaysSo` — `copyCitation(.apa)` → clipboard `[CopiedText(text: <entry text>, html: "<i><entry text></i>")]`, message `.apaCopied`, `citationStyle == .apa`, the fake's `styles == [.apa]`.
  - `copyingIEEERemembersIEEE` — after `copyCitation(.ieee)`, a new store on the same defaults reads `.ieee`; message `.ieeeCopied`.
  - `copyingBibTeXHasNoRichText` — html nil, message `.bibtexCopied`.
  - port the existing incomplete/failed/no-longer-saved copy tests to `copyCitation(.bibtex)` with `.citationIncomplete`/`.copyFailed`.
  - `orderedStylesPutTheRememberedOneFirst` — `.ieee` → `[.ieee, .apa, .bibtex]`; `.apa` → `[.apa, .ieee, .bibtex]`.
- [ ] **Step 3:** `-only-testing:FeaturePaperDetailsTests` → build FAILS.
- [ ] **Step 4: Implement.** `copyCitation(_:)` follows today's `copyBibTeX()` (guard `copying`, `defer`), first `styles.set(style); citationStyle = style`, then `entry(openAlexID:style:)` → `copy(CopiedText(text: result.text, html: result.html))`, message: `.citationIncomplete` if incomplete, else `.apaCopied`/`.ieeeCopied`/`.bibtexCopied`. `moreOptions` lists one `Button` per `orderedStyles(actions.citationStyle)` (labels `details.copyApa`/`details.copyIeee`/`details.copyBibtex`, icon `doc.on.doc`) above the destructive Remove. `PaperDetailsScreen` sets `actions.copyCitation = { style in Task { await viewModel.copyCitation(style) } }` and `actions.citationStyle = viewModel.citationStyle`. The banner maps the new messages to the new strings. `AppContainer`:
```swift
            copy: { copied in
                if let html = copied.html {
                    // Rich text for Word, Pages and Google Docs, with plain text for everything else.
                    UIPasteboard.general.setItems([[UTType.html.identifier: html, UTType.utf8PlainText.identifier: copied.text]])
                } else {
                    UIPasteboard.general.string = copied.text
                }
            },
```
(`import UniformTypeIdentifiers`), and pass `styles: CitationStyleStore()`.
- [ ] **Step 5:** `-only-testing:FeaturePaperDetailsTests` → PASS; build the app (Debug) once; `python3 ios/scripts/check-translations.py` passes. The UI test `CollectionsFlowTests.testCopyBibTeXFromDetailsSaysItCopied` taps "Copy BibTeX" and expects "BibTeX copied" — both remain valid; if the remembered style changes the menu order it still finds the button by label.
- [ ] **Step 6:** commit `feat(ios): copy a paper's citation in APA, IEEE or BibTeX`.

---

### Task 7: Library — export a reference list

**Files:**
- Modify: `ios/HashiyaKit/Sources/HashiyaDiagnostics/AnalyticsTracking.swift` (`ExportFormat`: `case bibtex, apa, ieee, backup`)
- Modify: `ios/HashiyaKit/Tests/HashiyaDiagnosticsTests/DiagnosticsTests.swift` (rows for `apa`, `ieee`)
- Modify: `ios/HashiyaKit/Sources/HashiyaData/ExportFiles.swift` (new `write(_:fileName:)`, `fileName(collectionName:style:)`)
- Modify: `ios/HashiyaKit/Sources/FeatureLibrary/LibraryViewModel.swift` (`export(style:)`, `citationStyle`, styles store), `LibraryView.swift` (`ExportButton` → `Menu`), `Resources/Localizable.xcstrings`
- Modify: `ios/Hashiya/AppContainer.swift` (pass `CitationStyleStore()` to the Library view model)
- Modify: `ios/HashiyaUITests/CollectionsFlowTests.swift` (the export test taps the menu, then "BibTeX (.bib)")
- Test: `ios/HashiyaKit/Tests/HashiyaDataTests/ExportFilesTests.swift`, `ios/HashiyaKit/Tests/FeatureLibraryTests/LibraryCollectionsViewModelTests.swift`, `LibraryStringsTests.swift`

**Interfaces:**
- Produces: `ExportFiles.write(_ contents: String, fileName: String) throws -> URL` (the existing `write(_:name:)` becomes `write(bibtex, fileName: name + ".bib")`); `static func fileName(collectionName: String?, style: CitationStyle) -> String` → `"Thesis.bib"`, `"Thesis – APA.rtf"`, `"hashiya-library – IEEE.rtf"`, `"collection – APA.rtf"`; `LibraryViewModel.export(style: CitationStyle) async` (replacing `export()`), `public private(set) var citationStyle`, init `styles: CitationStyleStore = CitationStyleStore()`; `func exportStyles(_ remembered: CitationStyle) -> [CitationStyle]` in `FeatureLibrary` (same order rule as Task 6).

- [ ] **Step 1: Strings** (FeatureLibrary catalog): `library.exportReferences` "Export references" / «تصدير المراجع»; `library.exportBibtex` "BibTeX (.bib)" / «BibTeX (‎.bib)»; `library.exportApa` "APA 7 (.rtf)" / «APA 7 (‎.rtf)»; `library.exportIeee` "IEEE (.rtf)" / «IEEE (‎.rtf)» (the `‎` is U+200E, written `\u200E` in the catalog); remove `library.exportBib` after updating its uses (grep main, tests, UI tests).
- [ ] **Step 2: Failing tests.**
  - `ExportFilesTests`: `fileNamesPerStyle` (the four names above plus `"a-b – APA.rtf"` for `"a/b"`); `writesTheFileNameAsGiven` (an `.rtf` name is written as is, UTF-8, earlier files deleted).
  - `LibraryCollectionsViewModelTests`: existing export tests call `export(style: .bibtex)` with unchanged expectations; add `anAPAExportSharesTheRTFAndRemembersTheStyle` (the shared URL's last path component is `"Thesis – APA.rtf"`, its contents `{\rtf1 <exportText>}`, analytics `.export(format: .apa, withPdfs: false)`, a new store on the same defaults reads `.apa`).
  - `DiagnosticsTests`: `.export(format: .apa, …)` → `"format": "apa"`, same for `ieee`.
  - `exportStylesPutTheRememberedOneFirst`.
- [ ] **Step 3:** run → build FAILS.
- [ ] **Step 4: Implement.** `export(style:)` follows today's `export()`: `styles.set(style); citationStyle = style`, `citations.export(collectionID: id, style: style)`, `exportFiles.write(result.rtf ?? result.text, fileName: ExportFiles.fileName(collectionName: name, style: style))`, and logs `.export(format: style.exportFormat, withPdfs: false)` with a private mapping (`bibtex→.bibtex`, `apa→.apa`, `ieee→.ieee`); the review prompt and incomplete banner stay. `ExportButton` becomes a `Menu` (same `square.and.arrow.up` label, accessibility label `library.exportReferences`) with one `Button` per `exportStyles(viewModel.citationStyle)` calling `Task { await viewModel.export(style:) }`; the progress view while exporting is unchanged. UI test: open the menu with `app.buttons["Export references"]`, then tap `app.buttons["BibTeX (.bib)"]`, then continue as before.
- [ ] **Step 5:** `-only-testing:HashiyaDiagnosticsTests`, `-only-testing:HashiyaDataTests`, `-only-testing:FeatureLibraryTests` → PASS; build the app; translations check passes.
- [ ] **Step 6:** commit `feat(ios): export a reference list in APA, IEEE or BibTeX`.

---

### Task 8: Docs, changelog, snapshots

- [ ] `CHANGELOG.md` `[Unreleased]` → Added: "- **iOS: APA 7 and IEEE citations.** A paper's ⋯ menu copies its citation in APA 7, IEEE or BibTeX — APA and IEEE paste with their italics into Word, Pages or Google Docs — and the Library's export saves a whole collection as an APA 7 or IEEE reference list (.rtf) as well as BibTeX. The style you used last comes first." Also change the iOS rating-prompt line's "a BibTeX export" → "a reference export".
- [ ] Spec §8: mark the iOS PR delivered.
- [ ] Commit `docs: APA 7 and IEEE citations on iOS`.
- [ ] Controller, after pushing: `bash ios/scripts/record-snapshots-on-ci.sh`; commit only baselines whose screen actually changed (revert recorder noise), checking Arabic ones.
