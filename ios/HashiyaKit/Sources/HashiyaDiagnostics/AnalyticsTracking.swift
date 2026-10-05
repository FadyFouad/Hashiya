import os

/// Counts how features are used. Only the app target knows the service behind it. Events, parameters and their values
/// are closed lists, so nothing a person typed or read can be attached.
public protocol AnalyticsTracking: Sendable {
    func log(_ event: AnalyticsEvent)
    func setProperty(_ property: AnalyticsProperty, _ value: some ClosedValue)
    /// Starts or stops collection. Stopping also clears the analytics id and events not yet sent.
    func setEnabled(_ enabled: Bool)
}

/// Debug builds, extensions and tests: counts nothing.
public struct NoAnalytics: AnalyticsTracking {
    public init() {}
    public func log(_ event: AnalyticsEvent) {}
    public func setProperty(_ property: AnalyticsProperty, _ value: some ClosedValue) {}
    public func setEnabled(_ enabled: Bool) {}
}

/// The usage-statistics switch and the last value of each user property, for a service that forgets its user properties
/// when collection stops: turning collection on again sends them again. Nothing is sent while it is off.
public final class AnalyticsSwitch: Sendable {
    private struct State {
        var isOn = false
        var values: [AnalyticsProperty: String] = [:]
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    public init() {}

    public var isOn: Bool { state.withLock { $0.isOn } }

    /// Remembers `value` and, while collection is on, passes it to `send`.
    public func setProperty(_ property: AnalyticsProperty, _ value: some ClosedValue, send: (AnalyticsProperty, String) -> Void) {
        // `send` runs under the lock, so a switch turned off meanwhile never sees a value sent after it.
        state.withLockUnchecked { state in
            state.values[property] = value.rawValue
            if state.isOn { send(property, value.rawValue) }
        }
    }

    /// Passes `isOn` to `apply`, which switches the service; turning it on then passes every remembered value to `send`.
    public func setEnabled(_ isOn: Bool, apply: (Bool) -> Void, send: (AnalyticsProperty, String) -> Void) {
        state.withLockUnchecked { state in
            state.isOn = isOn
            apply(isOn)
            guard isOn else { return }
            for (property, value) in state.values { send(property, value) }
        }
    }
}

public enum AnalyticsProperty: String, CaseIterable, Sendable {
    case librarySizeBucket = "library_size_bucket"
    case language
    case hasOwnKey = "has_own_key"
}

public enum Screen: String, CaseIterable, Sendable, ClosedValue {
    case library, search, details, reader, settings, restore, export
}

public enum SearchKind: String, Sendable { case keyword, doi, arxiv, link }
public enum SearchRoute: String, Sendable { case user, shared, keyless, cached }
public enum LimitKind: String, Sendable { case daily, pageCap = "page_cap" }
public enum SaveSource: String, Sendable { case search, lookup, share }
public enum ExportFormat: String, Sendable { case bibtex, backup }
public enum PdfOrigin: String, Sendable { case downloaded, attached }

public enum ResultsBucket: String, Sendable {
    case zero = "0", upTo25 = "1-25", upTo200 = "26-200", over200 = "200+"

    public init(count: Int64) {
        switch count {
        case ...0: self = .zero
        case ...25: self = .upTo25
        case ...200: self = .upTo200
        default: self = .over200
        }
    }
}

/// The research area of a keyword search, worked out on the device from the results' OpenAlex topics (see
/// `ResearchCategory.classify`). Never from the query.
public enum ResearchCategory: String, CaseIterable, Sendable {
    case ai, computerVision = "computer_vision", theory, networks, systems, software, hci
    case informationSystems = "information_systems", graphics, signalProcessing = "signal_processing"
    case csOther = "cs_other", mathematics, engineering, physicalSciences = "physical_sciences"
    case lifeSciences = "life_sciences", socialSciences = "social_sciences", healthSciences = "health_sciences"
    case unknown
}

public enum AnalyticsEvent: Equatable, Sendable {
    case search(kind: SearchKind, hasFilters: Bool, route: SearchRoute, results: ResultsBucket, category: ResearchCategory?)
    /// `page` 2…40; larger values are sent as 40.
    case searchMore(page: Int)
    case searchLimitReached(LimitKind)
    case paperSaved(from: SaveSource)
    case paperRemoved
    case noteEdited
    case collectionCreated
    case paperAddedToCollection
    case export(format: ExportFormat, withPdfs: Bool)
    case restore(succeeded: Bool)
    case pdfOpened(source: PdfOrigin)
    case pdfDownloaded(succeeded: Bool)
    case screenView(Screen)

    public var name: String {
        switch self {
        case .search: "search"
        case .searchMore: "search_more"
        case .searchLimitReached: "search_limit_reached"
        case .paperSaved: "paper_saved"
        case .paperRemoved: "paper_removed"
        case .noteEdited: "note_edited"
        case .collectionCreated: "collection_created"
        case .paperAddedToCollection: "paper_added_to_collection"
        case .export: "export"
        case .restore: "restore"
        case .pdfOpened: "pdf_opened"
        case .pdfDownloaded: "pdf_downloaded"
        case .screenView: "screen_view"
        }
    }

    public var parameters: [String: String] {
        switch self {
        case let .search(kind, hasFilters, route, results, category):
            var parameters = ["kind": kind.rawValue, "has_filters": yesNo(hasFilters), "route": route.rawValue, "results_bucket": results.rawValue]
            if let category { parameters["category"] = category.rawValue }
            return parameters
        case let .searchMore(page): return ["page": String(min(max(page, 2), 40))]
        case let .searchLimitReached(kind): return ["kind": kind.rawValue]
        case let .paperSaved(from): return ["from": from.rawValue]
        case .paperRemoved, .noteEdited, .collectionCreated, .paperAddedToCollection: return [:]
        case let .export(format, withPdfs): return ["format": format.rawValue, "with_pdfs": yesNo(withPdfs)]
        case let .restore(succeeded): return ["result": succeeded ? "ok" : "failed"]
        case let .pdfOpened(source): return ["source": source.rawValue]
        case let .pdfDownloaded(succeeded): return ["result": succeeded ? "ok" : "failed"]
        case let .screenView(screen): return ["screen": screen.rawValue]
        }
    }
}

private func yesNo(_ value: Bool) -> String { value ? "yes" : "no" }
