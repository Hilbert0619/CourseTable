package io.github.hilbert0619.vocabkit

import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** The Swift tests' `VocabFixture`: times in UTC. */
object VocabFixture {
    val utc: ZoneId = ZoneOffset.UTC

    /** Study days starting at 04:00 UTC, the app's default rule in a fixed time zone. */
    val clock = StudyClock(utc)

    /** Days starting at midnight UTC, the convention of the FSRS reference tests. */
    val midnightClock = StudyClock(utc, TimeOfDay.MIDNIGHT)

    /** `"2026-09-30 21:15"` in UTC. */
    fun at(text: String): Instant {
        val parts = text.split(" ")
        val date = LocalDate.parse(parts[0])!!
        val time = if (parts.size > 1) TimeOfDay.parse(parts[1])!! else TimeOfDay.MIDNIGHT
        return date.toInstant(time, utc)
    }

    fun day(text: String): Int = LocalDate.parse(text)!!.daysSinceEpoch

    fun event(
        word: String,
        rating: Rating,
        time: String,
        mode: ReviewMode = ReviewMode.RECOGNITION,
        ms: Int = 5000,
        id: UUID = UUID.randomUUID(),
    ): ReviewEvent {
        val date = at(time)
        return ReviewEvent(id, word, rating, mode, date, clock.day(date), ms)
    }
}

fun assertClose(value: Double?, expected: Double, tolerance: Double = 1e-6) {
    if (value == null) fail("value is null")
    assertTrue(abs(value - expected) <= tolerance, "$value ≠ $expected")
}

/**
 * Oracle values from the FSRS-6 reference tests (ts-fsrs `FSRS-6.test.ts`, mirrored in swift-fsrs `FSRSV6Tests.swift`),
 * which use days that start at midnight UTC. The shared cases in spec/fixtures/vocab-scheduling.json repeat them.
 */
class FSRSReferenceTests {
    private val scheduler = Scheduler()
    private val start = VocabFixture.at("2022-12-29 12:30")

    private fun review(card: Card, rating: Rating, time: Instant, scheduler: Scheduler = this.scheduler): Card =
        scheduler.review(card, rating, time, VocabFixture.midnightClock.day(time))

    @Test
    fun firstAnswer() {
        val cards = Rating.entries.map { review(Card.of("a"), it, start) }
        assertEquals(listOf(0.212, 1.2931, 2.3065, 8.2956), cards.map { it.memory!!.stability })
        assertClose(cards[0].memory?.difficulty, 6.4133, 1e-7)
        assertClose(cards[1].memory?.difficulty, 5.11217071, 1e-7)
        assertClose(cards[2].memory?.difficulty, 2.11810397, 1e-7)
        assertClose(cards[3].memory?.difficulty, 1.0, 1e-7)
        assertEquals(listOf(0, 0, 0, 8), cards.map { it.scheduledDays })
        assertEquals(listOf(CardState.LEARNING, CardState.LEARNING, CardState.LEARNING, CardState.REVIEW), cards.map { it.state })
        assertEquals(listOf(1, 1, 1, 1), cards.map { it.reps })
        assertEquals(listOf(0, 0, 0, 0), cards.map { it.lapses })
    }

    @Test
    fun intervalHistory() {
        val ratings = listOf(Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.AGAIN, Rating.AGAIN,
            Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD)
        var card = Card.of("a")
        var now = start
        val intervals = mutableListOf<Int>()
        for (rating in ratings) {
            card = review(card, rating, now)
            intervals += card.scheduledDays
            // Answer exactly when due, as the reference test does.
            now = when (val due = card.due) {
                is Due.Moment -> due.instant
                is Due.Day -> now.plusSeconds(card.scheduledDays * 86_400L)
                null -> fail("no due date")
            }
        }
        assertEquals(listOf(0, 2, 11, 46, 163, 498, 0, 0, 2, 4, 7, 12, 21), intervals)
        assertEquals(1, card.lapses)
        assertEquals(13, card.reps)
    }

    private fun memorySequence(scheduler: Scheduler): Card {
        val ratings = listOf(Rating.AGAIN, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD)
        val gaps = listOf(0, 0, 1, 3, 8, 21)
        var card = Card.of("a")
        var now = start
        for ((rating, gap) in ratings.zip(gaps)) {
            now = now.plusSeconds(gap * 86_400L)
            card = review(card, rating, now, scheduler)
        }
        return card
    }

    @Test
    fun memoryStateWithShortTermScheduling() {
        val card = memorySequence(scheduler)
        assertClose(card.memory?.stability, 53.62691, 1e-4)
        assertClose(card.memory?.difficulty, 6.3574867, 1e-4)
    }

    @Test
    fun memoryStateWithoutShortTermScheduling() {
        val card = memorySequence(Scheduler(FSRSParameters(enableShortTerm = false)))
        assertClose(card.memory?.stability, 53.335106, 1e-4)
        assertClose(card.memory?.difficulty, 6.3574867, 1e-4)
    }

    @Test
    fun forgettingCurve() {
        val fsrs = FSRS()
        assertClose(fsrs.retrievability(1.0, 1.0), 0.9, 1e-4)
        assertClose(fsrs.retrievability(0.0, 5.0), 1.0, 1e-9)
        assertEquals(-0.1542, fsrs.decay)
        assertTrue(fsrs.retrievability(30.0, 10.0) < fsrs.retrievability(10.0, 10.0))
    }

    @Test
    fun intervalFollowsDesiredRetention() {
        assertEquals(10, FSRS().interval(10.4))
        assertEquals(1, FSRS().interval(0.2))
        val stricter = FSRS(FSRSParameters(desiredRetention = 0.95))
        assertTrue(stricter.interval(100.0) < 100)
        val looser = FSRS(FSRSParameters(desiredRetention = 0.8))
        assertTrue(looser.interval(100.0) > 100)
        assertEquals(30, FSRS(FSRSParameters(maximumInterval = 30)).interval(500.0))
    }

    @Test
    fun parametersAreKeptInRange() {
        val parameters = FSRSParameters(desiredRetention = 0.5, learningSteps = listOf(0, 5, 2000), relearningSteps = emptyList())
        assertEquals(0.7, parameters.desiredRetention)
        assertEquals(listOf(5), parameters.learningSteps)
        assertTrue(parameters.relearningSteps.isEmpty())
    }
}

class SchedulerTests {
    private val scheduler = Scheduler()
    private val clock = VocabFixture.clock

    private fun review(card: Card, rating: Rating, time: String): Card {
        val date = VocabFixture.at(time)
        return scheduler.review(card, rating, date, clock.day(date))
    }

    @Test
    fun learningSteps() {
        val new = Card.of("abandon")
        val again = review(new, Rating.AGAIN, "2026-09-30 10:00")
        assertEquals(Due.Moment(VocabFixture.at("2026-09-30 10:01")), again.due)
        val hard = review(new, Rating.HARD, "2026-09-30 10:00")
        assertEquals(Due.Moment(VocabFixture.at("2026-09-30 10:06")), hard.due) // (1 + 10) / 2, rounded up
        assertEquals(0, hard.step)
        val good = review(new, Rating.GOOD, "2026-09-30 10:00")
        assertEquals(Due.Moment(VocabFixture.at("2026-09-30 10:10")), good.due)
        assertEquals(1, good.step)
        val graduated = review(good, Rating.GOOD, "2026-09-30 10:10")
        assertEquals(CardState.REVIEW, graduated.state)
        assertEquals(Due.Day(VocabFixture.day("2026-09-30") + graduated.scheduledDays), graduated.due)
        assertEquals(VocabFixture.day("2026-09-30"), graduated.firstReviewDay)
    }

    @Test
    fun forgettingAReviewWordRelearnsIt() {
        var card = review(Card.of("abandon"), Rating.EASY, "2026-09-01 10:00")
        assertEquals(CardState.REVIEW, card.state)
        card = review(card, Rating.AGAIN, "2026-09-09 10:00")
        assertEquals(CardState.RELEARNING, card.state)
        assertEquals(1, card.lapses)
        assertEquals(Due.Moment(VocabFixture.at("2026-09-09 10:10")), card.due)
        card = review(card, Rating.GOOD, "2026-09-09 10:10")
        assertEquals(CardState.REVIEW, card.state)
        assertEquals(1, card.lapses)
        assertEquals(VocabFixture.day("2026-09-01"), card.firstReviewDay)
    }

    @Test
    fun lateNightCountsForThePreviousStudyDay() {
        val evening = review(Card.of("abandon"), Rating.GOOD, "2026-09-30 23:30")
        val afterMidnight = review(evening, Rating.GOOD, "2026-10-01 01:00")
        assertEquals(VocabFixture.day("2026-09-30"), evening.lastReviewDay)
        assertEquals(VocabFixture.day("2026-09-30"), afterMidnight.lastReviewDay)
        // Same study day → elapsed 0 → the short-term formula, same as reviewing ten minutes later.
        val tenMinutesLater = review(evening, Rating.GOOD, "2026-09-30 23:40")
        assertEquals(tenMinutesLater.memory, afterMidnight.memory)
        assertEquals(Due.Day(VocabFixture.day("2026-09-30") + afterMidnight.scheduledDays), afterMidnight.due)
    }

    @Test
    fun reviewIntervalsAreOrdered() {
        var card = review(Card.of("abandon"), Rating.GOOD, "2026-09-01 10:00")
        card = review(card, Rating.GOOD, "2026-09-01 10:10")
        val day = "2026-09-0${1 + card.scheduledDays} 09:00"
        val intervals = listOf(Rating.HARD, Rating.GOOD, Rating.EASY).map { review(card, it, day).scheduledDays }
        assertTrue(intervals[0] < intervals[1])
        assertTrue(intervals[1] < intervals[2])
    }

    @Test
    fun replayRebuildsTheSameCard() {
        val events = listOf(
            VocabFixture.event("abandon", Rating.GOOD, "2026-09-01 10:00"),
            VocabFixture.event("abandon", Rating.GOOD, "2026-09-01 10:10"),
            VocabFixture.event("abandon", Rating.AGAIN, "2026-09-03 21:00"),
            VocabFixture.event("abandon", Rating.GOOD, "2026-09-03 21:10"),
            VocabFixture.event("abandon", Rating.HARD, "2026-09-05 08:00"),
        )
        var card = Card.of("abandon")
        for (event in events) card = scheduler.review(card, event.rating, event.reviewedAt, event.studyDay)
        assertEquals(card, scheduler.replay(events, "abandon"))
        assertEquals(card, scheduler.replay(events.reversed(), "abandon"))
        assertEquals(card, scheduler.replay(events)["abandon"])
    }

    @Test
    fun replayOrdersSimultaneousEventsById() {
        val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val a = VocabFixture.event("x", Rating.AGAIN, "2026-09-01 10:00", id = second)
        val b = VocabFixture.event("x", Rating.EASY, "2026-09-01 10:00", id = first)
        assertEquals(listOf(first, second), Scheduler.ordered(listOf(a, b)).map { it.id })
        assertEquals(scheduler.replay(listOf(b, a), "x"), scheduler.replay(listOf(a, b), "x"))
    }

    @Test
    fun dueChecks() {
        val learning = review(Card.of("a"), Rating.GOOD, "2026-09-30 10:00")
        assertFalse(learning.isDue(VocabFixture.at("2026-09-30 10:09"), today = VocabFixture.day("2026-09-30")))
        assertTrue(learning.isDue(VocabFixture.at("2026-09-30 10:10"), today = VocabFixture.day("2026-09-30")))
        assertEquals(5, learning.dueDay(today = 5))
        assertFalse(Card.of("b").isDue(Instant.MAX, today = Int.MAX_VALUE))
        assertNull(Card.of("b").dueDay(today = 5))
        val graduated = review(Card.of("c"), Rating.EASY, "2026-09-30 10:00")
        assertTrue(graduated.isDue(on = VocabFixture.day("2026-10-08"), dayEnd = Instant.MIN))
        assertFalse(graduated.isDue(on = VocabFixture.day("2026-10-07"), dayEnd = Instant.MAX))
    }

    @Test
    fun retrievability() {
        val card = review(Card.of("a"), Rating.EASY, "2026-09-30 10:00")
        assertNull(scheduler.retrievability(Card.of("b"), 0))
        assertClose(scheduler.retrievability(card, VocabFixture.day("2026-09-30")), 1.0)
        val later = assertNotNull(scheduler.retrievability(card, VocabFixture.day("2026-10-08")))
        assertTrue(later > 0.85 && later < 0.95)
    }

    @Test
    fun headwordKeys() {
        assertEquals("don't mind", Headword.key(" Don’t  Mind "))
        assertEquals("abandon", Headword.key("ABANDON"))
        assertEquals("abandon", ReviewEvent(headword = "Abandon", rating = Rating.GOOD, mode = ReviewMode.CHOICE, reviewedAt = Instant.now(), studyDay = 1).headword)
    }
}

class StudyClockTests {
    private val clock = VocabFixture.clock

    @Test
    fun dayStartsAtFour() {
        assertEquals(VocabFixture.day("2026-09-29"), clock.day(VocabFixture.at("2026-09-30 03:59")))
        assertEquals(VocabFixture.day("2026-09-30"), clock.day(VocabFixture.at("2026-09-30 04:00")))
        assertEquals(VocabFixture.day("2026-09-30"), clock.day(VocabFixture.at("2026-09-30 23:59")))
        assertEquals(VocabFixture.at("2026-09-30 04:00"), clock.start(VocabFixture.day("2026-09-30")))
        assertEquals(VocabFixture.at("2026-10-01 04:00"), clock.end(VocabFixture.day("2026-09-30")))
        assertEquals(LocalDate(2026, 9, 30), clock.date(VocabFixture.day("2026-09-30")))
    }

    @Test
    fun otherTimeZonesAndDayStarts() {
        // China: UTC+8, no daylight saving.
        val clock = StudyClock(ZoneOffset.ofHours(8))
        // 2026-09-30 19:30 UTC is 2026-10-01 03:30 in Shanghai: still the study day of 09-30.
        assertEquals(VocabFixture.day("2026-09-30"), clock.day(VocabFixture.at("2026-09-30 19:30")))
        assertEquals(VocabFixture.day("2026-10-01"), clock.day(VocabFixture.at("2026-09-30 20:00")))
        assertEquals(VocabFixture.day("2026-09-30"), VocabFixture.midnightClock.day(VocabFixture.at("2026-09-30 00:00")))
    }
}
