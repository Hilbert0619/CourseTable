package io.github.hilbert0619.coursetable

import android.app.Application
import io.github.hilbert0619.coursetable.alerts.AlertPreferences
import io.github.hilbert0619.coursetable.alerts.Alerts
import io.github.hilbert0619.coursetable.alerts.Channels
import io.github.hilbert0619.coursetable.data.AppDatabase
import io.github.hilbert0619.coursetable.data.Repository
import io.github.hilbert0619.coursetable.focus.FocusEngine
import io.github.hilbert0619.coursetable.focus.SoundPlayer
import io.github.hilbert0619.coursetable.widget.ScheduleWidgetUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the app's single database and repository, the alert settings and the focus timer. */
class StudyHourApp : Application() {
    /** Work that outlives a screen: alert syncs, saving focus history. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: Repository by lazy {
        Repository(AppDatabase.open(this)).also { repository ->
            // Every change refreshes the home-screen widget and the alerts.
            repository.onChange = {
                ScheduleWidgetUpdates.refresh(this)
                Alerts.requestSync(this)
            }
        }
    }

    val alertPreferences: AlertPreferences by lazy { AlertPreferences(this) }

    /** Created on the main thread (it plays sounds there). */
    val focus: FocusEngine by lazy { FocusEngine(this, repository, SoundPlayer(this), scope) }

    override fun onCreate() {
        super.onCreate()
        Channels.create(this)
    }
}
