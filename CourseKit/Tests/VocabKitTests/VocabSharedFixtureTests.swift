import Foundation
import Testing
import CourseKit
@testable import VocabKit

/// The vocabulary cases in `spec/fixtures/vocab-*.json`, which the Android port's tests read too
/// (`android/core/src/test/kotlin/io/github/hilbert0619/vocabkit/VocabSharedFixtureTests.kt`): both platforms must
/// schedule, shuffle, grade, import and search the same way.
enum VocabSharedFixtures {
    static let directory = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()  // VocabKitTests
        .deletingLastPathComponent()  // Tests
        .deletingLastPathComponent()  // CourseKit
        .deletingLastPathComponent()  // the repository
        .appendingPathComponent("spec")
        .appendingPathComponent("fixtures")

    static func decode<T: Decodable>(_ type: T.Type, from name: String) throws -> T {
        try JSONDecoder().decode(T.self, from: Data(contentsOf: directory.appendingPathComponent(name)))
    }

    static let utc: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }()

    /// `"2026-09-30 21:15"` in UTC.
    static func at(_ text: String) -> Date { VocabFixture.at(text) }

    static func moment(_ date: Date) -> String {
        "\(LocalDate(date, calendar: utc)) \(TimeOfDay(date, calendar: utc))"
    }

    static func day(_ day: Int) -> String { LocalDate(daysSinceEpoch: day).description }

    struct LogEvent: Decodable {
        var word: String
        var rating: Int
        var at: String
        var id: String?
    }

    static func events(_ log: [LogEvent], clock: StudyClock) -> [ReviewEvent] {
        log.map { event in
            ReviewEvent(id: event.id.flatMap(UUID.init(uuidString:)) ?? UUID(), headword: event.word, rating: Rating(rawValue: event.rating)!,
                        mode: .recognition, reviewedAt: at(event.at), studyDay: clock.day(of: at(event.at)))
        }
    }
}

@Suite struct VocabSharedFixtureTests {
    // MARK: - Scheduling

    struct Scheduling: Decodable {
        struct Reference: Decodable {
            struct FirstAnswer: Decodable { var stability: [Double]; var difficulty: [Double]; var scheduledDays: [Int]; var state: [Int] }
            struct History: Decodable { var ratings: [Int]; var intervals: [Int] }
            struct Memory: Decodable {
                var enableShortTerm: Bool; var ratings: [Int]; var gaps: [Int]; var stability: Double; var difficulty: Double; var tolerance: Double
            }
            var start: String
            var firstAnswer: FirstAnswer
            var intervalHistory: History
            var memory: [Memory]
        }
        struct Parameters: Decodable {
            var desiredRetention: Double; var maximumInterval: Int; var learningSteps: [Int]; var relearningSteps: [Int]; var enableShortTerm: Bool
            var value: FSRSParameters {
                FSRSParameters(desiredRetention: desiredRetention, maximumInterval: maximumInterval, learningSteps: learningSteps,
                               relearningSteps: relearningSteps, enableShortTerm: enableShortTerm)
            }
        }
        struct CardCase: Decodable {
            var headword: String; var state: Int; var step: Int; var reps: Int; var lapses: Int; var scheduledDays: Int; var due: String?
            var stability: Double?; var difficulty: Double?; var lastReview: String?; var lastReviewDay: String?; var firstReviewDay: String?
        }
        struct Retrievability: Decodable { var day: String; var value: Double? }
        struct Replay: Decodable {
            var name: String; var parameters: Parameters; var dayStart: String; var events: [VocabSharedFixtures.LogEvent]
            var cards: [CardCase]; var retrievability: [Retrievability]?
        }
        struct Plan: Decodable {
            var now: String; var newWordsPerDay: Int; var newTarget: Int; var newDone: Int; var newRemaining: Int; var newWords: [String]
            var unseenInBook: Int; var reviewsDone: Int; var reviewsRemaining: Int; var progress: Double
        }
        var reference: [Reference]
        var replays: [Replay]
        var planLog: [[VocabSharedFixtures.LogEvent]]
        var planBook: [[String]]
        var plans: [Plan]
    }

    @Test func fsrsReference() throws {
        let reference = try VocabSharedFixtures.decode(Scheduling.self, from: "vocab-scheduling.json").reference[0]
        let start = VocabSharedFixtures.at(reference.start)
        let clock = VocabFixture.midnightClock
        let scheduler = Scheduler()
        let first = Rating.allCases.map { scheduler.review(Card(headword: "a"), rating: $0, at: start, day: clock.day(of: start)) }
        #expect(first.map { $0.memory!.stability } == reference.firstAnswer.stability)
        for (card, difficulty) in zip(first, reference.firstAnswer.difficulty) {
            expectClose(card.memory?.difficulty, difficulty, 1e-7)
        }
        #expect(first.map(\.scheduledDays) == reference.firstAnswer.scheduledDays)
        #expect(first.map(\.state.rawValue) == reference.firstAnswer.state)

        var card = Card(headword: "a")
        var now = start
        var intervals: [Int] = []
        for rating in reference.intervalHistory.ratings {
            card = scheduler.review(card, rating: Rating(rawValue: rating)!, at: now, day: clock.day(of: now))
            intervals.append(card.scheduledDays)
            if case .moment(let moment) = card.due { now = moment } else { now = now.addingTimeInterval(TimeInterval(card.scheduledDays) * 86_400) }
        }
        #expect(intervals == reference.intervalHistory.intervals)

        for memory in reference.memory {
            let scheduler = Scheduler(parameters: FSRSParameters(enableShortTerm: memory.enableShortTerm))
            var card = Card(headword: "a")
            var now = start
            for (rating, gap) in zip(memory.ratings, memory.gaps) {
                now = now.addingTimeInterval(TimeInterval(gap) * 86_400)
                card = scheduler.review(card, rating: Rating(rawValue: rating)!, at: now, day: clock.day(of: now))
            }
            expectClose(card.memory?.stability, memory.stability, memory.tolerance)
            expectClose(card.memory?.difficulty, memory.difficulty, memory.tolerance)
        }
    }

    @Test func replays() throws {
        for replay in try VocabSharedFixtures.decode(Scheduling.self, from: "vocab-scheduling.json").replays {
            let clock = StudyClock(calendar: VocabSharedFixtures.utc, dayStart: TimeOfDay(replay.dayStart)!)
            let scheduler = Scheduler(parameters: replay.parameters.value)
            let cards = scheduler.replay(VocabSharedFixtures.events(replay.events, clock: clock))
            #expect(cards.keys.sorted() == replay.cards.map(\.headword), "\(replay.name)")
            for expected in replay.cards {
                guard let card = cards[expected.headword] else { continue }
                let due: String? = switch card.due {
                case .moment(let date): "at " + VocabSharedFixtures.moment(date)
                case .day(let day): "day " + VocabSharedFixtures.day(day)
                case nil: nil
                }
                let actual = [String(card.state.rawValue), String(card.step), String(card.reps), String(card.lapses), String(card.scheduledDays),
                              due ?? "-", card.lastReview.map(VocabSharedFixtures.moment) ?? "-",
                              card.lastReviewDay.map(VocabSharedFixtures.day) ?? "-", card.firstReviewDay.map(VocabSharedFixtures.day) ?? "-"]
                let wanted = [String(expected.state), String(expected.step), String(expected.reps), String(expected.lapses),
                              String(expected.scheduledDays), expected.due ?? "-", expected.lastReview ?? "-", expected.lastReviewDay ?? "-",
                              expected.firstReviewDay ?? "-"]
                #expect(actual == wanted, "\(replay.name): \(expected.headword)")
                if let stability = expected.stability { expectClose(card.memory?.stability, stability) }
                if let difficulty = expected.difficulty { expectClose(card.memory?.difficulty, difficulty) }
            }
            for check in replay.retrievability ?? [] {
                let value = scheduler.retrievability(of: cards.values.first!, on: LocalDate(check.day)!.daysSinceEpoch)
                if let expected = check.value { expectClose(value, expected) } else { #expect(value == nil) }
            }
        }
    }

    @Test func dailyPlans() throws {
        let fixture = try VocabSharedFixtures.decode(Scheduling.self, from: "vocab-scheduling.json")
        let history = VocabSharedFixtures.events(fixture.planLog[0], clock: VocabFixture.clock)
        let cards = Scheduler().replay(history)
        for expected in fixture.plans {
            let plan = DailyPlan(cards: cards, history: history, book: fixture.planBook[0], newWordsPerDay: expected.newWordsPerDay,
                                 now: VocabSharedFixtures.at(expected.now), clock: VocabFixture.clock)
            let actual: [Int] = [plan.newTarget, plan.newDone, plan.newRemaining, plan.unseenInBook, plan.reviewsDone, plan.reviewsRemaining]
            let wanted: [Int] = [expected.newTarget, expected.newDone, expected.newRemaining, expected.unseenInBook, expected.reviewsDone,
                                 expected.reviewsRemaining]
            #expect(actual == wanted, "\(expected.now)")
            #expect(plan.newWords == expected.newWords, "\(expected.now)")
            expectClose(plan.progress, expected.progress)
        }
    }

    // MARK: - Randomness

    struct Randomness: Decodable {
        struct SplitMix: Decodable { var seed: String; var values: [String] }
        struct FNV: Decodable { var parts: [String]; var seed: String }
        struct Bounded: Decodable { var seed: String; var upperBounds: [String]; var values: [String] }
        struct Shuffle: Decodable { var seed: String; var count: Int; var order: [Int] }
        struct PoolEntry: Decodable { var word: String; var translation: String }
        struct Choice: Decodable { var word: String; var seed: String; var optionCount: Int; var options: [String]?; var correctIndex: Int? }
        struct Session: Decodable { var mode: String; var seed: String; var reviewLimit: Int?; var items: [String] }
        var splitMix: [SplitMix]
        var fnv: [FNV]
        var bounded: [Bounded]
        var shuffles: [Shuffle]
        var pool: [[PoolEntry]]
        var choices: [Choice]
        var sessionLog: [[VocabSharedFixtures.LogEvent]]
        var sessions: [Session]
    }

    @Test func seededRandomness() throws {
        let fixture = try VocabSharedFixtures.decode(Randomness.self, from: "vocab-random.json")
        for item in fixture.splitMix {
            var random = SeededRandom(seed: UInt64(item.seed)!)
            #expect((0..<item.values.count).map { _ in String(random.next()) } == item.values)
        }
        for item in fixture.fnv {
            let seed = switch item.parts.count {
            case 1: SeededRandom.seed(item.parts[0])
            case 2: SeededRandom.seed(item.parts[0], item.parts[1])
            default: SeededRandom.seed(item.parts[0], item.parts[1], item.parts[2])
            }
            #expect(String(seed) == item.seed, "\(item.parts)")
        }
        for item in fixture.bounded {
            var random = SeededRandom(seed: UInt64(item.seed)!)
            #expect(item.upperBounds.map { String(random.next(upperBound: UInt64($0)!)) } == item.values)
        }
        for item in fixture.shuffles {
            var random = SeededRandom(seed: UInt64(item.seed)!)
            #expect(Array(0..<item.count).shuffled(using: &random) == item.order, "seed \(item.seed)")
        }
    }

    @Test func choiceQuestions() throws {
        let fixture = try VocabSharedFixtures.decode(Randomness.self, from: "vocab-random.json")
        let entries = fixture.pool[0].map { WordEntry(headword: $0.word, translation: $0.translation) }
        let pool = ChoicePool(entries)
        for item in fixture.choices {
            let target = entries.first { $0.headword == Headword.key(item.word) }!
            let question = pool.question(for: target, seed: UInt64(item.seed)!, optionCount: item.optionCount)
            #expect(question?.options == item.options, "\(item.word) seed \(item.seed)")
            #expect(question?.correctIndex == item.correctIndex, "\(item.word) seed \(item.seed)")
        }
    }

    @Test func sessions() throws {
        let fixture = try VocabSharedFixtures.decode(Randomness.self, from: "vocab-random.json")
        let cards = Scheduler().replay(VocabSharedFixtures.events(fixture.sessionLog[0], clock: VocabFixture.clock))
        for expected in fixture.sessions {
            var session = StudySession(cards: cards, newWords: ["china", "abandoned", "abandon", "adopt"], now: VocabSharedFixtures.at("2026-09-30 20:00"),
                                       scheduler: Scheduler(), clock: VocabFixture.clock, mode: SessionMode(rawValue: expected.mode)!,
                                       seed: UInt64(expected.seed)!, reviewLimit: expected.reviewLimit)
            var now = VocabSharedFixtures.at("2026-09-30 20:00")
            var items: [String] = []
            var missed: Set<String> = []
            while let item = session.next(at: now), items.count < 40 {
                let kind = switch item.kind { case .new: "new"; case .learning: "learning"; case .review: "review" }
                items.append("\(item.headword) \(kind) \(item.mode.rawValue)")
                let rating: Rating = item.kind == .learning && missed.insert(item.headword).inserted ? .again : .good
                session.answer(item, rating: rating, at: now, durationMs: 1000)
                now = now.addingTimeInterval(90)
            }
            #expect(items == expected.items, "\(expected.mode) seed \(expected.seed)")
        }
    }

    // MARK: - Texts

    struct Texts: Decodable {
        struct Key: Decodable { var text: String; var key: String }
        struct Spelling: Decodable { var typed: String; var word: String; var correct: Bool }
        struct Hint: Decodable { var word: String; var hint: String }
        struct Diff: Decodable { var typed: String; var word: String; var parts: [[String]] }
        struct Sense: Decodable { var tag: String?; var label: String?; var meanings: [String]; var partOfSpeech: String?; var formatted: String }
        struct Senses: Decodable {
            var translation: String; var senses: [Sense]; var shortMeaning: String; var primaryPartOfSpeech: String?; var meaningTokens: [String]
        }
        struct Abbreviation: Decodable { var text: String; var partOfSpeech: String? }
        struct Word: Decodable {
            var headword: String; var display: String; var meaning: String?; var phonetic: String?; var partOfSpeech: String?
            var example: String?; var exampleTranslation: String?; var inDictionary: Bool; var line: Int
        }
        struct Import: Decodable {
            var text: String; var format: String?; var detected: String; var words: [Word]; var unknown: [Word]; var duplicates: [Word]; var unreadable: [Int]
        }
        struct PoolEntry: Decodable { var word: String; var translation: String; var bnc: Int?; var frq: Int?; var exchange: String }
        struct Search: Decodable { var query: String; var limit: Int; var results: [String] }
        var keys: [Key]
        var spelling: [Spelling]
        var hints: [Hint]
        var diffs: [Diff]
        var senses: [Senses]
        var abbreviations: [Abbreviation]
        var known: [[String]]
        var imports: [Import]
        var searchPool: [[PoolEntry]]
        var searches: [Search]
    }

    @Test func grading() throws {
        let fixture = try VocabSharedFixtures.decode(Texts.self, from: "vocab-text.json")
        for item in fixture.keys { #expect(Headword.key(item.text) == item.key, "\(item.text)") }
        for item in fixture.spelling { #expect(AnswerGrader.isCorrectSpelling(item.typed, of: item.word) == item.correct, "\(item.typed)") }
        for item in fixture.hints { #expect(AnswerGrader.hint(for: item.word) == item.hint) }
        for item in fixture.diffs {
            let parts = AnswerGrader.diff(typed: item.typed, word: item.word).map { part in
                switch part {
                case .same(let text): ["same", text]
                case .missing(let text): ["missing", text]
                case .extra(let text): ["extra", text]
                }
            }
            #expect(parts == item.parts, "\(item.typed) → \(item.word)")
        }
    }

    @Test func meanings() throws {
        let fixture = try VocabSharedFixtures.decode(Texts.self, from: "vocab-text.json")
        for item in fixture.senses {
            let entry = WordEntry(headword: "x", translation: item.translation)
            let senses = entry.senses.map { line in
                Texts.Sense(tag: line.partOfSpeechTag, label: line.label, meanings: line.meanings, partOfSpeech: line.partOfSpeech?.rawValue,
                            formatted: line.formatted())
            }
            #expect(senses.map(\.tag) == item.senses.map(\.tag), "\(item.translation)")
            #expect(senses.map(\.label) == item.senses.map(\.label), "\(item.translation)")
            #expect(senses.map(\.meanings) == item.senses.map(\.meanings), "\(item.translation)")
            #expect(senses.map(\.partOfSpeech) == item.senses.map(\.partOfSpeech), "\(item.translation)")
            #expect(senses.map(\.formatted) == item.senses.map(\.formatted), "\(item.translation)")
            #expect(entry.shortMeaning == item.shortMeaning, "\(item.translation)")
            #expect(entry.primaryPartOfSpeech?.rawValue == item.primaryPartOfSpeech, "\(item.translation)")
            #expect(entry.meaningTokens.sorted() == item.meaningTokens, "\(item.translation)")
        }
        for item in fixture.abbreviations {
            #expect(PartOfSpeech(abbreviation: item.text)?.rawValue == item.partOfSpeech, "\(item.text)")
        }
    }

    @Test func imports() throws {
        let fixture = try VocabSharedFixtures.decode(Texts.self, from: "vocab-text.json")
        let known = Set(fixture.known[0])
        func words(_ list: [ImportedWord]) -> [String] {
            list.map { "\($0.line) \($0.headword) | \($0.display) | \($0.meaning ?? "-") | \($0.phonetic ?? "-") | \($0.partOfSpeech ?? "-") | \($0.example ?? "-") | \($0.exampleTranslation ?? "-") | \($0.inDictionary)" }
        }
        func words(_ list: [Texts.Word]) -> [String] {
            list.map { "\($0.line) \($0.headword) | \($0.display) | \($0.meaning ?? "-") | \($0.phonetic ?? "-") | \($0.partOfSpeech ?? "-") | \($0.example ?? "-") | \($0.exampleTranslation ?? "-") | \($0.inDictionary)" }
        }
        for item in fixture.imports {
            let preview = WordListImport.preview(item.text, format: item.format.flatMap(WordListPreview.Format.init(rawValue:))) { known.contains($0) }
            #expect(preview.format.rawValue == item.detected, "\(item.text)")
            #expect(words(preview.words) == words(item.words), "\(item.text)")
            #expect(words(preview.unknown) == words(item.unknown), "\(item.text)")
            #expect(words(preview.duplicates) == words(item.duplicates), "\(item.text)")
            #expect(preview.unreadable == item.unreadable, "\(item.text)")
        }
    }

    @Test func search() throws {
        let fixture = try VocabSharedFixtures.decode(Texts.self, from: "vocab-text.json")
        let search = WordSearch(entries: fixture.searchPool[0].map {
            WordEntry(headword: $0.word, translation: $0.translation, bnc: $0.bnc, frq: $0.frq, exchange: $0.exchange)
        })
        for item in fixture.searches {
            let results = search.search(item.query, limit: item.limit).map { result in
                let match = switch result.match {
                case .exact: "exact"
                case .form(let form): "form " + form
                case .prefix: "prefix"
                case .similar: "similar"
                case .meaning: "meaning"
                }
                return "\(result.headword): \(match)"
            }
            #expect(results == item.results, "\(item.query)")
        }
    }
}
