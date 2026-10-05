import Foundation

/// Reports crashes' context and non-fatal failures. Only the app target knows the service behind it. Keys and sites are
/// closed lists, so free text — titles, searches, notes, paths — can't be attached by accident.
public protocol CrashReporting: Sendable {
    /// Starts or stops collection. Stopping also drops reports not yet sent.
    func setEnabled(_ enabled: Bool)
    func setKey(_ key: CrashKey, _ value: String)
    /// Reports `error` as `ReportedError` would describe it: never its message or user info.
    func record(_ error: any Error, site: CrashSite)
}

/// Debug builds, extensions and tests: reports nothing.
public struct NoCrashReporting: CrashReporting {
    public init() {}
    public func setEnabled(_ enabled: Bool) {}
    public func setKey(_ key: CrashKey, _ value: String) {}
    public func record(_ error: any Error, site: CrashSite) {}
}

public enum CrashKey: String, CaseIterable, Sendable {
    case screen, language, librarySizeBucket, backupInProgress
}

public enum CrashSite: String, CaseIterable, Sendable {
    case migration, databaseOpen, restore, export, pdfStore, unexpectedUiError
}

/// What a non-fatal report may carry: where it happened and what kind of error it was. Messages, user info and
/// associated values can hold titles, searches or file paths, so they never get here.
public struct ReportedError: Equatable, Sendable, CustomStringConvertible {
    public let site: CrashSite
    /// The error's Swift type name, e.g. "HashiyaData.BackupError" or "NSError".
    public let type: String
    public let domain: String
    public let code: Int

    public init(site: CrashSite, type: String, domain: String, code: Int) {
        self.site = site
        self.type = type
        self.domain = domain
        self.code = code
    }

    public init(error: any Error, site: CrashSite) {
        let bridged = error as NSError
        self.init(site: site, type: String(reflecting: Swift.type(of: error)), domain: bridged.domain, code: bridged.code)
    }

    public var description: String { "\(site.rawValue): \(type) \(domain) \(code)" }
}

/// The library's size, coarse enough to say nothing about a person.
public func librarySizeBucket(_ papers: Int) -> String {
    switch papers {
    case ...0: "0"
    case ...50: "1-50"
    case ...500: "51-500"
    case ...5000: "501-5000"
    default: "5000+"
    }
}

/// en, ar or system: the closed values the language key and property allow.
public func languageKey(_ code: String) -> String {
    switch code {
    case "en": "en"
    case "ar": "ar"
    default: "system"
    }
}
