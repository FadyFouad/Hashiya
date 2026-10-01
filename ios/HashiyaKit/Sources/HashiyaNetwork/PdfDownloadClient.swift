import Foundation
import os

/// A started download: the body as it arrives, and its length when the server sends one.
public struct PdfDownload: Sendable {
    public let chunks: AsyncThrowingStream<Data, Error>
    public let expectedLength: Int64?

    public init(chunks: AsyncThrowingStream<Data, Error>, expectedLength: Int64?) {
        self.chunks = chunks
        self.expectedLength = expectedLength
    }
}

/// Fetches a paper's open-access PDF from wherever its link points: arXiv, a repository or a publisher.
public protocol PdfDownloading: Sendable {
    /// GETs `url`, following redirects, and returns once the response has arrived.
    /// - Throws: `NetworkFailure.connectivity` when the server can't be reached; `.http(code:usedUserKey: false)` for an
    ///   error status, or with code 0 for a timeout; `CancellationError` when cancelled. The body's stream throws the same.
    func download(url: URL) async throws -> PdfDownload
}

/// Its own ephemeral session, separate from OpenAlex's, so the OpenAlex key and headers never go to other hosts.
public final class PdfDownloadClient: PdfDownloading {
    private let session: URLSession

    public init(session: URLSession = PdfDownloadClient.makeSession()) {
        self.session = session
    }

    /// 15 s to connect and between bytes; 2 minutes for the whole download; fails at once when offline; nothing cached.
    public static func makeConfiguration() -> URLSessionConfiguration {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.waitsForConnectivity = false
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 120
        configuration.urlCache = nil
        return configuration
    }

    public static func makeSession() -> URLSession {
        URLSession(configuration: makeConfiguration())
    }

    public func download(url: URL) async throws -> PdfDownload {
        var request = URLRequest(url: url)
        request.setValue("application/pdf, */*", forHTTPHeaderField: "Accept")
        let task = session.dataTask(with: request)
        let relay = Relay(task: task)
        task.delegate = relay
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in relay.start(continuation) }
        } onCancel: {
            relay.cancel()
        }
    }

    /// Turns the task's delegate calls into one response and a stream of chunks.
    private final class Relay: NSObject, URLSessionDataDelegate, @unchecked Sendable {
        private struct State {
            var response: CheckedContinuation<PdfDownload, Error>?
            var chunks: AsyncThrowingStream<Data, Error>.Continuation?
            /// Set once cancelled, so a cancel that beats `start` still ends the download instead of hanging it.
            var cancelled = false
        }

        private weak var task: URLSessionDataTask?
        private let state = OSAllocatedUnfairLock<State>(uncheckedState: State())

        init(task: URLSessionDataTask) {
            self.task = task
        }

        func start(_ continuation: CheckedContinuation<PdfDownload, Error>) {
            let alreadyCancelled = state.withLockUnchecked { state -> Bool in
                if state.cancelled { return true }
                state.response = continuation
                return false
            }
            if alreadyCancelled {
                task?.cancel()
                continuation.resume(throwing: CancellationError())
            } else {
                task?.resume()
            }
        }

        func cancel() {
            let pending = state.withLockUnchecked { state -> CheckedContinuation<PdfDownload, Error>? in
                state.cancelled = true
                defer { state.response = nil }
                return state.response
            }
            task?.cancel()
            pending?.resume(throwing: CancellationError())
        }

        func urlSession(
            _ session: URLSession,
            dataTask: URLSessionDataTask,
            didReceive response: URLResponse,
            completionHandler: @escaping @Sendable (URLSession.ResponseDisposition) -> Void
        ) {
            let pending = state.withLockUnchecked { state -> CheckedContinuation<PdfDownload, Error>? in
                defer { state.response = nil }
                return state.response
            }
            let status = (response as? HTTPURLResponse)?.statusCode ?? 200
            guard (200..<300).contains(status) else {
                pending?.resume(throwing: NetworkFailure.http(code: status, usedUserKey: false))
                completionHandler(.cancel)
                return
            }
            let (stream, continuation) = AsyncThrowingStream<Data, Error>.makeStream()
            continuation.onTermination = { [weak dataTask] termination in
                if case .cancelled = termination { dataTask?.cancel() }
            }
            state.withLockUnchecked { $0.chunks = continuation }
            let length = response.expectedContentLength
            pending?.resume(returning: PdfDownload(chunks: stream, expectedLength: length >= 0 ? length : nil))
            completionHandler(.allow)
        }

        func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
            _ = state.withLockUnchecked { $0.chunks }?.yield(data)
        }

        func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
            let (pending, chunks) = state.withLockUnchecked { state in
                defer {
                    state.response = nil
                    state.chunks = nil
                }
                return (state.response, state.chunks)
            }
            if let pending {
                pending.resume(throwing: Self.failure(for: error ?? URLError(.unknown)))
            } else if let error {
                chunks?.finish(throwing: Self.failure(for: error))
            } else {
                chunks?.finish()
            }
        }

        /// A timeout or an unusable link means the server didn't send the PDF; other transport failures are connectivity.
        static func failure(for error: Error) -> Error {
            guard let urlError = error as? URLError else { return NetworkFailure.unknown }
            switch urlError.code {
            case .cancelled: return CancellationError()
            case .timedOut, .badURL, .unsupportedURL: return NetworkFailure.http(code: 0, usedUserKey: false)
            default: return NetworkFailure.connectivity
            }
        }
    }
}
