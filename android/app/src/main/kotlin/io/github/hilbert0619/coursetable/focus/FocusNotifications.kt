package io.github.hilbert0619.coursetable.focus

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import io.github.hilbert0619.coursekit.FocusRun
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.plusSeconds
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.alerts.AlertNotifications
import io.github.hilbert0619.coursetable.alerts.AlertReceiver
import io.github.hilbert0619.coursetable.alerts.Channels
import io.github.hilbert0619.coursetable.alerts.NotificationIds
import io.github.hilbert0619.coursetable.ui.Tab
import java.time.Instant

/** The focus session's ongoing notification (the iOS Live Activity) and the notification at each switch. */
object FocusNotifications {
    /** Brand blue for focus, green for breaks (as the Focus tab). */
    private const val FOCUS_COLOR = 0xFF3A4DCD.toInt()
    private const val BREAK_COLOR = 0xFF2E9E4F.toInt()

    /**
     * "Focus · Block 2 of 5" / "Calculus review · ends at 21:30" with a countdown to the end of the phase, Pause or
     * Resume, and Stop. A Live Update on Android 16. `null` without a session.
     */
    fun running(context: Context, state: FocusState, now: Instant): Notification? {
        val run = state.run ?: return null
        val text = FocusText(context.resources)
        val status = run.status(now)
        val phaseEnd = now.plusSeconds(status.phaseRemaining)
        val detail = if (status.isPaused) {
            "${context.getString(R.string.paused)} · ${FocusText.clock(status.phaseRemaining)}"
        } else {
            val end = run.endDate(now) ?: phaseEnd
            context.getString(R.string.ends_at, TimeOfDay.from(end).toString())
        }
        val builder = NotificationCompat.Builder(context, Channels.FOCUS_RUNNING)
            .setSmallIcon(R.drawable.ic_tab_focus)
            .setColor(if (status.phase.isBreak) BREAK_COLOR else FOCUS_COLOR)
            .setContentTitle(text.phaseTitle(run, status.phase))
            .setContentText(listOf(run.goal, detail).filter { it.isNotEmpty() }.joinToString(" · "))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(AlertNotifications.openApp(context, Tab.FOCUS))
            .addAction(
                if (run.isPaused) R.drawable.ic_play else R.drawable.ic_pause,
                context.getString(if (run.isPaused) R.string.resume else R.string.pause),
                AlertReceiver.focusIntent(context, AlertReceiver.ACTION_FOCUS_TOGGLE),
            )
            .addAction(R.drawable.ic_stop, context.getString(R.string.stop), AlertReceiver.focusIntent(context, AlertReceiver.ACTION_FOCUS_STOP))
            .setRequestPromotedOngoing(true)
        if (status.isPaused) {
            builder.setShowWhen(false)
        } else {
            builder.setWhen(phaseEnd.toEpochMilli()).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        }
        return builder.build()
    }

    fun postRunning(context: Context, state: FocusState, now: Instant) {
        running(context, state, now)?.let { AlertNotifications.notify(context, null, NotificationIds.FOCUS_RUNNING, it) }
    }

    /** "Time for a break" / "5 min", "Back to focus" / "Block 2 of 5 · 25 min · Calculus review", "Focus session done". */
    fun postSwitch(context: Context, run: FocusRun, index: Int) {
        val (title, body) = FocusText(context.resources).switchAlert(run, index)
        val isBreak = run.plan.phases.getOrNull(index)?.isBreak == true
        val notification = NotificationCompat.Builder(context, Channels.FOCUS)
            .setSmallIcon(R.drawable.ic_tab_focus)
            .setColor(if (isBreak) BREAK_COLOR else FOCUS_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(AlertNotifications.openApp(context, Tab.FOCUS))
            .build()
        AlertNotifications.notify(context, null, NotificationIds.FOCUS_SWITCH, notification)
    }
}
