package io.github.hilbert0619.coursetable

import android.app.Application
import io.github.hilbert0619.coursetable.data.AppDatabase
import io.github.hilbert0619.coursetable.data.Repository
import io.github.hilbert0619.coursetable.widget.ScheduleWidgetUpdates

/** Holds the app's single database and repository. */
class StudyHourApp : Application() {
    val repository: Repository by lazy {
        Repository(AppDatabase.open(this)).also { repository ->
            // Every change refreshes the home-screen widget.
            repository.onChange = { ScheduleWidgetUpdates.refresh(this) }
        }
    }
}
