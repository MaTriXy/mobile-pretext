// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Pretext",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "Pretext", targets: ["Pretext"])],
    targets: [
        .target(name: "Pretext", path: "Sources/Pretext"),
        .testTarget(name: "PretextTests", dependencies: ["Pretext"], path: "Tests/PretextTests"),
    ]
)
