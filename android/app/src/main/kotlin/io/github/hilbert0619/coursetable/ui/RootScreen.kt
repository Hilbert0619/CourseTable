package io.github.hilbert0619.coursetable.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ai.AgentContext
import io.github.hilbert0619.coursetable.alerts.ReminderSettings
import io.github.hilbert0619.coursetable.ui.ai.LocalAgentContext
import io.github.hilbert0619.coursetable.ui.components.AppBackdrop
import io.github.hilbert0619.coursetable.ui.components.LocalAppearance
import io.github.hilbert0619.coursetable.ui.components.LocalGlass
import io.github.hilbert0619.coursetable.ui.components.LocalTabBackdrop
import io.github.hilbert0619.coursetable.ui.components.frosted
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.hilbert0619.coursetable.ui.words.LocalWordsController
import io.github.hilbert0619.coursetable.focus.FocusActions
import io.github.hilbert0619.coursetable.focus.FocusState
import io.github.hilbert0619.coursetable.ui.focus.FocusScreen
import io.github.hilbert0619.coursetable.ui.words.WordsController
import io.github.hilbert0619.coursetable.ui.words.WordsTab
import io.github.hilbert0619.coursetable.ui.settings.SettingsScreen
import io.github.hilbert0619.coursetable.ui.timetable.TimetableScreen
import io.github.hilbert0619.coursetable.ui.today.TodayScreen

/** The five tabs, as on iOS; Android hides Words for now ([RootScreen]). */
enum class Tab(
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    TODAY(R.string.tab_today, R.drawable.ic_tab_today, R.drawable.ic_tab_today_filled),
    TIMETABLE(R.string.tab_timetable, R.drawable.ic_tab_timetable, R.drawable.ic_tab_timetable_filled),
    FOCUS(R.string.tab_focus, R.drawable.ic_tab_focus, R.drawable.ic_tab_focus_filled),
    WORDS(R.string.tab_words, R.drawable.ic_tab_words, R.drawable.ic_tab_words_filled),
    SETTINGS(R.string.tab_settings, R.drawable.ic_tab_settings, R.drawable.ic_tab_settings_filled),
}

/**
 * The tab bar and the selected tab. [data] is `null` until the database has been read. [requestedTab] comes from a
 * notification; [onTabShown] clears it. Without [words] there's no Words tab (hidden for now: `BuildConfig.WORDS`).
 */
@Composable
fun RootScreen(
    data: AppData?,
    actions: AppActions,
    initialTab: Tab = Tab.TODAY,
    focus: FocusState = FocusState(),
    focusActions: FocusActions = FocusActions.None,
    reminders: ReminderSettings = ReminderSettings(),
    requestedTab: Tab? = null,
    onTabShown: () -> Unit = {},
    words: WordsController? = null,
) {
    val tabs = Tab.entries.filter { it != Tab.WORDS || words != null }
    var tab by rememberSaveable { mutableStateOf(initialTab.takeIf { it in tabs } ?: Tab.TODAY) }
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            if (requestedTab in tabs) tab = requestedTab
            onTabShown()
        }
    }
    val glass = LocalAppearance.current.settings.isGlass
    // The wallpaper behind the main tabs in the glass look, behind the timetable alone in the classic one.
    val backdropTabs = if (glass) setOf(Tab.TODAY, Tab.TIMETABLE, Tab.FOCUS, Tab.WORDS) else setOf(Tab.TIMETABLE)
    val showsBackdrop = data != null && tab in backdropTabs
    val glassBar = showsBackdrop && glass
    val haze = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        if (showsBackdrop) AppBackdrop(Modifier.fillMaxSize().hazeSource(haze))
        Scaffold(
            containerColor = if (showsBackdrop) Color.Transparent else MaterialTheme.colorScheme.background,
            bottomBar = {
                // A frosted tab bar on the wallpaper, as on iOS.
                CompositionLocalProvider(LocalGlass provides if (glassBar) haze else null) {
                    NavigationBar(
                        modifier = Modifier.frosted(RectangleShape, thick = true),
                        containerColor = if (glassBar) Color.Transparent else NavigationBarDefaults.containerColor,
                        tonalElevation = if (glassBar) 0.dp else NavigationBarDefaults.Elevation,
                    ) {
                        tabs.forEach { item ->
                            val selected = item == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { tab = item },
                                icon = { Icon(painterResource(if (selected) item.selectedIcon else item.icon), contentDescription = null) },
                                label = { Text(stringResource(item.title)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            val content = Modifier.padding(padding).consumeWindowInsets(padding)
            if (data == null) {
                Box(content.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }
            // What the Assistant's tools read and change, from any tab.
            val clock = LocalNowSource.current.clock
            val agent = AgentContext(data, actions, focus, focusActions, words?.vocab, clock)
            // The Words screens the Assistant links to need the Words controller outside the Words tab too.
            val locals = listOfNotNull(
                LocalAgentContext provides agent,
                words?.let { LocalWordsController provides it },
                LocalTabBackdrop provides if (showsBackdrop) haze else null,
            )
            CompositionLocalProvider(*locals.toTypedArray()) {
                when (tab) {
                    Tab.TODAY -> TodayScreen(data.timetable, data.todos, actions, content)
                    Tab.TIMETABLE -> TimetableScreen(data.timetable, data.templates, actions, content)
                    Tab.FOCUS -> FocusScreen(
                        focus, data.focusSessions, focusActions, content,
                        onDeleteSessions = actions::deleteFocusSessions, onClearHistory = actions::deleteAllFocusSessions,
                    )
                    Tab.WORDS -> words?.let { WordsTab(it, content) }
                    Tab.SETTINGS -> SettingsScreen(
                        data.timetable, reminders, actions, content,
                        focusSessionCount = data.focusSessions.size, completedTodoCount = data.todos.count { it.isDone },
                    )
                }
            }
        }
    }
}
