import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

struct AppConfigClientTests {
    private let appStore = "https://apps.apple.com/app/id0000000000"
    private let sample = """
    {
      "android": { "minimumVersionCode": 1, "storeUrl": "https://play.google.com/store/apps/details?id=com.etatech.hashiya" },
      "ios": { "minimumBuild": 3, "storeUrl": "https://apps.apple.com/app/id0000000000" }
    }
    """

    private func client(_ server: URLProtocolStub.Server) -> AppConfigClient {
        AppConfigClient(session: server.session)
    }

    @Test func requestsTheFileWithNoQueryOrKey() async throws {
        let server = URLProtocolStub.Server(always: .json(sample))

        _ = try await client(server).iosConfig()

        let request = try #require(server.requests.last)
        #expect(request.url?.host() == "fadyfouad.github.io")
        #expect(request.url?.path() == "/Hashiya-Privacy-Policy/app-config.json")
        #expect(request.url?.query() == nil)
        #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
    }

    @Test func readsTheIOSEntry() async throws {
        let server = URLProtocolStub.Server(always: .json(sample))
        #expect(try await client(server).iosConfig() == PlatformAppConfig(minimumBuild: 3, storeUrl: appStore))
    }

    @Test func ignoresUnknownKeys() async throws {
        let server = URLProtocolStub.Server(always: .json(#"{"ios":{"minimumBuild":2,"storeUrl":"\#(appStore)","message":"x"},"web":{}}"#))
        #expect(try await client(server).iosConfig() == PlatformAppConfig(minimumBuild: 2, storeUrl: appStore))
    }

    @Test func aMissingIOSEntryIsNil() async throws {
        let server = URLProtocolStub.Server(always: .json(#"{"android":{"minimumVersionCode":5}}"#))
        #expect(try await client(server).iosConfig() == nil)
    }

    @Test(arguments: [#"{"ios":{"minimumBuild":"2"}}"#, #"{"ios":{"minimumBuild":2.5}}"#, "<html>Not found</html>"])
    func unreadableContentIsMalformed(body: String) async {
        let server = URLProtocolStub.Server(always: .json(body))
        await #expect(throws: NetworkFailure.malformedResponse) { try await client(server).iosConfig() }
    }

    @Test func aMissingFileIsAnHTTPFailure() async {
        let server = URLProtocolStub.Server(always: .status(404, body: Data("Not found".utf8)))
        await #expect(throws: NetworkFailure.http(code: 404, usedUserKey: false)) { try await client(server).iosConfig() }
    }

    @Test func noConnectionIsConnectivity() async {
        let server = URLProtocolStub.Server(always: .failure(.notConnectedToInternet))
        await #expect(throws: NetworkFailure.connectivity) { try await client(server).iosConfig() }
    }
}
