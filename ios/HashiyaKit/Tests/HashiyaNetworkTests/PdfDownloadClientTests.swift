import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

/// Mirrors Android's `PdfDownloadDataSourceTest`, over `URLProtocolStub`.
struct PdfDownloadClientTests {
    private static let pdf = Data("%PDF-1.7\n%%EOF\n".utf8)
    private static let url = URL(string: "https://arxiv.org/pdf/1706.03762")!

    private func body(_ download: PdfDownload) async throws -> Data {
        var data = Data()
        for try await chunk in download.chunks { data.append(chunk) }
        return data
    }

    @Test func streamsTheBodyAndAsksForAPdf() async throws {
        let server = URLProtocolStub.Server(always: .status(200, body: Self.pdf))
        defer { server.invalidate() }

        let download = try await PdfDownloadClient(session: server.session).download(url: Self.url)

        #expect(try await body(download) == Self.pdf)
        #expect(server.requests.first?.value(forHTTPHeaderField: "Accept") == "application/pdf, */*")
        #expect(server.requests.first?.url == Self.url)
    }

    @Test func anErrorStatusIsHttpWithItsCode() async throws {
        let server = URLProtocolStub.Server(always: .status(404))
        defer { server.invalidate() }

        await #expect(throws: NetworkFailure.http(code: 404, usedUserKey: false)) {
            _ = try await PdfDownloadClient(session: server.session).download(url: Self.url)
        }
    }

    @Test func beingOfflineIsConnectivity() async throws {
        let server = URLProtocolStub.Server(always: .failure(.notConnectedToInternet))
        defer { server.invalidate() }

        await #expect(throws: NetworkFailure.connectivity) {
            _ = try await PdfDownloadClient(session: server.session).download(url: Self.url)
        }
    }

    @Test func aTimeoutIsAnHttpFailure() async throws {
        let server = URLProtocolStub.Server(always: .failure(.timedOut))
        defer { server.invalidate() }

        await #expect(throws: NetworkFailure.http(code: 0, usedUserKey: false)) {
            _ = try await PdfDownloadClient(session: server.session).download(url: Self.url)
        }
    }

    @Test func cancellingStopsTheRequest() async throws {
        let server = URLProtocolStub.Server(always: .stall)
        defer { server.invalidate() }
        let task = Task { try await PdfDownloadClient(session: server.session).download(url: Self.url) }
        #expect(await eventually { !server.requests.isEmpty })

        task.cancel()

        await #expect(throws: CancellationError.self) { _ = try await task.value }
    }

    @Test func theConfigurationIsEphemeralWithTheDownloadTimeouts() {
        let configuration = PdfDownloadClient.makeConfiguration()

        #expect(configuration.timeoutIntervalForRequest == 15)
        #expect(configuration.timeoutIntervalForResource == 120)
        #expect(configuration.waitsForConnectivity == false)
        #expect(configuration.urlCache == nil)
    }
}
