import Foundation
import HashiyaData
import HashiyaModel
import Observation
import os

@Observable
@MainActor
public final class SettingsViewModel {
    /// True while a key of the user's own is stored.
    public internal(set) var usingUserKey = false
    /// The space PDFs use, once `loadStorage()` has run.
    public internal(set) var storage: PdfStorage?

    @ObservationIgnored private let pdfs: any PdfRepository
    @ObservationIgnored private let preferences: any UserPreferencesRepository
    @ObservationIgnored private let observations = TaskBag()
    private var storedKey: String?
    private var editedKey: String?

    public init(preferences: any UserPreferencesRepository, pdfs: any PdfRepository) {
        self.pdfs = pdfs
        self.preferences = preferences
        // Seeded at once, so Settings never opens on "Using built-in key" while the stream starts.
        // Only written here, never read: SwiftUI creates this inside the sheet's observation scope, and a read
        // would make the sheet rebuild the view model (losing the typed key) on every stored-key update.
        let key = preferences.currentUserAPIKey
        storedKey = key
        usingUserKey = key != nil
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

    /// Reads the storage totals. The view calls this when Settings opens.
    public func loadStorage() async {
        do {
            storage = try await pdfs.storage()
        } catch {
            #if DEBUG
            Logger(subsystem: "com.etatech.hashiya", category: "settings").error("Reading the PDF storage failed")
            #endif
        }
    }

    /// Deletes every downloaded PDF (attached ones stay), then reads the totals again.
    public func deleteDownloaded() async {
        do {
            try await pdfs.deleteDownloaded()
        } catch {
            #if DEBUG
            Logger(subsystem: "com.etatech.hashiya", category: "settings").error("Deleting downloaded PDFs failed")
            #endif
        }
        await loadStorage()
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
