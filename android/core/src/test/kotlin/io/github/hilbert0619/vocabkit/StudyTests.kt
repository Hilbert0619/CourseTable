package io.github.hilbert0619.vocabkit

import io.github.hilbert0619.coursekit.LocalDate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyPlanTests {
    private val clock = VocabFixture.clock
    private val history = listOf(
        // Learned 09-27, graduated for 2 days: due 09-29, so overdue today.
        VocabFixture.event("abandon", Rating.GOOD, "2026-09-27 10:00"),
        VocabFixture.event("abandon", Rating.GOOD, "2026-09-27 10:10"),
        // Learned 09-25 with Easy: due 10-03.
        VocabFixture.event("ability", Rating.EASY, "2026-09-25 10:00"),
        // Learned 09-20, reviewed today.
        VocabFixture.event("swiftly", Rating.EASY, "2026-09-20 10:00"),
        VocabFixture.event("swiftly", Rating.GOOD, "2026-09-30 10:00"),
        // Learned today, in its learning steps.
        VocabFixture.event("desert", Rating.GOOD, "2026-09-30 11:00"),
    )
    private val book = listOf("ability", "desert", "abandon", "swiftly", "china", "abandoned", "adopt", "adapt")

    @Test
    fun todaysPlan() {
        val cards = Scheduler().replay(history)
        val plan = DailyPlan(cards, history, book, newWordsPerDay = 3, now = VocabFixture.at("2026-09-30 20:00"), clock = clock)
        assertEquals(3, plan.newTarget)
        assertEquals(1, plan.newDone)
        assertEquals(2, plan.newRemaining)
        assertEquals(listOf("china", "abandoned"), plan.newWords)
        assertEquals(4, plan.unseenInBook)
        assertEquals(1, plan.reviewsDone)
        assertEquals(1, plan.reviewsRemaining)
        assertEquals(0.4, plan.progress)
        assertFalse(plan.isComplete)
        assertNull(plan.estimatedMinutes) // fewer than 50 answers so far
    }

    @Test
    fun limitsAndEdges() {
        val cards = Scheduler().replay(history)
        val now = VocabFixture.at("2026-09-30 20:00")
        val done = DailyPlan(cards, history, book, newWordsPerDay = 1, now = now, clock = clock)
        assertEquals(0, done.newRemaining)
        assertTrue(done.newWords.isEmpty())
        val shortBook = DailyPlan(cards, history, listOf("China", "china"), newWordsPerDay = 20, now = now, clock = clock)
        assertEquals(1, shortBook.newRemaining)
        assertEquals(listOf("china"), shortBook.newWords)
        val empty = DailyPlan(emptyMap(), emptyList(), emptyList(), newWordsPerDay = 20, now = now, clock = clock)
        assertEquals(1.0, empty.progress)
        assertTrue(empty.isComplete)
        // Tomorrow, today's new word is a review-to-be and the counts start again.
        val tomorrow = DailyPlan(cards, history, book, newWordsPerDay = 3, now = VocabFixture.at("2026-10-01 09:00"), clock = clock)
        assertEquals(0, tomorrow.newDone)
        assertEquals(3, tomorrow.newRemaining)
        assertEquals(0, tomorrow.reviewsDone)
        assertEquals(2, tomorrow.reviewsRemaining) // abandon (overdue) and desert (still in a learning step)
    }

    @Test
    fun timeEstimate() {
        val events = mutableListOf<ReviewEvent>()
        for (index in 0 until 20) {
            repeat(2) {
                events += ReviewEvent(headword = "w$index", rating = Rating.GOOD, mode = ReviewMode.CHOICE, reviewedAt = Instant.now(), studyDay = 1, durationMs = 6000)
            }
            events += ReviewEvent(headword = "w$index", rating = Rating.GOOD, mode = ReviewMode.RECOGNITION, reviewedAt = Instant.now(), studyDay = 5, durationMs = 6000)
        }
        // 6 s × (10 new × 2 answers + 30 reviews × 1 answer) = 5 min.
        assertEquals(5, DailyPlan.estimate(events, today = 10, newWords = 10, reviews = 30))
        assertNull(DailyPlan.estimate(events.take(49), today = 10, newWords = 10, reviews = 30))
        assertNull(DailyPlan.estimate(events, today = 5, newWords = 10, reviews = 30)) // only past days count
        assertEquals(0, DailyPlan.estimate(events, today = 10, newWords = 0, reviews = 0))
    }

    @Test
    fun examCountdown() {
        val today = VocabFixture.day("2026-09-30")
        val countdown = assertNotNull(ExamCountdown.of(LocalDate(2026, 12, 19), today, wordsLeft = 4000))
        assertEquals(80, countdown.daysLeft)
        assertEquals(50, countdown.newWordsPerDayNeeded)
        assertEquals(51, ExamCountdown.of(LocalDate(2026, 12, 19), today, wordsLeft = 4001)?.newWordsPerDayNeeded)
        assertEquals(0, ExamCountdown.of(LocalDate(2026, 12, 19), today, wordsLeft = 0)?.newWordsPerDayNeeded)
        assertNull(ExamCountdown.of(LocalDate(2026, 9, 30), today, wordsLeft = 10))
    }
}

class StudySessionTests {
    private val clock = VocabFixture.clock
    private val scheduler = Scheduler()
    private val cards = Scheduler().replay(
        listOf(
            VocabFixture.event("abandon", Rating.GOOD, "2026-09-27 10:00"),
            VocabFixture.event("abandon", Rating.GOOD, "2026-09-27 10:10"), // due 09-29
            VocabFixture.event("ability", Rating.EASY, "2026-09-20 10:00"), // due 09-28
            VocabFixture.event("desert", Rating.EASY, "2026-09-25 10:00"), // due 10-03: not today
            VocabFixture.event("swiftly", Rating.AGAIN, "2026-09-30 19:55"), // learning step, due 19:56
        ),
    )

    private fun session(
        mode: SessionMode = SessionMode.RECOGNITION,
        seed: ULong = 1u,
        reviewLimit: Int? = null,
        scheduler: Scheduler = this.scheduler,
    ) = StudySession(cards, listOf("china", "abandoned", "abandon"), VocabFixture.at("2026-09-30 20:00"), scheduler, clock, mode, seed, reviewLimit)

    @Test
    fun wholeSession() {
        val session = session()
        var now = VocabFixture.at("2026-09-30 20:00")
        val kinds = mutableListOf<StudyItem.Kind>()
        val words = mutableListOf<String>()
        var missedOnce = false
        while (true) {
            val item = session.next(now) ?: break
            kinds += item.kind
            words += item.headword
            assertEquals(if (item.kind == StudyItem.Kind.NEW) ReviewMode.CHOICE else ReviewMode.RECOGNITION, item.mode)
            val rating = if (item.headword == "abandoned" && !missedOnce) Rating.AGAIN else Rating.GOOD
            if (rating == Rating.AGAIN) missedOnce = true
            session.answer(item, rating, now, durationMs = 4000)
            now = now.plusSeconds(60)
            assertTrue(kinds.size < 20)
        }
        val k = StudyItem.Kind.LEARNING
        assertEquals(listOf(k, StudyItem.Kind.REVIEW, StudyItem.Kind.REVIEW, StudyItem.Kind.NEW, StudyItem.Kind.NEW, k, k, k, k), kinds)
        assertEquals("swiftly", words[0])
        assertEquals(setOf("abandon", "ability"), words.subList(1, 3).toSet())
        assertEquals(listOf("china", "abandoned", "abandoned", "swiftly", "china", "abandoned"), words.drop(3))
        assertTrue(session.cards.values.all { it.state == CardState.REVIEW })
        assertEquals(9, session.events.size)
        assertTrue(session.events.all { it.studyDay == VocabFixture.day("2026-09-30") })
        assertEquals(SessionSummary(newWords = 2, reviewedWords = 2, answers = 9, correct = 8, durationMs = 36_000), session.summary)
        assertEquals(8.0 / 9.0, session.summary.accuracy)
        assertEquals(StudySession.Remaining(0, 0, 0), session.remaining(now))
        // Replaying the stored events gives the session's cards.
        val replayed = scheduler.replay(session.events)
        for ((word, card) in replayed) {
            assertEquals(session.cards[word], scheduler.replay(session.events.filter { it.headword == word } + eventsBefore(word), word))
            assertTrue(card.reps >= 1)
        }
    }

    /** The events [cards] were built from, per word. */
    private fun eventsBefore(word: String): List<ReviewEvent> = when (word) {
        "abandon" -> listOf(VocabFixture.event("abandon", Rating.GOOD, "2026-09-27 10:00"), VocabFixture.event("abandon", Rating.GOOD, "2026-09-27 10:10"))
        "ability" -> listOf(VocabFixture.event("ability", Rating.EASY, "2026-09-20 10:00"))
        "swiftly" -> listOf(VocabFixture.event("swiftly", Rating.AGAIN, "2026-09-30 19:55"))
        else -> emptyList()
    }

    @Test
    fun alreadyLearnedWordsAreNotNew() {
        assertEquals(StudySession.Remaining(2, 2, 1), session().remaining(VocabFixture.at("2026-09-30 20:00")))
    }

    @Test
    fun reviewOrderDependsOnlyOnTheSeed() {
        val now = VocabFixture.at("2026-09-30 20:00")
        fun reviewOrder(seed: ULong): List<String> {
            val session = session(seed = seed)
            val order = mutableListOf<String>()
            while (true) {
                val item = session.next(now) ?: break
                if (item.kind == StudyItem.Kind.NEW) break
                if (item.kind == StudyItem.Kind.REVIEW) order += item.headword
                session.answer(item, Rating.EASY, now, durationMs = 0)
            }
            return order
        }
        assertEquals(reviewOrder(5u), reviewOrder(5u))
        assertEquals(2, (1..10).map { reviewOrder(it.toULong()) }.toSet().size)
    }

    @Test
    fun reviewLimit() {
        assertEquals(1, session(reviewLimit = 1).remaining(VocabFixture.at("2026-09-30 20:00")).review)
        assertEquals(0, session(reviewLimit = 0).remaining(VocabFixture.at("2026-09-30 20:00")).review)
    }

    @Test
    fun mixedModeIsStablePerQuestion() {
        val now = VocabFixture.at("2026-09-30 20:00")
        val session = session(SessionMode.MIXED, seed = 3u)
        assertEquals(session.next(now), session.next(now))
        val modes = (0 until 30).mapNotNull { session(SessionMode.MIXED, seed = it.toULong()).next(now)?.mode }.toSet()
        assertEquals(ReviewMode.entries.toSet(), modes)
        assertEquals(ReviewMode.SPELLING, session(SessionMode.SPELLING).next(now)?.mode)
    }

    @Test
    fun stepsDueLaterWaitForTheNextSession() {
        val slow = Scheduler(FSRSParameters(learningSteps = listOf(30, 60)))
        val now = VocabFixture.at("2026-09-30 20:00")
        val session = StudySession(emptyMap(), listOf("abandon"), now, slow, clock, SessionMode.RECOGNITION, seed = 1u)
        val item = session.next(now)!!
        session.answer(item, Rating.GOOD, now, durationMs = 1000)
        assertNull(session.next(now))
        assertEquals(1, session.waiting(now).count)
        assertEquals(VocabFixture.at("2026-09-30 21:00"), session.waiting(now).firstDue)
        assertEquals("abandon", session.next(VocabFixture.at("2026-09-30 20:40"))?.headword)
    }
}

class VocabStatsTests {
    private val events = listOf(
        VocabFixture.event("abandon", Rating.GOOD, "2026-09-28 09:00", ms = 1000),
        VocabFixture.event("abandon", Rating.GOOD, "2026-09-28 09:10", ms = 1000),
        VocabFixture.event("ability", Rating.AGAIN, "2026-09-28 09:20", ms = 1000),
        VocabFixture.event("ability", Rating.GOOD, "2026-09-28 09:30", ms = 1000),
        VocabFixture.event("abandon", Rating.HARD, "2026-09-29 20:00", ms = 2000),
        VocabFixture.event("desert", Rating.GOOD, "2026-09-30 02:00", ms = 2000), // still the study day of 09-29
        VocabFixture.event("ability", Rating.GOOD, "2026-09-30 21:00", ms = 3000),
    )

    @Test
    fun dayNumbers() {
        val days = VocabStats.days(events)
        val d28 = days.getValue(VocabFixture.day("2026-09-28"))
        assertEquals(listOf(2, 0, 4, 3, 4000), listOf(d28.newWords, d28.reviewedWords, d28.answers, d28.correct, d28.durationMs))
        assertEquals(0.75, d28.accuracy)
        val d29 = days.getValue(VocabFixture.day("2026-09-29"))
        assertEquals(listOf(1, 1, 2, 2), listOf(d29.newWords, d29.reviewedWords, d29.answers, d29.correct))
        val d30 = days.getValue(VocabFixture.day("2026-09-30"))
        assertEquals(listOf(0, 1, 1), listOf(d30.newWords, d30.reviewedWords, d30.answers))
        assertNull(DayStats(0).accuracy)
    }

    @Test
    fun series() {
        val series = VocabStats.series(events, VocabFixture.day("2026-09-30"), count = 5)
        assertEquals((VocabFixture.day("2026-09-26")..VocabFixture.day("2026-09-30")).toList(), series.map { it.day })
        assertEquals(listOf(0, 0, 4, 2, 1), series.map { it.answers })
        assertEquals(listOf(false, false, true, true, true), series.map { it.studied })
    }

    @Test
    fun streak() {
        assertEquals(Streak(3, true), VocabStats.streak(events, VocabFixture.day("2026-09-30")))
        assertEquals(Streak(3, false), VocabStats.streak(events, VocabFixture.day("2026-10-01")))
        assertEquals(Streak(0, false), VocabStats.streak(events, VocabFixture.day("2026-10-02")))
        assertEquals(Streak(0, false), VocabStats.streak(emptyList(), 5))
    }

    private fun card(word: String, due: Due?, stability: Double = 10.0, lapses: Int = 0) = Card(
        headword = word,
        state = if (due == null) CardState.NEW else CardState.REVIEW,
        due = due,
        memory = if (due == null) null else MemoryState(stability, 5.0),
        lapses = lapses,
    )

    @Test
    fun buckets() {
        val today = 100
        val cards = listOf(
            card("a", Due.Day(99)), card("b", Due.Day(100)), card("c", Due.Moment(Instant.now())), card("d", Due.Day(102)),
            card("e", Due.Day(103)), card("f", Due.Day(104)), card("g", null),
        )
        assertEquals(StatusBuckets(needsReview = 3, approaching = 2, stable = 1), VocabStats.buckets(cards, today))
        assertEquals(3, VocabStats.dueCount(cards, day = 101, today = today))
        assertEquals(6, VocabStats.dueCount(cards, day = 104, today = today))
    }

    @Test
    fun bookProgress() {
        val cards = mapOf("a" to card("a", Due.Day(110), stability = 40.0), "b" to card("b", Due.Day(100), stability = 5.0), "c" to card("c", null))
        assertEquals(
            BookProgress(total = 4, learned = 2, mastered = 1, due = 1),
            VocabStats.progress(listOf("a", "B", "c", "d", "a"), cards, today = 100),
        )
    }

    @Test
    fun hardWords() {
        val cards = listOf(card("x", Due.Day(1), lapses = 2), card("y", Due.Day(1), lapses = 3), card("w", Due.Day(1), lapses = 2), card("z", Due.Day(1), lapses = 1))
        assertEquals(listOf("y", "w", "x"), VocabStats.hardWords(cards).map { it.headword })
    }
}

class SyncMergeTests {
    @Test
    fun mergingEvents() {
        val a = VocabFixture.event("abandon", Rating.GOOD, "2026-09-28 09:00")
        val b = VocabFixture.event("abandon", Rating.GOOD, "2026-09-28 09:10")
        val c = VocabFixture.event("ability", Rating.EASY, "2026-09-28 09:20")
        val incoming = listOf(a, b, b, c)
        val fresh = SyncMerge.newEvents(incoming, setOf(a.id))
        assertEquals(listOf(b, c), fresh)
        assertEquals(setOf("abandon", "ability"), SyncMerge.affectedWords(fresh))
        val rebuilt = SyncMerge.rebuiltCards(listOf(a, b, c), listOf(c), Scheduler())
        assertEquals(listOf("ability"), rebuilt.keys.toList())
        assertEquals(Scheduler().replay(listOf(c), "ability"), rebuilt["ability"])
    }

    @Test
    fun twoDevicesConverge() {
        // Each device studied offline; after syncing both hold all events and the same cards.
        val phone = listOf(VocabFixture.event("abandon", Rating.GOOD, "2026-09-28 09:00"), VocabFixture.event("abandon", Rating.GOOD, "2026-09-28 09:10"))
        val tablet = listOf(VocabFixture.event("abandon", Rating.AGAIN, "2026-09-30 21:00"), VocabFixture.event("abandon", Rating.GOOD, "2026-09-30 21:10"))
        val scheduler = Scheduler()
        val onPhone = scheduler.replay(phone + SyncMerge.newEvents(tablet, phone.map { it.id }.toSet()))
        val onTablet = scheduler.replay(tablet + SyncMerge.newEvents(phone, tablet.map { it.id }.toSet()))
        assertEquals(onTablet, onPhone)
        assertEquals(1, onPhone["abandon"]?.lapses)
    }

    @Test
    fun cursor() {
        val cursor = VocabFixture.at("2026-09-30 12:00")
        assertNull(SyncMerge.pullStart(null))
        assertEquals(VocabFixture.at("2026-09-30 11:50"), SyncMerge.pullStart(cursor))
        assertNull(SyncMerge.advance(null, emptyList()))
        assertEquals(cursor, SyncMerge.advance(cursor, emptyList()))
        assertEquals(
            VocabFixture.at("2026-09-30 12:05"),
            SyncMerge.advance(cursor, listOf(VocabFixture.at("2026-09-30 11:55"), VocabFixture.at("2026-09-30 12:05"))),
        )
    }

    @Test
    fun newestEditWins() {
        val early = Versioned(20, VocabFixture.at("2026-09-30 10:00"))
        val late = Versioned(30, VocabFixture.at("2026-09-30 11:00"))
        assertEquals(SyncMerge.Resolution.PUSH_LOCAL, SyncMerge.resolve(late, early))
        assertEquals(SyncMerge.Resolution.TAKE_REMOTE, SyncMerge.resolve(early, late))
        assertEquals(SyncMerge.Resolution.UNCHANGED, SyncMerge.resolve(early, early))
        assertEquals(SyncMerge.Resolution.PUSH_LOCAL, SyncMerge.resolve(early, null))
        assertEquals(SyncMerge.Resolution.TAKE_REMOTE, SyncMerge.resolve(null, early))
        assertEquals(SyncMerge.Resolution.UNCHANGED, SyncMerge.resolve<Int>(null, null))
    }

    @Test
    fun batches() {
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), SyncMerge.batches((1..5).toList(), size = 2))
        assertTrue(SyncMerge.batches(emptyList<Int>(), size = 2).isEmpty())
        assertEquals(listOf(500, 500, 200), SyncMerge.batches((1..1200).toList()).map { it.size })
    }
}
