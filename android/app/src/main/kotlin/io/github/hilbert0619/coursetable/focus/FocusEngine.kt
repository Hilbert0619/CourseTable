package io.github.hilbert0619.coursetable.focus

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import io.github.hilbert0619.coursekit.CourseKitJson
import io.github.hilbert0619.coursekit.FocusPlan
import io.github.hilbert0619.coursekit.FocusRun
import io.github.hilbert0619.coursekit.FocusSettings
import io.github.hilbert0619.coursekit.plusSeconds
import io.github.hilbert0619.coursetable.alerts.Alarms
import io.github.hilbert0619.coursetable.alerts.IslandCountdown
import io.github.hilbert0619.coursetable.alerts.LiveUpdates
import io.github.hilbert0619.coursetable.alerts.NotificationIds
import io.github.hilbert0619.coursetable.data.FocusSessionRecord
import io.github.hilbert0619.coursetable.data.Repository
import io.github.hilbert0619.coursetable.island.Island
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the Focus tab shows: the setup the person last used, and the session in progress. */
data class FocusState(
    val run: FocusRun? = null,
    val settings: FocusSettings = FocusSettings(),
    val goal: String = "",
    val sound: FocusSound = FocusSound.RAIN,
    val volume: Float = 0.6f,
    /** Playing the chosen sound on the setup screen. */
    val previewing: Boolean = false,
) {
    val plan: FocusPlan get() = FocusPlan(settings)
}

/** Everything the Focus tab can do. [FocusEngine] does it; screenshot tests pass [None]. */
interface FocusActions {
    fun setGoal(goal: String)
    fun setSettings(settings: FocusSettings)
    fun setSound(sound: FocusSound)
    fun setVolume(volume: Float)
    fun togglePreview()
    fun stopPreview()
    fun start()
    fun togglePause()
    fun skipBreak()
    fun stop()

    /** Catches up when a switch comes while the screen shows the session (its alarm may come a little later). */
    fun refresh()

    object None : FocusActions {
        override fun setGoal(goal: String) = Unit
        override fun setSettings(settings: FocusSettings) = Unit
        override fun setSound(sound: FocusSound) = Unit
        override fun setVolume(volume: Float) = Unit
        override fun togglePreview() = Unit
        override fun stopPreview() = Unit
        override fun start() = Unit
        override fun togglePause() = Unit
        override fun skipBreak() = Unit
        override fun stop() = Unit
        override fun refresh() = Unit
    }
}

/**
 * The focus timer (iOS `FocusEngine`). The run is a core [FocusRun] saved in `SharedPreferences`, so the app, the
 * notification's buttons and a relaunch (even one started by an alarm after the app was closed) all continue the same
 * session. An exact alarm at each switch between focus and break posts its notification and updates the ongoing one;
 * while a sound plays, [FocusSoundService] keeps the app running. Main thread only.
 */
class FocusEngine(
    private val context: Context,
    private val repository: Repository,
    private val sound: SoundOutput,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
) : FocusActions {
    private val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    /** The last switch notified: a phase index, or the number of phases for the end. */
    private var lastAlerted: Int
        get() = prefs.getInt(LAST_ALERTED, 0)
        set(value) = prefs.edit().putInt(LAST_ALERTED, value).apply()

    private val run: FocusRun? get() = _state.value.run

    // Setup

    override fun setGoal(goal: String) = change { it.copy(goal = goal) }

    override fun setSettings(settings: FocusSettings) = change { it.copy(settings = settings) }

    override fun setSound(sound: FocusSound) {
        change { it.copy(sound = sound) }
        updateSound()
    }

    override fun setVolume(volume: Float) {
        change { it.copy(volume = volume.coerceIn(0f, 1f)) }
        sound.setVolume(_state.value.volume)
    }

    override fun togglePreview() {
        change { it.copy(previewing = !it.previewing) }
        updateSound()
    }

    override fun stopPreview() {
        if (!_state.value.previewing) return
        change { it.copy(previewing = false) }
        updateSound()
    }

    // Session

    override fun start() {
        val now = clock.instant()
        val state = _state.value
        lastAlerted = 0
        change { it.copy(run = FocusRun(goal = state.goal.trim(), plan = state.plan, startedAt = now), previewing = false) }
        afterChange(now)
    }

    override fun togglePause() {
        val run = run ?: return
        val now = clock.instant()
        apply(if (run.isPaused) run.resume(now) else run.pause(now), now)
    }

    override fun skipBreak() {
        val run = run ?: return
        val now = clock.instant()
        val skipped = run.skipBreak(now)
        // Skipping is the switch: no notification for it.
        lastAlerted = maxOf(lastAlerted, skipped.status(now).phaseIndex)
        apply(skipped, now)
    }

    /** Ends the session early; it's kept in the history as stopped. */
    override fun stop() {
        val run = run ?: return
        finish(run, clock.instant(), finished = false)
    }

    /**
     * Catches up at an alarm, when the app comes to the foreground, or after a restart: notifies a switch that came,
     * ends a finished session, and sets the next alarm.
     */
    override fun refresh() {
        val run = run ?: return
        val now = clock.instant()
        dueAlert(run, lastAlerted, now)?.let { index ->
            FocusNotifications.postSwitch(context, run, index)
            lastAlerted = index
        }
        if (run.status(now).isFinished) finish(run, now, finished = true) else afterChange(now)
    }

    /** The minute in the island changed ([LiveUpdates]): the ongoing notification again, and the next minute. */
    fun islandMinute() {
        val run = run ?: return
        val now = clock.instant()
        if (run.status(now).isFinished) return refresh()
        FocusNotifications.postRunning(context, _state.value, now)
        setIslandMinute(run, now)
    }

    private fun apply(run: FocusRun, now: Instant) {
        change { it.copy(run = run) }
        afterChange(now)
    }

    /** The sound, the ongoing notification and the alarm for the next switch, after the run changed. */
    private fun afterChange(now: Instant) {
        val run = run ?: return
        updateSound()
        FocusNotifications.postRunning(context, _state.value, now)
        val next = run.upcomingSwitches(now, limit = 1).firstOrNull()
        if (next != null) Alarms.set(context, Alarms.FOCUS, next.date) else Alarms.cancel(context, Alarms.FOCUS)
        setIslandMinute(run, now)
    }

    /** On an island that shows the minutes left, an alarm when the number changes (the switch has its own). */
    private fun setIslandMinute(run: FocusRun, now: Instant) {
        val status = run.status(now)
        val minute = if (!status.isPaused && LiveUpdates.islandNeedsText(context)) {
            IslandCountdown.nextChange(now.plusSeconds(status.phaseRemaining), now)
        } else {
            null
        }
        Alarms.setWhileAwake(context, Alarms.FOCUS_ISLAND, minute)
    }

    private fun finish(run: FocusRun, at: Instant, finished: Boolean) {
        val status = run.status(at)
        val end = if (finished) run.startedAt.plusSeconds(run.plan.totalSeconds + run.pausedSeconds - run.skippedSeconds) else at
        val record = FocusSessionRecord(
            id = run.id, goal = run.goal, startedAt = run.startedAt, endedAt = minOf(end, at), focusedSeconds = status.focused.toInt(),
            plannedSeconds = run.plan.focusSeconds, finished = finished,
        )
        scope.launch { repository.recordFocus(record) }
        change { it.copy(run = null) }
        Alarms.cancel(context, Alarms.FOCUS)
        Alarms.cancel(context, Alarms.FOCUS_ISLAND)
        updateSound()
        NotificationManagerCompat.from(context).cancel(NotificationIds.FOCUS_RUNNING)
        Island.changed()
    }

    /** Plays the sound while a session runs (not paused) or a preview is on; the service keeps a session's going. */
    private fun updateSound() {
        val state = _state.value
        val running = state.run != null && !state.run.isPaused
        if ((running || state.previewing) && state.sound != FocusSound.NONE) sound.play(state.sound, state.volume) else sound.stop()
        if (running && state.sound != FocusSound.NONE) FocusSoundService.start(context) else FocusSoundService.stop(context)
    }

    // Saving

    private fun change(transform: (FocusState) -> FocusState) {
        val previous = _state.value
        val state = transform(previous)
        _state.value = state
        val editor = prefs.edit()
            .putString(SETTINGS, CourseKitJson.encodeToString(FocusSettings.serializer(), state.settings))
            .putString(GOAL, state.goal)
            .putString(SOUND, state.sound.id)
            .putFloat(VOLUME, state.volume)
        if (state.run != null) editor.putString(RUN, CourseKitJson.encodeToString(FocusRun.serializer(), state.run)) else editor.remove(RUN)
        // A changed run is written at once: an alarm may start a new process right after it.
        if (state.run != previous.run) editor.commit() else editor.apply()
    }

    private fun load(): FocusState {
        fun <T> decode(key: String, decoder: (String) -> T): T? =
            prefs.getString(key, null)?.let { text -> runCatching { decoder(text) }.getOrNull() }
        return FocusState(
            run = decode(RUN) { CourseKitJson.decodeFromString(FocusRun.serializer(), it) },
            settings = decode(SETTINGS) { CourseKitJson.decodeFromString(FocusSettings.serializer(), it) } ?: FocusSettings(),
            goal = prefs.getString(GOAL, null).orEmpty(),
            sound = FocusSound.of(prefs.getString(SOUND, null)) ?: FocusSound.RAIN,
            volume = prefs.getFloat(VOLUME, 0.6f),
        )
    }

    companion object {
        private const val RUN = "run"
        private const val SETTINGS = "settings"
        private const val GOAL = "goal"
        private const val SOUND = "sound"
        private const val VOLUME = "volume"
        private const val LAST_ALERTED = "lastAlerted"

        /** The switch to notify at [now] (a phase index, or the phase count once it's over), unless already done. */
        fun dueAlert(run: FocusRun, lastAlerted: Int, now: Instant): Int? {
            val status = run.status(now)
            val index = if (status.isFinished) run.plan.phases.size else status.phaseIndex
            return index.takeIf { it > lastAlerted }
        }
    }
}
