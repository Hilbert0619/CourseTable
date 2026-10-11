@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.words

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.EditorDialog
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.coursetable.ui.components.WarningRow
import io.github.hilbert0619.vocabkit.DailyPlan
import io.github.hilbert0619.vocabkit.ExamCountdown
import io.github.hilbert0619.vocabkit.VocabStats
import io.github.hilbert0619.vocabkit.store.SyncEngine
import io.github.hilbert0619.vocabkit.store.VocabApp
import io.github.hilbert0619.vocabkit.store.VocabState

/** Places reachable from the Words home screen, saved as text so they survive a rotation. */
sealed interface WordsRoute {
    data object Search : WordsRoute
    data object Favorites : WordsRoute
    data object HardWords : WordsRoute
    data object Statistics : WordsRoute
    data object Books : WordsRoute
    data object Settings : WordsRoute
    data class Word(val headword: String) : WordsRoute

    val key: String
        get() = when (this) {
            Search -> "search"
            Favorites -> "favorites"
            HardWords -> "hard"
            Statistics -> "stats"
            Books -> "books"
            Settings -> "settings"
            is Word -> "word:$headword"
        }

    companion object {
        fun of(key: String): WordsRoute? = when {
            key.startsWith("word:") -> Word(key.removePrefix("word:"))
            else -> listOf(Search, Favorites, HardWords, Statistics, Books, Settings).firstOrNull { it.key == key }
        }
    }
}

/** The Words tab (iOS `WordsTab`): sign-in until there is an account, then the learner's home and its screens. */
@Composable
fun WordsTab(controller: WordsController, modifier: Modifier = Modifier, initialRoutes: List<WordsRoute> = emptyList()) {
    CompositionLocalProvider(LocalWordsController provides controller) {
        val vocab = controller.vocab
        val auth by vocab.auth.collectAsStateWithLifecycle()
        LaunchedEffect(vocab) { controller.launch { start() } }
        when (auth) {
            VocabApp.AuthState.Starting -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            VocabApp.AuthState.SignedOut -> SignInScreen(modifier)
            is VocabApp.AuthState.SignedIn -> WordsNavigation(modifier, initialRoutes)
        }

        val needsNewPassword by vocab.needsNewPassword.collectAsStateWithLifecycle()
        if (needsNewPassword) NewPasswordDialog()
        val linkMessage by vocab.linkMessage.collectAsStateWithLifecycle()
        linkMessage?.let { message ->
            val text = WordsText(LocalContext.current.resources)
            AlertDialog(
                onDismissRequest = vocab::dismissLinkMessage,
                title = { Text(stringResource(R.string.tab_words)) },
                text = {
                    Text(
                        when (message) {
                            VocabApp.LinkMessage.Confirmed -> stringResource(R.string.email_confirmed)
                            is VocabApp.LinkMessage.Failed -> text.error(message.error)
                        },
                    )
                },
                confirmButton = { TextButton(onClick = vocab::dismissLinkMessage) { Text(stringResource(R.string.ok)) } },
            )
        }
    }
}

@Composable
private fun WordsNavigation(modifier: Modifier, initialRoutes: List<WordsRoute>) {
    var keys by rememberSaveable { mutableStateOf(initialRoutes.map { it.key }) }
    val stack = keys.mapNotNull(WordsRoute::of)
    fun push(route: WordsRoute) {
        keys = keys + route.key
    }
    fun pop() {
        keys = keys.dropLast(1)
    }
    BackHandler(enabled = stack.isNotEmpty()) { pop() }
    when (val route = stack.lastOrNull()) {
        null -> WordsHome(onOpen = ::push, modifier = modifier)
        WordsRoute.Search -> WordSearchScreen(onOpen = ::push, onBack = ::pop, modifier = modifier)
        WordsRoute.Favorites -> WordListScreen(hardWords = false, onOpen = ::push, onBack = ::pop, modifier = modifier)
        WordsRoute.HardWords -> WordListScreen(hardWords = true, onOpen = ::push, onBack = ::pop, modifier = modifier)
        WordsRoute.Statistics -> VocabStatsScreen(onBack = ::pop, modifier = modifier)
        WordsRoute.Books -> BooksScreen(onBack = ::pop, modifier = modifier)
        WordsRoute.Settings -> VocabSettingsScreen(onBack = ::pop, modifier = modifier)
        is WordsRoute.Word -> WordDetailScreen(route.headword, onBack = ::pop, modifier = modifier)
    }
}

/** The learner's home (iOS `WordsHomeView`): today's task, the exam, the book, the lists, and syncing. */
@Composable
fun WordsHome(onOpen: (WordsRoute) -> Unit, modifier: Modifier = Modifier) {
    val controller = LocalWordsController.current
    val vocab = controller.vocab
    val state by vocab.model.state.collectAsStateWithLifecycle()
    val phase by vocab.sync.phase.collectAsStateWithLifecycle()
    val now = controller.now()
    val plan = state.plan(now)
    var studying by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_words)) },
                actions = {
                    IconButton(onClick = { onOpen(WordsRoute.Settings) }) {
                        AppIcon(R.drawable.ic_tab_settings, stringResource(R.string.study_settings))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = phase == SyncEngine.Phase.Syncing,
            onRefresh = { controller.launch { syncNow() } },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                vocab.storeProblem?.let { problem ->
                    Section { WarningRow(stringResource(R.string.store_problem, problem), blocking = true) }
                }
                TodaySection(state, plan, onStart = { studying = true })
                state.examCountdown(now)?.let { ExamSection(it) }
                BookSection(state, onOpen)
                Section {
                    LinkRow(R.drawable.ic_search, stringResource(R.string.search)) { onOpen(WordsRoute.Search) }
                    RowDivider()
                    LinkRow(R.drawable.ic_star, stringResource(R.string.favourites), "${state.favorites.size}") { onOpen(WordsRoute.Favorites) }
                    RowDivider()
                    LinkRow(R.drawable.ic_hard_words, stringResource(R.string.hard_words), "${state.hardWords.size}") { onOpen(WordsRoute.HardWords) }
                    RowDivider()
                    LinkRow(R.drawable.ic_bar_chart, stringResource(R.string.statistics)) { onOpen(WordsRoute.Statistics) }
                }
                Section { SyncStatusRow() }
                Spacer(Modifier.heightIn(min = 8.dp))
            }
        }
    }

    if (studying) {
        EditorDialog(onDismiss = { studying = false }) {
            StudySessionScreen(onClose = {
                studying = false
                controller.launch { syncNow() }
            })
        }
    }
}

@Composable
private fun TodaySection(state: VocabState, plan: DailyPlan, onStart: () -> Unit) {
    Section(title = stringResource(R.string.tab_today)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TodayNumber(stringResource(R.string.new_words), "${plan.newDone}/${plan.newDone + plan.newRemaining}")
                TodayNumber(stringResource(R.string.reviews_left), "${plan.reviewsRemaining}")
                TodayNumber(stringResource(R.string.reviewed), "${plan.reviewsDone}")
            }
            LinearProgressIndicator(
                progress = { plan.progress.toFloat() },
                color = if (plan.isComplete) GOOD_GREEN else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            val minutes = plan.estimatedMinutes
            if (minutes != null && !plan.isComplete) {
                Text(stringResource(R.string.minutes_left_estimate, minutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (plan.isComplete) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(R.drawable.ic_done_seal, null, Modifier.size(22.dp), tint = GOOD_GREEN)
                    Text(stringResource(R.string.done_for_today), style = MaterialTheme.typography.titleMedium, color = GOOD_GREEN)
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = state.bookWords.isNotEmpty() || plan.reviewsRemaining > 0,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(if (plan.newDone + plan.reviewsDone == 0) R.string.start else R.string.continue_label), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun TodayNumber(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExamSection(countdown: ExamCountdown) {
    Section(title = stringResource(R.string.exam), footer = stringResource(R.string.exam_footer)) {
        ValueRow(stringResource(R.string.days_to_exam), "${countdown.daysLeft}")
        RowDivider()
        ValueRow(stringResource(R.string.words_not_started), "${countdown.wordsLeft}")
        RowDivider()
        ValueRow(stringResource(R.string.new_words_a_day_to_finish), "${countdown.newWordsPerDayNeeded}")
    }
}

@Composable
private fun BookSection(state: VocabState, onOpen: (WordsRoute) -> Unit) {
    val controller = LocalWordsController.current
    val download by controller.vocab.sync.download.collectAsStateWithLifecycle()
    Section(title = stringResource(R.string.book)) {
        Column(
            Modifier.fillMaxWidth().clickable { onOpen(WordsRoute.Books) }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val book = state.currentBook
            if (book == null) {
                Text(stringResource(R.string.choose_a_book), color = MaterialTheme.colorScheme.primary)
                return@Column
            }
            Text(book.name, style = MaterialTheme.typography.titleMedium)
            val progress = download
            when {
                progress != null -> {
                    Text(stringResource(R.string.downloading_words), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { progress.done.toFloat() / maxOf(progress.total, 1) }, modifier = Modifier.fillMaxWidth())
                }
                state.bookWords.isEmpty() -> Text(stringResource(R.string.not_downloaded_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val bookProgress = VocabStats.progress(state.bookWords, state.cards, state.today(controller.now()))
                    Text(
                        stringResource(R.string.learned_of_mastered, bookProgress.learned, bookProgress.total, bookProgress.mastered),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkRow(@DrawableRes icon: Int, label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** When the last sync happened, or what went wrong, with a button to sync now. */
@Composable
fun SyncStatusRow() {
    val controller = LocalWordsController.current
    val vocab = controller.vocab
    val phase by vocab.sync.phase.collectAsStateWithLifecycle()
    val lastSync by vocab.sync.lastSync.collectAsStateWithLifecycle()
    val state by vocab.model.state.collectAsStateWithLifecycle()
    val text = WordsText(LocalContext.current.resources)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (val current = phase) {
                SyncEngine.Phase.Syncing -> {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.syncing), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
                is SyncEngine.Phase.Failed -> {
                    AppIcon(R.drawable.ic_cloud_off, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(10.dp))
                    Text(text.syncError(current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
                SyncEngine.Phase.Idle -> {
                    AppIcon(R.drawable.ic_cloud_done, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    val last = lastSync
                    Text(
                        if (last == null) {
                            stringResource(R.string.not_synced_yet)
                        } else if (controller.now().toEpochMilli() - last.toEpochMilli() < DateUtils.MINUTE_IN_MILLIS) {
                            stringResource(R.string.synced_just_now)
                        } else {
                            val ago = DateUtils.getRelativeTimeSpanString(last.toEpochMilli(), controller.now().toEpochMilli(), DateUtils.MINUTE_IN_MILLIS)
                            stringResource(R.string.synced_ago, ago.toString())
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (phase != SyncEngine.Phase.Syncing) {
                TextButton(onClick = { controller.launch { syncNow() } }) { Text(stringResource(R.string.sync_now)) }
            }
        }
        if (state.unsentCount > 0) {
            Text(stringResource(R.string.answers_waiting, state.unsentCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
