@testable import FeatureSettings
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct SettingsSnapshotTests {
    @Test func builtInKey() async {
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository())
        try? await Task.sleep(for: .milliseconds(20))
        assertHashiyaSnapshots(of: SettingsView(viewModel: viewModel), named: "builtIn", arabicText: "يتم استخدام المفتاح المدمج")
    }

    @Test func userKey() async {
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(key: "my-openalex-key"))
        _ = await eventually { viewModel.usingUserKey }
        assertHashiyaSnapshots(of: SettingsView(viewModel: viewModel), named: "userKey", arabicText: "يتم استخدام مفتاحك")
    }
}
