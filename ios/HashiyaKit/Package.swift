// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "HashiyaKit",
    defaultLocalization: "en",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "HashiyaModel", targets: ["HashiyaModel"]),
    ],
    targets: [
        .target(name: "HashiyaModel"),
        .testTarget(name: "HashiyaModelTests", dependencies: ["HashiyaModel"]),
    ]
)
