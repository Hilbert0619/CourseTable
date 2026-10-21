import Testing
@testable import CourseKit

@Suite struct BackgroundTests {
    /// The system's secondary text (#3C3C43 at 60 % in light mode, #EBEBF5 at 60 % in dark mode) and the accent.
    static let secondaryLight = RGB(hex: 0x3C3C43)
    static let secondaryDark = RGB(hex: 0xEBEBF5)
    static let accentLight = RGB(0.345, 0.420, 0.918)
    static let accentDark = RGB(0.510, 0.580, 1.000)

    /// Step 5 of the glass recipe: large soft shapes, with text readable anywhere on them (titles, section headers
    /// and buttons sit right on the wallpaper).
    @Test(arguments: SoftWallpaper.allCases)
    func readability(_ wallpaper: SoftWallpaper) {
        for color in wallpaper.palette(dark: false).colors {
            #expect(RGB(0, 0, 0).contrast(with: color) >= 10)
            #expect(Self.secondaryLight.over(color, alpha: 0.6).contrast(with: color) >= 3)
            #expect(Self.accentLight.contrast(with: color) >= 3)
        }
        for color in wallpaper.palette(dark: true).colors {
            #expect(RGB.white.contrast(with: color) >= 10)
            #expect(Self.secondaryDark.over(color, alpha: 0.6).contrast(with: color) >= 3)
            #expect(Self.accentDark.contrast(with: color) >= 3)
        }
    }

    @Test(arguments: SoftWallpaper.allCases)
    func fourLargeShapes(_ wallpaper: SoftWallpaper) {
        for dark in [false, true] {
            let palette = wallpaper.palette(dark: dark)
            #expect(palette.shapes.count == 4)
            #expect(palette.shapes.allSatisfy { $0.width >= 1 && $0.height >= 0.8 })
            #expect(Set(palette.colors).count == 5)
        }
    }

    @Test func wallpapersDiffer() {
        let palettes = SoftWallpaper.allCases.map { $0.palette(dark: false) }
        #expect(Set(palettes).count == SoftWallpaper.allCases.count)
        #expect(RGB(hex: 0xF7F1EA) == SoftWallpaper.dawn.palette(dark: false).base)
    }

    @Test func storedChoices() {
        let choices: [BackgroundChoice] = [.paper, .photo] + SoftWallpaper.allCases.map { .wallpaper($0) }
        for choice in choices {
            #expect(BackgroundChoice(stored: choice.stored) == choice)
        }
        #expect(BackgroundChoice(stored: "") == nil)
        #expect(BackgroundChoice(stored: "wallpaper.unknown") == nil)
        #expect(BackgroundChoice.wallpaper(.sky).stored == "wallpaper.sky")
    }

    /// The shared cases in `spec/fixtures/backgrounds.json`, which the Android port reads too.
    struct Fixture: Decodable {
        struct Shape: Decodable { var color: String; var x: Double; var y: Double; var width: Double; var height: Double }
        struct Palette: Decodable { var base: String; var shapes: [Shape] }
        struct Wallpaper: Decodable { var id: String; var light: Palette; var dark: Palette }
        struct Floors: Decodable {
            struct Colors: Decodable { var text: [Double]; var secondary: String; var accent: [Double] }
            var text: Double
            var secondary: Double
            var accent: Double
            var secondaryAlpha: Double
            var light: Colors
            var dark: Colors
        }
        struct ResolveCase: Decodable { var about: String; var stored: String; var glass: Bool; var hasPhoto: Bool; var expected: String }
        var defaultWallpaper: String
        var wallpapers: [Wallpaper]
        var readability: Floors
        var stored: [String]
        var invalid: [String]
        var resolve: [ResolveCase]
    }

    static func hex(_ text: String) -> RGB { RGB(hex: UInt32(text.dropFirst(), radix: 16)!) }

    static func rgb(_ values: [Double]) -> RGB { RGB(values[0], values[1], values[2]) }

    @Test func sharedCases() throws {
        let fixture = try SharedFixtures.decode(Fixture.self, from: "backgrounds.json")
        #expect(fixture.wallpapers.map(\.id) == SoftWallpaper.allCases.map(\.rawValue))
        #expect(fixture.defaultWallpaper == BackgroundChoice.defaultWallpaper.rawValue)
        for entry in fixture.wallpapers {
            let wallpaper = try #require(SoftWallpaper(rawValue: entry.id))
            for (dark, expected) in [(false, entry.light), (true, entry.dark)] {
                let palette = wallpaper.palette(dark: dark)
                #expect(palette.base == Self.hex(expected.base), "\(entry.id) base, dark \(dark)")
                #expect(palette.shapes.count == expected.shapes.count)
                for (shape, want) in zip(palette.shapes, expected.shapes) {
                    #expect(shape == SoftWallpaper.Shape(color: Self.hex(want.color), x: want.x, y: want.y, width: want.width,
                                                         height: want.height), "\(entry.id) shape, dark \(dark)")
                }
            }
        }
        let floors = fixture.readability
        #expect(floors.light.secondary == "#3C3C43" && Self.hex(floors.light.secondary) == Self.secondaryLight)
        #expect(Self.hex(floors.dark.secondary) == Self.secondaryDark)
        #expect(Self.rgb(floors.light.accent) == Self.accentLight && Self.rgb(floors.dark.accent) == Self.accentDark)
        for wallpaper in SoftWallpaper.allCases {
            for (dark, colors) in [(false, floors.light), (true, floors.dark)] {
                for color in wallpaper.palette(dark: dark).colors {
                    #expect(Self.rgb(colors.text).contrast(with: color) >= floors.text)
                    #expect(Self.hex(colors.secondary).over(color, alpha: floors.secondaryAlpha).contrast(with: color) >= floors.secondary)
                    #expect(Self.rgb(colors.accent).contrast(with: color) >= floors.accent)
                }
            }
        }
        for text in fixture.stored {
            #expect(BackgroundChoice(stored: text)?.stored == text, "\(text)")
        }
        #expect(fixture.stored.count == SoftWallpaper.allCases.count + 2)
        for text in fixture.invalid {
            #expect(BackgroundChoice(stored: text) == nil, "\(text)")
        }
        for test in fixture.resolve {
            #expect(BackgroundChoice.resolve(stored: test.stored, glass: test.glass, hasPhoto: test.hasPhoto).stored == test.expected,
                    "\(test.about)")
        }
    }

    @Test func resolvedChoice() {
        // Nothing chosen: 0.7.0's timetable photo, else the default wallpaper (glass) or the paper (classic).
        #expect(BackgroundChoice.resolve(stored: "", glass: true, hasPhoto: false) == .wallpaper(.dawn))
        #expect(BackgroundChoice.resolve(stored: "", glass: false, hasPhoto: false) == .paper)
        #expect(BackgroundChoice.resolve(stored: "", glass: true, hasPhoto: true) == .photo)
        #expect(BackgroundChoice.resolve(stored: "", glass: false, hasPhoto: true) == .photo)
        // A choice holds in both looks.
        #expect(BackgroundChoice.resolve(stored: "wallpaper.ember", glass: false, hasPhoto: true) == .wallpaper(.ember))
        #expect(BackgroundChoice.resolve(stored: "paper", glass: true, hasPhoto: true) == .paper)
        // The photo was cleared in Storage.
        #expect(BackgroundChoice.resolve(stored: "photo", glass: true, hasPhoto: false) == .wallpaper(.dawn))
        #expect(BackgroundChoice.resolve(stored: "photo", glass: false, hasPhoto: false) == .paper)
    }
}
