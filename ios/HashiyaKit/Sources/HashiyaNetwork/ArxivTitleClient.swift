import Foundation

/// Reads paper titles from arXiv's API (`GET https://export.arxiv.org/api/query?id_list=<id>`). It has its own
/// URLSession, never sends the OpenAlex key and logs nothing. Cancelling the calling task cancels the request.
public final class ArxivTitleClient: ArxivTitleService {
    public static let baseURL = URL(string: "https://export.arxiv.org")!
    public static let userAgent = "Hashiya-iOS (https://github.com/FadyFouad/Hashiya)"

    private let session: URLSession
    private let baseURL: URL

    /// - Parameter session: by default an ephemeral session with the OpenAlex session's timeouts.
    public init(session: URLSession = URLSession(configuration: OpenAlexSession.makeConfiguration()), baseURL: URL = ArxivTitleClient.baseURL) {
        self.session = session
        self.baseURL = baseURL
    }

    public func title(id: String) async throws -> String? {
        guard var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false) else { throw NetworkFailure.unknown }
        components.percentEncodedPath = "/api/query"
        components.percentEncodedQueryItems = [URLQueryItem(name: "id_list", value: OpenAlexHTTP.encode(id))]
        guard let url = components.url else { throw NetworkFailure.unknown }
        var request = URLRequest(url: url)
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch is CancellationError {
            throw CancellationError()
        } catch is URLError {
            throw NetworkFailure.connectivity
        } catch {
            throw NetworkFailure.unknown
        }
        guard let http = response as? HTTPURLResponse else { throw NetworkFailure.unknown }
        guard (200...299).contains(http.statusCode) else {
            throw NetworkFailure.http(code: http.statusCode, usedUserKey: false)
        }
        guard let xml = String(data: data, encoding: .utf8) else { throw NetworkFailure.malformedResponse }
        return try parseArxivTitle(xml)
    }
}

/// The first entry's title with entities decoded and whitespace collapsed; nil for an empty feed or arXiv's
/// error entry. Throws `NetworkFailure.malformedResponse` when the text is not a feed or the entry has no title.
/// Regex-based like Android's, not a full XML parser.
public func parseArxivTitle(_ xml: String) throws -> String? {
    guard feedStart.firstMatch(in: xml, range: NSRange(location: 0, length: (xml as NSString).length)) != nil else {
        throw NetworkFailure.malformedResponse
    }
    guard let entry = firstGroup(entryElement, in: xml) else { return nil }
    if firstGroup(entryID, in: entry)?.contains("/api/errors") == true { return nil }
    guard let rawTitle = firstGroup(entryTitle, in: entry) else { throw NetworkFailure.malformedResponse }
    let title = decodingXMLEntities(rawTitle).split(whereSeparator: \.isWhitespace).joined(separator: " ")
    return title.isEmpty ? nil : title
}

private let feedStart = try! NSRegularExpression(pattern: #"<feed[ \t\n\x{0B}\f\r>]"#)
private let entryElement = try! NSRegularExpression(pattern: "<entry>(.*?)</entry>", options: .dotMatchesLineSeparators)
private let entryID = try! NSRegularExpression(pattern: "<id>(.*?)</id>", options: .dotMatchesLineSeparators)
private let entryTitle = try! NSRegularExpression(pattern: "<title[^>]*>(.*?)</title>", options: .dotMatchesLineSeparators)
private let numericEntity = try! NSRegularExpression(pattern: "&#(x[0-9a-fA-F]+|[0-9]+);")

/// Group 1 of the first match, or nil.
private func firstGroup(_ regex: NSRegularExpression, in text: String) -> String? {
    let string = text as NSString
    guard let match = regex.firstMatch(in: text, range: NSRange(location: 0, length: string.length)) else { return nil }
    return string.substring(with: match.range(at: 1))
}

/// Numeric entities first, then the named ones, `&amp;` last so "&amp;lt;" stays "&lt;".
private func decodingXMLEntities(_ value: String) -> String {
    let result = NSMutableString(string: value)
    for match in numericEntity.matches(in: value, range: NSRange(location: 0, length: result.length)).reversed() {
        let code = result.substring(with: match.range(at: 1))
        let number = code.hasPrefix("x") ? UInt32(code.dropFirst(), radix: 16) : UInt32(code)
        guard let scalar = number.flatMap(Unicode.Scalar.init) else { continue }
        result.replaceCharacters(in: match.range, with: String(Character(scalar)))
    }
    return (result as String)
        .replacingOccurrences(of: "&lt;", with: "<")
        .replacingOccurrences(of: "&gt;", with: ">")
        .replacingOccurrences(of: "&quot;", with: "\"")
        .replacingOccurrences(of: "&apos;", with: "'")
        .replacingOccurrences(of: "&amp;", with: "&")
}
