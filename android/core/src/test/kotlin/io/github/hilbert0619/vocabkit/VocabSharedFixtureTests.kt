package io.github.hilbert0619.vocabkit

import io.github.hilbert0619.coursekit.CourseKitJson
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

/**
 * The vocabulary cases in `spec/fixtures/vocab-*.json`, which the Swift package's tests read too
 * (`CourseKit/Tests/VocabKitTests/VocabSharedFixtureTests.swift`): both platforms must schedule, shuffle, grade, import
 * and search the same way. The expected values come from the iOS implementation.
 */
class VocabSharedFixtureTests {
    private val directory = File(System.getProperty("fixtures.dir") ?: error("The test task sets fixtures.dir"))

    private inline fun <reified T> decode(name: String): T =
        CourseKitJson.decodeFromString(kotlinx.serialization.serializer<T>(), File(directory, name).readText())

    /** Collects every mismatch, so one run shows them all. */
    private class Mismatches {
        val lines = mutableListOf<String>()
        fun <T> check(what: String, expected: T, actual: T) {
            if (expected != actual) lines += "$what: expected $expected, got $actual"
        }
        fun close(what: String, expected: Double?, actual: Double?, tolerance: Double = 1e-6) {
            if (expected == null || actual == null) return check(what, expected, actual)
            if (abs(expected - actual) > tolerance) lines += "$what: expected $expected, got $actual"
        }
        fun assertNone() = assertTrue(lines.isEmpty(), lines.joinToString("\n"))
    }

    private fun at(text: String): Instant = VocabFixture.at(text)
    private fun moment(instant: Instant): String =
        "${LocalDate.from(instant, VocabFixture.utc)} ${TimeOfDay.from(instant, VocabFixture.utc)}"
    private fun day(day: Int): String = LocalDate(daysSinceEpoch = day).toString()

    @Serializable
    class LogEvent(val word: String, val rating: Int, val at: String, val id: String? = null)

    private fun events(log: List<LogEvent>, clock: StudyClock): List<ReviewEvent> = log.map { event ->
        ReviewEvent(
            id = event.id?.let(UUID::fromString) ?: UUID.randomUUID(), headword = event.word, rating = Rating.of(event.rating)!!,
            mode = ReviewMode.RECOGNITION, reviewedAt = at(event.at), studyDay = clock.day(at(event.at)),
        )
    }

    // Scheduling

    @Serializable
    class Scheduling(
        val reference: List<Reference>,
        val replays: List<Replay>,
        val planLog: List<List<LogEvent>>,
        val planBook: List<List<String>>,
        val plans: List<Plan>,
    ) {
        @Serializable
        class Reference(val start: String, val firstAnswer: FirstAnswer, val intervalHistory: History, val memory: List<Memory>) {
            @Serializable class FirstAnswer(val stability: List<Double>, val difficulty: List<Double>, val scheduledDays: List<Int>, val state: List<Int>)
            @Serializable class History(val ratings: List<Int>, val intervals: List<Int>)
            @Serializable
            class Memory(
                val enableShortTerm: Boolean, val ratings: List<Int>, val gaps: List<Int>, val stability: Double, val difficulty: Double,
                val tolerance: Double,
            )
        }

        @Serializable
        class Parameters(
            val desiredRetention: Double, val maximumInterval: Int, val learningSteps: List<Int>, val relearningSteps: List<Int>,
            val enableShortTerm: Boolean,
        ) {
            val value get() = FSRSParameters(
                desiredRetention = desiredRetention, maximumInterval = maximumInterval, learningSteps = learningSteps,
                relearningSteps = relearningSteps, enableShortTerm = enableShortTerm,
            )
        }

        @Serializable
        class CardCase(
            val headword: String, val state: Int, val step: Int, val reps: Int, val lapses: Int, val scheduledDays: Int,
            val due: String? = null, val stability: Double? = null, val difficulty: Double? = null, val lastReview: String? = null,
            val lastReviewDay: String? = null, val firstReviewDay: String? = null,
        )

        @Serializable class Retrievability(val day: String, val value: Double? = null)

        @Serializable
        class Replay(
            val name: String, val parameters: Parameters, val dayStart: String, val events: List<LogEvent>, val cards: List<CardCase>,
            val retrievability: List<Retrievability>? = null,
        )

        @Serializable
        class Plan(
            val now: String, val newWordsPerDay: Int, val newTarget: Int, val newDone: Int, val newRemaining: Int, val newWords: List<String>,
            val unseenInBook: Int, val reviewsDone: Int, val reviewsRemaining: Int, val progress: Double,
        )
    }

    @Test
    fun fsrsReference() {
        val reference = decode<Scheduling>("vocab-scheduling.json").reference[0]
        val start = at(reference.start)
        val clock = VocabFixture.midnightClock
        val scheduler = Scheduler()
        val mismatches = Mismatches()
        val first = Rating.entries.map { scheduler.review(Card.of("a"), it, start, clock.day(start)) }
        mismatches.check("first stability", reference.firstAnswer.stability, first.map { it.memory!!.stability })
        first.zip(reference.firstAnswer.difficulty).forEachIndexed { index, (card, difficulty) ->
            mismatches.close("first difficulty $index", difficulty, card.memory?.difficulty, 1e-7)
        }
        mismatches.check("first scheduled days", reference.firstAnswer.scheduledDays, first.map { it.scheduledDays })
        mismatches.check("first states", reference.firstAnswer.state, first.map { it.state.rawValue })

        var card = Card.of("a")
        var now = start
        val intervals = mutableListOf<Int>()
        for (rating in reference.intervalHistory.ratings) {
            card = scheduler.review(card, Rating.of(rating)!!, now, clock.day(now))
            intervals += card.scheduledDays
            now = (card.due as? Due.Moment)?.instant ?: now.plusSeconds(card.scheduledDays * 86_400L)
        }
        mismatches.check("interval history", reference.intervalHistory.intervals, intervals)

        for (memory in reference.memory) {
            val memoryScheduler = Scheduler(FSRSParameters(enableShortTerm = memory.enableShortTerm))
            var memoryCard = Card.of("a")
            var time = start
            for ((rating, gap) in memory.ratings.zip(memory.gaps)) {
                time = time.plusSeconds(gap * 86_400L)
                memoryCard = memoryScheduler.review(memoryCard, Rating.of(rating)!!, time, clock.day(time))
            }
            mismatches.close("stability (short term ${memory.enableShortTerm})", memory.stability, memoryCard.memory?.stability, memory.tolerance)
            mismatches.close("difficulty (short term ${memory.enableShortTerm})", memory.difficulty, memoryCard.memory?.difficulty, memory.tolerance)
        }
        mismatches.assertNone()
    }

    @Test
    fun replays() {
        val mismatches = Mismatches()
        for (replay in decode<Scheduling>("vocab-scheduling.json").replays) {
            val clock = StudyClock(VocabFixture.utc, TimeOfDay.parse(replay.dayStart)!!)
            val scheduler = Scheduler(replay.parameters.value)
            val cards = scheduler.replay(events(replay.events, clock))
            mismatches.check("${replay.name}: words", replay.cards.map { it.headword }, cards.keys.sorted())
            for (expected in replay.cards) {
                val card = cards[expected.headword] ?: continue
                val due = when (val due = card.due) {
                    is Due.Moment -> "at " + moment(due.instant)
                    is Due.Day -> "day " + day(due.day)
                    null -> null
                }
                val actual = listOf(
                    "${card.state.rawValue}", "${card.step}", "${card.reps}", "${card.lapses}", "${card.scheduledDays}", due ?: "-",
                    card.lastReview?.let(::moment) ?: "-", card.lastReviewDay?.let(::day) ?: "-", card.firstReviewDay?.let(::day) ?: "-",
                )
                val wanted = listOf(
                    "${expected.state}", "${expected.step}", "${expected.reps}", "${expected.lapses}", "${expected.scheduledDays}",
                    expected.due ?: "-", expected.lastReview ?: "-", expected.lastReviewDay ?: "-", expected.firstReviewDay ?: "-",
                )
                mismatches.check("${replay.name}: ${expected.headword} (state step reps lapses days due last lastDay firstDay)", wanted, actual)
                expected.stability?.let { mismatches.close("${replay.name}: ${expected.headword} stability", it, card.memory?.stability) }
                expected.difficulty?.let { mismatches.close("${replay.name}: ${expected.headword} difficulty", it, card.memory?.difficulty) }
            }
            for (check in replay.retrievability.orEmpty()) {
                val value = scheduler.retrievability(cards.values.first(), LocalDate.parse(check.day)!!.daysSinceEpoch)
                mismatches.close("${replay.name}: retrievability on ${check.day}", check.value, value)
            }
        }
        mismatches.assertNone()
    }

    @Test
    fun dailyPlans() {
        val fixture = decode<Scheduling>("vocab-scheduling.json")
        val history = events(fixture.planLog[0], VocabFixture.clock)
        val cards = Scheduler().replay(history)
        val mismatches = Mismatches()
        for (expected in fixture.plans) {
            val plan = DailyPlan(cards, history, fixture.planBook[0], expected.newWordsPerDay, at(expected.now), VocabFixture.clock)
            mismatches.check(
                "${expected.now}: target done remaining unseen reviewsDone reviewsRemaining",
                listOf(expected.newTarget, expected.newDone, expected.newRemaining, expected.unseenInBook, expected.reviewsDone, expected.reviewsRemaining),
                listOf(plan.newTarget, plan.newDone, plan.newRemaining, plan.unseenInBook, plan.reviewsDone, plan.reviewsRemaining),
            )
            mismatches.check("${expected.now}: new words", expected.newWords, plan.newWords)
            mismatches.close("${expected.now}: progress", expected.progress, plan.progress)
        }
        mismatches.assertNone()
    }

    // Randomness

    @Serializable
    class Randomness(
        val splitMix: List<SplitMix>,
        val fnv: List<Fnv>,
        val bounded: List<Bounded>,
        val shuffles: List<Shuffle>,
        val pool: List<List<PoolEntry>>,
        val choices: List<Choice>,
        val sessionLog: List<List<LogEvent>>,
        val sessions: List<Session>,
    ) {
        @Serializable class SplitMix(val seed: String, val values: List<String>)
        @Serializable class Fnv(val parts: List<String>, val seed: String)
        @Serializable class Bounded(val seed: String, val upperBounds: List<String>, val values: List<String>)
        @Serializable class Shuffle(val seed: String, val count: Int, val order: List<Int>)
        @Serializable class PoolEntry(val word: String, val translation: String)
        @Serializable class Choice(val word: String, val seed: String, val optionCount: Int, val options: List<String>? = null, val correctIndex: Int? = null)
        @Serializable class Session(val mode: String, val seed: String, val reviewLimit: Int? = null, val items: List<String>)
    }

    @Test
    fun seededRandomness() {
        val fixture = decode<Randomness>("vocab-random.json")
        val mismatches = Mismatches()
        for (item in fixture.splitMix) {
            val random = SeededRandom(item.seed.toULong())
            mismatches.check("SplitMix64 from ${item.seed}", item.values, item.values.map { random.next().toString() })
        }
        for (item in fixture.fnv) {
            mismatches.check("FNV-1a of ${item.parts}", item.seed, SeededRandom.seed(*item.parts.toTypedArray()).toString())
        }
        for (item in fixture.bounded) {
            val random = SeededRandom(item.seed.toULong())
            mismatches.check("bounded draws from ${item.seed}", item.values, item.upperBounds.map { random.next(it.toULong()).toString() })
        }
        for (item in fixture.shuffles) {
            mismatches.check("shuffle of ${item.count} with ${item.seed}", item.order, (0 until item.count).toList().shuffled(SeededRandom(item.seed.toULong())))
        }
        mismatches.assertNone()
    }

    @Test
    fun choiceQuestions() {
        val fixture = decode<Randomness>("vocab-random.json")
        val entries = fixture.pool[0].map { WordEntry(headword = it.word, translation = it.translation) }
        val pool = ChoicePool(entries)
        val mismatches = Mismatches()
        for (item in fixture.choices) {
            val target = entries.first { it.headword == Headword.key(item.word) }
            val question = pool.question(target, item.seed.toULong(), item.optionCount)
            mismatches.check("${item.word} seed ${item.seed} (${item.optionCount} options)", item.options, question?.options)
            mismatches.check("${item.word} seed ${item.seed} (${item.optionCount} options) correct index", item.correctIndex, question?.correctIndex)
        }
        mismatches.assertNone()
    }

    @Test
    fun sessions() {
        val fixture = decode<Randomness>("vocab-random.json")
        val cards = Scheduler().replay(events(fixture.sessionLog[0], VocabFixture.clock))
        val mismatches = Mismatches()
        for (expected in fixture.sessions) {
            val start = at("2026-09-30 20:00")
            val session = StudySession(
                cards, listOf("china", "abandoned", "abandon", "adopt"), start, Scheduler(), VocabFixture.clock,
                SessionMode.of(expected.mode)!!, expected.seed.toULong(), expected.reviewLimit,
            )
            var now = start
            val items = mutableListOf<String>()
            val missed = mutableSetOf<String>()
            while (items.size < 40) {
                val item = session.next(now) ?: break
                items += "${item.headword} ${item.kind.name.lowercase()} ${item.mode.rawValue}"
                val rating = if (item.kind == StudyItem.Kind.LEARNING && missed.add(item.headword)) Rating.AGAIN else Rating.GOOD
                session.answer(item, rating, now, durationMs = 1000)
                now = now.plusSeconds(90)
            }
            mismatches.check("${expected.mode} session, seed ${expected.seed}", expected.items, items)
        }
        mismatches.assertNone()
    }

    // Texts

    @Serializable
    class Texts(
        val keys: List<Key>,
        val spelling: List<Spelling>,
        val hints: List<Hint>,
        val diffs: List<Diff>,
        val senses: List<Senses>,
        val abbreviations: List<Abbreviation>,
        val known: List<List<String>>,
        val imports: List<Import>,
        val searchPool: List<List<PoolEntry>>,
        val searches: List<Search>,
    ) {
        @Serializable class Key(val text: String, val key: String)
        @Serializable class Spelling(val typed: String, val word: String, val correct: Boolean)
        @Serializable class Hint(val word: String, val hint: String)
        @Serializable class Diff(val typed: String, val word: String, val parts: List<List<String>>)
        @Serializable
        data class Sense(val tag: String? = null, val label: String? = null, val meanings: List<String>, val partOfSpeech: String? = null, val formatted: String)
        @Serializable
        class Senses(
            val translation: String, val senses: List<Sense>, val shortMeaning: String, val primaryPartOfSpeech: String? = null,
            val meaningTokens: List<String>,
        )
        @Serializable class Abbreviation(val text: String, val partOfSpeech: String? = null)
        @Serializable
        class Word(
            val headword: String, val display: String, val meaning: String? = null, val phonetic: String? = null,
            val partOfSpeech: String? = null, val example: String? = null, val exampleTranslation: String? = null, val inDictionary: Boolean,
            val line: Int,
        )
        @Serializable
        class Import(
            val text: String, val format: String? = null, val detected: String, val words: List<Word>, val unknown: List<Word>,
            val duplicates: List<Word>, val unreadable: List<Int>,
        )
        @Serializable class PoolEntry(val word: String, val translation: String, val bnc: Int? = null, val frq: Int? = null, val exchange: String)
        @Serializable class Search(val query: String, val limit: Int, val results: List<String>)
    }

    @Test
    fun grading() {
        val fixture = decode<Texts>("vocab-text.json")
        val mismatches = Mismatches()
        for (item in fixture.keys) mismatches.check("key of \"${item.text}\"", item.key, Headword.key(item.text))
        for (item in fixture.spelling) mismatches.check("\"${item.typed}\" for ${item.word}", item.correct, AnswerGrader.isCorrectSpelling(item.typed, item.word))
        for (item in fixture.hints) mismatches.check("hint for ${item.word}", item.hint, AnswerGrader.hint(item.word))
        for (item in fixture.diffs) {
            val parts = AnswerGrader.diff(item.typed, item.word).map { part ->
                when (part) {
                    is DiffPart.Same -> listOf("same", part.text)
                    is DiffPart.Missing -> listOf("missing", part.text)
                    is DiffPart.Extra -> listOf("extra", part.text)
                }
            }
            mismatches.check("diff of \"${item.typed}\" and ${item.word}", item.parts, parts)
        }
        mismatches.assertNone()
    }

    @Test
    fun meanings() {
        val fixture = decode<Texts>("vocab-text.json")
        val mismatches = Mismatches()
        for (item in fixture.senses) {
            val entry = WordEntry(headword = "x", translation = item.translation)
            val senses = entry.senses.map { Texts.Sense(it.partOfSpeechTag, it.label, it.meanings, it.partOfSpeech?.rawValue, it.formatted()) }
            mismatches.check("senses of \"${item.translation}\"", item.senses, senses)
            mismatches.check("short meaning of \"${item.translation}\"", item.shortMeaning, entry.shortMeaning)
            mismatches.check("part of speech of \"${item.translation}\"", item.primaryPartOfSpeech, entry.primaryPartOfSpeech?.rawValue)
            mismatches.check("tokens of \"${item.translation}\"", item.meaningTokens, entry.meaningTokens.sorted())
        }
        for (item in fixture.abbreviations) mismatches.check("abbreviation \"${item.text}\"", item.partOfSpeech, PartOfSpeech.of(item.text)?.rawValue)
        mismatches.assertNone()
    }

    @Test
    fun imports() {
        val fixture = decode<Texts>("vocab-text.json")
        val known = fixture.known[0].toSet()
        fun words(list: List<ImportedWord>) = list.map {
            "${it.line} ${it.headword} | ${it.display} | ${it.meaning ?: "-"} | ${it.phonetic ?: "-"} | ${it.partOfSpeech ?: "-"} | " +
                "${it.example ?: "-"} | ${it.exampleTranslation ?: "-"} | ${it.inDictionary}"
        }
        fun expected(list: List<Texts.Word>) = list.map {
            "${it.line} ${it.headword} | ${it.display} | ${it.meaning ?: "-"} | ${it.phonetic ?: "-"} | ${it.partOfSpeech ?: "-"} | " +
                "${it.example ?: "-"} | ${it.exampleTranslation ?: "-"} | ${it.inDictionary}"
        }
        val mismatches = Mismatches()
        for (item in fixture.imports) {
            val format = item.format?.let { name -> WordListPreview.Format.entries.first { it.name.lowercase() == name } }
            val preview = WordListImport.preview(item.text, format) { it in known }
            val what = "import of \"${item.text.take(40)}…\""
            mismatches.check("$what: format", item.detected, preview.format.name.lowercase())
            mismatches.check("$what: words", expected(item.words), words(preview.words))
            mismatches.check("$what: unknown", expected(item.unknown), words(preview.unknown))
            mismatches.check("$what: duplicates", expected(item.duplicates), words(preview.duplicates))
            mismatches.check("$what: unreadable", item.unreadable, preview.unreadable)
        }
        mismatches.assertNone()
    }

    @Test
    fun search() {
        val fixture = decode<Texts>("vocab-text.json")
        val search = WordSearch(fixture.searchPool[0].map { WordEntry(headword = it.word, translation = it.translation, bnc = it.bnc, frq = it.frq, exchange = it.exchange) })
        val mismatches = Mismatches()
        for (item in fixture.searches) {
            val results = search.search(item.query, item.limit).map { result ->
                val match = when (val match = result.match) {
                    WordSearch.Match.Exact -> "exact"
                    is WordSearch.Match.Form -> "form ${match.form}"
                    WordSearch.Match.Prefix -> "prefix"
                    WordSearch.Match.Similar -> "similar"
                    WordSearch.Match.Meaning -> "meaning"
                }
                "${result.headword}: $match"
            }
            mismatches.check("search \"${item.query}\" (limit ${item.limit})", item.results, results)
        }
        mismatches.assertNone()
    }
}
