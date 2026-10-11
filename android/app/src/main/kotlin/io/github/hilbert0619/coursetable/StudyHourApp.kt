package io.github.hilbert0619.coursetable

import android.app.Application
import android.util.Log
import io.github.hilbert0619.coursetable.alerts.AlertPreferences
import io.github.hilbert0619.coursetable.alerts.Alerts
import io.github.hilbert0619.coursetable.alerts.Channels
import io.github.hilbert0619.coursetable.data.AppDatabase
import io.github.hilbert0619.coursetable.data.Repository
import io.github.hilbert0619.coursetable.focus.FocusEngine
import io.github.hilbert0619.coursetable.focus.SoundPlayer
import io.github.hilbert0619.coursetable.widget.ScheduleWidgetUpdates
import io.github.hilbert0619.coursetable.words.Pronunciation
import io.github.hilbert0619.coursetable.words.RoomVocabStore
import io.github.hilbert0619.coursetable.words.SupabaseBackend
import io.github.hilbert0619.coursetable.words.VocabDatabase
import io.github.hilbert0619.vocabkit.store.VocabApp
import io.github.hilbert0619.vocabkit.store.VocabModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the app's databases and repository, the alert settings, the focus timer and the Words tab. */
class StudyHourApp : Application() {
    /** Work that outlives a screen: alert syncs, saving focus history, Words syncs. A failure is logged, not fatal. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> Log.e("StudyHour", "Background work failed", error) })

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

    private val vocabLazy = lazy { VocabApp(SupabaseBackend(), VocabModel(RoomVocabStore(VocabDatabase.open(this)))) }

    /** The Words tab: its own database (`vocabulary.db`) and the Supabase project. Created when first needed. */
    val vocab: VocabApp by vocabLazy

    /** The Words tab if it has been opened in this process (nothing to sync otherwise). */
    val vocabIfCreated: VocabApp? get() = if (vocabLazy.isInitialized()) vocab else null

    val pronunciation: Pronunciation by lazy { Pronunciation(this) }

    override fun onCreate() {
        super.onCreate()
        Channels.create(this)
    }
}
