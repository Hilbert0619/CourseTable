import Foundation
#if canImport(CoreGraphics)
import CoreGraphics  // CGSize and CGRect with their initialisers (Foundation alone has them only off Apple platforms)
#endif
import Testing
@testable import CourseKit

/// The shared cases in `spec/fixtures/focus-wallpapers.json`, plus the parts that are Swift's own.
@Suite struct FocusWallpaperTests {
    struct Fixture: Decodable {
        struct Detail: Decodable {
            var id: String
            var color: String
            var focusY: Double
            var author: String
            var authorURL: String?
            var sourceURL: String?
        }
        struct CandidateCase: Decodable {
            var about: String
            var catalog: [String]
            var downloaded: [String]
            var recent: [String]
            var canDownload: Bool
            var expected: [String]
        }
        struct RememberCase: Decodable {
            var id: String
            var recent: [String]
            var catalogCount: Int
            var expected: [String]
        }
        struct LayoutCase: Decodable {
            var image: [Double]
            var container: [Double]
            var focusY: Double
            var expected: [Double]
        }
        struct EvictionCase: Decodable {
            struct Entry: Decodable { var id: String; var bytes: Int; var lastUsed: Double }
            var about: String
            var entries: [Entry]
            var catalog: [String]?
            var keeping: [String]
            var maxFiles: Int
            var maxBytes: Int
            var expected: [String]
        }

        var usable: [String]
        var usableDetails: [Detail]
        var candidates: [CandidateCase]
        var remember: [RememberCase]
        var layout: [LayoutCase]
        var evictions: [EvictionCase]
    }

    let fixture: Fixture
    let rows: Data

    init() throws {
        fixture = try SharedFixtures.decode(Fixture.self, from: "focus-wallpapers.json")
        let object = try JSONSerialization.jsonObject(with: SharedFixtures.data("focus-wallpapers.json")) as! [String: Any]
        rows = try JSONSerialization.data(withJSONObject: object["rows"]!)
    }

    static func wallpaper(_ id: String) -> FocusWallpaper {
        FocusWallpaper(id: id, url: URL(string: "https://example.com/\(id).jpg")!, width: 1290, height: 2796)
    }

    @Test func rowsTheAppCanUse() throws {
        let catalog = try FocusWallpaperCatalog.decode(rows)
        #expect(catalog.map(\.id) == fixture.usable)
        for (wallpaper, detail) in zip(catalog, fixture.usableDetails) {
            #expect(wallpaper.id == detail.id)
            #expect(wallpaper.color == detail.color)
            #expect(wallpaper.focusY == detail.focusY)
            #expect(wallpaper.author == detail.author)
            #expect(wallpaper.authorURL?.absoluteString == detail.authorURL)
            #expect(wallpaper.sourceURL?.absoluteString == detail.sourceURL)
        }
        #expect(catalog.first?.fileName == "lake.jpg")
    }

    @Test func candidates() {
        for test in fixture.candidates {
            let found = FocusWallpaperPicker.candidates(from: test.catalog.map(Self.wallpaper), downloaded: Set(test.downloaded),
                                                        recent: test.recent, canDownload: test.canDownload)
            #expect(found.map(\.id) == test.expected, "\(test.about)")
        }
    }

    @Test func pickingStaysAmongTheCandidates() {
        var generator = SystemRandomNumberGenerator()
        let catalog = ["a", "b", "c", "d"].map(Self.wallpaper)
        var seen = Set<String>()
        for _ in 0..<200 {
            let picked = FocusWallpaperPicker.pick(from: catalog, downloaded: [], recent: ["a"], canDownload: true, using: &generator)
            seen.insert(picked!.id)
        }
        #expect(seen == ["b", "c", "d"])
        #expect(FocusWallpaperPicker.pick(from: catalog, downloaded: [], recent: [], canDownload: false, using: &generator) == nil)
    }

    @Test func remember() {
        for test in fixture.remember {
            #expect(FocusWallpaperPicker.remember(test.id, in: test.recent, catalogCount: test.catalogCount) == test.expected)
        }
    }

    @Test func layout() {
        for test in fixture.layout {
            let frame = FocusWallpaperLayout.frame(imageSize: CGSize(width: test.image[0], height: test.image[1]),
                                                   in: CGSize(width: test.container[0], height: test.container[1]),
                                                   focusY: test.focusY)
            let values = [frame.minX, frame.minY, frame.width, frame.height].map(Double.init)
            #expect(zip(values, test.expected).allSatisfy { abs($0 - $1) < 0.01 }, "\(test) → \(values)")
        }
    }

    @Test func evictions() {
        for test in fixture.evictions {
            let entries = test.entries.map {
                FocusWallpaperCache.Entry(id: $0.id, bytes: $0.bytes, lastUsed: Date(timeIntervalSince1970: $0.lastUsed))
            }
            let removed = FocusWallpaperCache.evictions(entries, catalog: test.catalog.map(Set.init), keeping: Set(test.keeping),
                                                        maxFiles: test.maxFiles, maxBytes: test.maxBytes)
            #expect(removed == test.expected, "\(test.about)")
        }
    }

    @Test func theRequestAsksForTheActiveRows() {
        let url = FocusWallpaperCatalog.requestURL(base: URL(string: "https://abc.supabase.co")!)
        #expect(url.absoluteString == "https://abc.supabase.co/rest/v1/focus_wallpapers?select=id,url,width,height,color,focus_y,author,author_url,source_url,licence&active=is.true&order=id")
    }

    @Test func theListIsAskedForAgainAfterHalfADay() {
        let now = Date(timeIntervalSinceReferenceDate: 800_000_000)
        #expect(FocusWallpaperCatalog.isStale(fetchedAt: nil, now: now))
        #expect(!FocusWallpaperCatalog.isStale(fetchedAt: now.addingTimeInterval(-3600), now: now))
        #expect(FocusWallpaperCatalog.isStale(fetchedAt: now.addingTimeInterval(-12 * 3600), now: now))
        // A clock set back: ask again rather than trust a list "from the future".
        #expect(FocusWallpaperCatalog.isStale(fetchedAt: now.addingTimeInterval(60), now: now))
    }

    @Test func averageColour() {
        var wallpaper = Self.wallpaper("a")
        wallpaper.color = "#ff8000"
        #expect(wallpaper.rgb?.red == 1)
        #expect(abs((wallpaper.rgb?.green ?? 0) - 128.0 / 255) < 0.0001)
        #expect(wallpaper.rgb?.blue == 0)
        wallpaper.color = "orange"
        #expect(wallpaper.rgb == nil)
    }

    @Test func theMigrationListsTheBundledWallpapers() throws {
        // supabase/migrations/*_focus_wallpapers_unsplash.sql names a file in supabase/wallpapers/ for every row.
        let repository = SharedFixtures.directory.deletingLastPathComponent().deletingLastPathComponent()
        let migrations = repository.appendingPathComponent("supabase").appendingPathComponent("migrations")
        let name = try FileManager.default.contentsOfDirectory(atPath: migrations.path).first { $0.hasSuffix("_focus_wallpapers_unsplash.sql") }
        let sql = try String(contentsOf: migrations.appendingPathComponent(try #require(name)), encoding: .utf8)
        let files = sql.components(separatedBy: "/storage/v1/object/public/wallpapers/").dropFirst().map { $0.prefix { $0 != "'" } }
        let folder = repository.appendingPathComponent("supabase").appendingPathComponent("wallpapers")
        let onDisk = Set(try FileManager.default.contentsOfDirectory(atPath: folder.path))
        #expect(files.count == 30)
        #expect(Set(files.map(String.init)) == onDisk)
    }
}
