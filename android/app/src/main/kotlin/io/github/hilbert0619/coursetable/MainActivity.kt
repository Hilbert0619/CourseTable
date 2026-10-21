package io.github.hilbert0619.coursetable

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.coursetable.alerts.Alerts
import io.github.hilbert0619.coursetable.ui.AppViewModel
import io.github.hilbert0619.coursetable.ui.RootScreen
import io.github.hilbert0619.coursetable.ui.Tab
import io.github.hilbert0619.coursetable.ui.ai.LocalAI
import io.github.hilbert0619.coursetable.ui.components.AppearanceState
import io.github.hilbert0619.coursetable.ui.components.LocalAppearance
import io.github.hilbert0619.coursetable.ui.components.LocalAppearanceActions
import io.github.hilbert0619.coursetable.ui.focus.LocalWallpaperActions
import io.github.hilbert0619.coursetable.ui.focus.LocalWallpapers
import io.github.hilbert0619.coursetable.ui.theme.StudyHourTheme
import io.github.hilbert0619.coursetable.ui.words.WordsController
import io.github.hilbert0619.vocabkit.AuthLink
import java.time.Instant

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels { AppViewModel.Factory }
    private val app: StudyHourApp get() = application as StudyHourApp

    /** The tab a notification asked for ([EXTRA_TAB]), until it's shown. */
    private var requestedTab by mutableStateOf<Tab?>(null)

    /** The Words tab's actions run in the app's scope, so leaving a screen doesn't cut a sync short. */
    private val words by lazy { WordsController(app.vocab, app.scope, app.pronunciation::speak, Instant::now) }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Alerts.requestSync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            requestedTab = tabOf(intent)
            openLink(intent)
        }
        askForNotificationsOnce()
        setContent {
            StudyHourTheme {
                val data by viewModel.data.collectAsStateWithLifecycle()
                val focus by app.focus.state.collectAsStateWithLifecycle()
                val reminders by app.alertPreferences.settings.collectAsStateWithLifecycle()
                val appearance by app.appearance.preferences.settings.collectAsStateWithLifecycle()
                val photo by app.appearance.background.image.collectAsStateWithLifecycle()
                val wallpapers by app.wallpapers.state.collectAsStateWithLifecycle()
                CompositionLocalProvider(
                    LocalAI provides app.ai,
                    LocalAppearance provides AppearanceState(appearance, photo),
                    LocalAppearanceActions provides app.appearance,
                    LocalWallpapers provides wallpapers,
                    LocalWallpaperActions provides app.wallpapers,
                ) {
                    RootScreen(
                        data, viewModel,
                        focus = focus, focusActions = app.focus, reminders = reminders, words = words,
                        requestedTab = requestedTab, onTabShown = { requestedTab = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabOf(intent)?.let { requestedTab = it }
        openLink(intent)
    }

    /** A sign-in email link (`io.github.hilbert0619.coursetable://auth-callback#…`): shows its result in the Words tab. */
    private fun openLink(intent: Intent?) {
        val url = intent?.data?.takeIf { it.scheme.equals(AuthLink.SCHEME, ignoreCase = true) }?.toString() ?: return
        requestedTab = Tab.WORDS
        words.launch { open(url) }
    }

    override fun onStart() {
        super.onStart()
        // Catch up with whatever happened while the app wasn't running.
        Alerts.requestSync(this)
        app.focus.refresh()
        // Coming back uploads answers given offline and fetches other devices' answers.
        app.vocabIfCreated?.let { vocab -> words.launch { vocab.syncNow() } }
    }

    override fun onStop() {
        super.onStop()
        app.focus.stopPreview()
    }

    /** The live class notification is on from the start, so ask once at launch (Android 13+). */
    private fun askForNotificationsOnce() {
        val preferences = app.alertPreferences
        if (Build.VERSION.SDK_INT < 33 || preferences.askedForNotifications) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        preferences.askedForNotifications = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun tabOf(intent: Intent?): Tab? = intent?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } }

    companion object {
        /** Opens the app on a tab ([Tab.name]): the class and focus notifications use it. */
        const val EXTRA_TAB = "tab"
    }
}
