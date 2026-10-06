import Foundation
import UIKit

/// This install's version, as stores and support quote it (Latin digits in every language).
public struct AppVersion: Equatable, Sendable {
    public let name: String
    public let build: String

    public init(name: String, build: String) {
        self.name = name
        self.build = build
    }

    public var label: String { "\(name) (\(build))" }

    public static var current: AppVersion {
        let info = Bundle.main.infoDictionary ?? [:]
        return AppVersion(name: info["CFBundleShortVersionString"] as? String ?? "", build: info["CFBundleVersion"] as? String ?? "")
    }
}

/// The feedback email and the store page.
enum Feedback {
    static let address = "fady.fouad.a@gmail.com"
    static let rateURL = URL(string: "https://apps.apple.com/app/id6817343027?action=write-review")!

    /// What the developer needs to reproduce a report; nothing from the library.
    static func infoLine(version: AppVersion, system: String, model: String, language: String) -> String {
        "Hashiya \(version.label) · \(system) · \(model) · \(language)"
    }

    /// A draft to the developer, the message first and the info line under it.
    static func mailURL(subject: String, version: AppVersion, system: String, model: String, language: String) -> URL {
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = address
        components.queryItems = [
            URLQueryItem(name: "subject", value: subject),
            URLQueryItem(name: "body", value: "\n\n" + infoLine(version: version, system: system, model: model, language: language)),
        ]
        return components.url!
    }

    /// "iOS 26.0" (or "iPadOS 26.0").
    @MainActor static var system: String { "\(UIDevice.current.systemName) \(UIDevice.current.systemVersion)" }

    /// The model identifier, e.g. "iPhone17,1"; the simulated one on a simulator.
    static var deviceModel: String {
        if let simulated = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] { return simulated }
        var info = utsname()
        uname(&info)
        return withUnsafeBytes(of: &info.machine) { bytes in
            String(decoding: bytes.prefix { $0 != 0 }, as: UTF8.self)
        }
    }
}
