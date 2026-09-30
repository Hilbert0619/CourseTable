import Foundation
import Testing
import CourseKit
@testable import VocabKit

@Suite struct DailyPlanTests {
    let clock = VocabFixture.clock
    let history = [
        // Learned 09-27, graduated for 2 days: due 09-29, so overdue today.
        VocabFixture.event("abandon", .good, "2026-09-27 10:00"),
        VocabFixture.event("abandon", .good, "2026-09-27 10:10"),
        // Learned 09-25 with Easy: due 10-03.
        VocabFixture.event("ability", .easy, "2026-09-25 10:00"),
        // Learned 09-20, reviewed today.
        VocabFixture.event("swiftly", .easy, "2026-09-20 10:00"),
        VocabFixture.event("swiftly", .good, "2026-09-30 10:00"),
        // Learned today, in its learning steps.
        VocabFixture.event("desert", .good, "2026-09-30 11:00"),
    ]
    let book = ["ability", "desert", "abandon", "swiftly", "china", "abandoned", "adopt", "adapt"]

    @Test func todaysPlan() {
        let cards = Scheduler().replay(history)
        let plan = DailyPlan(cards: cards, history: history, book: book, newWordsPerDay: 3,
                             now: VocabFixture.at("2026-09-30 20:00"), clock: clock)
        #expect(plan.newTarget == 3)
        #expect(plan.newDone == 1)
        #expect(plan.newRemaining == 2)
        #expect(plan.newWords == ["china", "abandoned"])
        #expect(plan.unseenInBook == 4)
        #expect(plan.reviewsDone == 1)
        #expect(plan.reviewsRemaining == 1)
        #expect(plan.progress == 0.4)
        #expect(!plan.isComplete)
        #expect(plan.estimatedMinutes == nil)  // fewer than 50 answers so far
    }

    @Test func limitsAndEdges() {
        let cards = Scheduler().replay(history)
        let now = VocabFixture.at("2026-09-30 20:00")
        let done = DailyPlan(cards: cards, history: history, book: book, newWordsPerDay: 1, now: now, clock: clock)
        #expect(done.newRemaining == 0)
        #expect(done.newWords.isEmpty)
        let shortBook = DailyPlan(cards: cards, history: history, book: ["China", "china"], newWordsPerDay: 20, now: now, clock: clock)
        #expect(shortBook.newRemaining == 1)
        #expect(shortBook.newWords == ["china"])
        let empty = DailyPlan(cards: [:], history: [], book: [], newWordsPerDay: 20, now: now, clock: clock)
        #expect(empty.progress == 1)
        #expect(empty.isComplete)
        // Tomorrow, today's new word is a review-to-be and the counts start again.
        let tomorrow = DailyPlan(cards: cards, history: history, book: book, newWordsPerDay: 3,
                                 now: VocabFixture.at("2026-10-01 09:00"), clock: clock)
        #expect(tomorrow.newDone == 0)
        #expect(tomorrow.newRemaining == 3)
        #expect(tomorrow.reviewsDone == 0)
        #expect(tomorrow.reviewsRemaining == 2)  // abandon (overdue) and desert (still in a learning step)
    }

    @Test func timeEstimate() {
        var events: [ReviewEvent] = []
        for index in 0..<20 {
            for _ in 0..<2 {
                events.append(ReviewEvent(headword: "w\(index)", rating: .good, mode: .choice, reviewedAt: .now, studyDay: 1, durationMs: 6000))
            }
            events.append(ReviewEvent(headword: "w\(index)", rating: .good, mode: .recognition, reviewedAt: .now, studyDay: 5, durationMs: 6000))
        }
        // 6 s × (10 new × 2 answers + 30 reviews × 1 answer) = 5 min.
        #expect(DailyPlan.estimate(history: events, today: 10, newWords: 10, reviews: 30) == 5)
        #expect(DailyPlan.estimate(history: Array(events.prefix(49)), today: 10, newWords: 10, reviews: 30) == nil)
        #expect(DailyPlan.estimate(history: events, today: 5, newWords: 10, reviews: 30) == nil)  // only past days count
        #expect(DailyPlan.estimate(history: events, today: 10, newWords: 0, reviews: 0) == 0)
    }

    @Test func examCountdown() throws {
        let today = VocabFixture.day("2026-09-30")
        let countdown = try #require(ExamCountdown(examDate: LocalDate(year: 2026, month: 12, day: 19), today: today, wordsLeft: 4000))
        #expect(countdown.daysLeft == 80)
        #expect(countdown.newWordsPerDayNeeded == 50)
        #expect(ExamCountdown(examDate: LocalDate(year: 2026, month: 12, day: 19), today: today, wordsLeft: 4001)?.newWordsPerDayNeeded == 51)
        #expect(ExamCountdown(examDate: LocalDate(year: 2026, month: 12, day: 19), today: today, wordsLeft: 0)?.newWordsPerDayNeeded == 0)
        #expect(ExamCountdown(examDate: LocalDate(year: 2026, month: 9, day: 30), today: today, wordsLeft: 10) == nil)
    }
}

@Suite struct StudySessionTests {
    let clock = VocabFixture.clock
    let scheduler = Scheduler()
    let cards = Scheduler().replay([
        VocabFixture.event("abandon", .good, "2026-09-27 10:00"),
        VocabFixture.event("abandon", .good, "2026-09-27 10:10"),  // due 09-29
        VocabFixture.event("ability", .easy, "2026-09-20 10:00"),  // due 09-28
        VocabFixture.event("desert", .easy, "2026-09-25 10:00"),  // due 10-03: not today
        VocabFixture.event("swiftly", .again, "2026-09-30 19:55"),  // learning step, due 19:56
    ])

    func session(_ mode: SessionMode = .recognition, seed: UInt64 = 1, reviewLimit: Int? = nil,
                 scheduler: Scheduler? = nil) -> StudySession {
        StudySession(cards: cards, newWords: ["china", "abandoned", "abandon"], now: VocabFixture.at("2026-09-30 20:00"),
                     scheduler: scheduler ?? self.scheduler, clock: clock, mode: mode, seed: seed, reviewLimit: reviewLimit)
    }

    @Test func wholeSession() {
        var session = session()
        var now = VocabFixture.at("2026-09-30 20:00")
        var kinds: [StudyItem.Kind] = []
        var words: [String] = []
        var missedOnce = false
        while let item = session.next(at: now) {
            kinds.append(item.kind)
            words.append(item.headword)
            #expect(item.mode == (item.kind == .new ? .choice : .recognition))
            let rating: Rating = item.headword == "abandoned" && !missedOnce ? .again : .good
            if rating == .again { missedOnce = true }
            session.answer(item, rating: rating, at: now, durationMs: 4000)
            now = now.addingTimeInterval(60)
            #expect(kinds.count < 20)
        }
        #expect(kinds == [.learning, .review, .review, .new, .new, .learning, .learning, .learning, .learning])
        #expect(words[0] == "swiftly")
        #expect(Set(words[1...2]) == ["abandon", "ability"])
        #expect(words[3...] == ["china", "abandoned", "abandoned", "swiftly", "china", "abandoned"])
        #expect(session.cards.values.allSatisfy { $0.state == .review })
        #expect(session.events.count == 9)
        #expect(session.events.map(\.studyDay).allSatisfy { $0 == VocabFixture.day("2026-09-30") })
        #expect(session.summary == SessionSummary(newWords: 2, reviewedWords: 2, answers: 9, correct: 8, durationMs: 36_000))
        #expect(session.summary.accuracy == 8.0 / 9.0)
        #expect(session.remaining(at: now) == (0, 0, 0))
        // Replaying the stored events gives the session's cards.
        let replayed = scheduler.replay(session.events)
        for (word, card) in replayed {
            #expect(scheduler.replay(session.events.filter { $0.headword == word } + eventsBefore(word), headword: word) == session.cards[word])
            #expect(card.reps >= 1)
        }
    }

    /// The events `cards` were built from, per word.
    func eventsBefore(_ word: String) -> [ReviewEvent] {
        switch word {
        case "abandon": [VocabFixture.event("abandon", .good, "2026-09-27 10:00"), VocabFixture.event("abandon", .good, "2026-09-27 10:10")]
        case "ability": [VocabFixture.event("ability", .easy, "2026-09-20 10:00")]
        case "swiftly": [VocabFixture.event("swiftly", .again, "2026-09-30 19:55")]
        default: []
        }
    }

    @Test func alreadyLearnedWordsAreNotNew() {
        let session = session()
        #expect(session.remaining(at: VocabFixture.at("2026-09-30 20:00")) == (2, 2, 1))
    }

    @Test func reviewOrderDependsOnlyOnTheSeed() {
        let now = VocabFixture.at("2026-09-30 20:00")
        func reviewOrder(seed: UInt64) -> [String] {
            var session = session(seed: seed)
            var order: [String] = []
            while let item = session.next(at: now), item.kind != .new {
                if item.kind == .review { order.append(item.headword) }
                session.answer(item, rating: .easy, at: now, durationMs: 0)
            }
            return order
        }
        #expect(reviewOrder(seed: 5) == reviewOrder(seed: 5))
        #expect(Set((1...10).map { reviewOrder(seed: $0) }).count == 2)
    }

    @Test func reviewLimit() {
        #expect(session(reviewLimit: 1).remaining(at: VocabFixture.at("2026-09-30 20:00")).review == 1)
        #expect(session(reviewLimit: 0).remaining(at: VocabFixture.at("2026-09-30 20:00")).review == 0)
    }

    @Test func mixedModeIsStablePerQuestion() {
        let now = VocabFixture.at("2026-09-30 20:00")
        let session = session(.mixed, seed: 3)
        #expect(session.next(at: now) == session.next(at: now))
        var modes: Set<ReviewMode> = []
        for seed in 0..<30 {
            if let item = self.session(.mixed, seed: UInt64(seed)).next(at: now) { modes.insert(item.mode) }
        }
        #expect(modes == Set(ReviewMode.allCases))
        #expect(self.session(.spelling).next(at: now)?.mode == .spelling)
    }

    @Test func stepsDueLaterWaitForTheNextSession() {
        let slow = Scheduler(parameters: FSRSParameters(learningSteps: [30, 60]))
        var session = StudySession(cards: [:], newWords: ["abandon"], now: VocabFixture.at("2026-09-30 20:00"),
                                   scheduler: slow, clock: clock, mode: .recognition, seed: 1)
        let now = VocabFixture.at("2026-09-30 20:00")
        let item = session.next(at: now)!
        session.answer(item, rating: .good, at: now, durationMs: 1000)
        #expect(session.next(at: now) == nil)
        #expect(session.waiting(at: now).count == 1)
        #expect(session.waiting(at: now).firstDue == VocabFixture.at("2026-09-30 21:00"))
        #expect(session.next(at: VocabFixture.at("2026-09-30 20:40"))?.headword == "abandon")
    }
}

@Suite struct VocabStatsTests {
    let events = [
        VocabFixture.event("abandon", .good, "2026-09-28 09:00", ms: 1000),
        VocabFixture.event("abandon", .good, "2026-09-28 09:10", ms: 1000),
        VocabFixture.event("ability", .again, "2026-09-28 09:20", ms: 1000),
        VocabFixture.event("ability", .good, "2026-09-28 09:30", ms: 1000),
        VocabFixture.event("abandon", .hard, "2026-09-29 20:00", ms: 2000),
        VocabFixture.event("desert", .good, "2026-09-30 02:00", ms: 2000),  // still the study day of 09-29
        VocabFixture.event("ability", .good, "2026-09-30 21:00", ms: 3000),
    ]

    @Test func dayNumbers() {
        let days = VocabStats.days(events)
        let d28 = days[VocabFixture.day("2026-09-28")]!
        #expect((d28.newWords, d28.reviewedWords, d28.answers, d28.correct, d28.durationMs) == (2, 0, 4, 3, 4000))
        #expect(d28.accuracy == 0.75)
        let d29 = days[VocabFixture.day("2026-09-29")]!
        #expect((d29.newWords, d29.reviewedWords, d29.answers, d29.correct) == (1, 1, 2, 2))
        let d30 = days[VocabFixture.day("2026-09-30")]!
        #expect((d30.newWords, d30.reviewedWords, d30.answers) == (0, 1, 1))
        #expect(DayStats(day: 0).accuracy == nil)
    }

    @Test func series() {
        let series = VocabStats.series(events, endingOn: VocabFixture.day("2026-09-30"), count: 5)
        #expect(series.map(\.day) == (VocabFixture.day("2026-09-26")...VocabFixture.day("2026-09-30")).map { $0 })
        #expect(series.map(\.answers) == [0, 0, 4, 2, 1])
        #expect(series.map(\.studied) == [false, false, true, true, true])
    }

    @Test func streak() {
        #expect(VocabStats.streak(events, today: VocabFixture.day("2026-09-30")) == (3, true))
        #expect(VocabStats.streak(events, today: VocabFixture.day("2026-10-01")) == (3, false))
        #expect(VocabStats.streak(events, today: VocabFixture.day("2026-10-02")) == (0, false))
        #expect(VocabStats.streak([], today: 5) == (0, false))
    }

    func card(_ word: String, _ due: Due?, stability: Double = 10, lapses: Int = 0) -> Card {
        var card = Card(headword: word)
        card.state = due == nil ? .new : .review
        card.due = due
        card.memory = due == nil ? nil : MemoryState(stability: stability, difficulty: 5)
        card.lapses = lapses
        return card
    }

    @Test func buckets() {
        let today = 100
        let cards = [card("a", .day(99)), card("b", .day(100)), card("c", .moment(.now)), card("d", .day(102)),
                     card("e", .day(103)), card("f", .day(104)), card("g", nil)]
        #expect(VocabStats.buckets(cards, today: today) == StatusBuckets(needsReview: 3, approaching: 2, stable: 1))
        #expect(VocabStats.dueCount(cards, by: 101, today: today) == 3)
        #expect(VocabStats.dueCount(cards, by: 104, today: today) == 6)
    }

    @Test func bookProgress() {
        let cards = ["a": card("a", .day(110), stability: 40), "b": card("b", .day(100), stability: 5), "c": card("c", nil)]
        #expect(VocabStats.progress(book: ["a", "B", "c", "d", "a"], cards: cards, today: 100)
                == BookProgress(total: 4, learned: 2, mastered: 1, due: 1))
    }

    @Test func hardWords() {
        let cards = [card("x", .day(1), lapses: 2), card("y", .day(1), lapses: 3), card("w", .day(1), lapses: 2), card("z", .day(1), lapses: 1)]
        #expect(VocabStats.hardWords(cards).map(\.headword) == ["y", "w", "x"])
    }
}

@Suite struct SyncMergeTests {
    @Test func mergingEvents() {
        let a = VocabFixture.event("abandon", .good, "2026-09-28 09:00")
        let b = VocabFixture.event("abandon", .good, "2026-09-28 09:10")
        let c = VocabFixture.event("ability", .easy, "2026-09-28 09:20")
        let incoming = [a, b, b, c]
        let fresh = SyncMerge.newEvents(incoming, knownIDs: [a.id])
        #expect(fresh == [b, c])
        #expect(SyncMerge.affectedWords(fresh) == ["abandon", "ability"])
        let rebuilt = SyncMerge.rebuiltCards(allEvents: [a, b, c], newEvents: [c], scheduler: Scheduler())
        #expect(Array(rebuilt.keys) == ["ability"])
        #expect(rebuilt["ability"] == Scheduler().replay([c], headword: "ability"))
    }

    @Test func twoDevicesConverge() {
        // Each device studied offline; after syncing both hold all events and the same cards.
        let phone = [VocabFixture.event("abandon", .good, "2026-09-28 09:00"), VocabFixture.event("abandon", .good, "2026-09-28 09:10")]
        let tablet = [VocabFixture.event("abandon", .again, "2026-09-30 21:00"), VocabFixture.event("abandon", .good, "2026-09-30 21:10")]
        let scheduler = Scheduler()
        let onPhone = scheduler.replay(phone + SyncMerge.newEvents(tablet, knownIDs: Set(phone.map(\.id))))
        let onTablet = scheduler.replay(tablet + SyncMerge.newEvents(phone, knownIDs: Set(tablet.map(\.id))))
        #expect(onPhone == onTablet)
        #expect(onPhone["abandon"]?.lapses == 1)
    }

    @Test func cursor() {
        let cursor = VocabFixture.at("2026-09-30 12:00")
        #expect(SyncMerge.pullStart(cursor: nil) == nil)
        #expect(SyncMerge.pullStart(cursor: cursor) == VocabFixture.at("2026-09-30 11:50"))
        #expect(SyncMerge.advance(nil, receivedAt: []) == nil)
        #expect(SyncMerge.advance(cursor, receivedAt: []) == cursor)
        #expect(SyncMerge.advance(cursor, receivedAt: [VocabFixture.at("2026-09-30 11:55"), VocabFixture.at("2026-09-30 12:05")])
                == VocabFixture.at("2026-09-30 12:05"))
    }

    @Test func newestEditWins() {
        let early = Versioned(20, updatedAt: VocabFixture.at("2026-09-30 10:00"))
        let late = Versioned(30, updatedAt: VocabFixture.at("2026-09-30 11:00"))
        #expect(SyncMerge.resolve(local: late, remote: early) == .pushLocal)
        #expect(SyncMerge.resolve(local: early, remote: late) == .takeRemote)
        #expect(SyncMerge.resolve(local: early, remote: early) == .unchanged)
        #expect(SyncMerge.resolve(local: early, remote: nil) == .pushLocal)
        #expect(SyncMerge.resolve(local: nil, remote: early) == .takeRemote)
        #expect(SyncMerge.resolve(local: Optional<Versioned<Int>>.none, remote: nil) == .unchanged)
    }

    @Test func batches() {
        #expect(SyncMerge.batches(Array(1...5), size: 2) == [[1, 2], [3, 4], [5]])
        #expect(SyncMerge.batches([Int](), size: 2).isEmpty)
        #expect(SyncMerge.batches(Array(1...1200)).map(\.count) == [500, 500, 200])
    }
}
