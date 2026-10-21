import SwiftUI
import PhotosUI
import UIKit
import CourseKit

/// Settings → Appearance (docs/design-0.7.md §5): glass or classic, what's behind it (a built-in wallpaper, your
/// photo or a Focus wallpaper you've seen, or the paper), and how course blocks look.
struct AppearanceView: View {
    @AppStorage(AppAppearance.styleKey) private var style = AppAppearance.defaultStyle
    @AppStorage(AppBackground.key) private var stored = ""
    @AppStorage(TimetableAppearance.blockStyleKey) private var blockStyle = TimetableAppearance.defaultBlockStyle
    @AppStorage(BackgroundPhoto.fadeKey) private var fade = BackgroundPhoto.defaultFade
    @AppStorage(AppBackground.glassFadeKey) private var glassFade = AppBackground.defaultGlassFade
    @AppStorage(BackgroundPhoto.blockOpacityKey) private var blockOpacity = BackgroundPhoto.defaultBlockOpacity
    @State private var photoItem: PhotosPickerItem?
    @State private var wallpapers: [FocusWallpaper] = []
    @State private var thumbnails: [String: UIImage] = [:]
    private var photo: BackgroundPhoto { BackgroundPhoto.shared }

    var body: some View {
        let choice = AppBackground.resolve(stored, style: style)
        Form {
            Section {
                Picker("Style", selection: $style) {
                    Text("Glass").tag(AppStyle.glass)
                    Text("Classic").tag(AppStyle.classic)
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("appStylePicker")
                StylePreview(style: style)
                    .listRowInsets(EdgeInsets())
            } header: {
                Text("Style")
            } footer: {
                style == .glass
                    ? Text("Today, Timetable, Focus and Words on the background below, with cards and buttons of glass.")
                    : Text("The warm paper of the icon. The background below shows behind the timetable only.")
            }

            Section {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 10) {
                        BackgroundTile(title: Text("Paper"), isSelected: choice == .paper) {
                            stored = BackgroundChoice.paper.stored
                        } picture: {
                            Theme.paper
                        }
                        .accessibilityIdentifier("background-paper")
                        // Your photo first: it's the one most likely chosen, and stays in view.
                        if let image = photo.image {
                            BackgroundTile(title: Text("Photo"), isSelected: choice == .photo) {
                                stored = BackgroundChoice.photo.stored
                            } picture: {
                                Image(uiImage: image).resizable().scaledToFill()
                            }
                            .accessibilityIdentifier("background-photo")
                        }
                        ForEach(SoftWallpaper.allCases) { wallpaper in
                            BackgroundTile(title: Text(wallpaper.name), isSelected: choice == .wallpaper(wallpaper)) {
                                stored = BackgroundChoice.wallpaper(wallpaper).stored
                            } picture: {
                                SoftWallpaperView(wallpaper: wallpaper)
                            }
                            .accessibilityIdentifier("background-\(wallpaper.rawValue)")
                        }
                    }
                    .padding(.vertical, 4)
                }
                PhotosPicker(selection: $photoItem, matching: .images) {
                    Label {
                        photo.image == nil ? Text("Choose a Photo…") : Text("Choose Another Photo…")
                    } icon: {
                        Image(systemName: "photo.on.rectangle")
                    }
                }
                .accessibilityIdentifier("chooseBackgroundPhoto")
                if !wallpapers.isEmpty {
                    FocusWallpaperRow(wallpapers: wallpapers, thumbnails: thumbnails) { picture in
                        photo.set(picture)
                        stored = BackgroundChoice.photo.stored
                    }
                }
                if choice == .photo {
                    LabeledContent("Fade") {
                        Slider(value: style == .glass ? $glassFade : $fade, in: 0...0.8)
                            .frame(maxWidth: 200)
                    }
                    Button("Remove Photo", role: .destructive) {
                        photo.clear()
                        stored = ""
                    }
                    .accessibilityIdentifier("removeBackgroundPhoto")
                }
            } header: {
                Text("Background")
            } footer: {
                wallpapers.isEmpty
                    ? Text("A wallpaper of soft shapes keeps text easy to read. Or use one of your own photos; the Focus wallpapers you've seen also show here.")
                    : Text("A wallpaper of soft shapes keeps text easy to read. Or use your own photo, or one of the Focus wallpapers you've seen.")
            }

            Section {
                Picker("Course blocks", selection: $blockStyle) {
                    Text("Soft").tag(CourseBlockStyle.soft)
                    Text("Solid").tag(CourseBlockStyle.solid)
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("blockStylePicker")
                BlockPreview(style: blockStyle, appStyle: style, fillOpacity: choice == .paper ? 1 : blockOpacity)
                    .listRowInsets(EdgeInsets())
                if choice != .paper {
                    LabeledContent("Block opacity") {
                        Slider(value: $blockOpacity, in: 0.5...1)
                            .frame(maxWidth: 200)
                    }
                }
            } header: {
                Text("Course Blocks")
            } footer: {
                Text("Soft: a light tint with dark text, easy to read. Solid: full colour with white text.")
            }
        }
        .paperBackground()
        .navigationTitle("Appearance")
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
                    photo.set(picture)
                    stored = BackgroundChoice.photo.stored
                }
                photoItem = nil
            }
        }
    }
}

/// One background to pick: a phone-shaped picture with its name, ringed when chosen.
private struct BackgroundTile<Picture: View>: View {
    let title: Text
    let isSelected: Bool
    let action: () -> Void
    @ViewBuilder let picture: () -> Picture

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                picture()
                    .frame(width: 64, height: 110)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(isSelected ? Color.accentColor : Color.secondary.opacity(0.25), lineWidth: isSelected ? 3 : 0.5)
                    }
                    .overlay(alignment: .bottomTrailing) {
                        if isSelected {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.body)
                                .foregroundStyle(.white, Color.accentColor)
                                .padding(5)
                        }
                    }
                title
                    .font(.caption)
                    .foregroundStyle(isSelected ? Color.accentColor : Color.primary)
                    .lineLimit(1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// The Focus wallpapers you've seen, to use one as the background photo.
private struct FocusWallpaperRow: View {
    let wallpapers: [FocusWallpaper]
    let thumbnails: [String: UIImage]
    let use: (UIImage) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(wallpapers) { wallpaper in
                    Button {
                        Task {
                            if let picture = await WallpaperStore.shared.picture(of: wallpaper) {
                                use(picture)
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
                    .accessibilityIdentifier("focusWallpaper-\(wallpaper.id)")
                }
            }
            .padding(.vertical, 4)
        }
    }
}

/// The look at a glance: a card and a button on the background, in glass or on the paper.
private struct StylePreview: View {
    let style: AppStyle

    var body: some View {
        let course = SampleCourses.all(today: LocalDate(.now))[1]
        let tint = CoursePalette.block(course.colorIndex, style: .soft)
        HStack(alignment: .bottom, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: course.name)
                    .font(.headline)
                Text(verbatim: "\(course.room) · 10:00")
                    .font(.subheadline)
                    .opacity(0.85)
            }
            .foregroundStyle(tint.text)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .surface(style, tint: tint.fill)
            Button {} label: {
                Image(systemName: "play.fill")
                    .padding(.vertical, 6)
            }
            .glassButtonStyle(style, prominent: true)
            .allowsHitTesting(false)
        }
        .padding(16)
        .frame(height: 130)
        .background {
            if style == .glass {
                AppBackdrop(style: .glass)
            } else {
                Theme.paper
            }
        }
        .clipped()
        .accessibilityHidden(true)
    }
}

/// Three sample blocks in the chosen style, on the background as the timetable shows it.
private struct BlockPreview: View {
    let style: CourseBlockStyle
    let appStyle: AppStyle
    let fillOpacity: Double

    var body: some View {
        // The demo timetable's courses, in the app's language.
        let samples = Array(SampleCourses.all(today: LocalDate(.now)).prefix(3))
        HStack(spacing: 6) {
            ForEach(samples) { course in
                CourseBlock(course: course, style: style, fillOpacity: fillOpacity, glass: appStyle == .glass)
                    .frame(height: 96)
            }
        }
        .padding(12)
        .background { AppBackdrop(style: appStyle) }
        .clipped()
        .accessibilityHidden(true)
    }
}
