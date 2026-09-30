import Foundation

/// Where a word is in the learning cycle (the FSRS card states).
public enum CardState: Int, Codable, Sendable, CaseIterable {
    /// Never answered.
    case new = 0
    /// Being learned today: comes back after minutes (learning steps).
    case learning = 1
    /// Graduated: comes back after days.
    case review = 2
    /// Forgotten in a review: comes back after minutes, then graduates again.
    case relearning = 3
}

/// How a question is asked. The word has one card however it is asked; the mode only changes the
/// question and how the answer turns into a rating (see `AnswerGrader`).
public enum ReviewMode: String, Codable, Sendable, CaseIterable {
    /// See the word, recall the meaning, then rate yourself.
    case recognition
    /// Pick the meaning out of four.
    case choice
    /// Hear the word and see the meaning, then type it.
    case spelling
}

/// When a word is due. Words in learning steps are due at a moment; graduated words on a study day.
public enum Due: Hashable, Sendable, Codable {
    case moment(Date)
    /// A study day number (see `StudyClock`).
    case day(Int)
}

/// One answer to one word: the record that is stored, synced, and replayed to rebuild every card.
public struct ReviewEvent: Hashable, Sendable, Codable, Identifiable {
    /// Made on the device, so uploading the same event twice is harmless.
    public var id: UUID
    public var headword: String
    public var rating: Rating
    public var mode: ReviewMode
    public var reviewedAt: Date
    /// The study day the answer counts for, fixed when it was given (see `StudyClock`).
    public var studyDay: Int
    /// Time from showing the question to answering it.
    public var durationMs: Int

    public init(
        id: UUID = UUID(),
        headword: String,
        rating: Rating,
        mode: ReviewMode,
        reviewedAt: Date,
        studyDay: Int,
        durationMs: Int = 0
    ) {
        self.id = id
        self.headword = Headword.key(headword)
        self.rating = rating
        self.mode = mode
        self.reviewedAt = reviewedAt
        self.studyDay = studyDay
        self.durationMs = max(0, durationMs)
    }
}

/// A user's learning state for one word. It is derived: replaying the word's `ReviewEvent`s through
/// the `Scheduler` always gives the same card.
public struct Card: Hashable, Sendable, Codable {
    public var headword: String
    public var state: CardState = .new
    /// `nil` until the first answer.
    public var memory: MemoryState?
    /// Index into the learning or relearning steps.
    public var step = 0
    public var reps = 0
    /// Times the word was forgotten after graduating.
    public var lapses = 0
    /// Days between the last review and the due day (0 in learning steps).
    public var scheduledDays = 0
    /// `nil` for new words.
    public var due: Due?
    public var lastReview: Date?
    public var lastReviewDay: Int?
    /// The study day of the first answer: the day the word was learned.
    public var firstReviewDay: Int?

    public init(headword: String) {
        self.headword = Headword.key(headword)
    }

    /// Whether the word should be shown now: learning words once their moment has come, graduated
    /// words from their due day on. New words are never "due".
    public func isDue(at now: Date, today: Int) -> Bool {
        switch due {
        case .moment(let moment): moment <= now
        case .day(let day): day <= today
        case nil: false
        }
    }

    /// Whether the word needs to be answered at some point during study day `today`.
    public func isDue(on today: Int, dayEnd: Date) -> Bool {
        switch due {
        case .moment(let moment): moment < dayEnd
        case .day(let day): day <= today
        case nil: false
        }
    }

    /// Study day the word is due, if it has been learned. Learning-step words count as due today.
    public func dueDay(today: Int) -> Int? {
        switch due {
        case .moment: today
        case .day(let day): day
        case nil: nil
        }
    }
}

/// Word keys: the same word typed or listed in different ways maps to one key.
public enum Headword {
    /// Lower-case, trimmed, single spaces, straight apostrophes: `" Don’t  Mind "` → `"don't mind"`.
    public static func key(_ word: String) -> String {
        let straightened = word.replacingOccurrences(of: "\u{2019}", with: "'").replacingOccurrences(of: "\u{2018}", with: "'")
        return straightened.lowercased().split(whereSeparator: \.isWhitespace).joined(separator: " ")
    }
}
