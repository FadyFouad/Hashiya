import Foundation
import HashiyaData
import HashiyaModel
import HashiyaNetwork
import HashiyaTesting
import Testing

private struct StubConfigService: AppConfigService {
    let answer: @Sendable () throws -> PlatformAppConfig?
    var openAlex = OpenAlexLimits.defaults
    func fetch() async throws -> RemoteAppConfig { RemoteAppConfig(ios: try answer(), openAlex: openAlex) }
}

private let store = "https://apps.apple.com/app/id0000000000"

private func repository(_ answer: @escaping @Sendable () throws -> PlatformAppConfig?) -> ConfigAppUpdateRepository {
    ConfigAppUpdateRepository(service: StubConfigService(answer: answer))
}

struct ConfigAppUpdateRepositoryTests {
    @Test func aBuildBelowTheMinimumMustUpdate() async {
        let update = await repository { PlatformAppConfig(minimumBuild: 5, storeUrl: store) }.requiredUpdate(currentBuild: 4)
        #expect(update == RequiredUpdate(storeURL: URL(string: store)!))
    }

    @Test(arguments: [5, 6])
    func theMinimumAndNewerBuildsAreAllowed(build: Int) async {
        #expect(await repository { PlatformAppConfig(minimumBuild: 5, storeUrl: store) }.requiredUpdate(currentBuild: build) == nil)
    }

    @Test func noIOSEntryMeansNoBlock() async {
        #expect(await repository { nil }.requiredUpdate(currentBuild: 1) == nil)
    }

    @Test func noMinimumMeansNoBlock() async {
        #expect(await repository { PlatformAppConfig(minimumBuild: nil, storeUrl: store) }.requiredUpdate(currentBuild: 1) == nil)
    }

    @Test(arguments: [nil, "", "http://apps.apple.com/app/id0000000000", "app store", "https://", "https:///app"] as [String?])
    func aStoreLinkThatIsNotHTTPSMeansNoBlock(link: String?) async {
        #expect(await repository { PlatformAppConfig(minimumBuild: 5, storeUrl: link) }.requiredUpdate(currentBuild: 1) == nil)
    }

    @Test func aFailureMeansNoBlock() async {
        #expect(await repository { throw NetworkFailure.connectivity }.requiredUpdate(currentBuild: 1) == nil)
    }

    @Test func savesTheOpenAlexLimitsItFetched() async {
        let defaults = TestDefaults.make()
        let limits = OpenAlexLimits(dailyDeviceCalls: 7, maxPagesPerQuery: 2, baseURL: nil)
        let repository = ConfigAppUpdateRepository(
            service: StubConfigService(answer: { nil }, openAlex: limits),
            limitsStore: OpenAlexLimitsStore(defaults: defaults)
        )
        _ = await repository.requiredUpdate(currentBuild: 1)
        #expect(OpenAlexLimitsStore(defaults: defaults).limits == limits)
    }

    @Test func aFailedFetchKeepsTheStoredLimits() async {
        let defaults = TestDefaults.make()
        let stored = OpenAlexLimits(dailyDeviceCalls: 9, maxPagesPerQuery: 3, baseURL: nil)
        OpenAlexLimitsStore(defaults: defaults).save(stored)
        let repository = ConfigAppUpdateRepository(
            service: StubConfigService(answer: { throw NetworkFailure.connectivity }),
            limitsStore: OpenAlexLimitsStore(defaults: defaults)
        )
        _ = await repository.requiredUpdate(currentBuild: 1)
        #expect(OpenAlexLimitsStore(defaults: defaults).limits == stored)
    }
}

@MainActor
struct AppUpdateModelTests {
    private let update = RequiredUpdate(storeURL: URL(string: store)!)

    @Test func checksTheBuildAsANumber() async {
        let repository = FakeAppUpdateRepository()
        await AppUpdateModel(repository: repository, currentBuild: "7").check()
        #expect(repository.checkedBuilds == [7])
    }

    @Test func blocksWhenAnUpdateIsRequired() async {
        let model = AppUpdateModel(repository: FakeAppUpdateRepository(result: update), currentBuild: "1")
        await model.check()
        #expect(model.requiredUpdate == update)
    }

    @Test(arguments: [nil, "", "1.0.1", "abc"] as [String?])
    func aBuildThatIsNotAWholeNumberIsNeverChecked(build: String?) async {
        let repository = FakeAppUpdateRepository(result: update)
        let model = AppUpdateModel(repository: repository, currentBuild: build)
        await model.check()
        #expect(model.requiredUpdate == nil)
        #expect(repository.checkedBuilds.isEmpty)
    }

    @Test func staysBlockedAfterALaterCheckFindsNothing() async {
        let repository = FakeAppUpdateRepository(result: update)
        let model = AppUpdateModel(repository: repository, currentBuild: "1")
        await model.check()

        repository.setResult(nil)
        await model.check()

        #expect(model.requiredUpdate == update)
        #expect(repository.checkedBuilds == [1])
    }

    @Test func checksAgainWhenNotBlocked() async {
        let repository = FakeAppUpdateRepository()
        let model = AppUpdateModel(repository: repository, currentBuild: "1")
        await model.check()
        await model.check()
        #expect(repository.checkedBuilds == [1, 1])
    }
}
