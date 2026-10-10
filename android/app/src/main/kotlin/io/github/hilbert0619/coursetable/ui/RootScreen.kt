package io.github.hilbert0619.coursetable.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursetable.BuildConfig
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.theme.StudyHourTheme

/** The five tabs, as on iOS. */
enum class Tab(
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    @param:StringRes val placeholder: Int,
) {
    TODAY(R.string.tab_today, R.drawable.ic_tab_today, R.drawable.ic_tab_today_filled, R.string.placeholder_today),
    TIMETABLE(
        R.string.tab_timetable, R.drawable.ic_tab_timetable, R.drawable.ic_tab_timetable_filled,
        R.string.placeholder_timetable,
    ),
    FOCUS(R.string.tab_focus, R.drawable.ic_tab_focus, R.drawable.ic_tab_focus_filled, R.string.placeholder_focus),
    WORDS(R.string.tab_words, R.drawable.ic_tab_words, R.drawable.ic_tab_words_filled, R.string.placeholder_words),
    SETTINGS(
        R.string.tab_settings, R.drawable.ic_tab_settings, R.drawable.ic_tab_settings_filled,
        R.string.placeholder_settings,
    ),
}

@Composable
fun RootScreen(initialTab: Tab = Tab.TODAY) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    val selected = item == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = item },
                        icon = {
                            Icon(painterResource(if (selected) item.selectedIcon else item.icon), contentDescription = null)
                        },
                        label = { Text(stringResource(item.title)) },
                    )
                }
            }
        },
    ) { padding ->
        Placeholder(tab, Modifier.padding(padding))
    }
}

/** What each tab shows until its screens are built. */
@Composable
private fun Placeholder(tab: Tab, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
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
                Text(
                    stringResource(tab.placeholder),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.placeholder_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (tab == Tab.SETTINGS) {
                    Text(
                        "${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun RootScreenPreview() {
    StudyHourTheme { RootScreen() }
}
