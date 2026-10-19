import SwiftUI
import PhotosUI
import Observation
import OSLog
import UIKit
import CourseKit

/// The photo behind the timetable (Settings → Timetable Appearance): one of the downloaded Focus wallpapers or your
/// own, downscaled and kept in Application Support (it isn't a cache: clearing it is up to you, in Storage).
@MainActor
@Observable
final class TimetableBackground {
    static let shared = TimetableBackground()

    nonisolated static let fadeKey = "timetable.backgroundFade"
    nonisolated static let blockOpacityKey = "timetable.blockOpacity"
    nonisolated static let defaultFade = 0.45
    nonisolated static let defaultBlockOpacity = 0.9
    /// The longest side kept: enough for any iPhone screen at 3×.
    private static let maxPixels: CGFloat = 2400
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "TimetableBackground")

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

    /// Uses `picture` behind the timetable (downscaled, saved as JPEG).
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
            Self.logger.error("Saving the timetable background failed: \(error.localizedDescription)")
        }
        image = resized
    }

    /// No photo behind the timetable.
    func clear() {
        try? FileManager.default.removeItem(at: file)
        image = nil
    }

    /// Bytes on disk, for Settings → Storage.
    var bytes: Int {
        (try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
    }
}

/// Behind the timetable: the paper colour, or the chosen photo faded towards it.
struct TimetableBackdrop: View {
    @AppStorage(TimetableBackground.fadeKey) private var fade = TimetableBackground.defaultFade
    private var background: TimetableBackground { TimetableBackground.shared }

    var body: some View {
        ZStack {
            Theme.paper
            if let image = background.image {
                GeometryReader { proxy in
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: proxy.size.width, height: proxy.size.height)
                        .clipped()
                }
                Theme.paper.opacity(fade)
            }
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

/// Settings → Timetable Appearance: how course blocks look, and a photo behind the timetable.
struct TimetableAppearanceView: View {
    @AppStorage(TimetableAppearance.blockStyleKey) private var blockStyle = TimetableAppearance.defaultBlockStyle
    @AppStorage(TimetableBackground.fadeKey) private var fade = TimetableBackground.defaultFade
    @AppStorage(TimetableBackground.blockOpacityKey) private var blockOpacity = TimetableBackground.defaultBlockOpacity
    @State private var photoItem: PhotosPickerItem?
    @State private var wallpapers: [FocusWallpaper] = []
    @State private var thumbnails: [String: UIImage] = [:]
    private var background: TimetableBackground { TimetableBackground.shared }

    var body: some View {
        Form {
            Section {
                Picker("Course blocks", selection: $blockStyle) {
                    Text("Soft").tag(CourseBlockStyle.soft)
                    Text("Solid").tag(CourseBlockStyle.solid)
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("blockStylePicker")
                BlockPreview(style: blockStyle, fillOpacity: background.image == nil ? 1 : blockOpacity)
                    .listRowInsets(EdgeInsets())
            } header: {
                Text("Course Blocks")
            } footer: {
                Text("Soft: a light tint with dark text, easy to read. Solid: full colour with white text.")
            }

            Section {
                if let image = background.image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(height: 160)
                        .frame(maxWidth: .infinity)
                        .clipped()
                        .overlay { Theme.paper.opacity(fade) }
                        .listRowInsets(EdgeInsets())
                        .accessibilityHidden(true)
                    LabeledContent("Fade") {
                        Slider(value: $fade, in: 0...0.8)
                            .frame(maxWidth: 200)
                    }
                    LabeledContent("Block opacity") {
                        Slider(value: $blockOpacity, in: 0.5...1)
                            .frame(maxWidth: 200)
                    }
                    Button("Remove Photo", role: .destructive) { background.clear() }
                        .accessibilityIdentifier("removeTimetableBackground")
                }
                PhotosPicker(selection: $photoItem, matching: .images) {
                    Label {
                        background.image == nil ? Text("Choose a Photo…") : Text("Choose Another Photo…")
                    } icon: {
                        Image(systemName: "photo.on.rectangle")
                    }
                }
                .accessibilityIdentifier("chooseTimetablePhoto")
                if !wallpapers.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(wallpapers) { wallpaper in
                                Button {
                                    Task {
                                        if let picture = await WallpaperStore.shared.picture(of: wallpaper) {
                                            background.set(picture)
                                        }
                                    }
                                } label: {
                                    Group {
                                        if let thumbnail = thumbnails[wallpaper.id] {
                                            Image(uiImage: thumbnail).resizable().scaledToFill()
                                        } else if let rgb = wallpaper.rgb {
                                            Color(red: rgb.red, green: rgb.green, blue: rgb.blue)
                                        }
                                    }
                                    .frame(width: 64, height: 110)
                                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(Text("Photo by \(wallpaper.author)"))
                                .accessibilityIdentifier("timetableWallpaper-\(wallpaper.id)")
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            } header: {
                Text("Background")
            } footer: {
                wallpapers.isEmpty
                    ? Text("Use one of your own photos. The Focus wallpapers you've seen also show here.")
                    : Text("Your own photo, or one of the Focus wallpapers you've seen.")
            }
        }
        .paperBackground()
        .navigationTitle("Timetable Appearance")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            wallpapers = WallpaperStore.shared.downloadedWallpapers()
            for wallpaper in wallpapers where thumbnails[wallpaper.id] == nil {
                thumbnails[wallpaper.id] = await WallpaperStore.shared.picture(of: wallpaper, fitting: CGSize(width: 192, height: 330))
            }
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let picture = UIImage(data: data) {
                    background.set(picture)
                }
                photoItem = nil
            }
        }
    }
}

/// Three sample blocks in the chosen style, on the paper (and the photo, if there is one).
private struct BlockPreview: View {
    let style: CourseBlockStyle
    let fillOpacity: Double

    var body: some View {
        // The demo timetable's courses, in the app's language.
        let samples = Array(SampleCourses.all(today: LocalDate(.now)).prefix(3))
        HStack(spacing: 6) {
            ForEach(samples) { course in
                CourseBlock(course: course, style: style, fillOpacity: fillOpacity)
                    .frame(height: 96)
            }
        }
        .padding(12)
        .background { TimetableBackdrop() }
        .clipped()
        .accessibilityHidden(true)
    }
}
