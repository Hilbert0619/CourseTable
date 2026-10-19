import Foundation
import Testing
@testable import CourseKit

/// The shared cases in `spec/fixtures/course-colors.json`: the values both platforms use, and their contrast.
@Suite struct CourseColorTests {
    struct Fixture: Decodable {
        struct Minimum: Decodable {
            var solidWithWhite: Double
            var soft: Double
            var dark: Double
        }
        struct Entry: Decodable {
            var name: String
            var solid: [Double]
            var softFill: [Double]
            var softText: [Double]
            var darkFill: [Double]
            var darkText: [Double]
        }
        struct ContrastCase: Decodable {
            var a: [Double]
            var b: [Double]
            var ratio: Double
        }
        var minimumContrast: Minimum
        var colors: [Entry]
        var contrastCases: [ContrastCase]
    }

    let fixture: Fixture

    init() throws {
        fixture = try SharedFixtures.decode(Fixture.self, from: "course-colors.json")
    }

    static func rgb(_ values: [Double]) -> RGB { RGB(values[0], values[1], values[2]) }

    static func close(_ a: RGB, _ b: RGB) -> Bool {
        abs(a.red - b.red) < 0.0005 && abs(a.green - b.green) < 0.0005 && abs(a.blue - b.blue) < 0.0005
    }

    @Test func valuesMatchTheSharedCases() {
        #expect(CourseColors.all.map(\.name) == fixture.colors.map(\.name))
        for (color, entry) in zip(CourseColors.all, fixture.colors) {
            #expect(Self.close(color.solid, Self.rgb(entry.solid)), "\(entry.name) solid")
            #expect(Self.close(color.softFill, Self.rgb(entry.softFill)), "\(entry.name) soft fill")
            #expect(Self.close(color.softText, Self.rgb(entry.softText)), "\(entry.name) soft text")
            #expect(Self.close(color.darkFill, Self.rgb(entry.darkFill)), "\(entry.name) dark fill")
            #expect(Self.close(color.darkText, Self.rgb(entry.darkText)), "\(entry.name) dark text")
        }
    }

    @Test func everyBlockIsReadable() {
        let minimum = fixture.minimumContrast
        for color in CourseColors.all {
            let solid = color.block(.solid, dark: false)
            #expect(solid.text.contrast(with: solid.fill) >= minimum.solidWithWhite, "\(color.name) solid")
            #expect(color.block(.solid, dark: true) == solid, "solid is the same in dark mode")
            let soft = color.block(.soft, dark: false)
            #expect(soft.text.contrast(with: soft.fill) >= minimum.soft, "\(color.name) soft")
            let dark = color.block(.soft, dark: true)
            #expect(dark.text.contrast(with: dark.fill) >= minimum.dark, "\(color.name) soft in dark mode")
        }
    }

    @Test func contrastRatios() {
        for test in fixture.contrastCases {
            let ratio = Self.rgb(test.a).contrast(with: Self.rgb(test.b))
            #expect(abs(ratio - test.ratio) < 0.01, "\(test.a) on \(test.b): \(ratio)")
            #expect(abs(Self.rgb(test.b).contrast(with: Self.rgb(test.a)) - ratio) < 1e-12, "symmetric")
        }
    }

    @Test func indicesWrapAround() {
        #expect(CourseColors.color(0).name == "coral")
        #expect(CourseColors.color(10).name == "coral")
        #expect(CourseColors.color(-1).name == "slate")
        #expect(CourseColors.color(23).name == CourseColors.color(3).name)
    }

    @Test func coloursStayDistinct() {
        // No two soft fills (what the timetable shows most) look alike: at least 0.05 apart on some channel. Blue and
        // indigo needed a nudge in hue for this (docs/design-0.7.md).
        for (i, a) in CourseColors.all.enumerated() {
            for b in CourseColors.all[(i + 1)...] {
                let gap = max(abs(a.softFill.red - b.softFill.red), abs(a.softFill.green - b.softFill.green),
                              abs(a.softFill.blue - b.softFill.blue))
                #expect(gap >= 0.05, "\(a.name) and \(b.name) soft fills are too close (\(gap))")
            }
        }
    }
}
