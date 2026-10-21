import SwiftUI
import Observation
import OSLog
import UIKit
import CourseKit

/// What's behind the glass (Settings → Appearance), and behind the timetable in either look.
enum AppBackground {
    /// `BackgroundChoice.stored`; empty until something is chosen.
    static let key = "appearance.background"
    /// How much paper lies over a photo in the glass look (the classic look has `BackgroundPhoto.fadeKey`).
    static let glassFadeKey = "appearance.glassFade"
    static let defaultGlassFade = 0.1

    /// The choice in effect.
    @MainActor
    static func resolve(_ stored: String, style: AppStyle) -> BackgroundChoice {
        BackgroundChoice.resolve(stored: stored, glass: style == .glass, hasPhoto: BackgroundPhoto.shared.image != nil)
    }
}

/// The photo behind the glass and the timetable: one of the downloaded Focus wallpapers or your own, downscaled and
/// kept in Application Support (it isn't a cache: clearing it is up to you, in Storage). The file keeps its 0.7.0
/// name, when it was the timetable's alone.
@MainActor
@Observable
final class BackgroundPhoto {
    static let shared = BackgroundPhoto()

    nonisolated static let fadeKey = "timetable.backgroundFade"
    nonisolated static let blockOpacityKey = "timetable.blockOpacity"
    nonisolated static let defaultFade = 0.45
    nonisolated static let defaultBlockOpacity = 0.9
    /// The longest side kept: enough for any iPhone screen at 3×.
    private static let maxPixels: CGFloat = 2400
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "BackgroundPhoto")

    private(set) var image: UIImage?
    let file: URL

    private init() {
        file = UITestLaunch.isActive
            ? URL.temporaryDirectory.appending(path: "TimetableBackground-\(UUID().uuidString).jpg")
            : URL.applicationSupportDirectory.appending(path: "TimetableBackground.jpg")
        if UITestLaunch.isActive && ProcessInfo.processInfo.arguments.contains("-timetableBackgroundDemo") {
            image = DemoWallpaper.image
        } else {
            image = UIImage(contentsOfFile: file.path)
        }
    }

    /// Uses `picture` as the background photo (downscaled, saved as JPEG).
    func set(_ picture: UIImage) {
        let size = picture.size
        let scale = min(1, Self.maxPixels / max(size.width * picture.scale, size.height * picture.scale, 1))
        let target = CGSize(width: size.width * picture.scale * scale, height: size.height * picture.scale * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let resized = UIGraphicsImageRenderer(size: target, format: format).image { _ in
            picture.draw(in: CGRect(origin: .zero, size: target))
        }
        do {
            try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
            try resized.jpegData(compressionQuality: 0.85)?.write(to: file, options: .atomic)
        } catch {
            Self.logger.error("Saving the background photo failed: \(error.localizedDescription)")
        }
        image = resized
    }

    /// No background photo.
    func clear() {
        try? FileManager.default.removeItem(at: file)
        image = nil
    }

    /// Bytes on disk, for Settings → Storage.
    var bytes: Int {
        (try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
    }
}

/// Behind a main tab in the glass look, and behind the timetable in either look: the chosen wallpaper, the photo
/// with some paper over it (Fade), or the paper alone.
struct AppBackdrop: View {
    let style: AppStyle
    @AppStorage(AppBackground.key) private var stored = ""
    @AppStorage(BackgroundPhoto.fadeKey) private var fade = BackgroundPhoto.defaultFade
    @AppStorage(AppBackground.glassFadeKey) private var glassFade = AppBackground.defaultGlassFade
    private var photo: BackgroundPhoto { BackgroundPhoto.shared }

    var body: some View {
        ZStack {
            Theme.paper
            switch AppBackground.resolve(stored, style: style) {
            case .paper:
                EmptyView()
            case .wallpaper(let wallpaper):
                SoftWallpaperView(wallpaper: wallpaper)
            case .photo:
                if let image = photo.image {
                    GeometryReader { proxy in
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                            .frame(width: proxy.size.width, height: proxy.size.height)
                            .clipped()
                    }
                    Theme.paper.opacity(style == .glass ? glassFade : fade)
                }
            }
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

/// A built-in wallpaper: large shapes of colour on the base, melting into each other (`SoftWallpaper`). Each shape is
/// an elliptical gradient that fades out the way the shape blurred by 16 % of the width would: no blur pass (an
/// opaque blur in a drawing group came out black and unblurred on iOS 26), so it's cheap and the same everywhere.
struct SoftWallpaperView: View {
    let wallpaper: SoftWallpaper
    @Environment(\.colorScheme) private var colorScheme

    /// How far the colour reaches beyond the shape's own size.
    private static let spread: CGFloat = 1.56
    /// Its opacity from the centre (0) to the gradient's edge (1): close to a Gaussian blur's falloff.
    private static let falloff: [(location: CGFloat, opacity: Double)] = [
        (0, 1), (0.28, 1), (0.46, 0.84), (0.64, 0.5), (0.82, 0.16), (1, 0),
    ]

    var body: some View {
        let palette = wallpaper.palette(dark: colorScheme == .dark)
        GeometryReader { proxy in
            let size = proxy.size
            ZStack {
                Color(rgb: palette.base)
                ForEach(Array(palette.shapes.enumerated()), id: \.offset) { _, shape in
                    let color = Color(rgb: shape.color)
                    EllipticalGradient(stops: Self.falloff.map { Gradient.Stop(color: color.opacity($0.opacity), location: $0.location) },
                                       center: .center, startRadiusFraction: 0, endRadiusFraction: 0.5)
                        .frame(width: size.width * shape.width * Self.spread, height: size.width * shape.height * Self.spread)
                        .position(x: size.width * shape.x, y: size.height * shape.y)
                }
            }
            .frame(width: size.width, height: size.height)
            .clipped()
        }
        .accessibilityHidden(true)
    }
}

extension SoftWallpaper {
    var name: LocalizedStringResource {
        switch self {
        case .dawn: "Dawn"
        case .lagoon: "Lagoon"
        case .meadow: "Meadow"
        case .lavender: "Lavender"
        case .sky: "Sky"
        case .ember: "Ember"
        }
    }
}
