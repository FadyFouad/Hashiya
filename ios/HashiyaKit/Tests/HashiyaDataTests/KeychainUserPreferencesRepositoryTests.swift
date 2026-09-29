import HashiyaData
import os
import Testing

/// An in-memory Keychain.
final class InMemoryKeychainStore: KeychainStore {
    private let items = OSAllocatedUnfairLock<[String: String]>(initialState: [:])

    init(_ items: [String: String] = [:]) {
        self.items.withLock { $0 = items }
    }

    var contents: [String: String] { items.withLock { $0 } }

    func read(service: String, account: String) throws -> String? {
        items.withLock { $0["\(service)/\(account)"] }
    }

    func write(_ value: String, service: String, account: String) throws {
        items.withLock { $0["\(service)/\(account)"] = value }
    }

    func delete(service: String, account: String) throws {
        _ = items.withLock { $0.removeValue(forKey: "\(service)/\(account)") }
    }
}

struct KeychainUserPreferencesRepositoryTests {
    private let itemKey = "com.etatech.hashiya.openalex/user_api_key"

    @Test func loadsTheStoredKeyAtInit() {
        let repository = KeychainUserPreferencesRepository(keychain: InMemoryKeychainStore([itemKey: "stored"]))
        #expect(repository.userKey == "stored")
        #expect(repository.currentUserAPIKey == "stored")
    }

    @Test func startsWithNoKey() {
        #expect(KeychainUserPreferencesRepository(keychain: InMemoryKeychainStore()).userKey == nil)
    }

    @Test func savesTheKeyTrimmed() async throws {
        let keychain = InMemoryKeychainStore()
        let repository = KeychainUserPreferencesRepository(keychain: keychain)
        try await repository.setUserAPIKey("  my-key \n")

        #expect(repository.userKey == "my-key")
        #expect(keychain.contents == [itemKey: "my-key"])
    }

    @Test func aBlankKeyDeletesTheItem() async throws {
        let keychain = InMemoryKeychainStore([itemKey: "stored"])
        let repository = KeychainUserPreferencesRepository(keychain: keychain)
        try await repository.setUserAPIKey("   ")

        #expect(repository.userKey == nil)
        #expect(keychain.contents.isEmpty)
    }

    @Test func updatesYieldTheCurrentValueThenEachChange() async throws {
        let repository = KeychainUserPreferencesRepository(keychain: InMemoryKeychainStore([itemKey: "first"]))
        var updates = repository.userAPIKeyUpdates().makeAsyncIterator()
        #expect(await updates.next() == "first")

        try await repository.setUserAPIKey("second")
        #expect(await updates.next() == "second")

        try await repository.setUserAPIKey("second")
        try await repository.setUserAPIKey("")
        #expect(await updates.next() == .some(nil))
    }

    @Test func everySubscriberGetsChanges() async throws {
        let repository = KeychainUserPreferencesRepository(keychain: InMemoryKeychainStore())
        var first = repository.userAPIKeyUpdates().makeAsyncIterator()
        var second = repository.userAPIKeyUpdates().makeAsyncIterator()
        #expect(await first.next() == .some(nil))
        #expect(await second.next() == .some(nil))

        try await repository.setUserAPIKey("key")
        #expect(await first.next() == "key")
        #expect(await second.next() == "key")
    }
}
