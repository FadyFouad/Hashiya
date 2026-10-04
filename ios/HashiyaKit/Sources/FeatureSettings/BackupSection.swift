import HashiyaData
import HashiyaDesignSystem
import SwiftUI

/// Where the Export screen is pushed from Settings.
enum SettingsDestination: Hashable {
    case export
}

/// The Backup section of Settings: export, and restore from a file.
struct BackupSection: View {
    let summary: BackupSummary?
    let onRestore: () -> Void

    var body: some View {
        Section {
            Text(verbatim: L10n.string("backup.description"))
                .font(.hashiya(.body))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
            NavigationLink(value: SettingsDestination.export) {
                Text(verbatim: L10n.string("backup.export"))
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurface)
            }
            .disabled((summary?.papers ?? 0) == 0)
            .accessibilityIdentifier("settings.exportLibrary")
            Button(action: onRestore) {
                Text(verbatim: L10n.string("backup.restore")).font(.hashiya(.body))
            }
            .accessibilityIdentifier("settings.restoreBackup")
        } header: {
            Text(verbatim: L10n.string("backup.section"))
                .font(.hashiya(.stateTitle))
                .foregroundStyle(HashiyaColors.onSurface)
                .textCase(nil)
        }
    }
}
