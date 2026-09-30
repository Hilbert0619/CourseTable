import Foundation

/// Which question type a session uses for learning steps and reviews.
public enum SessionMode: String, Codable, Sendable, CaseIterable {
    case recognition
    case choice
    case spelling
    /// A different type per question, picked by the session seed.
    case mixed
}

/// One question in a session.
public struct StudyItem: Hashable, Sendable {
    public enum Kind: Hashable, Sendable {
        /// First time: show the word page, then a multiple-choice check.
        case new
        /// A learning or relearning step.
        case learning
        /// A graduated word due today.
        case review
    }

    public var headword: String
    public var kind: Kind
    public var mode: ReviewMode
}

/// A study session. Order: learning steps that are due, then due reviews (shuffled), then new words
/// in book order. When nothing else is left, learning steps due within `learnAhead` are shown early.
/// Words that come back later the same day (learning steps) are answered again in the session.
public struct StudySession: Sendable {
    public let scheduler: Scheduler
    public let clock: StudyClock
    public let mode: SessionMode
    public let seed: UInt64
    /// The session's cards, updated after each answer.
    public private(set) var cards: [String: Card]
    /// Every answer given, oldest first: these are stored and synced.
    public private(set) var events: [ReviewEvent] = []

    private var reviewQueue: [String]
    private var newQueue: [String]
    private var learning: Set<String>
    private var introduced: Set<String> = []
    private var reviewed: Set<String> = []

    /// How early a learning step may be shown when there is nothing else to study.
    public static let learnAhead: TimeInterval = 20 * 60

    /// - Parameters:
    ///   - cards: all of the learner's cards.
    ///   - newWords: words to introduce, in order (usually `DailyPlan.newWords`).
    ///   - reviewLimit: at most this many due reviews (`nil`: all of them).
    public init(cards: [String: Card], newWords: [String], now: Date, scheduler: Scheduler, clock: StudyClock,
                mode: SessionMode, seed: UInt64, reviewLimit: Int? = nil) {
        self.scheduler = scheduler
        self.clock = clock
        self.mode = mode
        self.seed = seed
        self.cards = cards
        let today = clock.day(of: now)
        var random = SeededRandom(seed: seed)
        let due = cards.values.filter { $0.state == .review && $0.isDue(at: now, today: today) }.map(\.headword).sorted()
        reviewQueue = Array(due.shuffled(using: &random).prefix(reviewLimit ?? .max))
        var seen: Set<String> = []
        newQueue = newWords.map(Headword.key).filter { (cards[$0]?.state ?? .new) == .new && seen.insert($0).inserted }
        learning = Set(cards.values.filter { $0.state == .learning || $0.state == .relearning }.map(\.headword))
    }

    /// The next question, or `nil` when the session is done.
    public func next(at now: Date) -> StudyItem? {
        if let word = learningWord(dueBy: now) { return item(word, .learning) }
        if let word = reviewQueue.first { return item(word, .review) }
        if let word = newQueue.first { return item(word, .new) }
        if let word = learningWord(dueBy: now.addingTimeInterval(Self.learnAhead)) { return item(word, .learning) }
        return nil
    }

    /// Records an answer to `item` and returns the event to store.
    @discardableResult
    public mutating func answer(_ item: StudyItem, rating: Rating, at time: Date, durationMs: Int,
                                id: UUID = UUID()) -> ReviewEvent {
        let day = clock.day(of: time)
        let card = scheduler.review(cards[item.headword] ?? Card(headword: item.headword), rating: rating, at: time, day: day)
        cards[item.headword] = card
        if let index = reviewQueue.firstIndex(of: item.headword) { reviewQueue.remove(at: index) }
        if let index = newQueue.firstIndex(of: item.headword) { newQueue.remove(at: index) }
        if card.state == .learning || card.state == .relearning {
            learning.insert(item.headword)
        } else {
            learning.remove(item.headword)
        }
        switch item.kind {
        case .new: introduced.insert(item.headword)
        case .review: reviewed.insert(item.headword)
        case .learning: break
        }
        let event = ReviewEvent(id: id, headword: item.headword, rating: rating, mode: item.mode,
                                reviewedAt: time, studyDay: day, durationMs: durationMs)
        events.append(event)
        return event
    }

    /// Questions left, not counting learning steps due later than `learnAhead`.
    public func remaining(at now: Date) -> (new: Int, review: Int, learning: Int) {
        let limit = now.addingTimeInterval(Self.learnAhead)
        return (newQueue.count, reviewQueue.count, learning.filter { dueMoment($0).map { $0 <= limit } ?? false }.count)
    }

    /// Learning steps that will be due after the session ends, and when the first one is.
    public func waiting(at now: Date) -> (count: Int, firstDue: Date?) {
        let limit = now.addingTimeInterval(Self.learnAhead)
        let later = learning.compactMap(dueMoment).filter { $0 > limit }
        return (later.count, later.min())
    }

    public var summary: SessionSummary {
        SessionSummary(newWords: introduced.count, reviewedWords: reviewed.count, answers: events.count,
                       correct: events.filter(\.rating.isRecalled).count, durationMs: events.map(\.durationMs).reduce(0, +))
    }

    private func item(_ headword: String, _ kind: StudyItem.Kind) -> StudyItem {
        let reps = cards[headword]?.reps ?? 0
        let questionMode: ReviewMode = switch (kind, mode) {
        case (.new, _): .choice
        case (_, .recognition): .recognition
        case (_, .choice): .choice
        case (_, .spelling): .spelling
        case (_, .mixed):
            ReviewMode.allCases[Int(SeededRandom.seed(String(seed), headword, String(reps)) % UInt64(ReviewMode.allCases.count))]
        }
        return StudyItem(headword: headword, kind: kind, mode: questionMode)
    }

    private func dueMoment(_ headword: String) -> Date? {
        if case .moment(let moment) = cards[headword]?.due { return moment }
        return nil
    }

    private func learningWord(dueBy limit: Date) -> String? {
        learning.compactMap { word in dueMoment(word).map { (word, $0) } }
            .filter { $0.1 <= limit }
            .min { ($0.1, $0.0) < ($1.1, $1.0) }?.0
    }
}

/// The end-of-session numbers.
public struct SessionSummary: Hashable, Sendable {
    public var newWords: Int
    public var reviewedWords: Int
    public var answers: Int
    public var correct: Int
    public var durationMs: Int

    /// Share of answers that weren't Again; `nil` with no answers.
    public var accuracy: Double? { answers == 0 ? nil : Double(correct) / Double(answers) }
}
