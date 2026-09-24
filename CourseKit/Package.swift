// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "CourseKit",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "CourseKit", targets: ["CourseKit"]),
    ],
    targets: [
        .target(name: "CourseKit"),
        .testTarget(name: "CourseKitTests", dependencies: ["CourseKit"]),
    ]
)
