import Foundation
import os

/// Scripted HTTP for tests, without a live network. Each `URLProtocolStub.Server` has its own
/// URLSession and recorded requests, so tests that use different servers can run in parallel.
public final class URLProtocolStub: URLProtocol, @unchecked Sendable {
    public enum Reply: Sendable {
        /// A response with this status and body.
        case status(Int, body: Data = Data())
        /// A transport failure, as URLSession reports it.
        case failure(URLError.Code)

        /// A 200 response with a UTF-8 body.
        public static func json(_ body: String) -> Reply { .status(200, body: Data(body.utf8)) }
    }

    public final class Server: Sendable {
        public let session: URLSession
        fileprivate let id = UUID().uuidString
        private let state: OSAllocatedUnfairLock<State>

        private struct State {
            var reply: @Sendable (URLRequest) -> Reply
            var requests: [URLRequest] = []
        }

        public init(reply: @escaping @Sendable (URLRequest) -> Reply) {
            state = OSAllocatedUnfairLock(initialState: State(reply: reply))
            let configuration = URLSessionConfiguration.ephemeral
            configuration.protocolClasses = [URLProtocolStub.self]
            configuration.httpAdditionalHeaders = [URLProtocolStub.serverHeader: id]
            session = URLSession(configuration: configuration)
            URLProtocolStub.servers.withLock { $0[id] = self }
        }

        /// Replies to every request with `reply`.
        public convenience init(always reply: Reply) {
            self.init { _ in reply }
        }

        /// Every request received so far, oldest first.
        public var requests: [URLRequest] { state.withLock { $0.requests } }

        /// The decoded query items of the last request, by name.
        public var lastQuery: [String: String] {
            guard let url = requests.last?.url,
                  let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems else { return [:] }
            return Dictionary(items.map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { _, last in last })
        }

        public func setReply(_ reply: @escaping @Sendable (URLRequest) -> Reply) {
            state.withLock { $0.reply = reply }
        }

        /// Stops routing requests to this server.
        public func invalidate() {
            _ = URLProtocolStub.servers.withLock { $0.removeValue(forKey: id) }
            session.invalidateAndCancel()
        }

        fileprivate func receive(_ request: URLRequest) -> Reply {
            state.withLock { state in
                state.requests.append(request)
                return state.reply(request)
            }
        }
    }

    private static let serverHeader = "X-URLProtocolStub-Server"
    private static let servers = OSAllocatedUnfairLock<[String: Server]>(initialState: [:])

    override public class func canInit(with request: URLRequest) -> Bool {
        request.value(forHTTPHeaderField: serverHeader) != nil
    }

    override public class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override public func startLoading() {
        guard let id = request.value(forHTTPHeaderField: Self.serverHeader),
              let server = Self.servers.withLock({ $0[id] }),
              let url = request.url else {
            client?.urlProtocol(self, didFailWithError: URLError(.unsupportedURL))
            return
        }
        switch server.receive(request) {
        case let .status(code, body):
            let response = HTTPURLResponse(url: url, statusCode: code, httpVersion: "HTTP/1.1", headerFields: nil)!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: body)
            client?.urlProtocolDidFinishLoading(self)
        case let .failure(code):
            client?.urlProtocol(self, didFailWithError: URLError(code, userInfo: [NSURLErrorFailingURLErrorKey: url]))
        }
    }

    override public func stopLoading() {}
}
