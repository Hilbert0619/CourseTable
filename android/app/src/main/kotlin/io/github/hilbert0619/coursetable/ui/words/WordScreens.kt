@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.words

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.coursetable.ui.javaDate
import io.github.hilbert0619.vocabkit.CardState
import io.github.hilbert0619.vocabkit.WordEntry
import io.github.hilbert0619.vocabkit.WordSearch
import java.time.format.DateTimeFormatter

/** A screen of the Words tab with a back arrow. */
@Composable
fun WordsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, stringResource(R.string.back)) } },
                actions = { actions() },
            )
        },
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}

/** A word's page (iOS `WordDetailView`): pronunciation, meanings, English definitions, forms, lists and progress. */
@Composable
fun WordDetailScreen(headword: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val controller = LocalWordsController.current
    val state by controller.vocab.model.state.collectAsStateWithLifecycle()
    val entry = state.entry(headword)
    val favorite = entry != null && entry.headword in state.favorites
    WordsScaffold(
        title = entry?.display ?: headword,
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (entry != null) {
                IconButton(onClick = { controller.launch { model.setFavorite(entry.headword, !favorite) } }) {
                    AppIcon(
                        if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star,
                        stringResource(if (favorite) R.string.remove_from_favourites else R.string.add_to_favourites),
                        tint = if (favorite) Color(0xFFF2B300) else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    ) { content ->
        if (entry == null) {
            Box(content, contentAlignment = Alignment.Center) { Text(stringResource(R.string.word_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@WordsScaffold
        }
        Column(content.verticalScroll(rememberScrollState())) {
            Section {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    WordHeader(entry, modifier = Modifier.align(Alignment.CenterHorizontally))
                    MeaningsView(entry)
                }
            }
            if (entry.definition.isNotEmpty()) {
                Section(title = stringResource(R.string.english)) {
                    Text(entry.definition, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            }
            if (entry.forms.isNotEmpty()) {
                Section(title = stringResource(R.string.forms)) { FormsView(entry, Modifier.padding(16.dp)) }
            }
            AboutSection(entry)
            ProgressSection(entry)
        }
    }
}

@Composable
private fun AboutSection(entry: WordEntry) {
    val lists = examLists(entry)
    val rows = buildList {
        if (lists.isNotEmpty()) add(Triple(stringResource(R.string.word_lists), lists, Color.Unspecified))
        if (entry.collins > 0) add(Triple(stringResource(R.string.collins), "★".repeat(entry.collins), Color(0xFFE08A00)))
        if (entry.oxford) add(Triple(stringResource(R.string.oxford_3000), "✓", Color.Unspecified))
        entry.frequencyRank?.let { add(Triple(stringResource(R.string.frequency_rank), "#$it", Color.Unspecified)) }
    }
    if (rows.isEmpty()) return
    Section(title = stringResource(R.string.about_the_word)) {
        rows.forEachIndexed { index, (label, value, color) ->
            if (index > 0) RowDivider()
            ValueRow(label, value, valueColor = color)
        }
    }
}

/** The exam lists a word belongs to, e.g. `考研 · CET-6`. */
private fun examLists(entry: WordEntry): String {
    val names = mapOf("zk" to "中考", "gk" to "高考", "cet4" to "CET-4", "cet6" to "CET-6", "ky" to "考研", "toefl" to "TOEFL", "ielts" to "IELTS", "gre" to "GRE")
    return entry.tags.mapNotNull { names[it] }.joinToString(" · ")
}

/** How the learner is doing with a word, from its card. */
@Composable
private fun ProgressSection(entry: WordEntry) {
    val controller = LocalWordsController.current
    val state by controller.vocab.model.state.collectAsStateWithLifecycle()
    val card = state.cards[entry.headword]
    val text = WordsText(LocalContext.current.resources)
    val locale = LocalContext.current.resources.configuration.locales[0]
    Section(title = stringResource(R.string.your_progress)) {
        if (card == null || card.state == CardState.NEW) {
            Text(stringResource(R.string.not_learned_yet), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            return@Section
        }
        val today = state.today(controller.now())
        ValueRow(stringResource(R.string.status), stringResource(stateName(card.state)))
        RowDivider()
        ValueRow(stringResource(R.string.answers), "${card.reps}")
        RowDivider()
        ValueRow(stringResource(R.string.forgotten), "${card.lapses}")
        card.dueDay(today)?.let { day ->
            RowDivider()
            val pattern = DateFormat.getBestDateTimePattern(locale, "MMMd")
            ValueRow(stringResource(R.string.next_review), DateTimeFormatter.ofPattern(pattern, locale).format(state.clock.date(day).javaDate))
        }
        state.scheduler.retrievability(card, today)?.let { recall ->
            RowDivider()
            ValueRow(stringResource(R.string.chance_of_recall_today), text.percent(recall))
        }
    }
}

private fun stateName(state: CardState): Int = when (state) {
    CardState.NEW -> R.string.state_new
    CardState.LEARNING -> R.string.state_learning
    CardState.REVIEW -> R.string.state_reviewing
    CardState.RELEARNING -> R.string.state_relearning
}

/** Search the words on the phone (iOS `WordSearchView`). */
@Composable
fun WordSearchScreen(onOpen: (WordsRoute) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier, initialQuery: String = "") {
    val controller = LocalWordsController.current
    val state by controller.vocab.model.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val results = if (query.isBlank()) emptyList() else state.search.search(query)
    WordsScaffold(stringResource(R.string.search), onBack, modifier) { content ->
        Column(content) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_prompt)) },
                leadingIcon = { AppIcon(R.drawable.ic_search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                query.isBlank() -> EmptyState(R.drawable.ic_search, stringResource(R.string.search_words), stringResource(R.string.search_words_detail))
                results.isEmpty() -> EmptyState(R.drawable.ic_search, stringResource(R.string.no_results, query.trim()), null)
                else -> LazyColumn {
                    items(results, key = { it.headword }) { result ->
                        WordRow(state.entry(result.headword), result.headword, note(result.match), Modifier.clickable { onOpen(WordsRoute.Word(result.headword)) })
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun note(match: WordSearch.Match): String? = when (match) {
    is WordSearch.Match.Form -> stringResource(R.string.form_note, match.form)
    WordSearch.Match.Similar -> stringResource(R.string.similar_spelling)
    else -> null
}

/** Favourites, or the words forgotten most often (iOS `WordListView`). */
@Composable
fun WordListScreen(hardWords: Boolean, onOpen: (WordsRoute) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val controller = LocalWordsController.current
    val state by controller.vocab.model.state.collectAsStateWithLifecycle()
    val words = if (hardWords) state.hardWords.map { it.headword } else state.favorites.sorted()
    WordsScaffold(stringResource(if (hardWords) R.string.hard_words else R.string.favourites), onBack, modifier) { content ->
        if (words.isEmpty()) {
            Box(content) {
                if (hardWords) {
                    EmptyState(R.drawable.ic_hard_words, stringResource(R.string.no_hard_words), stringResource(R.string.no_hard_words_detail))
                } else {
                    EmptyState(R.drawable.ic_star, stringResource(R.string.no_favourites), stringResource(R.string.no_favourites_detail))
                }
            }
            return@WordsScaffold
        }
        LazyColumn(content) {
            items(words, key = { it }) { word ->
                val note = if (hardWords) state.cards[word]?.let { stringResource(R.string.forgotten_times, it.lapses) } else null
                WordRow(state.entry(word), word, note, Modifier.clickable { onOpen(WordsRoute.Word(word)) })
                HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

/** A big icon, a title and a line of help, for empty lists. */
@Composable
fun EmptyState(icon: Int, title: String, detail: String?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppIcon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
