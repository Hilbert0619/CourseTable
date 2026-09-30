import Foundation

/// A value with the time it was last changed, for "newest edit wins" syncing (settings, favourites,
/// the learner's own books).
public struct Versioned<Value: Hashable & Sendable>: Hashable, Sendable {
    public var value: Value
    public var updatedAt: Date

    public init(_ value: Value, updatedAt: Date) {
        self.value = value
        self.updatedAt = updatedAt
    }
}

/// The sync rules that don't need the network (docs/vocabulary-spec.md §8). Answers are never edited,
/// so merging them is taking the union by id and replaying the affected words.
public enum SyncMerge {
    /// Pulls start this far before the cursor, so rows that were committed late aren't missed.
    public static let pullOverlap: TimeInterval = 10 * 60
    public static let pushBatchSize = 500

    /// Downloaded events the device doesn't have yet.
    public static func newEvents(_ incoming: [ReviewEvent], knownIDs: Set<UUID>) -> [ReviewEvent] {
        var seen = knownIDs
        return incoming.filter { seen.insert($0.id).inserted }
    }

    /// Words whose cards must be rebuilt after receiving `events`.
    public static func affectedWords(_ events: [ReviewEvent]) -> Set<String> {
        Set(events.map(\.headword))
    }

    /// Rebuilds the cards of the words touched by `newEvents`, from all of those words' events.
    public static func rebuiltCards(allEvents: [ReviewEvent], newEvents: [ReviewEvent],
                                    scheduler: Scheduler) -> [String: Card] {
        let words = affectedWords(newEvents)
        return scheduler.replay(allEvents.filter { words.contains($0.headword) })
    }

    /// Where the next pull starts: `received_at > start` (`nil`: from the beginning).
    public static func pullStart(cursor: Date?) -> Date? {
        cursor?.addingTimeInterval(-pullOverlap)
    }

    /// The cursor after a pull: the newest server time seen.
    public static func advance(_ cursor: Date?, receivedAt: [Date]) -> Date? {
        ([cursor].compactMap { $0 } + receivedAt).max()
    }

    public enum Resolution: Hashable, Sendable {
        case unchanged
        /// Upload the device's value.
        case pushLocal
        /// Keep the server's value.
        case takeRemote
    }

    /// Newest edit wins; on a tie nothing changes.
    public static func resolve<Value>(local: Versioned<Value>?, remote: Versioned<Value>?) -> Resolution {
        switch (local, remote) {
        case (nil, nil): .unchanged
        case (_?, nil): .pushLocal
        case (nil, _?): .takeRemote
        case let (local?, remote?):
            if local.updatedAt > remote.updatedAt { .pushLocal } else if remote.updatedAt > local.updatedAt { .takeRemote } else { .unchanged }
        }
    }

    /// Splits uploads into batches of `size`.
    public static func batches<T>(_ items: [T], size: Int = pushBatchSize) -> [[T]] {
        stride(from: 0, to: items.count, by: max(1, size)).map { Array(items[$0..<min($0 + size, items.count)]) }
    }
}
