@testable import FeatureSearch
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct SearchViewModelTests {
    private let sleeper = ManualSleeper()
    private let library = FakeLibraryRepository()
    private let preferences = FakeUserPreferencesRepository()

    private func makeViewModel(_ repository: FakeSearchRepository) -> SearchViewModel {
        SearchViewModel(repository: repository, library: library, preferences: preferences, sleep: sleeper.sleep)
    }

    /// Answers every first page with `papers` and no next page.
    private func repository(_ papers: [Paper] = SamplePapers.all, total: Int64 = 48_210) -> FakeSearchRepository {
        FakeSearchRepository(page: .of(papers, total: total))
    }

    /// Types `text` and lets the debounce elapse.
    private func type(_ text: String, into viewModel: SearchViewModel) async {
        viewModel.updateText(text)
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(300))
        await viewModel.waitForPendingWork()
    }

    @Test func aBlankQueryIsIdleWithNoRequest() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        viewModel.updateText("   ")
        viewModel.submitNow()
        await viewModel.waitForPendingWork()

        #expect(viewModel.phase == .idle)
        #expect(repository.calls.isEmpty)
    }

    @Test func typingWaitsForThePauseThenSearchesOnce() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        viewModel.updateText("b")
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(200))
        viewModel.updateText("bert")
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(299))
        #expect(repository.calls.isEmpty)
        #expect(viewModel.phase == .idle)

        sleeper.advance(by: .milliseconds(1))
        await viewModel.waitForPendingWork()

        #expect(repository.calls == [.init(query: SearchQuery(text: "bert"), cursor: nil)])
        #expect(viewModel.phase == .results)
        #expect(viewModel.papers == SamplePapers.all)
    }

    @Test func theSearchKeySkipsTheDebounce() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        viewModel.updateText("bert")
        viewModel.submitNow()
        await viewModel.waitForPendingWork()

        #expect(repository.calls.map(\.query.text) == ["bert"])
        #expect(sleeper.pendingCount == 0)
    }

    @Test func aSuggestionFillsTheFieldAndSubmitsAtOnce() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        viewModel.applySuggestion("CRISPR")
        await viewModel.waitForPendingWork()

        #expect(viewModel.text == "CRISPR")
        #expect(repository.calls.map(\.query.text) == ["CRISPR"])
    }

    @Test func theSubmittedTextIsTrimmed() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        await type("  bert \n", into: viewModel)

        #expect(viewModel.text == "  bert \n")
        #expect(viewModel.query.text == "bert")
        #expect(repository.calls.map(\.query.text) == ["bert"])
    }

    @Test func theSameQueryAgainDoesNothing() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        await type("bert", into: viewModel)
        await type("bert ", into: viewModel)

        #expect(repository.calls.count == 1)
    }

    @Test func chipChangesApplyAtOnce() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        await type("bert", into: viewModel)

        viewModel.setSort(.mostCited)
        await viewModel.waitForPendingWork()
        viewModel.setYears(.since(2020))
        await viewModel.waitForPendingWork()
        viewModel.setOpenAccessOnly(true)
        await viewModel.waitForPendingWork()

        #expect(repository.calls.map(\.query) == [
            SearchQuery(text: "bert"),
            SearchQuery(text: "bert", sort: .mostCited),
            SearchQuery(text: "bert", sort: .mostCited, years: .since(2020)),
            SearchQuery(text: "bert", sort: .mostCited, years: .since(2020), openAccessOnly: true),
        ])
        #expect(sleeper.pendingCount == 0)
    }

    @Test func chipChangesWhileIdleOnlyChangeTheChips() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        viewModel.setSort(.newest)
        await viewModel.waitForPendingWork()

        #expect(viewModel.query.sort == .newest)
        #expect(repository.calls.isEmpty)
        #expect(viewModel.phase == .idle)
    }

    @Test func clearFiltersKeepsTheSort() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        await type("bert", into: viewModel)
        viewModel.setSort(.newest)
        viewModel.setYears(.between(from: 2015, to: 2020))
        viewModel.setOpenAccessOnly(true)
        viewModel.clearFilters()
        await viewModel.waitForPendingWork()

        #expect(viewModel.query == SearchQuery(text: "bert", sort: .newest))
        #expect(repository.calls.last?.query == SearchQuery(text: "bert", sort: .newest))
    }

    @Test func clearingTheTextIsIdleAtOnce() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        await type("bert", into: viewModel)
        viewModel.updateText("")

        #expect(viewModel.phase == .idle)
        #expect(viewModel.papers.isEmpty)
        #expect(viewModel.totalCount == nil)
        #expect(sleeper.pendingCount == 0)
    }

    @Test func exposesTheTotalCount() async {
        let viewModel = makeViewModel(repository(total: 48_210))
        #expect(viewModel.totalCount == nil)
        await type("bert", into: viewModel)

        #expect(viewModel.totalCount == 48_210)
    }

    @Test func aPageWithNoPapersIsEmpty() async {
        let viewModel = makeViewModel(repository([], total: 0))
        await type("zzzz", into: viewModel)

        #expect(viewModel.phase == .empty)
    }

    @Test func aFirstPageErrorFailsAndRetryReruns() async {
        let repository = FakeSearchRepository { _, _ in throw SearchError.offline }
        let viewModel = makeViewModel(repository)
        await type("bert", into: viewModel)
        #expect(viewModel.phase == .failed(.offline))

        repository.setHandler { _, _ in .of(SamplePapers.all) }
        viewModel.retry()
        await viewModel.waitForPendingWork()

        #expect(viewModel.phase == .results)
        #expect(repository.calls.count == 2)
    }

    @Test func aSlowEarlierSearchNeverOverwritesANewerOne() async {
        let slow = AsyncGate()
        let repository = FakeSearchRepository { query, _ in
            if query.text == "bert" {
                await slow.wait()
                return .of([SamplePapers.bert], total: 1)
            }
            return .of([SamplePapers.vit], total: 7)
        }
        let viewModel = makeViewModel(repository)
        viewModel.updateText("bert")
        viewModel.submitNow()
        viewModel.updateText("vit")
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
        slow.open()
        await Task.yield()
        await viewModel.waitForPendingWork()

        #expect(viewModel.papers == [SamplePapers.vit])
        #expect(viewModel.totalCount == 7)
        #expect(viewModel.phase == .results)
        #expect(repository.calls.map(\.query.text) == ["vit"])
    }

    @Test func aSearchAlreadyInFlightCannotOverwriteANewerOne() async {
        let slow = AsyncGate()
        let repository = FakeSearchRepository { query, _ in
            if query.text == "bert" {
                await slow.wait()
                return .of([SamplePapers.bert], total: 1)
            }
            return .of([SamplePapers.vit], total: 7)
        }
        let viewModel = makeViewModel(repository)
        viewModel.updateText("bert")
        viewModel.submitNow()
        #expect(await eventually { repository.calls.count == 1 })
        viewModel.updateText("vit")
        viewModel.submitNow()
        #expect(await eventually { viewModel.phase == .results })

        slow.open()
        await viewModel.waitForPendingWork()

        #expect(repository.calls.map(\.query.text) == ["bert", "vit"])
        #expect(viewModel.papers == [SamplePapers.vit])
        #expect(viewModel.totalCount == 7)
        #expect(viewModel.phase == .results)
    }

    @Test func supersededSearchesAreForgottenOnceTheyFinish() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        viewModel.updateText("bert")
        viewModel.submitNow()
        #expect(await eventually { viewModel.phase == .results })
        viewModel.updateText("vit")
        viewModel.submitNow()
        viewModel.updateText("attention")
        viewModel.submitNow()
        #expect(await eventually { viewModel.phase == .results })

        #expect(repository.calls.map(\.query.text) == ["bert", "attention"])
        #expect(await eventually { viewModel.supersededSearchCount == 0 })
    }

    @Test func aNextPageInFlightCannotLandAfterTheChipsChange() async {
        let slow = AsyncGate()
        let repository = FakeSearchRepository { query, cursor in
            if cursor == "c2" {
                await slow.wait()
                return .of([SamplePapers.bert], total: 2, next: nil)
            }
            return query.sort == .newest
                ? .of([SamplePapers.vit], total: 1)
                : .of([SamplePapers.attention], total: 2, next: "c2")
        }
        let viewModel = makeViewModel(repository)
        await type("transformers", into: viewModel)
        viewModel.loadMore()
        #expect(await eventually { repository.calls.count == 2 })
        viewModel.setSort(.newest)
        #expect(await eventually { viewModel.phase == .results && viewModel.papers == [SamplePapers.vit] })

        slow.open()
        await viewModel.waitForPendingWork()

        #expect(viewModel.papers == [SamplePapers.vit])
        #expect(viewModel.append == .endReached)
    }

    @Test func nextPagesAppendUntilTheEnd() async {
        let repository = FakeSearchRepository { _, cursor in
            switch cursor {
            case nil: .of([SamplePapers.attention], total: 3, next: "c2")
            case "c2": .of([SamplePapers.bert], total: 3, next: "c3")
            default: .of([SamplePapers.vit], total: 3, next: nil)
            }
        }
        let viewModel = makeViewModel(repository)
        await type("transformers", into: viewModel)
        #expect(viewModel.append == .idle)

        viewModel.loadMore()
        await viewModel.waitForPendingWork()
        viewModel.loadMore()
        await viewModel.waitForPendingWork()
        viewModel.loadMore()
        await viewModel.waitForPendingWork()

        #expect(viewModel.papers == SamplePapers.all)
        #expect(repository.calls.map(\.cursor) == [nil, "c2", "c3"])
        #expect(viewModel.append == .endReached)
    }

    @Test func duplicatesAcrossPagesAreDropped() async {
        let repository = FakeSearchRepository { _, cursor in
            cursor == nil
                ? .of([SamplePapers.attention, SamplePapers.bert], total: 3, next: "c2")
                : .of([SamplePapers.bert, SamplePapers.vit, SamplePapers.vit], total: 3, next: nil)
        }
        let viewModel = makeViewModel(repository)
        await type("transformers", into: viewModel)
        viewModel.loadMore()
        await viewModel.waitForPendingWork()

        #expect(viewModel.papers.map(\.openAlexID) == SamplePapers.all.map(\.openAlexID))
    }

    @Test func aPageOfOnlyDuplicatesLoadsTheNextOneAtOnce() async {
        let repository = FakeSearchRepository { _, cursor in
            switch cursor {
            case nil: .of([SamplePapers.attention, SamplePapers.bert], total: 3, next: "c2")
            case "c2": .of([SamplePapers.bert], total: 3, next: "c3")
            default: .of([SamplePapers.vit], total: 3, next: nil)
            }
        }
        let viewModel = makeViewModel(repository)
        await type("transformers", into: viewModel)
        viewModel.loadMore()
        await viewModel.waitForPendingWork()

        #expect(repository.calls.map(\.cursor) == [nil, "c2", "c3"])
        #expect(viewModel.papers.map(\.openAlexID) == SamplePapers.all.map(\.openAlexID))
        #expect(viewModel.append == .endReached)
    }

    @Test func skippingDuplicatePagesStopsAfterThree() async {
        let repository = FakeSearchRepository { _, cursor in
            switch cursor {
            case nil: .of([SamplePapers.attention], total: 2, next: "c2")
            case "c2": .of([SamplePapers.attention], total: 2, next: "c3")
            case "c3": .of([SamplePapers.attention], total: 2, next: "c4")
            case "c4": .of([SamplePapers.attention], total: 2, next: "c5")
            default: .of([SamplePapers.bert], total: 2, next: nil)
            }
        }
        let viewModel = makeViewModel(repository)
        await type("transformers", into: viewModel)
        viewModel.loadMore()
        await viewModel.waitForPendingWork()

        #expect(repository.calls.map(\.cursor) == [nil, "c2", "c3", "c4"])
        #expect(viewModel.papers == [SamplePapers.attention])
        #expect(viewModel.append == .idle)

        viewModel.loadMore()
        await viewModel.waitForPendingWork()

        #expect(repository.calls.map(\.cursor) == [nil, "c2", "c3", "c4", "c5"])
        #expect(viewModel.papers == [SamplePapers.attention, SamplePapers.bert])
        #expect(viewModel.append == .endReached)
    }

    @Test func anAppendFailureKeepsResultsAndRetryReloadsTheSameCursor() async {
        let repository = FakeSearchRepository { _, cursor in
            if cursor == "c2" { throw SearchError.rateLimited }
            return .of([SamplePapers.attention], total: 2, next: "c2")
        }
        let viewModel = makeViewModel(repository)
        await type("attention", into: viewModel)
        viewModel.loadMore()
        await viewModel.waitForPendingWork()

        #expect(viewModel.append == .failed(.rateLimited))
        #expect(viewModel.papers == [SamplePapers.attention])
        #expect(viewModel.phase == .results)

        viewModel.loadMore()
        await viewModel.waitForPendingWork()
        #expect(repository.calls.count == 2)

        repository.setHandler { _, _ in .of([SamplePapers.bert], total: 2, next: nil) }
        viewModel.retryAppend()
        await viewModel.waitForPendingWork()

        #expect(repository.calls.map(\.cursor) == [nil, "c2", "c2"])
        #expect(viewModel.papers == [SamplePapers.attention, SamplePapers.bert])
        #expect(viewModel.append == .endReached)
    }

    @Test func anAPIKeyChangeRerunsTheActiveSearch() async throws {
        let repository = FakeSearchRepository { _, _ in throw SearchError.invalidUserKey }
        let viewModel = makeViewModel(repository)
        await type("bert", into: viewModel)
        #expect(viewModel.phase == .failed(.invalidUserKey))

        repository.setHandler { _, _ in .of(SamplePapers.all) }
        try await preferences.setUserAPIKey("fixed-key")

        #expect(await eventually { viewModel.phase == .results })
        #expect(repository.calls.count == 2)
    }

    @Test func anAPIKeyChangeWhileIdleDoesNothing() async throws {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        try await preferences.setUserAPIKey("new-key")
        try await preferences.setUserAPIKey("")
        await viewModel.waitForPendingWork()
        try await Task.sleep(for: .milliseconds(50))

        #expect(repository.calls.isEmpty)
        #expect(viewModel.phase == .idle)
    }

    @Test func savedIDsComeFromTheLibraryNotThePapers() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.bert])
        let repository = repository()
        let viewModel = SearchViewModel(repository: repository, library: library, preferences: preferences, sleep: sleeper.sleep)
        await type("transformers", into: viewModel)

        #expect(await eventually { viewModel.savedIDs == [SamplePapers.bert.openAlexID] })
        #expect(viewModel.isSaved(SamplePapers.bert))
        #expect(!viewModel.isSaved(SamplePapers.attention))
        #expect(viewModel.papers == SamplePapers.all)
    }

    @Test func savingUpdatesTheBadgeWithoutANewSearch() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        await type("transformers", into: viewModel)
        await viewModel.toggleSave(SamplePapers.vit)

        #expect(await eventually { viewModel.isSaved(SamplePapers.vit) })
        #expect(library.savedPapers == [SamplePapers.vit])
        #expect(repository.calls.count == 1)
    }

    @Test func toggleSaveThenRemove() async {
        let viewModel = makeViewModel(repository())
        await viewModel.toggleSave(SamplePapers.attention)
        #expect(await eventually { viewModel.isSaved(SamplePapers.attention) })

        await viewModel.toggleSave(SamplePapers.attention)
        #expect(await eventually { !viewModel.isSaved(SamplePapers.attention) })
        #expect(library.savedPapers.isEmpty)
    }

    @Test func aSaveFailureShowsItsMessageOnce() async {
        library.setFailSaves(true)
        let viewModel = makeViewModel(repository())
        await viewModel.toggleSave(SamplePapers.attention)
        #expect(viewModel.message == .saveFailed)

        viewModel.message = nil
        try? await Task.sleep(for: .milliseconds(20))
        #expect(viewModel.message == nil)
        #expect(!viewModel.isSaved(SamplePapers.attention))
    }

    @Test func aRemoveFailureShowsItsMessage() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        library.setFailRemoves(true)
        let viewModel = SearchViewModel(repository: repository(), library: library, preferences: preferences, sleep: sleeper.sleep)
        #expect(await eventually { viewModel.isSaved(SamplePapers.attention) })

        await viewModel.toggleSave(SamplePapers.attention)

        #expect(viewModel.message == .removeFailed)
        #expect(library.savedPapers == [SamplePapers.attention])
    }

    @Test func restoringSubmitsTheTextAndChipsAtOnce() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        let chips = SearchSceneState(sort: "mostCited", yearKind: "since", yearFrom: 2020, yearTo: nil, openAccess: true)
        viewModel.restore(text: "bert", query: chips.query(text: ""))
        await viewModel.waitForPendingWork()

        let expected = SearchQuery(text: "bert", sort: .mostCited, years: .since(2020), openAccessOnly: true)
        #expect(viewModel.text == "bert")
        #expect(viewModel.query == expected)
        #expect(repository.calls.map(\.query) == [expected])
        #expect(sleeper.pendingCount == 0)

        viewModel.restore(text: "other", query: SearchQuery(text: ""))
        #expect(viewModel.text == "bert")
    }

    @Test func restoringNothingKeepsTheCurrentState() async {
        let repository = repository()
        let viewModel = makeViewModel(repository)
        await type("bert", into: viewModel)
        viewModel.restore(text: "", query: SearchQuery(text: ""))
        await viewModel.waitForPendingWork()

        #expect(viewModel.phase == .results)
        #expect(viewModel.text == "bert")
        #expect(repository.calls.count == 1)
    }
}

struct SearchSceneStateTests {
    @Test(arguments: [
        SearchQuery(text: "bert"),
        SearchQuery(text: "bert", sort: .mostCited, years: .since(2020), openAccessOnly: true),
        SearchQuery(text: "bert", sort: .newest, years: .between(from: 2015, to: 2020)),
    ])
    func roundTripsTheChips(query: SearchQuery) {
        #expect(SearchSceneState(query).query(text: "bert") == query)
    }

    @Test func storesTheAndroidValues() {
        let state = SearchSceneState(SearchQuery(text: "", sort: .mostCited, years: .between(from: 2015, to: 2020), openAccessOnly: true))
        #expect(state == SearchSceneState(sort: "mostCited", yearKind: "between", yearFrom: 2015, yearTo: 2020, openAccess: true))
        #expect(SearchSceneState(SearchQuery(text: "", years: .since(2024))).yearFrom == 2024)
        #expect(SearchSceneState(SearchQuery(text: "")).yearKind == nil)
    }

    @Test func invalidValuesFallBackToDefaults() {
        let unknownSort = SearchSceneState(sort: "MostCited", yearKind: nil, yearFrom: nil, yearTo: nil, openAccess: false)
        #expect(unknownSort.query(text: "") == SearchQuery(text: ""))

        let reversed = SearchSceneState(sort: "newest", yearKind: "between", yearFrom: 2021, yearTo: 2020, openAccess: false)
        #expect(reversed.query(text: "").years == .anyTime)

        let missingYear = SearchSceneState(sort: "relevance", yearKind: "since", yearFrom: nil, yearTo: nil, openAccess: false)
        #expect(missingYear.query(text: "").years == .anyTime)

        let unknownKind = SearchSceneState(sort: "relevance", yearKind: "decade", yearFrom: 2010, yearTo: 2019, openAccess: false)
        #expect(unknownKind.query(text: "").years == .anyTime)
    }
}
