/// OpenAlex often gives `http://` PDF links. App Transport Security blocks cleartext, and on some networks plain HTTP is
/// intercepted by the provider's redirect page, so PDFs are always fetched over HTTPS. The hosts that serve PDFs (arXiv,
/// journals) all offer it. Android does the same since its PDF download fix.
public func upgradeToHTTPS(_ url: String) -> String {
    let scheme = "http://"
    guard url.lowercased().hasPrefix(scheme) else { return url }
    return "https://" + url.dropFirst(scheme.count)
}
