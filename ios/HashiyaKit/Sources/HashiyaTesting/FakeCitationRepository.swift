import Foundation
import HashiyaData
import HashiyaModel
import os

/// Scripted citations, recording every call. Exports can be held, so a test can see one in progress.
public final class FakeCitationRepository: CitationRepository {
    public struct Failure: Error {}

    public static let sampleEntry = CitationResult(text: "@article{k,\n}\n", complete: true)

    private struct State {
        var entry: CitationResult?
        var export: CitationResult
        var fail = false
        /// Non-nil while exports are held: the waiting exports.
        var heldExports: [CheckedContinuation<Void, Never>]?
        var exportCalls: [Int64?] = []
        var entryCalls: [String] = []
        var styles: [CitationStyle] = []
    }

    private let state: OSAllocatedUnfairLock<State>

    /// - Parameters:
    ///   - entry: what every `entry(openAlexID:)` returns (nil: not saved).
    ///   - export: what every `export(collectionID:)` returns.
    public init(entry: CitationResult? = FakeCitationRepository.sampleEntry, export: CitationResult = FakeCitationRepository.sampleEntry) {
        state = OSAllocatedUnfairLock(initialState: State(entry: entry, export: export))
    }

    /// Every `export` call's collection, in order, recorded when it starts.
    public var exportCalls: [Int64?] { state.withLock { $0.exportCalls } }
    /// Every `entry` call's OpenAlex ID, in order.
    public var entryCalls: [String] { state.withLock { $0.entryCalls } }
    /// Every call's style, `entry` and `export` alike, in order.
    public var styles: [CitationStyle] { state.withLock { $0.styles } }
    /// The exports waiting while exports are held.
    public var heldExports: Int { state.withLock { $0.heldExports?.count ?? 0 } }

    /// When true, `entry` and `export` throw `Failure`.
    public func setFail(_ fail: Bool) { state.withLock { $0.fail = fail } }
    public func setEntry(_ entry: CitationResult?) { state.withLock { $0.entry = entry } }
    public func setExport(_ export: CitationResult) { state.withLock { $0.export = export } }

    /// From now on `export` waits until `releaseExports()`.
    public func holdExports() {
        state.withLock { if $0.heldExports == nil { $0.heldExports = [] } }
    }

    /// Lets every held export run, in order, and stops holding.
    public func releaseExports() {
        let waiting = state.withLock { state -> [CheckedContinuation<Void, Never>] in
            defer { state.heldExports = nil }
            return state.heldExports ?? []
        }
        waiting.forEach { $0.resume() }
    }

    public func entry(openAlexID: String, style: CitationStyle) async throws -> CitationResult? {
        try state.withLock { state in
            state.entryCalls.append(openAlexID)
            state.styles.append(style)
            if state.fail { throw Failure() }
            guard var result = state.entry else { return nil }
            if style != .bibtex { result.html = "<i>\(result.text)</i>" }
            return result
        }
    }

    public func export(collectionID: Int64?, style: CitationStyle) async throws -> CitationResult {
        state.withLock {
            $0.exportCalls.append(collectionID)
            $0.styles.append(style)
        }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let held = state.withLock { state -> Bool in
                guard state.heldExports != nil else { return false }
                state.heldExports?.append(continuation)
                return true
            }
            if !held { continuation.resume() }
        }
        return try state.withLock { state in
            if state.fail { throw Failure() }
            var result = state.export
            if style != .bibtex { result.rtf = "{\\rtf1 \(result.text)}" }
            return result
        }
    }
}
