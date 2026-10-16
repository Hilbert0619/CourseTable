import Foundation
#if canImport(CoreGraphics)
import CoreGraphics  // CGSize and CGRect with their initialisers (Foundation alone has them only off Apple platforms)
#endif

/// A photo behind the Focus countdown (docs/todos-focus-spec.md §7): one row of the backend's `focus_wallpapers`.
public struct FocusWallpaper: Hashable, Codable, Sendable, Identifiable {
    public var id: String
    /// The picture, on https.
    public var url: URL
    public var width: Int
    public var height: Int
    /// The picture's average colour, `#rrggbb`, shown while it loads.
    public var color: String
    /// How far down the picture its subject is, 0 (top) to 1 (bottom).
    public var focusY: Double
    public var author: String
    public var authorURL: URL?
    public var sourceURL: URL?
    public var licence: String

    public init(id: String, url: URL, width: Int, height: Int, color: String = "#808080", focusY: Double = 0.5,
                author: String = "", authorURL: URL? = nil, sourceURL: URL? = nil, licence: String = "") {
        self.id = id
        self.url = url
        self.width = width
        self.height = height
        self.color = color
        self.focusY = focusY
        self.author = author
        self.authorURL = authorURL
        self.sourceURL = sourceURL
        self.licence = licence
    }

    enum CodingKeys: String, CodingKey {
        case id, url, width, height, color, author, licence
        case focusY = "focus_y"
        case authorURL = "author_url"
        case sourceURL = "source_url"
    }

    /// Reads a row leniently: optional fields may be missing or empty, `focus_y` is brought into 0…1.
    public init(from decoder: any Decoder) throws {
        let row = try decoder.container(keyedBy: CodingKeys.self)
        id = try row.decode(String.self, forKey: .id)
        url = try row.decode(URL.self, forKey: .url)
        width = try row.decode(Int.self, forKey: .width)
        height = try row.decode(Int.self, forKey: .height)
        color = (try? row.decodeIfPresent(String.self, forKey: .color)) ?? "#808080"
        focusY = min(max((try? row.decodeIfPresent(Double.self, forKey: .focusY)) ?? 0.5, 0), 1)
        author = (try? row.decodeIfPresent(String.self, forKey: .author)) ?? ""
        authorURL = Self.link(try? row.decodeIfPresent(String.self, forKey: .authorURL))
        sourceURL = Self.link(try? row.decodeIfPresent(String.self, forKey: .sourceURL))
        licence = (try? row.decodeIfPresent(String.self, forKey: .licence)) ?? ""
    }

    private static func link(_ text: String?) -> URL? {
        guard let text, text.hasPrefix("https://") else { return nil }
        return URL(string: text)
    }

    /// The file a downloaded copy is kept in: the id, which the backend limits to `a-z`, `0-9` and dashes.
    public var fileName: String { "\(id).jpg" }

    /// The average colour's red, green and blue, 0…1; nil if `color` isn't `#rrggbb`.
    public var rgb: (red: Double, green: Double, blue: Double)? {
        guard color.count == 7, color.first == "#", let value = UInt32(color.dropFirst(), radix: 16) else { return nil }
        return (Double(value >> 16 & 0xff) / 255, Double(value >> 8 & 0xff) / 255, Double(value & 0xff) / 255)
    }

    /// Whether the app can use the row: an https picture with a size and an id that is safe as a file name.
    public var isUsable: Bool {
        url.scheme == "https" && width > 0 && height > 0 && !id.isEmpty && id.count <= 60
            && id.allSatisfy { ("a"..."z").contains($0) || ("0"..."9").contains($0) || $0 == "-" }
    }
}

/// The list of wallpapers: where the app asks for it and how it reads the answer.
public enum FocusWallpaperCatalog {
    /// How long a downloaded list is used before the app asks again.
    public static let refreshInterval: TimeInterval = 12 * 60 * 60

    /// The REST API request for the active rows (`apikey` header: the publishable key; no account needed).
    public static func requestURL(base: URL) -> URL {
        var components = URLComponents(url: base.appendingPathComponent("rest/v1/focus_wallpapers"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "select", value: "id,url,width,height,color,focus_y,author,author_url,source_url,licence"),
            URLQueryItem(name: "active", value: "is.true"),
            URLQueryItem(name: "order", value: "id"),
        ]
        return components.url!
    }

    /// The usable wallpapers in an answer (rows the app can't use are left out; a repeated id counts once).
    public static func decode(_ data: Data) throws -> [FocusWallpaper] {
        struct Lenient: Decodable {
            var wallpaper: FocusWallpaper?
            init(from decoder: any Decoder) throws { wallpaper = try? FocusWallpaper(from: decoder) }
        }
        var seen = Set<String>()
        return try JSONDecoder().decode([Lenient].self, from: data).compactMap(\.wallpaper)
            .filter { $0.isUsable && seen.insert($0.id).inserted }
    }

    /// Whether a list downloaded at `fetchedAt` should be asked for again at `now`.
    public static func isStale(fetchedAt: Date?, now: Date) -> Bool {
        guard let fetchedAt else { return true }
        return now.timeIntervalSince(fetchedAt) >= refreshInterval || now < fetchedAt
    }
}

/// Which wallpaper a session shows: a random one that wasn't shown recently.
public enum FocusWallpaperPicker {
    /// The longest list of recently shown wallpapers that is kept.
    public static let maxRecent = 10

    /// The wallpapers the next session may show. Recently shown ones are skipped while there are others; without
    /// a connection only downloaded ones can be shown.
    public static func candidates(from catalog: [FocusWallpaper], downloaded: Set<String>, recent: [String],
                                  canDownload: Bool) -> [FocusWallpaper] {
        let reachable = canDownload ? catalog : catalog.filter { downloaded.contains($0.id) }
        let fresh = reachable.filter { !recent.contains($0.id) }
        return fresh.isEmpty ? reachable : fresh
    }

    public static func pick<G: RandomNumberGenerator>(from catalog: [FocusWallpaper], downloaded: Set<String>, recent: [String],
                                                      canDownload: Bool, using generator: inout G) -> FocusWallpaper? {
        candidates(from: catalog, downloaded: downloaded, recent: recent, canDownload: canDownload).randomElement(using: &generator)
    }

    /// The recent list after `id` is shown: newest first, no repeats, at most half the catalog (so there's always a
    /// choice left) and at most `maxRecent`.
    public static func remember(_ id: String, in recent: [String], catalogCount: Int) -> [String] {
        let limit = min(maxRecent, max(catalogCount / 2, 1))
        return Array(([id] + recent.filter { $0 != id }).prefix(limit))
    }
}

/// Where a wallpaper goes in a frame of another shape: scaled to fill it, centred across, and moved up or down so
/// its subject (`focusY`) is as close to the middle as the picture allows.
public enum FocusWallpaperLayout {
    public static func frame(imageSize: CGSize, in container: CGSize, focusY: Double) -> CGRect {
        guard imageSize.width > 0, imageSize.height > 0, container.width > 0, container.height > 0 else {
            return CGRect(origin: .zero, size: container)
        }
        let scale = max(container.width / imageSize.width, container.height / imageSize.height)
        let size = CGSize(width: imageSize.width * scale, height: imageSize.height * scale)
        let top = min(max(CGFloat(focusY) * size.height - container.height / 2, 0), size.height - container.height)
        return CGRect(x: (container.width - size.width) / 2, y: -top, width: size.width, height: size.height)
    }
}

/// Keeps the downloaded wallpapers within bounds.
public enum FocusWallpaperCache {
    /// A downloaded picture: its wallpaper's id, size and when it was last shown.
    public struct Entry: Hashable, Sendable {
        public var id: String
        public var bytes: Int
        public var lastUsed: Date

        public init(id: String, bytes: Int, lastUsed: Date) {
            self.id = id
            self.bytes = bytes
            self.lastUsed = lastUsed
        }
    }

    public static let maxFiles = 40
    public static let maxBytes = 60_000_000

    /// The ids of the pictures to delete: those no longer in the catalog (when it's known), then the least recently
    /// used until at most `maxFiles` pictures and `maxBytes` remain. Those in `keeping` (the one on screen) stay.
    public static func evictions(_ entries: [Entry], catalog: Set<String>?, keeping: Set<String> = [],
                                 maxFiles: Int = maxFiles, maxBytes: Int = maxBytes) -> [String] {
        var removed: [String] = []
        var kept: [Entry] = []
        for entry in entries {
            if let catalog, !catalog.contains(entry.id), !keeping.contains(entry.id) {
                removed.append(entry.id)
            } else {
                kept.append(entry)
            }
        }
        var count = kept.count
        var bytes = kept.reduce(0) { $0 + $1.bytes }
        let oldestFirst = kept.sorted { ($0.lastUsed, $0.id) < ($1.lastUsed, $1.id) }
        for entry in oldestFirst where count > maxFiles || bytes > maxBytes {
            guard !keeping.contains(entry.id) else { continue }
            removed.append(entry.id)
            count -= 1
            bytes -= entry.bytes
        }
        return removed
    }
}
