package io.github.hilbert0619.coursetable.alerts

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.hilbert0619.coursetable.StudyHourApp
import io.github.hilbert0619.coursetable.widget.ScheduleWidgetUpdates
import kotlinx.coroutines.launch

/** The app's alarms ([Alarms]) and the buttons on its notifications. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as StudyHourApp
        when (intent.action) {
            ACTION_SYNC -> app.runAsync(this) { Alerts.sync(app) }
            ACTION_CLOSE, ACTION_DISMISSED -> {
                val signature = intent.getStringExtra(EXTRA_SIGNATURE) ?: return
                val end = intent.getLongExtra(EXTRA_END, 0)
                app.runAsync(this) { Alerts.dismiss(app, signature, end, intent.getStringExtra(EXTRA_TAG)) }
            }
            ACTION_TEST -> AlertNotifications.postTest(app)
            // The focus engine works on the main thread, where receivers run.
            ACTION_FOCUS_ALARM -> app.focus.refresh()
            ACTION_FOCUS_TOGGLE -> app.focus.togglePause()
            ACTION_FOCUS_STOP -> app.focus.stop()
        }
    }

    companion object {
        const val ACTION_SYNC = "io.github.hilbert0619.coursetable.SYNC_ALERTS"
        const val ACTION_TEST = "io.github.hilbert0619.coursetable.TEST_REMINDER"
        const val ACTION_CLOSE = "io.github.hilbert0619.coursetable.CLOSE_CLASS"
        const val ACTION_DISMISSED = "io.github.hilbert0619.coursetable.CLASS_DISMISSED"
        const val ACTION_FOCUS_ALARM = "io.github.hilbert0619.coursetable.FOCUS_ALARM"
        const val ACTION_FOCUS_TOGGLE = "io.github.hilbert0619.coursetable.FOCUS_TOGGLE"
        const val ACTION_FOCUS_STOP = "io.github.hilbert0619.coursetable.FOCUS_STOP"
        private const val EXTRA_TAG = "tag"
        private const val EXTRA_SIGNATURE = "signature"
        private const val EXTRA_END = "end"

        fun alarmIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(context, Alarms.requestCode(action), Intent(context, AlertReceiver::class.java).setAction(action), Alarms.FLAGS)

        /** The Close button of a live class notification ([data] tells the classes apart). */
        fun closeIntent(context: Context, data: Uri, tag: String, signature: String, end: Long): PendingIntent {
            val intent = Intent(context, AlertReceiver::class.java).setAction(ACTION_CLOSE).setData(data)
                .putExtra(EXTRA_TAG, tag).putExtra(EXTRA_SIGNATURE, signature).putExtra(EXTRA_END, end)
            return PendingIntent.getBroadcast(context, 0, intent, Alarms.FLAGS)
        }

        /** Sent when the person swipes a live class notification away. */
        fun dismissIntent(context: Context, data: Uri, signature: String, end: Long): PendingIntent {
            val intent = Intent(context, AlertReceiver::class.java).setAction(ACTION_DISMISSED).setData(data)
                .putExtra(EXTRA_SIGNATURE, signature).putExtra(EXTRA_END, end)
            return PendingIntent.getBroadcast(context, 0, intent, Alarms.FLAGS)
        }

        /** A button on the focus notification. */
        fun focusIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(context, 0, Intent(context, AlertReceiver::class.java).setAction(action), Alarms.FLAGS)
    }
}

/** A restart, a new version of the app, a change of the clock or time zone, exact alarms allowed: everything again. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as StudyHourApp
        app.focus.refresh()
        app.runAsync(this) {
            Alerts.sync(app)
            ScheduleWidgetUpdates.refresh(app)
            ScheduleWidgetUpdates.scheduleNext(app, app.repository.currentTimetable())
        }
    }
}

/** Runs [block] off the main thread, keeping the receiver alive until it's done. */
private fun StudyHourApp.runAsync(receiver: BroadcastReceiver, block: suspend () -> Unit) {
    val pending = receiver.goAsync()
    scope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
