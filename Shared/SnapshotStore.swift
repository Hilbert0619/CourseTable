import Foundation
import CourseKit

/// The JSON file the app writes and the widget reads, in the shared App Group container.
enum SnapshotStore {
    static let appGroupID = "group.io.github.hilbert0619.CourseTable"
    static let fileName = "schedule-snapshot.json"

    /// `nil` when the App Group isn't available, e.g. in unsigned CI builds.
    static var defaultURL: URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroupID)?
            .appendingPathComponent(fileName)
    }

    static func write(_ snapshot: ScheduleSnapshot, to url: URL) throws {
        try snapshot.encoded().write(to: url, options: .atomic)
    }

    static func read(from url: URL? = SnapshotStore.defaultURL) -> ScheduleSnapshot? {
        guard let url, let data = try? Data(contentsOf: url) else { return nil }
        return try? ScheduleSnapshot.decode(from: data)
    }
}
