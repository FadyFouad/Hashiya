import HashiyaData
import HashiyaModel
import os

public final class FakeAppUpdateRepository: AppUpdateRepository, Sendable {
    private struct State {
        var result: RequiredUpdate?
        var checkedBuilds: [Int] = []
    }

    private let state: OSAllocatedUnfairLock<State>

    public init(result: RequiredUpdate? = nil) {
        state = OSAllocatedUnfairLock(initialState: State(result: result))
    }

    public func setResult(_ result: RequiredUpdate?) {
        state.withLock { $0.result = result }
    }

    /// Every build number checked so far, oldest first.
    public var checkedBuilds: [Int] { state.withLock { $0.checkedBuilds } }

    public func requiredUpdate(currentBuild: Int) async -> RequiredUpdate? {
        state.withLock {
            $0.checkedBuilds.append(currentBuild)
            return $0.result
        }
    }
}
