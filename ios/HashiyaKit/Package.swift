// swift-tools-version: 6.0
import PackageDescription

let grdb: Target.Dependency = .product(name: "GRDB", package: "GRDB.swift")

let package = Package(
    name: "HashiyaKit",
    defaultLocalization: "en",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "HashiyaModel", targets: ["HashiyaModel"]),
        .library(name: "HashiyaNetwork", targets: ["HashiyaNetwork"]),
        .library(name: "HashiyaDatabase", targets: ["HashiyaDatabase"]),
        .library(name: "HashiyaTesting", targets: ["HashiyaTesting"]),
    ],
    dependencies: [
        .package(url: "https://github.com/groue/GRDB.swift.git", exact: "7.11.1"),
    ],
    targets: [
        .target(name: "HashiyaModel"),
        .target(name: "HashiyaNetwork"),
        .target(name: "HashiyaDatabase", dependencies: [grdb]),
        .target(
            name: "HashiyaTesting",
            dependencies: ["HashiyaNetwork"],
            resources: [.copy("Resources/Fixtures")]
        ),
        .testTarget(name: "HashiyaModelTests", dependencies: ["HashiyaModel"]),
        .testTarget(name: "HashiyaNetworkTests", dependencies: ["HashiyaNetwork", "HashiyaTesting"]),
        .testTarget(name: "HashiyaDatabaseTests", dependencies: ["HashiyaDatabase", grdb]),
    ]
)
