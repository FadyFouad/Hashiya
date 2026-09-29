import Foundation
import HashiyaModel
import HashiyaNetwork
import Observation

public protocol AppUpdateRepository: Sendable {
    /// A required update when `currentBuild` is below the published minimum; nil otherwise, and on any failure.
    func requiredUpdate(currentBuild: Int) async -> RequiredUpdate?
}

/// Never blocks by mistake: offline, an error, or a missing or malformed field all mean "no update required".
public struct ConfigAppUpdateRepository: AppUpdateRepository {
    private let service: any AppConfigService

    public init(service: any AppConfigService) {
        self.service = service
    }

    /// The real one, reading GitHub Pages.
    public static func live() -> ConfigAppUpdateRepository {
        ConfigAppUpdateRepository(service: AppConfigClient())
    }

    public func requiredUpdate(currentBuild: Int) async -> RequiredUpdate? {
        guard let config = try? await service.iosConfig(),
              let minimum = config.minimumBuild,
              let link = config.storeUrl, let storeURL = URL(string: link), storeURL.scheme == "https",
              currentBuild < minimum else { return nil }
        return RequiredUpdate(storeURL: storeURL)
    }
}

/// Whether this build must update. `check()` runs on launch and every return to the foreground; the app shows
/// normally while it runs. Once blocked, it stays blocked for the life of the process.
@MainActor
@Observable
public final class AppUpdateModel {
    public private(set) var requiredUpdate: RequiredUpdate?

    @ObservationIgnored private let repository: any AppUpdateRepository
    @ObservationIgnored private let currentBuild: Int?
    @ObservationIgnored private var isChecking = false

    /// - Parameter currentBuild: `CFBundleVersion`; a value that isn't a whole number means no check.
    public init(repository: any AppUpdateRepository, currentBuild: String?) {
        self.repository = repository
        self.currentBuild = currentBuild.flatMap { Int($0) }
    }

    public func check() async {
        guard requiredUpdate == nil, !isChecking, let currentBuild else { return }
        isChecking = true
        defer { isChecking = false }
        if let update = await repository.requiredUpdate(currentBuild: currentBuild) {
            requiredUpdate = update
        }
    }
}
