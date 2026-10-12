// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "CourseKit",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "CourseKit", targets: ["CourseKit"]),
        .library(name: "VocabKit", targets: ["VocabKit"]),
        .library(name: "AIKit", targets: ["AIKit"]),
        .executable(name: "vocab-content", targets: ["VocabContent"]),
    ],
    targets: [
        .target(name: "CourseKit"),
        .testTarget(name: "CourseKitTests", dependencies: ["CourseKit"]),
        // Vocabulary learning: scheduling (FSRS-6), study plan and session, word lists, search, statistics, sync.
        .target(name: "VocabKit", dependencies: ["CourseKit"]),
        .testTarget(name: "VocabKitTests", dependencies: ["VocabKit"]),
        // Builds content/kaoyan-ecdict.csv from ECDICT's ecdict.csv (see docs/vocabulary-spec.md §4).
        .executableTarget(name: "VocabContent", dependencies: ["VocabKit"]),
        // AI helpers with the user's own key: provider presets, requests, streamed answers, errors, prompts, to-do drafts
        // (see docs/ai-spec.md).
        .target(name: "AIKit", dependencies: ["CourseKit", "VocabKit"]),
        .testTarget(name: "AIKitTests", dependencies: ["AIKit"]),
    ]
)
