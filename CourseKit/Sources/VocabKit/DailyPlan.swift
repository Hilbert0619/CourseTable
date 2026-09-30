import Foundation
import CourseKit

/// Today's task: new words to learn and words to review, worked out from the cards and the history.
public struct DailyPlan: Hashable, Sendable {
    public var newTarget: Int
    /// Words answered for the first time today (from any book).
    public var newDone: Int
    /// New words still to learn today, limited by what is left in the book.
    public var newRemaining: Int
    /// Words learned before today that were reviewed today.
    public var reviewsDone: Int
    /// Words learned before today that are due by the end of today.
    public var reviewsRemaining: Int
    /// Words of the book never answered.
    public var unseenInBook: Int
    /// The next new words of the book, in book order (`newRemaining` of them).
    public var newWords: [String]
    /// Rough time for what is left, from the learner's own answer times; `nil` until there are enough
    /// answers to base it on.
    public var estimatedMinutes: Int?

    /// Answers needed before a time estimate is shown.
    public static let answersForEstimate = 50

    /// Done ÷ (done + remaining), 1 when there is nothing to do.
    public var progress: Double {
        let done = newDone + reviewsDone
        let total = done + newRemaining + reviewsRemaining
        return total == 0 ? 1 : Double(done) / Double(total)
    }

    public var isComplete: Bool { newRemaining == 0 && reviewsRemaining == 0 }

    public init(cards: [String: Card], history: [ReviewEvent], book: [String], newWordsPerDay: Int,
                now: Date, clock: StudyClock) {
        let today = clock.day(of: now)
        let dayEnd = clock.end(of: today)
        let learnedBefore = cards.values.filter { ($0.firstReviewDay ?? .max) < today }

        newTarget = max(0, newWordsPerDay)
        newDone = cards.values.filter { $0.firstReviewDay == today }.count
        let unseen = book.map(Headword.key).filter { (cards[$0]?.state ?? .new) == .new }
        unseenInBook = Set(unseen).count
        newRemaining = max(0, min(newTarget - newDone, unseenInBook))
        var seen: Set<String> = []
        newWords = Array(unseen.filter { seen.insert($0).inserted }.prefix(newRemaining))

        let learnedKeys = Set(learnedBefore.map(\.headword))
        reviewsDone = Set(history.filter { $0.studyDay == today && learnedKeys.contains($0.headword) }.map(\.headword)).count
        reviewsRemaining = learnedBefore.filter { $0.isDue(on: today, dayEnd: dayEnd) }.count
        estimatedMinutes = Self.estimate(history: history, today: today, newWords: newRemaining, reviews: reviewsRemaining)
    }

    /// Median answer time × answers expected: for new words the average number of answers on the day a
    /// word was learned, for reviews the average answers per word per review day.
    static func estimate(history: [ReviewEvent], today: Int, newWords: Int, reviews: Int) -> Int? {
        let past = history.filter { $0.studyDay < today }
        guard past.count >= answersForEstimate else { return nil }
        let durations = past.map(\.durationMs).sorted()
        let median = Double(durations[durations.count / 2]) / 1000

        let firstDay = Dictionary(grouping: past, by: \.headword).mapValues { $0.map(\.studyDay).min()! }
        var answersOnFirstDay = 0
        var answersOnReviewDays = 0
        var reviewDays: Set<String> = []
        for event in past {
            if event.studyDay == firstDay[event.headword] {
                answersOnFirstDay += 1
            } else {
                answersOnReviewDays += 1
                reviewDays.insert("\(event.headword)|\(event.studyDay)")
            }
        }
        let perNewWord = firstDay.isEmpty ? 1 : Double(answersOnFirstDay) / Double(firstDay.count)
        let perReview = reviewDays.isEmpty ? 1 : Double(answersOnReviewDays) / Double(reviewDays.count)
        let seconds = median * (Double(newWords) * perNewWord + Double(reviews) * perReview)
        return Int((seconds / 60).rounded(.up))
    }
}

/// Days left until the exam and the pace needed to finish the book. An estimate, not a promise.
public struct ExamCountdown: Hashable, Sendable {
    public var examDate: LocalDate
    /// Study days before the exam day, counting today.
    public var daysLeft: Int
    public var wordsLeft: Int
    /// New words a day needed to see every word of the book before the exam.
    public var newWordsPerDayNeeded: Int

    /// `nil` once the exam day has come.
    public init?(examDate: LocalDate, today: Int, wordsLeft: Int) {
        let days = examDate.daysSinceEpoch - today
        guard days > 0 else { return nil }
        self.examDate = examDate
        self.daysLeft = days
        self.wordsLeft = wordsLeft
        self.newWordsPerDayNeeded = (wordsLeft + days - 1) / days
    }
}
