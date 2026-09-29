@testable import FeatureSettings
import HashiyaTesting
import Observation
import os
import Testing

@MainActor
struct SettingsViewModelTests {
    @Test func startsOnTheBuiltInKey() async {
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository())
        try? await Task.sleep(for: .milliseconds(20))
        #expect(!viewModel.usingUserKey)
        #expect(viewModel.keyInput == "")
    }

    @Test func showsTheStoredKey() async {
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(key: "stored-key"))
        #expect(await eventually { viewModel.usingUserKey })
        #expect(viewModel.keyInput == "stored-key")
    }

    @Test func showsTheStoredKeyAsSoonAsItOpens() {
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(key: "stored-key"))
        #expect(viewModel.usingUserKey)
        #expect(viewModel.keyInput == "stored-key")
    }

    /// SwiftUI builds the Settings sheet's view model inside the observation scope that builds the sheet.
    /// Reading its state in init makes that scope observe it, so each stored-key update rebuilds the sheet's
    /// view model and drops what the user typed; the Save then stores the empty field.
    @Test func creatingItDoesNotMakeTheCreatorObserveIt() async {
        let preferences = FakeUserPreferencesRepository()
        let creatorInvalidated = OSAllocatedUnfairLock(initialState: false)
        let viewModel = withObservationTracking {
            SettingsViewModel(preferences: preferences)
        } onChange: {
            creatorInvalidated.withLock { $0 = true }
        }
        viewModel.keyInput = "my-key"
        await viewModel.save()

        #expect(await eventually { viewModel.usingUserKey })
        #expect(!creatorInvalidated.withLock { $0 })
    }

    @Test func savesTheKeyTrimmed() async {
        let preferences = FakeUserPreferencesRepository()
        let viewModel = SettingsViewModel(preferences: preferences)
        viewModel.keyInput = "  my-key  "
        await viewModel.save()

        #expect(preferences.key == "my-key")
        #expect(await eventually { viewModel.usingUserKey })
        #expect(viewModel.keyInput == "my-key")
    }

    @Test func savingBlankRevertsToTheBuiltInKey() async {
        let preferences = FakeUserPreferencesRepository(key: "stored-key")
        let viewModel = SettingsViewModel(preferences: preferences)
        #expect(await eventually { viewModel.usingUserKey })
        viewModel.keyInput = "   "
        await viewModel.save()

        #expect(preferences.key == nil)
        #expect(await eventually { !viewModel.usingUserKey })
        #expect(viewModel.keyInput == "")
    }

    @Test func resetRevertsToTheBuiltInKey() async {
        let preferences = FakeUserPreferencesRepository(key: "stored-key")
        let viewModel = SettingsViewModel(preferences: preferences)
        #expect(await eventually { viewModel.usingUserKey })
        viewModel.keyInput = "half-typed"
        await viewModel.reset()

        #expect(preferences.key == nil)
        #expect(await eventually { !viewModel.usingUserKey })
        #expect(viewModel.keyInput == "")
    }
}
