import Foundation

/// JSON fixtures copied byte-for-byte from the Android tests (`core/network/src/test/resources`).
public enum Fixtures {
    /// The contents of `Resources/Fixtures/<name>`, e.g. "works_page.json".
    public static func data(_ name: String) -> Data {
        let url = Bundle.module.url(forResource: name, withExtension: nil, subdirectory: "Fixtures")!
        return try! Data(contentsOf: url)
    }

    public static func string(_ name: String) -> String {
        String(decoding: data(name), as: UTF8.self)
    }
}
