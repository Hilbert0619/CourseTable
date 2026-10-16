import Foundation
import Observation
import OSLog
import UIKit
import CourseKit

/// The photo behind the Focus countdown (docs/todos-focus-spec.md §7). Each session gets a random one from the
/// backend's list (`focus_wallpapers`, readable without an account) that wasn't shown recently. A picture is
/// downloaded once and kept in Caches/Wallpapers; without a connection a kept one is shown, and with none kept
/// the countdown keeps its plain look.
@MainActor
@Observable
final class WallpaperStore {
    static let shared = WallpaperStore()

    /// The wallpaper on the countdown. `image` is nil while it downloads: its average colour shows meanwhile.
    struct Shown: Equatable {
        var wallpaper: FocusWallpaper
        var image: UIImage?
    }

    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "Wallpapers")
    private static let enabledKey = "focus.wallpaper.enabled"
    private static let recentKey = "focus.wallpaper.recent"
    /// The running session's wallpaper, so a relaunch shows the same one.
    private static let sessionKey = "focus.wallpaper.session"
    private static let catalogFile = "catalog.json"

    /// Settings → Focus → Wallpaper.
    var isEnabled: Bool {
        didSet {
            defaults.set(isEnabled, forKey: Self.enabledKey)
            if !isEnabled {
                loading?.cancel()
                shown = nil
            }
        }
    }
    private(set) var shown: Shown?
    /// A new wallpaper is being found or downloaded.
    private(set) var isLoading = false

    /// Where the pictures and the list are kept.
    let folder: URL
    @ObservationIgnored private var catalog: [FocusWallpaper] = []
    @ObservationIgnored private var fetchedAt: Date?
    @ObservationIgnored private var loading: Task<Void, Never>?
    private let defaults: UserDefaults
    private let session: URLSession

    private struct SavedCatalog: Codable {
        var fetchedAt: Date
        var wallpapers: [FocusWallpaper]
    }

    private init() {
        // UI tests start from the defaults each time and never touch the network or the real cache.
        defaults = UITestLaunch.isActive ? UserDefaults(suiteName: "wallpaper.uiTesting.\(UUID().uuidString)")! : .standard
        isEnabled = defaults.object(forKey: Self.enabledKey) as? Bool ?? true
        folder = UITestLaunch.isActive
            ? URL.temporaryDirectory.appending(path: "Wallpapers-\(UUID().uuidString)", directoryHint: .isDirectory)
            : URL.cachesDirectory.appending(path: "Wallpapers", directoryHint: .isDirectory)
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 20
        configuration.urlCache = nil
        session = URLSession(configuration: configuration)
        if let data = try? Data(contentsOf: folder.appending(path: Self.catalogFile)),
           let saved = try? JSONDecoder().decode(SavedCatalog.self, from: data) {
            catalog = saved.wallpapers
            fetchedAt = saved.fetchedAt
        }
    }

    // MARK: - Sessions

    /// A session started: find it a new wallpaper.
    func sessionStarted() {
        defaults.removeObject(forKey: Self.sessionKey)
        shown = nil
        showAnother()
    }

    /// The session ended: the next one gets a new wallpaper.
    func sessionEnded() {
        loading?.cancel()
        loading = nil
        isLoading = false
        defaults.removeObject(forKey: Self.sessionKey)
        shown = nil
    }

    /// The countdown appeared (also after a relaunch): show the session's wallpaper, or find one.
    func restore() {
        guard isEnabled, shown == nil, loading == nil else { return }
        if let id = defaults.string(forKey: Self.sessionKey), let wallpaper = catalog.first(where: { $0.id == id }),
           FileManager.default.fileExists(atPath: file(for: wallpaper).path) {
            shown = Shown(wallpaper: wallpaper, image: nil)
            loading = Task {
                if let image = await image(for: wallpaper, download: false) {
                    shown = Shown(wallpaper: wallpaper, image: image)
                }
                loading = nil
            }
        } else {
            showAnother()
        }
    }

    /// Another wallpaper for the session in progress (the shuffle button).
    func showAnother() {
        guard isEnabled else { return }
        loading?.cancel()
        loading = Task {
            await findNext()
            if !Task.isCancelled { loading = nil }
        }
    }

    private func findNext() async {
        isLoading = true
        defer { isLoading = false }
        if UITestLaunch.isActive {
            shown = Shown(wallpaper: DemoWallpaper.wallpaper, image: DemoWallpaper.image)
            return
        }
        var online = true
        if catalog.isEmpty || FocusWallpaperCatalog.isStale(fetchedAt: fetchedAt, now: .now) {
            online = await refreshCatalog()
        }
        guard !Task.isCancelled else { return }
        var recent = defaults.stringArray(forKey: Self.recentKey) ?? []
        if let current = shown?.wallpaper.id { recent.insert(current, at: 0) }
        var generator = SystemRandomNumberGenerator()
        // Without a connection (or if the download fails) only kept pictures can be shown.
        for canDownload in online ? [true, false] : [false] {
            guard let wallpaper = FocusWallpaperPicker.pick(from: catalog, downloaded: downloadedIDs(), recent: recent,
                                                            canDownload: canDownload, using: &generator) else { continue }
            if shown == nil { shown = Shown(wallpaper: wallpaper, image: nil) }
            guard let image = await image(for: wallpaper, download: canDownload), !Task.isCancelled else { continue }
            shown = Shown(wallpaper: wallpaper, image: image)
            defaults.set(wallpaper.id, forKey: Self.sessionKey)
            let remembered = FocusWallpaperPicker.remember(wallpaper.id, in: defaults.stringArray(forKey: Self.recentKey) ?? [],
                                                           catalogCount: catalog.count)
            defaults.set(remembered, forKey: Self.recentKey)
            trimCache(keeping: wallpaper.id, catalogIsFresh: online)
            return
        }
        // Nothing to show: keep the one on screen, if any; drop a placeholder that never loaded.
        if shown?.image == nil { shown = nil }
    }

    // MARK: - Network

    /// Downloads the list of wallpapers. False if it couldn't (no connection, server error).
    private func refreshCatalog() async -> Bool {
        var request = URLRequest(url: FocusWallpaperCatalog.requestURL(base: BackendConfig.url))
        request.setValue(BackendConfig.publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                Self.logger.error("The wallpaper list answered \((response as? HTTPURLResponse)?.statusCode ?? 0)")
                return false
            }
            catalog = try FocusWallpaperCatalog.decode(data)
            fetchedAt = .now
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            try JSONEncoder().encode(SavedCatalog(fetchedAt: .now, wallpapers: catalog))
                .write(to: folder.appending(path: Self.catalogFile), options: .atomic)
            return true
        } catch {
            Self.logger.notice("The wallpaper list couldn't be loaded: \(error.localizedDescription)")
            return false
        }
    }

    /// The picture, from the cache or (if `download`) downloaded into it, ready to draw.
    private func image(for wallpaper: FocusWallpaper, download: Bool) async -> UIImage? {
        let file = file(for: wallpaper)
        let manager = FileManager.default
        if !manager.fileExists(atPath: file.path) {
            guard download else { return nil }
            do {
                let (temporary, response) = try await session.download(from: wallpaper.url)
                defer { try? manager.removeItem(at: temporary) }
                guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
                try manager.createDirectory(at: folder, withIntermediateDirectories: true)
                try manager.moveItem(at: temporary, to: file)
            } catch {
                Self.logger.notice("Wallpaper \(wallpaper.id) couldn't be downloaded: \(error.localizedDescription)")
                return nil
            }
        }
        try? manager.setAttributes([.modificationDate: Date.now], ofItemAtPath: file.path)
        guard let image = UIImage(contentsOfFile: file.path) else {
            try? manager.removeItem(at: file)
            return nil
        }
        return await image.byPreparingForDisplay() ?? image
    }

    // MARK: - Cache

    private func file(for wallpaper: FocusWallpaper) -> URL {
        folder.appending(path: wallpaper.fileName)
    }

    private func pictures() -> [(id: String, bytes: Int, lastUsed: Date)] {
        let keys: [URLResourceKey] = [.fileSizeKey, .contentModificationDateKey]
        let files = (try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: keys)) ?? []
        return files.filter { $0.pathExtension == "jpg" }.map { url in
            let values = try? url.resourceValues(forKeys: Set(keys))
            return (url.deletingPathExtension().lastPathComponent, values?.fileSize ?? 0, values?.contentModificationDate ?? .distantPast)
        }
    }

    private func downloadedIDs() -> Set<String> {
        Set(pictures().map(\.id))
    }

    private func trimCache(keeping id: String, catalogIsFresh: Bool) {
        let entries = pictures().map { FocusWallpaperCache.Entry(id: $0.id, bytes: $0.bytes, lastUsed: $0.lastUsed) }
        let stale = FocusWallpaperCache.evictions(entries, catalog: catalogIsFresh ? Set(catalog.map(\.id)) : nil, keeping: [id])
        for name in stale {
            try? FileManager.default.removeItem(at: folder.appending(path: "\(name).jpg"))
        }
    }

    /// The kept pictures, for Settings → Storage.
    func cacheUsage() -> (count: Int, bytes: Int) {
        let kept = pictures()
        return (kept.count, FolderUsage.bytes(in: folder))
    }

    /// Settings → Storage: deletes the kept pictures and the list (the one on screen stays until the session ends).
    func clearCache() {
        try? FileManager.default.removeItem(at: folder)
        catalog = []
        fetchedAt = nil
    }
}

/// UI tests' wallpaper: a drawn dusk over hills, so screenshots never depend on the network.
enum DemoWallpaper {
    static let wallpaper = FocusWallpaper(
        id: "demo", url: URL(string: "https://example.com/demo.jpg")!, width: 1290, height: 2796, color: "#3b4a6b",
        focusY: 0.5, author: "Demo Photographer", sourceURL: URL(string: "https://unsplash.com"), licence: "Unsplash License"
    )

    @MainActor static let image: UIImage = {
        let size = CGSize(width: 430, height: 932)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            let cg = context.cgContext
            let sky = [UIColor(red: 0.13, green: 0.17, blue: 0.36, alpha: 1), UIColor(red: 0.55, green: 0.42, blue: 0.62, alpha: 1),
                       UIColor(red: 0.98, green: 0.66, blue: 0.48, alpha: 1)]
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: sky.map(\.cgColor) as CFArray,
                                      locations: [0, 0.55, 0.8])!
            cg.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])
            for (index, shade) in [0.32, 0.22, 0.12].enumerated() {
                let base = size.height * (0.62 + 0.08 * CGFloat(index))
                let hill = UIBezierPath()
                hill.move(to: CGPoint(x: 0, y: base))
                hill.addCurve(to: CGPoint(x: size.width, y: base - 40 + CGFloat(index) * 30),
                              controlPoint1: CGPoint(x: size.width * 0.3, y: base - 120),
                              controlPoint2: CGPoint(x: size.width * 0.6, y: base + 60))
                hill.addLine(to: CGPoint(x: size.width, y: size.height))
                hill.addLine(to: CGPoint(x: 0, y: size.height))
                UIColor(red: shade * 0.6, green: shade * 0.7, blue: shade, alpha: 1).setFill()
                hill.fill()
            }
            UIColor(white: 1, alpha: 0.9).setFill()
            UIBezierPath(ovalIn: CGRect(x: size.width * 0.68, y: size.height * 0.2, width: 34, height: 34)).fill()
        }
    }()
}
