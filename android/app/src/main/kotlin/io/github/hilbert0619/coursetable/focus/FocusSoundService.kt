package io.github.hilbert0619.coursetable.focus

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.hilbert0619.coursetable.StudyHourApp
import io.github.hilbert0619.coursetable.alerts.Channels
import io.github.hilbert0619.coursetable.alerts.NotificationIds
import io.github.hilbert0619.coursetable.R
import java.time.Instant

/**
 * A foreground service (type media playback) while a focus session plays a sound, so Android keeps the app and the
 * sound running in the background (iOS: the `audio` background mode). It shows the focus notification; when it
 * stops (paused, no sound) the notification stays for [FocusEngine] to update, and goes when the session ends.
 */
class FocusSoundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as StudyHourApp
        val state = app.focus.state.value
        // startForeground is required after startForegroundService, even if the session ended meanwhile.
        val notification = FocusNotifications.running(this, state, Instant.now())
            ?: NotificationCompat.Builder(this, Channels.FOCUS_RUNNING)
                .setSmallIcon(R.drawable.ic_tab_focus)
                .setContentTitle(getString(R.string.tab_focus))
                .setSilent(true)
                .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, NotificationIds.FOCUS_RUNNING, notification, type)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Couldn't run in the foreground", error)
            stopSelf()
            return START_NOT_STICKY
        }
        if (state.run == null || state.run.isPaused || state.sound == FocusSound.NONE) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val running = (application as StudyHourApp).focus.state.value.run != null
        ServiceCompat.stopForeground(this, if (running) ServiceCompat.STOP_FOREGROUND_DETACH else ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FocusSoundService"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, FocusSoundService::class.java))
            } catch (error: IllegalStateException) {
                // Not allowed from the background (Android 12+): the sound plays while the app is in use.
                Log.w(TAG, "Couldn't start the sound service", error)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusSoundService::class.java))
        }
    }
}
