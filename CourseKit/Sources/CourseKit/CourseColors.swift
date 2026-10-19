import Foundation

/// An sRGB colour, each channel 0–1.
public struct RGB: Hashable, Codable, Sendable {
    public var red: Double
    public var green: Double
    public var blue: Double

    public init(_ red: Double, _ green: Double, _ blue: Double) {
        self.red = red
        self.green = green
        self.blue = blue
    }

    /// WCAG 2 relative luminance.
    public var luminance: Double {
        func linear(_ c: Double) -> Double { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }

    /// WCAG 2 contrast ratio with `other`, 1–21.
    public func contrast(with other: RGB) -> Double {
        let (light, dark) = luminance >= other.luminance ? (luminance, other.luminance) : (other.luminance, luminance)
        return (light + 0.05) / (dark + 0.05)
    }

    public static let white = RGB(1, 1, 1)
}

/// How course blocks are drawn on the timetable (Settings → Timetable Appearance).
public enum CourseBlockStyle: String, CaseIterable, Codable, Sendable {
    /// A light tint with dark text in the same hue (a deep tint with light text in dark mode). The default.
    case soft
    /// The full colour with white text, as before 0.7.
    case solid
}

/// One of the ten course colours (docs/design-0.7.md), in every style. Derived from the original hues in OKLCH:
/// `solid` is the hue darkened until white text reaches 3.5:1 (it is also the course's stripe and dot colour);
/// the soft fills and texts reach at least 7:1.
public struct CourseColor: Hashable, Codable, Sendable {
    public var name: String
    public var solid: RGB
    public var softFill: RGB
    public var softText: RGB
    public var darkFill: RGB
    public var darkText: RGB

    public init(name: String, solid: RGB, softFill: RGB, softText: RGB, darkFill: RGB, darkText: RGB) {
        self.name = name
        self.solid = solid
        self.softFill = softFill
        self.softText = softText
        self.darkFill = darkFill
        self.darkText = darkText
    }

    /// A block's fill and text colour in `style`, for light or dark appearance.
    public func block(_ style: CourseBlockStyle, dark: Bool) -> (fill: RGB, text: RGB) {
        switch style {
        case .solid: (solid, .white)
        case .soft: dark ? (darkFill, darkText) : (softFill, softText)
        }
    }
}

public enum CourseColors {
    /// In `colorIndex` order: courses keep their colour across versions.
    public static let all: [CourseColor] = [
        CourseColor(name: "coral", solid: RGB(0.890, 0.350, 0.330), softFill: RGB(1.000, 0.867, 0.853),
                    softText: RGB(0.499, 0.126, 0.126), darkFill: RGB(0.317, 0.136, 0.125), darkText: RGB(0.997, 0.786, 0.764)),
        CourseColor(name: "orange", solid: RGB(0.819, 0.434, 0.005), softFill: RGB(0.996, 0.877, 0.794),
                    softText: RGB(0.431, 0.215, 0.000), darkFill: RGB(0.302, 0.157, 0.030), darkText: RGB(0.989, 0.802, 0.671)),
        CourseColor(name: "mustard", solid: RGB(0.675, 0.511, 0.002), softFill: RGB(0.973, 0.895, 0.726),
                    softText: RGB(0.357, 0.266, 0.005), darkFill: RGB(0.257, 0.187, 0.000), darkText: RGB(0.926, 0.835, 0.640)),
        CourseColor(name: "green", solid: RGB(0.240, 0.603, 0.325), softFill: RGB(0.799, 0.949, 0.818),
                    softText: RGB(0.005, 0.343, 0.129), darkFill: RGB(0.084, 0.236, 0.118), darkText: RGB(0.727, 0.897, 0.749)),
        CourseColor(name: "teal", solid: RGB(0.108, 0.588, 0.568), softFill: RGB(0.773, 0.943, 0.929),
                    softText: RGB(0.015, 0.326, 0.314), darkFill: RGB(0.039, 0.231, 0.222), darkText: RGB(0.700, 0.889, 0.874)),
        CourseColor(name: "blue", solid: RGB(0.220, 0.520, 0.860), softFill: RGB(0.808, 0.922, 0.996),
                    softText: RGB(0.000, 0.277, 0.541), darkFill: RGB(0.089, 0.203, 0.335), darkText: RGB(0.744, 0.856, 0.995)),
        CourseColor(name: "indigo", solid: RGB(0.380, 0.400, 0.840), softFill: RGB(0.868, 0.901, 0.995),
                    softText: RGB(0.225, 0.234, 0.547), darkFill: RGB(0.167, 0.182, 0.337), darkText: RGB(0.806, 0.834, 0.997)),
        CourseColor(name: "purple", solid: RGB(0.600, 0.380, 0.830), softFill: RGB(0.927, 0.877, 0.999),
                    softText: RGB(0.344, 0.189, 0.498), darkFill: RGB(0.231, 0.160, 0.312), darkText: RGB(0.884, 0.803, 0.995)),
        CourseColor(name: "pink", solid: RGB(0.846, 0.348, 0.588), softFill: RGB(0.996, 0.860, 0.912),
                    softText: RGB(0.472, 0.126, 0.305), darkFill: RGB(0.303, 0.134, 0.211), darkText: RGB(0.994, 0.773, 0.861)),
        CourseColor(name: "slate", solid: RGB(0.470, 0.520, 0.580), softFill: RGB(0.881, 0.905, 0.935),
                    softText: RGB(0.245, 0.285, 0.334), darkFill: RGB(0.180, 0.201, 0.227), darkText: RGB(0.821, 0.848, 0.881)),
    ]

    /// The colour for a course's `colorIndex` (any integer: it wraps around).
    public static func color(_ index: Int) -> CourseColor {
        all[((index % all.count) + all.count) % all.count]
    }
}
