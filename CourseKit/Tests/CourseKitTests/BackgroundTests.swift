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
