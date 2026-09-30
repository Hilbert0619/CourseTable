import Foundation
import Testing
import CourseKit
@testable import VocabKit

enum VocabFixture {
    static let utc: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }()

    /// Study days starting at 04:00 UTC, the app's default rule in a fixed time zone.
    static let clock = StudyClock(calendar: utc)
    /// Days starting at midnight UTC, the convention of the FSRS reference tests.
    static let midnightClock = StudyClock(calendar: utc, dayStart: .midnight)

    /// `"2026-09-30 21:15"` in UTC.
    static func at(_ text: String) -> Date {
        let parts = text.split(separator: " ")
        let date = LocalDate(String(parts[0]))!
        let time = parts.count > 1 ? TimeOfDay(String(parts[1]))! : .midnight
        return date.date(at: time, calendar: utc)
    }

    static func day(_ text: String) -> Int { LocalDate(text)!.daysSinceEpoch }

    static func event(_ word: String, _ rating: Rating, _ time: String, mode: ReviewMode = .recognition,
                      ms: Int = 5000, id: UUID = UUID()) -> ReviewEvent {
        let date = at(time)
        return ReviewEvent(id: id, headword: word, rating: rating, mode: mode, reviewedAt: date,
                           studyDay: clock.day(of: date), durationMs: ms)
    }
}

func expectClose(_ value: Double?, _ expected: Double, _ tolerance: Double = 1e-6,
                 sourceLocation: SourceLocation = #_sourceLocation) {
    guard let value else {
        Issue.record("value is nil", sourceLocation: sourceLocation)
        return
    }
    #expect(abs(value - expected) <= tolerance, "\(value) ≠ \(expected)", sourceLocation: sourceLocation)
}

/// Oracle values from the FSRS-6 reference tests (ts-fsrs `FSRS-6.test.ts`, mirrored in swift-fsrs
/// `FSRSV6Tests.swift`), which use days that start at midnight UTC.
@Suite struct FSRSReferenceTests {
    let scheduler = Scheduler()
    let start = VocabFixture.at("2022-12-29 12:30")

    func review(_ card: Card, _ rating: Rating, at time: Date, scheduler: Scheduler? = nil) -> Card {
        (scheduler ?? self.scheduler).review(card, rating: rating, at: time, day: VocabFixture.midnightClock.day(of: time))
    }

    @Test func firstAnswer() {
        let cards = Rating.allCases.map { review(Card(headword: "a"), $0, at: start) }
        #expect(cards.map { $0.memory!.stability } == [0.212, 1.2931, 2.3065, 8.2956])
        expectClose(cards[0].memory?.difficulty, 6.4133, 1e-7)
        expectClose(cards[1].memory?.difficulty, 5.11217071, 1e-7)
        expectClose(cards[2].memory?.difficulty, 2.11810397, 1e-7)
        expectClose(cards[3].memory?.difficulty, 1.0, 1e-7)
        #expect(cards.map(\.scheduledDays) == [0, 0, 0, 8])
        #expect(cards.map(\.state) == [.learning, .learning, .learning, .review])
        #expect(cards.map(\.reps) == [1, 1, 1, 1])
        #expect(cards.map(\.lapses) == [0, 0, 0, 0])
    }

    @Test func intervalHistory() {
        let ratings: [Rating] = [.good, .good, .good, .good, .good, .good, .again, .again, .good, .good, .good, .good, .good]
        var card = Card(headword: "a")
        var now = start
        var intervals: [Int] = []
        for rating in ratings {
            card = review(card, rating, at: now)
            intervals.append(card.scheduledDays)
            // Answer exactly when due, as the reference test does.
            switch card.due {
            case .moment(let moment): now = moment
            case .day: now = now.addingTimeInterval(TimeInterval(card.scheduledDays) * 86_400)
            case nil: Issue.record("no due date")
            }
        }
        #expect(intervals == [0, 2, 11, 46, 163, 498, 0, 0, 2, 4, 7, 12, 21])
        #expect(card.lapses == 1)
        #expect(card.reps == 13)
    }

    func memorySequence(_ scheduler: Scheduler) -> Card {
        let ratings: [Rating] = [.again, .good, .good, .good, .good, .good]
        let gaps: [Double] = [0, 0, 1, 3, 8, 21]
        var card = Card(headword: "a")
        var now = start
        for (rating, gap) in zip(ratings, gaps) {
            now = now.addingTimeInterval(gap * 86_400)
            card = review(card, rating, at: now, scheduler: scheduler)
        }
        return card
    }

    @Test func memoryStateWithShortTermScheduling() {
        let card = memorySequence(scheduler)
        expectClose(card.memory?.stability, 53.62691, 1e-4)
        expectClose(card.memory?.difficulty, 6.3574867, 1e-4)
    }

    @Test func memoryStateWithoutShortTermScheduling() {
        let card = memorySequence(Scheduler(parameters: FSRSParameters(enableShortTerm: false)))
        expectClose(card.memory?.stability, 53.335106, 1e-4)
        expectClose(card.memory?.difficulty, 6.3574867, 1e-4)
    }

    @Test func forgettingCurve() {
        let fsrs = FSRS()
        expectClose(fsrs.retrievability(elapsedDays: 1, stability: 1), 0.9, 1e-4)
        expectClose(fsrs.retrievability(elapsedDays: 0, stability: 5), 1, 1e-9)
        #expect(fsrs.decay == -0.1542)
        #expect(fsrs.retrievability(elapsedDays: 30, stability: 10) < fsrs.retrievability(elapsedDays: 10, stability: 10))
    }

    @Test func intervalFollowsDesiredRetention() {
        #expect(FSRS().interval(stability: 10.4) == 10)
        #expect(FSRS().interval(stability: 0.2) == 1)
        let stricter = FSRS(parameters: FSRSParameters(desiredRetention: 0.95))
        #expect(stricter.interval(stability: 100) < 100)
        let looser = FSRS(parameters: FSRSParameters(desiredRetention: 0.8))
        #expect(looser.interval(stability: 100) > 100)
        #expect(FSRS(parameters: FSRSParameters(maximumInterval: 30)).interval(stability: 500) == 30)
    }

    @Test func parametersAreKeptInRange() {
        let parameters = FSRSParameters(desiredRetention: 0.5, learningSteps: [0, 5, 2000], relearningSteps: [])
        #expect(parameters.desiredRetention == 0.7)
        #expect(parameters.learningSteps == [5])
        #expect(parameters.relearningSteps.isEmpty)
    }
}

@Suite struct SchedulerTests {
    let scheduler = Scheduler()
    let clock = VocabFixture.clock

    func review(_ card: Card, _ rating: Rating, _ time: String) -> Card {
        let date = VocabFixture.at(time)
        return scheduler.review(card, rating: rating, at: date, day: clock.day(of: date))
    }

    @Test func learningSteps() {
        let new = Card(headword: "abandon")
        let again = review(new, .again, "2026-09-30 10:00")
        #expect(again.due == .moment(VocabFixture.at("2026-09-30 10:01")))
        let hard = review(new, .hard, "2026-09-30 10:00")
        #expect(hard.due == .moment(VocabFixture.at("2026-09-30 10:06")))  // (1 + 10) / 2, rounded up
        #expect(hard.step == 0)
        let good = review(new, .good, "2026-09-30 10:00")
        #expect(good.due == .moment(VocabFixture.at("2026-09-30 10:10")))
        #expect(good.step == 1)
        let graduated = review(good, .good, "2026-09-30 10:10")
        #expect(graduated.state == .review)
        #expect(graduated.due == .day(VocabFixture.day("2026-09-30") + graduated.scheduledDays))
        #expect(graduated.firstReviewDay == VocabFixture.day("2026-09-30"))
    }

    @Test func forgettingAReviewWordRelearnsIt() {
        var card = review(Card(headword: "abandon"), .easy, "2026-09-01 10:00")
        #expect(card.state == .review)
        card = review(card, .again, "2026-09-09 10:00")
        #expect(card.state == .relearning)
        #expect(card.lapses == 1)
        #expect(card.due == .moment(VocabFixture.at("2026-09-09 10:10")))
        card = review(card, .good, "2026-09-09 10:10")
        #expect(card.state == .review)
        #expect(card.lapses == 1)
        #expect(card.firstReviewDay == VocabFixture.day("2026-09-01"))
    }

    @Test func lateNightCountsForThePreviousStudyDay() {
        let evening = review(Card(headword: "abandon"), .good, "2026-09-30 23:30")
        let afterMidnight = review(evening, .good, "2026-10-01 01:00")
        #expect(evening.lastReviewDay == VocabFixture.day("2026-09-30"))
        #expect(afterMidnight.lastReviewDay == VocabFixture.day("2026-09-30"))
        // Same study day → elapsed 0 → the short-term formula, same as reviewing ten minutes later.
        let tenMinutesLater = review(evening, .good, "2026-09-30 23:40")
        #expect(afterMidnight.memory == tenMinutesLater.memory)
        #expect(afterMidnight.due == .day(VocabFixture.day("2026-09-30") + afterMidnight.scheduledDays))
    }

    @Test func reviewIntervalsAreOrdered() {
        var card = review(Card(headword: "abandon"), .good, "2026-09-01 10:00")
        card = review(card, .good, "2026-09-01 10:10")
        let day = "2026-09-0\(1 + card.scheduledDays) 09:00"
        let intervals = [Rating.hard, .good, .easy].map { review(card, $0, day).scheduledDays }
        #expect(intervals[0] < intervals[1])
        #expect(intervals[1] < intervals[2])
    }

    @Test func replayRebuildsTheSameCard() {
        let events = [
            VocabFixture.event("abandon", .good, "2026-09-01 10:00"),
            VocabFixture.event("abandon", .good, "2026-09-01 10:10"),
            VocabFixture.event("abandon", .again, "2026-09-03 21:00"),
            VocabFixture.event("abandon", .good, "2026-09-03 21:10"),
            VocabFixture.event("abandon", .hard, "2026-09-05 08:00"),
        ]
        var card = Card(headword: "abandon")
        for event in events {
            card = scheduler.review(card, rating: event.rating, at: event.reviewedAt, day: event.studyDay)
        }
        #expect(scheduler.replay(events, headword: "abandon") == card)
        #expect(scheduler.replay(events.reversed(), headword: "abandon") == card)
        #expect(scheduler.replay(events)["abandon"] == card)
    }

    @Test func replayOrdersSimultaneousEventsById() {
        let first = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
        let second = UUID(uuidString: "00000000-0000-0000-0000-000000000002")!
        let a = VocabFixture.event("x", .again, "2026-09-01 10:00", id: second)
        let b = VocabFixture.event("x", .easy, "2026-09-01 10:00", id: first)
        #expect(Scheduler.ordered([a, b]).map(\.id) == [first, second])
        #expect(scheduler.replay([a, b], headword: "x") == scheduler.replay([b, a], headword: "x"))
    }

    @Test func dueChecks() {
        let learning = review(Card(headword: "a"), .good, "2026-09-30 10:00")
        #expect(!learning.isDue(at: VocabFixture.at("2026-09-30 10:09"), today: VocabFixture.day("2026-09-30")))
        #expect(learning.isDue(at: VocabFixture.at("2026-09-30 10:10"), today: VocabFixture.day("2026-09-30")))
        #expect(learning.dueDay(today: 5) == 5)
        #expect(!Card(headword: "b").isDue(at: .distantFuture, today: .max))
        #expect(Card(headword: "b").dueDay(today: 5) == nil)
        let graduated = review(Card(headword: "c"), .easy, "2026-09-30 10:00")
        #expect(graduated.isDue(on: VocabFixture.day("2026-10-08"), dayEnd: .distantPast))
        #expect(!graduated.isDue(on: VocabFixture.day("2026-10-07"), dayEnd: .distantFuture))
    }

    @Test func retrievability() {
        let card = review(Card(headword: "a"), .easy, "2026-09-30 10:00")
        #expect(scheduler.retrievability(of: Card(headword: "b"), on: 0) == nil)
        expectClose(scheduler.retrievability(of: card, on: VocabFixture.day("2026-09-30")), 1)
        let later = scheduler.retrievability(of: card, on: VocabFixture.day("2026-10-08"))!
        #expect(later > 0.85 && later < 0.95)
    }

    @Test func headwordKeys() {
        #expect(Headword.key(" Don’t  Mind ") == "don't mind")
        #expect(Headword.key("ABANDON") == "abandon")
        #expect(ReviewEvent(headword: "Abandon", rating: .good, mode: .choice, reviewedAt: .now, studyDay: 1).headword == "abandon")
    }
}

@Suite struct StudyClockTests {
    let clock = VocabFixture.clock

    @Test func dayStartsAtFour() {
        #expect(clock.day(of: VocabFixture.at("2026-09-30 03:59")) == VocabFixture.day("2026-09-29"))
        #expect(clock.day(of: VocabFixture.at("2026-09-30 04:00")) == VocabFixture.day("2026-09-30"))
        #expect(clock.day(of: VocabFixture.at("2026-09-30 23:59")) == VocabFixture.day("2026-09-30"))
        #expect(clock.start(of: VocabFixture.day("2026-09-30")) == VocabFixture.at("2026-09-30 04:00"))
        #expect(clock.end(of: VocabFixture.day("2026-09-30")) == VocabFixture.at("2026-10-01 04:00"))
        #expect(clock.date(of: VocabFixture.day("2026-09-30")) == LocalDate(year: 2026, month: 9, day: 30))
    }

    @Test func otherTimeZonesAndDayStarts() {
        var shanghai = Calendar(identifier: .gregorian)
        shanghai.timeZone = TimeZone(secondsFromGMT: 8 * 3600)!  // China: UTC+8, no daylight saving
        let clock = StudyClock(calendar: shanghai)
        // 2026-09-30 19:30 UTC is 2026-10-01 03:30 in Shanghai: still the study day of 09-30.
        #expect(clock.day(of: VocabFixture.at("2026-09-30 19:30")) == VocabFixture.day("2026-09-30"))
        #expect(clock.day(of: VocabFixture.at("2026-09-30 20:00")) == VocabFixture.day("2026-10-01"))
        #expect(VocabFixture.midnightClock.day(of: VocabFixture.at("2026-09-30 00:00")) == VocabFixture.day("2026-09-30"))
    }
}
