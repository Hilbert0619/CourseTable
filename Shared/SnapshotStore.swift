import Foundation
import CourseKit

/// The JSON file the app writes and the widget reads, in the shared App Group container.
enum SnapshotStore {
    /// The App Group in the entitlements files, used by Xcode and TestFlight builds.
    static let appGroupID = "group.io.github.hilbert0619.CourseTable"
    static let fileName = "schedule-snapshot.json"
    /// The widget's bundle identifier is the app's plus this suffix.
    static let widgetBundleSuffix = ".Widget"

    /// `nil` when no App Group is available, e.g. in unsigned CI builds.
    static let defaultURL: URL? = {
        let listedGroups = Bundle.main.object(forInfoDictionaryKey: "ALTAppGroups") as? [String] ?? []
        let candidates = appGroupCandidates(bundleIdentifier: Bundle.main.bundleIdentifier, listedGroups: listedGroups)
        for groupID in candidates {
            if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID) {
                return container.appendingPathComponent(fileName)
            }
        }
        return nil
    }()

    /// App Group identifiers to try, in order; the first one the app is entitled to wins.
    ///
    /// Sideloading tools re-sign with the user's own team and rename things: iloader and
    /// AltStore/SideStore turn the app into `<bundle id>.<TEAMID>` and give it (and the widget) the
    /// group `group.<that id>`; AltStore/SideStore also list their groups under `ALTAppGroups` in
    /// Info.plist. Deriving the group from the running bundle's identifier lets the app and the
    /// widget land on the same group whichever way the app was installed.
    static func appGroupCandidates(bundleIdentifier: String?, listedGroups: [String] = []) -> [String] {
        var candidates = listedGroups
        if var appID = bundleIdentifier {
            if appID.hasSuffix(widgetBundleSuffix) {
                appID.removeLast(widgetBundleSuffix.count)
            }
            candidates.append("group.\(appID)")
        }
        candidates.append(appGroupID)
        var seen: Set<String> = []
        return candidates.filter { seen.insert($0).inserted }
    }

    static func write(_ snapshot: ScheduleSnapshot, to url: URL) throws {
        try snapshot.encoded().write(to: url, options: .atomic)
    }

    static func read(from url: URL? = SnapshotStore.defaultURL) -> ScheduleSnapshot? {
        guard let url, let data = try? Data(contentsOf: url) else { return nil }
        return try? ScheduleSnapshot.decode(from: data)
    }
}
