import Foundation
import HashiyaData
import Observation
import os

@Observable
@MainActor
public final class SettingsViewModel {
    /// True while a key of the user's own is stored.
    public internal(set) var usingUserKey = false

    @ObservationIgnored private let preferences: any UserPreferencesRepository
    @ObservationIgnored private let observations = TaskBag()
    private var storedKey: String?
    private var editedKey: String?

    public init(preferences: any UserPreferencesRepository) {
        self.preferences = preferences
        // Seeded at once, so Settings never opens on "Using built-in key" while the stream starts.
        storedKey = preferences.currentUserAPIKey
        usingUserKey = storedKey != nil
        observations.add(Task { [weak self] in
            for await key in preferences.userAPIKeyUpdates() {
                guard let self else { return }
                self.storedKey = key
                self.usingUserKey = key != nil
            }
        })
    }

    /// The stored key until the user edits the field, then the edited text.
    public var keyInput: String {
        get { editedKey ?? storedKey ?? "" }
        set { editedKey = newValue }
    }

    /// Stores the field (trimmed; blank goes back to the built-in key) and ends the edit.
    public func save() async {
        await store(keyInput)
    }

    /// Removes the stored key and ends the edit.
    public func reset() async {
        await store("")
    }

    private func store(_ key: String) async {
        do {
            try await preferences.setUserAPIKey(key)
            editedKey = nil
        } catch {
            #if DEBUG
            Logger(subsystem: "com.etatech.hashiya", category: "settings").error("Saving the API key failed")
            #endif
        }
    }
}
