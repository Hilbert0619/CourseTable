import Foundation

/// One study day's numbers.
public struct DayStats: Hashable, Sendable {
    public var day: Int
    /// Words answered for the first time.
    public var newWords = 0
    /// Words learned earlier that were answered.
    public var reviewedWords = 0
    public var answers = 0
    /// Answers that weren't Again.
    public var correct = 0
    public var durationMs = 0

    public var accuracy: Double? { answers == 0 ? nil : Double(correct) / Double(answers) }
    public var studied: Bool { answers > 0 }
}

/// Review status of learned words.
public struct StatusBuckets: Hashable, Sendable {
    /// Due today (including learning steps).
    public var needsReview = 0
    /// Due within the next `approachingDays` days.
    public var approaching = 0
    /// Due later than that.
    public var stable = 0
}

/// A book's progress.
public struct BookProgress: Hashable, Sendable {
    public var total: Int
    /// Answered at least once.
    public var learned: Int
    /// Stability of at least `masteredStability` days.
    public var mastered: Int
    /// Learned and due today.
    public var due: Int
}

/// Statistics from the stored answers and cards only. Every threshold here is a display rule, shown in
/// the app next to the number it produces; none of them affects scheduling.
public enum VocabStats {
    /// A word counts as mastered once FSRS expects it to stay above 90% recall for this many days.
    public static let masteredStability = 30.0
    public static let approachingDays = 3
    /// A word forgotten this many times after learning it is a "hard word".
    public static let hardWordLapses = 2

    /// Numbers for every study day with answers.
    public static func days(_ events: [ReviewEvent]) -> [Int: DayStats] {
        let firstDay = Dictionary(grouping: events, by: \.headword).mapValues { $0.map(\.studyDay).min()! }
        var result: [Int: DayStats] = [:]
        var wordsOnDay: [Int: Set<String>] = [:]
        for event in events {
            var stats = result[event.studyDay] ?? DayStats(day: event.studyDay)
            stats.answers += 1
            if event.rating.isRecalled { stats.correct += 1 }
            stats.durationMs += event.durationMs
            if wordsOnDay[event.studyDay, default: []].insert(event.headword).inserted {
                if firstDay[event.headword] == event.studyDay { stats.newWords += 1 } else { stats.reviewedWords += 1 }
            }
            result[event.studyDay] = stats
        }
        return result
    }

    /// The `count` days ending on `today`, oldest first, with empty days included.
    public static func series(_ events: [ReviewEvent], endingOn today: Int, count: Int = 30) -> [DayStats] {
        let byDay = days(events)
        return (0..<max(0, count)).reversed().map { offset in
            let day = today - offset
            return byDay[day] ?? DayStats(day: day)
        }
    }

    /// Consecutive study days up to today. Before the first answer of the day the streak still counts
    /// up to yesterday, so it only breaks once a whole day is missed.
    public static func streak(_ events: [ReviewEvent], today: Int) -> (days: Int, includesToday: Bool) {
        let studied = Set(events.map(\.studyDay))
        let includesToday = studied.contains(today)
        var day = includesToday ? today : today - 1
        var length = 0
        while studied.contains(day) {
            length += 1
            day -= 1
        }
        return (length, includesToday)
    }

    public static func buckets(_ cards: some Sequence<Card>, today: Int) -> StatusBuckets {
        var buckets = StatusBuckets()
        for card in cards {
            guard let dueDay = card.dueDay(today: today) else { continue }
            if dueDay <= today {
                buckets.needsReview += 1
            } else if dueDay <= today + approachingDays {
                buckets.approaching += 1
            } else {
                buckets.stable += 1
            }
        }
        return buckets
    }

    public static func progress(book: [String], cards: [String: Card], today: Int) -> BookProgress {
        let keys = Set(book.map(Headword.key))
        let learned = keys.compactMap { cards[$0] }.filter { $0.state != .new }
        return BookProgress(
            total: keys.count,
            learned: learned.count,
            mastered: learned.filter { ($0.memory?.stability ?? 0) >= masteredStability }.count,
            due: learned.filter { ($0.dueDay(today: today) ?? .max) <= today }.count
        )
    }

    /// Most-forgotten first.
    public static func hardWords(_ cards: some Sequence<Card>) -> [Card] {
        cards.filter { $0.lapses >= hardWordLapses }.sorted { ($0.lapses, $1.headword) > ($1.lapses, $0.headword) }
    }

    /// Learned words that will be due by study day `day` if nothing is reviewed before then, e.g.
    /// tomorrow's reviews.
    public static func dueCount(_ cards: some Sequence<Card>, by day: Int, today: Int) -> Int {
        cards.filter { ($0.dueDay(today: today) ?? .max) <= day }.count
    }
}
