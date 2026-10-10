package io.github.hilbert0619.coursetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursetable.StudyHourApp
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Keeps the widget current: redrawn after every change to the data, and at each class start and end (the iOS widget
 * timeline), with an inexact alarm that needs no permission.
 */
object ScheduleWidgetUpdates {
    suspend fun refresh(context: Context) {
        if (GlanceAppWidgetManager(context).getGlanceIds(ScheduleWidget::class.java).isEmpty()) return
        ScheduleWidget().updateAll(context)
    }

    /** Wakes [RefreshReceiver] at the next moment the widget's content changes (a class start or end, or midnight). */
    fun scheduleNext(context: Context, timetable: Timetable, now: Instant = Instant.now()) {
        val moment = Moment.from(now)
        val next = timetable.timelineMoments(moment).firstOrNull { it > moment } ?: return
        val at = next.toInstant().toEpochMilli()
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        // Not a wake-up alarm: if the phone is asleep, the widget isn't being looked at either.
        alarms.setWindow(AlarmManager.RTC, at, 60_000, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    class RefreshReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val app = context.applicationContext as StudyHourApp
                    refresh(context)
                    scheduleNext(context, app.repository.currentTimetable())
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
