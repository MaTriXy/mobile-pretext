// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "PretextSampleApp",
    platforms: [.iOS(.v17), .macOS(.v14)],
    dependencies: [
        .package(path: ".."),
    ],
    targets: [
        .executableTarget(
            name: "PretextSampleApp",
            dependencies: [
                .product(name: "Pretext", package: "ios"),
            ],
            path: "Sources"
        ),
    ]
)
