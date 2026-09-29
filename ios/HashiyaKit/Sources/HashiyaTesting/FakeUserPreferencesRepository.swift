import Foundation
import HashiyaData
import os

/// An in-memory user key with the real repository's rules: trimmed, blank removes it.
public final class FakeUserPreferencesRepository: UserPreferencesRepository {
    private struct State {
        var key: String?
        var continuations: [UUID: AsyncStream<String?>.Continuation] = [:]
    }

    private let state: OSAllocatedUnfairLock<State>

    public init(key: String? = nil) {
        state = OSAllocatedUnfairLock(initialState: State(key: key))
    }

    public var key: String? { state.withLock { $0.key } }

    public func userAPIKeyUpdates() -> AsyncStream<String?> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.continuations[id] = continuation
                continuation.yield(state.key)
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLock { $0.continuations.removeValue(forKey: id) }
            }
        }
    }

    public func setUserAPIKey(_ key: String) async throws {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        let newKey: String? = trimmed.isEmpty ? nil : trimmed
        state.withLock { state in
            guard state.key != newKey else { return }
            state.key = newKey
            state.continuations.values.forEach { $0.yield(newKey) }
        }
    }
}
