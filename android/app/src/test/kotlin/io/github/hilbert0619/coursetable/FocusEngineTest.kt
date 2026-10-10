package io.github.hilbert0619.coursetable

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hilbert0619.coursekit.FocusPlan
import io.github.hilbert0619.coursekit.FocusRun
import io.github.hilbert0619.coursekit.FocusSettings
import io.github.hilbert0619.coursetable.alerts.NotificationIds
import io.github.hilbert0619.coursetable.data.AppDatabase
import io.github.hilbert0619.coursetable.data.Repository
import io.github.hilbert0619.coursetable.focus.FocusEngine
import io.github.hilbert0619.coursetable.focus.FocusSound
import io.github.hilbert0619.coursetable.focus.SoundOutput
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The focus timer's state, notifications, alarms and history (Robolectric, in-memory database, a clock we move). */
@RunWith(AndroidJUnit4::class)
class FocusEngineTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var database: AppDatabase
    private lateinit var repository: Repository
    private val scope = CoroutineScope(Job())
    private val sound = RecordingSound()
    private val clock = MovableClock(Instant.parse("2026-09-28T12:00:00Z"))
    private lateinit var engine: FocusEngine

    private val notifications get() = application.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        database = AppDatabase.inMemory(application)
        repository = Repository(database, clock)
        engine = newEngine()
        // 2 h 30 min: five 25-minute blocks with breaks (FocusTests in :core).
        engine.setSettings(FocusSettings(totalMinutes = 150))
        engine.setGoal("  Calculus review ")
        engine.setSound(FocusSound.NONE)
    }

    @After
    fun tearDown() = database.close()

    private fun newEngine() = FocusEngine(application, repository, sound, scope, clock)

    private fun history() = runBlocking {
        scope.coroutineContext.job.children.forEach { it.join() }
        repository.focusSessions.first()
    }

    private fun switchNotification() = notifications.activeNotifications.firstOrNull { it.id == NotificationIds.FOCUS_SWITCH }?.notification

    @Test
    fun startingSavesTheRunAndSetsTheAlarmForTheFirstBreak() {
        engine.start()
        val run = engine.state.value.run
        assertNotNull(run)
        assertEquals("Calculus review", run!!.goal)
        // Another engine (a new process) continues the same session.
        assertEquals(run, newEngine().state.value.run)
        val alarm = shadowOf(application.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm()!!
        assertEquals(clock.instant().plus(Duration.ofMinutes(25)).toEpochMilli(), alarm.triggerAtMs)
        assertTrue(notifications.activeNotifications.any { it.id == NotificationIds.FOCUS_RUNNING })
    }

    @Test
    fun pauseAndResume() {
        engine.start()
        clock.advance(Duration.ofMinutes(10))
        engine.togglePause()
        assertTrue(engine.state.value.run!!.isPaused)
        clock.advance(Duration.ofMinutes(5))
        engine.togglePause()
        val run = engine.state.value.run!!
        assertFalse(run.isPaused)
        assertEquals(300.0, run.pausedSeconds, 0.001)
        assertEquals(600.0, run.elapsed(clock.instant()), 0.001)
    }

    @Test
    fun eachSwitchIsNotifiedOnce() {
        engine.start()
        clock.advance(Duration.ofMinutes(25))
        engine.refresh()
        val first = switchNotification()
        assertNotNull(first)
        assertEquals("Time for a break", first!!.extras.getCharSequence("android.title")?.toString())
        notifications.cancel(NotificationIds.FOCUS_SWITCH)
        clock.advance(Duration.ofSeconds(30))
        engine.refresh()
        assertNull(switchNotification())
    }

    @Test
    fun skippingABreakIsNotNotified() {
        engine.start()
        clock.advance(Duration.ofMinutes(26))
        engine.refresh()
        notifications.cancel(NotificationIds.FOCUS_SWITCH)
        engine.skipBreak()
        engine.refresh()
        assertNull(switchNotification())
        assertEquals(2, engine.state.value.run!!.status(clock.instant()).phaseIndex)
    }

    @Test
    fun aFinishedSessionGoesIntoTheHistory() {
        engine.start()
        clock.advance(Duration.ofMinutes(151))
        engine.refresh()
        assertNull(engine.state.value.run)
        val session = history().single()
        assertTrue(session.finished)
        assertEquals(FocusPlan(FocusSettings(totalMinutes = 150)).focusSeconds, session.focusedSeconds)
        assertEquals("Focus session done", switchNotification()!!.extras.getCharSequence("android.title")?.toString())
        assertFalse(notifications.activeNotifications.any { it.id == NotificationIds.FOCUS_RUNNING })
    }

    @Test
    fun stoppingKeepsTheTimeFocused() {
        engine.start()
        clock.advance(Duration.ofMinutes(10))
        engine.stop()
        assertNull(engine.state.value.run)
        assertNull(newEngine().state.value.run)
        val session = history().single()
        assertFalse(session.finished)
        assertEquals(600, session.focusedSeconds)
    }

    @Test
    fun soundPlaysWhileRunningAndStopsWhenPaused() {
        engine.setSound(FocusSound.RAIN)
        engine.start()
        assertEquals(FocusSound.RAIN, sound.playing)
        engine.togglePause()
        assertNull(sound.playing)
        engine.togglePause()
        assertEquals(FocusSound.RAIN, sound.playing)
        engine.stop()
        assertNull(sound.playing)
    }

    @Test
    fun theDueSwitch() {
        val start = clock.instant()
        val run = FocusRun(goal = "", plan = FocusPlan(FocusSettings(totalMinutes = 60)), startedAt = start)
        assertNull(FocusEngine.dueAlert(run, 0, start.plusSeconds(60)))
        assertEquals(1, FocusEngine.dueAlert(run, 0, start.plus(Duration.ofMinutes(25))))
        assertNull(FocusEngine.dueAlert(run, 1, start.plus(Duration.ofMinutes(29))))
        assertEquals(run.plan.phases.size, FocusEngine.dueAlert(run, 2, start.plus(Duration.ofMinutes(61))))
    }

    private class RecordingSound : SoundOutput {
        var playing: FocusSound? = null

        override fun play(sound: FocusSound, volume: Float) {
            playing = sound.takeIf { it != FocusSound.NONE }
        }

        override fun setVolume(volume: Float) = Unit

        override fun stop() {
            playing = null
        }
    }

    private class MovableClock(private var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun instant(): Instant = now
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MovableClock(now, zone)
    }
}
