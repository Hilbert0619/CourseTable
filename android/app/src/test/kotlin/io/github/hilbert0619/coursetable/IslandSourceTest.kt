package io.github.hilbert0619.coursetable

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseWeeks
import io.github.hilbert0619.coursekit.FocusPlan
import io.github.hilbert0619.coursekit.FocusRun
import io.github.hilbert0619.coursekit.FocusSettings
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.Semester
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Weekday
import io.github.hilbert0619.coursekit.upcomingActivityWindows
import io.github.hilbert0619.coursetable.alerts.AlertNotifications
import io.github.hilbert0619.coursetable.alerts.Channels
import io.github.hilbert0619.coursetable.alerts.LivePost
import io.github.hilbert0619.coursetable.alerts.NotificationIds
import io.github.hilbert0619.coursetable.focus.FocusNotifications
import io.github.hilbert0619.coursetable.focus.FocusState
import io.github.hilbert0619.coursetable.island.IslandKind
import io.github.hilbert0619.coursetable.island.IslandSource
import java.time.Duration
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The island reads the countdowns back from StudyHour's own notifications (Robolectric). */
@RunWith(AndroidJUnit4::class)
class IslandSourceTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate(2026, 9, 28)
    private val calculus = Course(
        name = "Calculus", teacher = "Li", room = "A101", weekday = Weekday.MONDAY, startPeriod = 1, periodCount = 2,
        weeks = CourseWeeks(1, 16), colorIndex = 3,
    )
    private val timetable = Timetable(Semester(LocalDate(2026, 9, 7)), courses = listOf(calculus))

    private fun at(time: String) = Moment(monday, TimeOfDay.parse(time)!!).toInstant(zone)

    @Before
    fun allowNotifications() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Channels.create(context)
    }

    @Test
    fun nothingShowsWithoutACountdown() {
        AlertNotifications.postTest(context)
        assertTrue(IslandSource.read(context).isEmpty())
    }

    @Test
    fun theTestLiveUpdateCountsDownTwoMinutesWithItsCloseButton() {
        val now = at("09:00")
        AlertNotifications.postTestLive(context, now)
        val entry = IslandSource.read(context).single()
        assertEquals(IslandKind.TEST, entry.item.kind)
        assertEquals("Test Live Update", entry.item.title)
        assertEquals(now.plusSeconds(120), entry.item.target)
        assertEquals(listOf("Close"), entry.actions.map { it.title })
        assertNotNull(entry.open)
    }

    @Test
    fun aClassInProgressCountsDownToItsEnd() {
        val window = timetable.upcomingActivityWindows(Moment(monday, TimeOfDay(0)), 10).first()
        val now = at("08:30")
        AlertNotifications.postLive(context, LivePost(window, started = true, alert = false), now, zone, islandText = false)
        val item = IslandSource.read(context).single().item
        assertEquals(IslandKind.CLASS, item.kind)
        assertEquals("Calculus", item.title)
        assertTrue(item.text, item.text.startsWith("Until 09:40"))
        assertEquals(at("09:40"), item.target)
        assertTrue(item.started)
    }

    @Test
    fun aPausedSessionHasNoCountdown() {
        val now = at("09:00")
        val run = FocusRun(goal = "Calculus review", plan = FocusPlan(FocusSettings(totalMinutes = 150)), startedAt = now.minus(Duration.ofMinutes(7)))
        val notification = FocusNotifications.running(context, FocusState(run = run.pause(now)), now)!!
        AlertNotifications.notify(context, null, NotificationIds.FOCUS_RUNNING, notification)
        val entry = IslandSource.read(context).single()
        assertEquals(IslandKind.FOCUS, entry.item.kind)
        assertNull(entry.item.target)
        assertEquals(listOf("Resume", "Stop"), entry.actions.map { it.title })
    }
}
