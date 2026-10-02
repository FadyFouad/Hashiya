import Foundation
import HashiyaNetwork

/// OpenAlex often gives `http://` PDF links. App Transport Security blocks cleartext, and on some networks plain HTTP is
/// intercepted by the provider's redirect page, so PDFs are always fetched over HTTPS. The hosts that serve PDFs (arXiv,
/// journals) all offer it. Android does the same since its PDF download fix.
public func upgradeToHTTPS(_ url: String) -> String {
    let scheme = "http://"
    guard url.lowercased().hasPrefix(scheme) else { return url }
    return "https://" + url.dropFirst(scheme.count)
}

/// How many of OpenAlex's other links a download tries after the stored one fails.
public let maxFallbackPDFLinks = 3

/// The open-access PDF links in `locations` to try after `tried` failed: over HTTPS, each once, without `tried`, arXiv's
/// first (it serves real PDFs reliably), then in OpenAlex's order, at most `maxFallbackPDFLinks`. Mirrors Android's
/// `fallbackPdfLinks`.
public func fallbackPDFLinks(_ locations: [NetworkLocation], tried: String) -> [String] {
    var seen: Set<String> = []
    var arxiv: [String] = []
    var others: [String] = []
    for location in locations where location.isOA {
        guard let raw = location.pdfURL?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { continue }
        let link = upgradeToHTTPS(raw)
        guard seen.insert(link).inserted else { continue }
        if isArxiv(link: link, source: location.source) { arxiv.append(link) } else { others.append(link) }
    }
    let triedLink = upgradeToHTTPS(tried)
    return Array((arxiv + others).filter { $0 != triedLink }.prefix(maxFallbackPDFLinks))
}

private func isArxiv(link: String, source: NetworkSource?) -> Bool {
    let host = URL(string: link)?.host()?.lowercased()
    let arxivHost = host == "arxiv.org" || host?.hasSuffix(".arxiv.org") == true
    return arxivHost || source?.displayName?.range(of: "arXiv", options: .caseInsensitive) != nil
}
