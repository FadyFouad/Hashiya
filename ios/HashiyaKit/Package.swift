// swift-tools-version: 6.0
import PackageDescription

let grdb: Target.Dependency = .product(name: "GRDB", package: "GRDB.swift")
let zip: Target.Dependency = .product(name: "ZIPFoundation", package: "ZIPFoundation")
let snapshotTesting: Target.Dependency = .product(name: "SnapshotTesting", package: "swift-snapshot-testing")

let package = Package(
    name: "HashiyaKit",
    defaultLocalization: "en",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "HashiyaModel", targets: ["HashiyaModel"]),
        .library(name: "HashiyaBibTeX", targets: ["HashiyaBibTeX"]),
        .library(name: "HashiyaCitation", targets: ["HashiyaCitation"]),
        .library(name: "HashiyaNetwork", targets: ["HashiyaNetwork"]),
        .library(name: "HashiyaDatabase", targets: ["HashiyaDatabase"]),
        .library(name: "HashiyaDiagnostics", targets: ["HashiyaDiagnostics"]),
        .library(name: "HashiyaData", targets: ["HashiyaData"]),
        .library(name: "HashiyaDesignSystem", targets: ["HashiyaDesignSystem"]),
        .library(name: "HashiyaTesting", targets: ["HashiyaTesting"]),
        .library(name: "FeatureSearch", targets: ["FeatureSearch"]),
        .library(name: "FeatureLibrary", targets: ["FeatureLibrary"]),
        .library(name: "FeaturePaperDetails", targets: ["FeaturePaperDetails"]),
        .library(name: "FeatureReader", targets: ["FeatureReader"]),
        .library(name: "FeatureSettings", targets: ["FeatureSettings"]),
    ],
    dependencies: [
        .package(url: "https://github.com/groue/GRDB.swift.git", exact: "7.11.1"),
        .package(url: "https://github.com/weichsel/ZIPFoundation.git", exact: "0.9.19"),
        .package(url: "https://github.com/pointfreeco/swift-snapshot-testing.git", exact: "1.19.6"),
    ],
    targets: [
        .target(name: "HashiyaModel"),
        .target(name: "HashiyaBibTeX", dependencies: ["HashiyaModel"]),
        .target(name: "HashiyaCitation", dependencies: ["HashiyaModel"]),
        .target(name: "HashiyaNetwork"),
        .target(name: "HashiyaDiagnostics"),
        .target(name: "HashiyaDatabase", dependencies: ["HashiyaModel", grdb]),
        .target(name: "HashiyaData", dependencies: ["HashiyaModel", "HashiyaNetwork", "HashiyaDatabase", "HashiyaBibTeX", "HashiyaCitation", "HashiyaDiagnostics", zip]),
        .target(name: "HashiyaDesignSystem", dependencies: ["HashiyaModel"], resources: [.process("Resources")]),
        .target(
            name: "FeatureSearch",
            dependencies: ["HashiyaData", "HashiyaModel", "HashiyaDesignSystem", "HashiyaDiagnostics"],
            resources: [.process("Resources")]
        ),
        .target(
            name: "FeatureLibrary",
            dependencies: ["HashiyaData", "HashiyaModel", "HashiyaDesignSystem", "HashiyaDiagnostics"],
            resources: [.process("Resources")]
        ),
        .target(
            name: "FeaturePaperDetails",
            dependencies: ["HashiyaData", "HashiyaModel", "HashiyaDesignSystem", "HashiyaDiagnostics"],
            resources: [.process("Resources")]
        ),
        .target(
            name: "FeatureReader",
            dependencies: ["HashiyaData", "HashiyaModel", "HashiyaDesignSystem", "HashiyaDiagnostics"],
            resources: [.process("Resources")]
        ),
        .target(
            name: "FeatureSettings",
            dependencies: ["HashiyaData", "HashiyaModel", "HashiyaDesignSystem", "HashiyaDiagnostics"],
            resources: [.process("Resources")]
        ),
        .target(
            name: "HashiyaTesting",
            dependencies: ["HashiyaData", "HashiyaModel", "HashiyaNetwork", "HashiyaDesignSystem", "HashiyaDiagnostics", snapshotTesting],
            resources: [.copy("Resources/Fixtures")]
        ),
        .testTarget(name: "HashiyaModelTests", dependencies: ["HashiyaModel"]),
        .testTarget(name: "HashiyaDiagnosticsTests", dependencies: ["HashiyaDiagnostics", "HashiyaTesting"]),
        .testTarget(name: "HashiyaBibTeXTests", dependencies: ["HashiyaBibTeX", "HashiyaModel"]),
        .testTarget(name: "HashiyaCitationTests", dependencies: ["HashiyaCitation", "HashiyaModel"]),
        .testTarget(name: "HashiyaNetworkTests", dependencies: ["HashiyaNetwork", "HashiyaTesting"]),
        .testTarget(name: "HashiyaDatabaseTests", dependencies: ["HashiyaDatabase", "HashiyaModel", grdb]),
        .testTarget(
            name: "HashiyaDataTests",
            dependencies: ["HashiyaData", "HashiyaDatabase", "HashiyaDiagnostics", "HashiyaModel", "HashiyaNetwork", "HashiyaTesting", grdb, zip]
        ),
        .testTarget(
            name: "HashiyaDesignSystemTests",
            dependencies: ["HashiyaDesignSystem", "HashiyaModel", "HashiyaTesting"]
        ),
        .testTarget(
            name: "FeatureSearchTests",
            dependencies: ["FeatureSearch", "HashiyaData", "HashiyaDesignSystem", "HashiyaDiagnostics", "HashiyaModel", "HashiyaTesting"]
        ),
        .testTarget(
            name: "FeatureLibraryTests",
            dependencies: ["FeatureLibrary", "HashiyaData", "HashiyaDesignSystem", "HashiyaDiagnostics", "HashiyaModel", "HashiyaTesting"]
        ),
        .testTarget(
            name: "FeaturePaperDetailsTests",
            dependencies: ["FeaturePaperDetails", "HashiyaData", "HashiyaDesignSystem", "HashiyaDiagnostics", "HashiyaModel", "HashiyaTesting"]
        ),
        .testTarget(
            name: "FeatureReaderTests",
            dependencies: ["FeatureReader", "HashiyaData", "HashiyaDesignSystem", "HashiyaDiagnostics", "HashiyaModel", "HashiyaTesting"]
        ),
        .testTarget(
            name: "FeatureSettingsTests",
            dependencies: ["FeatureSettings", "HashiyaData", "HashiyaDesignSystem", "HashiyaDiagnostics", "HashiyaModel", "HashiyaTesting"]
        ),
    ]
)
