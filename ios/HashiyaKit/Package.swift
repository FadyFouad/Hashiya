// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "HashiyaKit",
    defaultLocalization: "en",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "HashiyaModel", targets: ["HashiyaModel"]),
        .library(name: "HashiyaNetwork", targets: ["HashiyaNetwork"]),
        .library(name: "HashiyaTesting", targets: ["HashiyaTesting"]),
    ],
    targets: [
        .target(name: "HashiyaModel"),
        .target(name: "HashiyaNetwork"),
        .target(
            name: "HashiyaTesting",
            dependencies: ["HashiyaNetwork"],
            resources: [.copy("Resources/Fixtures")]
        ),
        .testTarget(name: "HashiyaModelTests", dependencies: ["HashiyaModel"]),
        .testTarget(name: "HashiyaNetworkTests", dependencies: ["HashiyaNetwork", "HashiyaTesting"]),
    ]
)
