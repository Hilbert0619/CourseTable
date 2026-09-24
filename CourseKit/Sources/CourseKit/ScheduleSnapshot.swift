import Foundation

/// The timetable as the app hands it to the home-screen widget (a JSON file in the shared App Group).
public struct ScheduleSnapshot: Hashable, Codable, Sendable {
    public static let currentVersion = 1

    public var version: Int
    public var timetable: Timetable

    public init(timetable: Timetable) {
        self.version = Self.currentVersion
        self.timetable = timetable
    }

    public func encoded() throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(self)
    }

    public static func decode(from data: Data) throws -> ScheduleSnapshot {
        let snapshot = try JSONDecoder().decode(ScheduleSnapshot.self, from: data)
        guard snapshot.version <= currentVersion else {
            throw SnapshotError.unsupportedVersion(snapshot.version)
        }
        return snapshot
    }
}

public enum SnapshotError: Error, Equatable {
    case unsupportedVersion(Int)
}
