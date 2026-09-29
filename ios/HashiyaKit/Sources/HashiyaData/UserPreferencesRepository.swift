import Foundation
import HashiyaNetwork
import os
import Security

public protocol UserPreferencesRepository: Sendable {
    /// The stored user key (nil = none, the built-in key is used), then each change.
    func userAPIKeyUpdates() -> AsyncStream<String?>
    /// Stores the key, trimmed. A blank key removes it.
    func setUserAPIKey(_ key: String) async throws
}

/// A small Keychain interface, so the repository can be tested with an in-memory fake.
public protocol KeychainStore: Sendable {
    func read(service: String, account: String) throws -> String?
    func write(_ value: String, service: String, account: String) throws
    func delete(service: String, account: String) throws
}

/// The user's OpenAlex key in the Keychain. The current value is kept in memory (loaded at init),
/// so the OpenAlex client reads it without touching the Keychain on every request. Never logged.
public final class KeychainUserPreferencesRepository: UserPreferencesRepository, UserAPIKeySource {
    public static let service = "com.etatech.hashiya.openalex"
    public static let account = "user_api_key"

    private struct State {
        var key: String?
        var continuations: [UUID: AsyncStream<String?>.Continuation] = [:]
    }

    private let keychain: any KeychainStore
    private let state: OSAllocatedUnfairLock<State>

    public init(keychain: any KeychainStore) {
        self.keychain = keychain
        let stored = (try? keychain.read(service: Self.service, account: Self.account)) ?? nil
        state = OSAllocatedUnfairLock(initialState: State(key: stored))
    }

    public var userKey: String? {
        state.withLock { $0.key }
    }

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
        if trimmed.isEmpty {
            try keychain.delete(service: Self.service, account: Self.account)
        } else {
            try keychain.write(trimmed, service: Self.service, account: Self.account)
        }
        let newKey: String? = trimmed.isEmpty ? nil : trimmed
        state.withLock { state in
            guard state.key != newKey else { return }
            state.key = newKey
            for continuation in state.continuations.values {
                continuation.yield(newKey)
            }
        }
    }
}

/// A generic-password Keychain item, accessible after first unlock, in `accessGroup` when given.
public struct SystemKeychainStore: KeychainStore {
    public struct Failure: Error, Equatable {
        public let status: OSStatus
    }

    public let accessGroup: String?

    public init(accessGroup: String?) {
        self.accessGroup = accessGroup
    }

    public func read(service: String, account: String) throws -> String? {
        var query = baseQuery(service: service, account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw Failure(status: status) }
        return String(decoding: data, as: UTF8.self)
    }

    public func write(_ value: String, service: String, account: String) throws {
        let data = Data(value.utf8)
        let query = baseQuery(service: service, account: account)
        let update = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if update == errSecSuccess { return }
        guard update == errSecItemNotFound else { throw Failure(status: update) }
        var item = query
        item[kSecValueData as String] = data
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        let add = SecItemAdd(item as CFDictionary, nil)
        guard add == errSecSuccess else { throw Failure(status: add) }
    }

    public func delete(service: String, account: String) throws {
        let status = SecItemDelete(baseQuery(service: service, account: account) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw Failure(status: status) }
    }

    private func baseQuery(service: String, account: String) -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        if let accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        return query
    }
}
