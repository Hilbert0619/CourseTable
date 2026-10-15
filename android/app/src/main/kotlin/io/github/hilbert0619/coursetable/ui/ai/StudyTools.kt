package io.github.hilbert0619.coursetable.ui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.aikit.AIPrompts
import io.github.hilbert0619.aikit.EssayTask
import io.github.hilbert0619.aikit.SentenceWords
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ai.AIStore
import io.github.hilbert0619.coursetable.ai.aiAnswerLanguage
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.MenuRow
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.coursetable.ui.words.LocalWordsController
import io.github.hilbert0619.coursetable.ui.words.WordsScaffold

private val WARNING = Color(0xFFE08A00)

/** The longest passage analysed, and the longest essay marked. */
const val SENTENCE_MAX_LENGTH = 2000
const val ESSAY_MAX_LENGTH = 6000

/**
 * Long-sentence analysis (长难句, iOS `SentenceAnalysisView`): paste or scan a sentence; the AI breaks it down and
 * translates it, and the book's words in it are listed for one-tap lookup.
 */
@Composable
fun SentenceAnalysisScreen(store: AIStore, onOpenWord: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by store.state.collectAsStateWithLifecycle()
    val words by LocalWordsController.current.vocab.model.state.collectAsStateWithLifecycle()
    val language = aiAnswerLanguage(LocalConfiguration.current)
    val focus = LocalFocusManager.current
    val run = rememberAIRun()
    var text by rememberSaveable { mutableStateOf("") }
    var analysed by rememberSaveable { mutableStateOf("") }
    var settingUp by remember { mutableStateOf(false) }
    val trimmed = text.trim()

    fun analyse() {
        focus.clearFocus()
        analysed = trimmed
        run.start(AIPrompts.sentenceAnalysis(trimmed, language), store)
    }

    WordsScaffold(stringResource(R.string.ai_long_sentences), onBack, modifier) { content ->
        Column(content.verticalScroll(rememberScrollState())) {
            if (!state.isSetUp) SetUpSection { settingUp = true }
            Section(title = stringResource(R.string.ai_sentence), footer = stringResource(R.string.ai_scan_footer)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.ai_sentence_hint)) },
                    minLines = 4,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
                TextScanButtons(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) { text = it }
            }
            Section {
                ActionRow(
                    R.drawable.ic_sparkles, stringResource(R.string.ai_analyse),
                    enabled = state.isSetUp && trimmed.isNotEmpty() && trimmed.length <= SENTENCE_MAX_LENGTH && !run.state.isRunning,
                ) { analyse() }
                if (trimmed.length > SENTENCE_MAX_LENGTH) TooLong(SENTENCE_MAX_LENGTH)
            }
            if (run.state.hasContent) {
                Section { AIAnswerView(run.state, Modifier.padding(16.dp), onRetry = { analyse() }) }
                val found = remember(analysed, words.words) { SentenceWords.entries(analysed) { words.entry(it) } }
                if (found.isNotEmpty()) {
                    Section(title = stringResource(R.string.ai_words_from_book), footer = stringResource(R.string.ai_words_from_book_footer)) {
                        found.forEachIndexed { index, entry ->
                            if (index > 0) RowDivider()
                            ValueRow(entry.display, entry.shortMeaning, onClick = { onOpenWord(entry.headword) })
                        }
                    }
                }
            }
        }
    }
    if (settingUp) AISettingsDialog(store, onDismiss = { settingUp = false })
}

/** Essay feedback (作文, iOS `EssayFeedbackView`): the exam task, the essay (pasted or scanned), then a mark and corrections. */
@Composable
fun EssayFeedbackScreen(store: AIStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by store.state.collectAsStateWithLifecycle()
    val language = aiAnswerLanguage(LocalConfiguration.current)
    val focus = LocalFocusManager.current
    val run = rememberAIRun()
    var task by rememberSaveable { mutableStateOf(EssayTask.ENGLISH1_LONG) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var essay by rememberSaveable { mutableStateOf("") }
    var settingUp by remember { mutableStateOf(false) }
    val wordCount = essay.split(Regex("[^\\p{L}'-]+")).count { it.isNotEmpty() }

    fun mark() {
        focus.clearFocus()
        run.start(AIPrompts.essayFeedback(essay, task, prompt.trim(), language), store)
    }

    WordsScaffold(stringResource(R.string.ai_writing_feedback), onBack, modifier) { content ->
        Column(content.verticalScroll(rememberScrollState())) {
            if (!state.isSetUp) SetUpSection { settingUp = true }
            Section(title = stringResource(R.string.ai_task)) {
                MenuRow(stringResource(R.string.ai_task), task, EssayTask.entries, { stringResource(taskTitle(it)) }) { task = it }
                RowDivider()
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text(stringResource(R.string.ai_task_instructions)) },
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Section(title = stringResource(R.string.ai_essay), footer = stringResource(R.string.ai_word_count, wordCount)) {
                OutlinedTextField(
                    value = essay,
                    onValueChange = { essay = it },
                    placeholder = { Text(stringResource(R.string.ai_your_essay)) },
                    minLines = 8,
                    maxLines = 30,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
                TextScanButtons(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) { scanned ->
                    essay = if (essay.isEmpty()) scanned else essay + "\n" + scanned
                }
            }
            Section(footer = stringResource(R.string.ai_score_estimate)) {
                ActionRow(
                    R.drawable.ic_sparkles, stringResource(R.string.ai_mark_essay),
                    enabled = state.isSetUp && wordCount >= 20 && essay.length <= ESSAY_MAX_LENGTH && !run.state.isRunning,
                ) { mark() }
                if (essay.length > ESSAY_MAX_LENGTH) TooLong(ESSAY_MAX_LENGTH)
            }
            if (run.state.hasContent) Section { AIAnswerView(run.state, Modifier.padding(16.dp), onRetry = { mark() }) }
        }
    }
    if (settingUp) AISettingsDialog(store, onDismiss = { settingUp = false })
}

@Composable
private fun SetUpSection(onClick: () -> Unit) {
    Section { ActionRow(R.drawable.ic_sparkles, stringResource(R.string.ai_set_up), onClick = onClick) }
}

@Composable
private fun TooLong(limit: Int) {
    Text(
        stringResource(R.string.ai_too_long, limit),
        style = MaterialTheme.typography.bodySmall,
        color = WARNING,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
    )
}

private fun taskTitle(task: EssayTask): Int = when (task) {
    EssayTask.ENGLISH1_SHORT -> R.string.ai_task_english1_short
    EssayTask.ENGLISH1_LONG -> R.string.ai_task_english1_long
    EssayTask.ENGLISH2_SHORT -> R.string.ai_task_english2_short
    EssayTask.ENGLISH2_LONG -> R.string.ai_task_english2_long
}
