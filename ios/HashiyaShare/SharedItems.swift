import FeatureSearch
import Foundation
import UniformTypeIdentifiers

/// What was shared: the first http(s) URL attachment, the text (content text and plain-text attachments) and the
/// page title (the item's title, or — when a URL was shared — its content text, where browsers often put it).
@MainActor
final class SharedItems {
    private let item: NSExtensionItem?

    init(_ item: NSExtensionItem?) {
        self.item = item
    }

    func read() async -> ShareLookupInput {
        guard let item else { return .nothing }
        let providers = item.attachments ?? []
        var url: URL?
        for provider in providers where provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
            if let candidate = await Self.loadURL(provider), ["http", "https"].contains(candidate.scheme?.lowercased()) {
                url = candidate
                break
            }
        }
        let contentText = item.attributedContentText?.string ?? ""
        var texts = [contentText]
        for provider in providers where provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
            if let text = await Self.loadText(provider) { texts.append(text) }
        }
        var title = item.attributedTitle?.string ?? ""
        if title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, url != nil {
            title = contentText
        }
        return shareLookupInput(url: url, text: texts.filter { !$0.isEmpty }.joined(separator: "\n"), title: title)
    }

    private static func loadURL(_ provider: NSItemProvider) async -> URL? {
        await withCheckedContinuation { continuation in
            _ = provider.loadObject(ofClass: URL.self) { url, _ in continuation.resume(returning: url) }
        }
    }

    /// A plain string, or an attributed string reduced to its characters.
    private static func loadText(_ provider: NSItemProvider) async -> String? {
        if provider.canLoadObject(ofClass: NSAttributedString.self), !provider.canLoadObject(ofClass: String.self) {
            return await withCheckedContinuation { continuation in
                _ = provider.loadObject(ofClass: NSAttributedString.self) { text, _ in
                    continuation.resume(returning: (text as? NSAttributedString)?.string)
                }
            }
        }
        return await withCheckedContinuation { continuation in
            _ = provider.loadObject(ofClass: String.self) { text, _ in continuation.resume(returning: text) }
        }
    }
}
