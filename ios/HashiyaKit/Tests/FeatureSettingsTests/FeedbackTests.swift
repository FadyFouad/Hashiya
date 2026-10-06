@testable import FeatureSettings
import Foundation
import HashiyaDesignSystem
import Testing

@MainActor
struct FeedbackTests {
    private let version = AppVersion(name: "0.3.0", build: "3")

    @Test func versionLabelUsesLatinDigits() {
        #expect(version.label == "0.3.0 (3)")
    }

    @Test func infoLine() {
        #expect(Feedback.infoLine(version: version, system: "iOS 26.0", model: "iPhone17,1", language: "ar") == "Hashiya 0.3.0 (3) · iOS 26.0 · iPhone17,1 · ar")
    }

    @Test func mailURLIsADraftToTheDeveloper() throws {
        let url = Feedback.mailURL(subject: "Hashiya feedback", version: version, system: "iOS 26.0", model: "iPhone17,1", language: "en")
        let components = try #require(URLComponents(url: url, resolvingAgainstBaseURL: false))
        #expect(components.scheme == "mailto")
        #expect(components.path == "fady.fouad.a@gmail.com")
        let items = Dictionary(uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        #expect(items["subject"] == "Hashiya feedback")
        #expect(items["body"] == "\n\nHashiya 0.3.0 (3) · iOS 26.0 · iPhone17,1 · en")
    }

    @Test func rateOpensTheWriteAReviewPage() {
        #expect(Feedback.rateURL.absoluteString == "https://apps.apple.com/app/id6817343027?action=write-review")
    }

    @Test func arabicLinesKeepTheirValuesInOrder() {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = "ar"
        defer { HashiyaLanguage.override = previous }
        // Foundation wraps every %@ in U+2068 … U+2069; the version line adds a left-to-right isolate around it, so
        // "0.3.0 (3)" can't flip in the right-to-left sentence.
        #expect(L10n.format("settings.version", version.label) == "الإصدار \u{2066}\u{2068}0.3.0 (3)\u{2069}\u{2069}")
        #expect(L10n.format("settings.feedbackCopied", Feedback.address) == "تم نسخ عنوان البريد: \u{2068}fady.fouad.a@gmail.com\u{2069}")
    }
}
