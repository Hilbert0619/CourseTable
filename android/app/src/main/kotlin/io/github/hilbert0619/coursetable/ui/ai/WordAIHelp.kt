@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.ai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.aikit.AIPrompts
import io.github.hilbert0619.aikit.WordHelpKind
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ai.AIStore
import io.github.hilbert0619.coursetable.ai.aiAnswerLanguage
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.EditorDialog
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.vocabkit.WordEntry

/**
 * The word page's AI help (iOS `WordAISection`): Explain, Memory Aid, Compare, Collocations. Finished answers are saved
 * on the device, so opening one again is instant and free; Ask Again gets a fresh one. Without a connection, a link to
 * set one up. Nothing at all where the app offers no AI ([LocalAI] unset).
 */
@Composable
fun WordAISection(entry: WordEntry) {
    val store = LocalAI.current ?: return
    val state by store.state.collectAsStateWithLifecycle()
    val language = aiAnswerLanguage(LocalConfiguration.current)
    val run = rememberAIRun()
    var kind by rememberSaveable(entry.headword) { mutableStateOf<WordHelpKind?>(null) }
    var settingUp by remember { mutableStateOf(false) }

    fun cacheKey(item: WordHelpKind) = "word|${entry.headword}|${item.serialName}|${language.serialName}"

    fun ask(item: WordHelpKind) {
        val key = cacheKey(item)
        run.start(AIPrompts.wordHelp(item, entry, language), store, onComplete = { answer ->
            store.saveAnswer(answer.text, run.state.model, key)
        })
    }

    fun open(item: WordHelpKind) {
        kind = item
        val saved = store.cachedAnswer(cacheKey(item))
        if (saved != null) run.show(saved) else ask(item)
    }

    Section(
        title = stringResource(R.string.ai_help),
        footer = if (!state.isSetUp) stringResource(R.string.ai_word_help_footer) else null,
    ) {
        if (!state.isSetUp) {
            ActionRow(R.drawable.ic_sparkles, stringResource(R.string.ai_set_up)) { settingUp = true }
            return@Section
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (item in WordHelpKind.entries) {
                FilterChip(
                    selected = kind == item,
                    onClick = { open(item) },
                    label = { Text(stringResource(title(item))) },
                    leadingIcon = { AppIcon(icon(item), null, Modifier.size(18.dp)) },
                )
            }
        }
        val current = kind
        if (current != null && run.state.hasContent) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AIAnswerView(run.state, onRetry = { ask(current) })
                if (!run.state.isRunning && run.state.error == null) {
                    OutlinedButton(onClick = { ask(current) }) {
                        AppIcon(R.drawable.ic_refresh, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.ai_ask_again), Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }

    if (settingUp) AISettingsDialog(store, onDismiss = { settingUp = false })
}

/** AI Help settings over the current screen, from a "Set Up AI Help…" link. */
@Composable
fun AISettingsDialog(store: AIStore, onDismiss: () -> Unit) {
    EditorDialog(onDismiss) { AISettingsScreen(store, onBack = onDismiss) }
}

/**
 * "Why?" after a wrong answer in a study session (iOS `WhyWrongButton`): the AI explains the difference in a sheet.
 * Hidden without AI. [picked] is the meaning chosen instead (multiple choice), [typed] the misspelling.
 */
@Composable
fun WhyWrongButton(entry: WordEntry, picked: String? = null, typed: String? = null, modifier: Modifier = Modifier) {
    val store = LocalAI.current ?: return
    val state by store.state.collectAsStateWithLifecycle()
    if (!state.isSetUp) return
    var showing by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showing = true }, modifier = modifier) {
        AppIcon(R.drawable.ic_sparkles, null, Modifier.size(18.dp))
        Text(stringResource(R.string.ai_why), Modifier.padding(start = 6.dp))
    }
    if (showing) WhyWrongSheet(store, entry, picked, typed, onDismiss = { showing = false })
}

@Composable
private fun WhyWrongSheet(store: AIStore, entry: WordEntry, picked: String?, typed: String?, onDismiss: () -> Unit) {
    val language = aiAnswerLanguage(LocalConfiguration.current)
    val run = rememberAIRun()
    fun ask() = run.start(AIPrompts.whyWrong(entry, picked, typed, language), store)
    LaunchedEffect(Unit) { if (!run.state.hasContent) ask() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.display, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
            AIAnswerView(run.state, onRetry = { ask() }, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

private fun title(kind: WordHelpKind): Int = when (kind) {
    WordHelpKind.EXPLAIN -> R.string.ai_explain
    WordHelpKind.MEMORY -> R.string.ai_memory_aid
    WordHelpKind.COMPARE -> R.string.ai_compare
    WordHelpKind.COLLOCATIONS -> R.string.ai_collocations
}

private fun icon(kind: WordHelpKind): Int = when (kind) {
    WordHelpKind.EXPLAIN -> R.drawable.ic_explain
    WordHelpKind.MEMORY -> R.drawable.ic_memory
    WordHelpKind.COMPARE -> R.drawable.ic_compare
    WordHelpKind.COLLOCATIONS -> R.drawable.ic_link
}

/** The iOS raw values, so saved answers' keys read the same. */
private val WordHelpKind.serialName: String
    get() = when (this) {
        WordHelpKind.EXPLAIN -> "explain"
        WordHelpKind.MEMORY -> "memory"
        WordHelpKind.COMPARE -> "compare"
        WordHelpKind.COLLOCATIONS -> "collocations"
    }

private val io.github.hilbert0619.aikit.AIAnswerLanguage.serialName: String
    get() = if (this == io.github.hilbert0619.aikit.AIAnswerLanguage.SIMPLIFIED_CHINESE) "simplifiedChinese" else "english"
