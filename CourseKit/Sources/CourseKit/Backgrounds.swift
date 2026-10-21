import Foundation

/// A built-in wallpaper for the glass look (docs/design-0.7.md §5): four large, soft shapes of colour, blurred into
/// each other. Pale in light mode and deep in dark mode, so text on them stays readable: black and white text reach
/// 10:1 on every colour, the system's secondary text and the accent colour 3:1 (`readability` in the tests).
public enum SoftWallpaper: String, CaseIterable, Identifiable, Sendable {
    case dawn
    case lagoon
    case meadow
    case lavender
    case sky
    case ember

    public var id: String { rawValue }

    /// One shape: its centre as fractions of the screen's width and height, its size as fractions of the width.
    public struct Shape: Hashable, Sendable {
        public var color: RGB
        public var x: Double
        public var y: Double
        public var width: Double
        public var height: Double
    }

    public struct Palette: Hashable, Sendable {
        public var base: RGB
        public var shapes: [Shape]

        /// The base and every shape's colour.
        public var colors: [RGB] { [base] + shapes.map(\.color) }
    }

    /// Three arrangements, so the wallpapers differ in more than colour: (x, y, width, height).
    private static let layouts: [[(Double, Double, Double, Double)]] = [
        [(0.10, 0.10, 1.15, 0.95), (0.95, 0.38, 1.05, 0.95), (0.15, 0.68, 1.15, 1.0), (0.90, 0.95, 1.15, 0.9)],
        [(0.85, 0.08, 1.2, 0.9), (0.05, 0.36, 1.1, 1.0), (0.90, 0.66, 1.1, 0.95), (0.20, 0.97, 1.2, 0.9)],
        [(0.50, 0.04, 1.4, 0.8), (0.00, 0.50, 1.0, 1.1), (1.00, 0.55, 1.0, 1.1), (0.50, 1.00, 1.4, 0.8)],
    ]

    /// Layout, light colours (base first), dark colours (base first).
    private var definition: (layout: Int, light: [UInt32], dark: [UInt32]) {
        switch self {
        case .dawn: (0, [0xF7F1EA, 0xFFD2B8, 0xF9CFDD, 0xE0D8F8, 0xFFE6B3], [0x15111A, 0x4A1F2C, 0x3B1D3D, 0x232456, 0x47321A])
        case .lagoon: (1, [0xEDF6F6, 0xAEE8E2, 0xC9E0F9, 0xCFF1DC, 0xE8F5CB], [0x071518, 0x0B4049, 0x0E2F57, 0x0B4436, 0x16351F])
        case .meadow: (2, [0xF3F7EC, 0xD0EDBC, 0xF5E8AE, 0xBDE6D3, 0xEAF2C2], [0x0C130B, 0x21431F, 0x3E3A12, 0x123E36, 0x2A3A15])
        case .lavender: (0, [0xF5F1FA, 0xE3D7F8, 0xF7D0E7, 0xD0DAF8, 0xEEE0F8], [0x110D19, 0x35215A, 0x4A1D44, 0x1C2A59, 0x2E1D48])
        case .sky: (1, [0xEFF5FB, 0xC6DCF9, 0xDAD8F8, 0xC4ECF5, 0xE1EBFB], [0x0A0F1C, 0x172E5C, 0x2B245C, 0x0C3B4E, 0x182448])
        case .ember: (2, [0xFBF3EC, 0xFFD0B2, 0xFFE3AC, 0xFBD2CD, 0xFFEAD3], [0x150B08, 0x56250F, 0x4E340C, 0x4C1822, 0x3A210E])
        }
    }

    public func palette(dark: Bool) -> Palette {
        let definition = definition
        let colors = (dark ? definition.dark : definition.light).map(RGB.init(hex:))
        let shapes = zip(Self.layouts[definition.layout], colors.dropFirst()).map { place, color in
            Shape(color: color, x: place.0, y: place.1, width: place.2, height: place.3)
        }
        return Palette(base: colors[0], shapes: shapes)
    }
}

extension RGB {
    /// From 0xRRGGBB.
    public init(hex: UInt32) {
        self.init(Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
    }

    /// `self` drawn at `alpha` over `background`.
    public func over(_ background: RGB, alpha: Double) -> RGB {
        RGB(red * alpha + background.red * (1 - alpha), green * alpha + background.green * (1 - alpha),
            blue * alpha + background.blue * (1 - alpha))
    }
}

/// What's behind the glass and the timetable (Settings → Appearance), kept as a string in the app's settings.
public enum BackgroundChoice: Hashable, Sendable {
    /// The warm paper, as in 0.7.0.
    case paper
    case wallpaper(SoftWallpaper)
    /// Your photo, or a Focus wallpaper you picked (one file on the iPhone).
    case photo

    public static let defaultWallpaper = SoftWallpaper.dawn

    /// "paper", "wallpaper.dawn", "photo"; `nil` for anything else (nothing chosen yet).
    public init?(stored: String) {
        switch stored {
        case "paper": self = .paper
        case "photo": self = .photo
        default:
            guard stored.hasPrefix("wallpaper."), let wallpaper = SoftWallpaper(rawValue: String(stored.dropFirst(10))) else {
                return nil
            }
            self = .wallpaper(wallpaper)
        }
    }

    public var stored: String {
        switch self {
        case .paper: "paper"
        case .wallpaper(let wallpaper): "wallpaper.\(wallpaper.rawValue)"
        case .photo: "photo"
        }
    }

    /// The background in effect. What you chose, as long as it can be shown (a photo you cleared in Storage can't).
    /// Before any choice: a photo you put behind the timetable in 0.7.0, else the default wallpaper in the glass look
    /// and the paper in the classic one.
    public static func resolve(stored: String, glass: Bool, hasPhoto: Bool) -> BackgroundChoice {
        if let choice = BackgroundChoice(stored: stored), choice != .photo || hasPhoto {
            return choice
        }
        if hasPhoto {
            return .photo
        }
        return glass ? .wallpaper(defaultWallpaper) : .paper
    }
}
