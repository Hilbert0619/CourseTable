package io.github.hilbert0619.coursetable.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.alerts.ReminderSettings
import io.github.hilbert0619.coursetable.focus.FocusActions
import io.github.hilbert0619.coursetable.focus.FocusState
import io.github.hilbert0619.coursetable.ui.focus.FocusScreen
import io.github.hilbert0619.coursetable.ui.settings.SettingsScreen
import io.github.hilbert0619.coursetable.ui.timetable.TimetableScreen
import io.github.hilbert0619.coursetable.ui.today.TodayScreen

/** The five tabs, as on iOS. */
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
 * notification; [onTabShown] clears it.
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
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            tab = requestedTab
            onTabShown()
        }
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    val selected = item == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(if (selected) item.selectedIcon else item.icon), contentDescription = null) },
                        label = { Text(stringResource(item.title)) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding).consumeWindowInsets(padding)
        if (data == null) {
            Box(content.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        when (tab) {
            Tab.TODAY -> TodayScreen(data.timetable, data.todos, actions, content)
            Tab.TIMETABLE -> TimetableScreen(data.timetable, data.templates, actions, content)
            Tab.FOCUS -> FocusScreen(focus, data.focusSessions, focusActions, content)
            Tab.WORDS -> Placeholder(tab, R.string.placeholder_words, content)
            Tab.SETTINGS -> SettingsScreen(data.timetable, reminders, actions, content)
        }
    }
}

/** What a tab shows until its screens are built. */
@Composable
private fun Placeholder(tab: Tab, @StringRes text: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            stringResource(tab.title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(
                        painterResource(tab.selectedIcon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(20.dp).size(40.dp),
                    )
                }
                Text(stringResource(text), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.placeholder_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
