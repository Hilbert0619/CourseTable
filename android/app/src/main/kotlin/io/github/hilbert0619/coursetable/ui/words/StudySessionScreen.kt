@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.words

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.ai.WhyWrongButton
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.vocabkit.Answer
import io.github.hilbert0619.vocabkit.AnswerGrader
import io.github.hilbert0619.vocabkit.Card
import io.github.hilbert0619.vocabkit.ChoiceQuestion
import io.github.hilbert0619.vocabkit.DiffPart
import io.github.hilbert0619.vocabkit.Rating
import io.github.hilbert0619.vocabkit.ReviewMode
import io.github.hilbert0619.vocabkit.SeededRandom
import io.github.hilbert0619.vocabkit.StudyItem
import io.github.hilbert0619.vocabkit.StudySession
import io.github.hilbert0619.vocabkit.VocabStats
import io.github.hilbert0619.vocabkit.WordEntry
import io.github.hilbert0619.coursetable.words.Pronunciation
import java.time.Duration
import java.time.Instant

/**
 * A study session (docs/vocabulary-spec.md §7, iOS `StudySessionView`): learning steps that are due, then due reviews,
 * then new words, until nothing is left for now. Every answer is saved straight away, so closing early loses nothing.
 */
@Composable
fun StudySessionScreen(onClose: () -> Unit, startedAt: Instant? = null) {
    val controller = LocalWordsController.current
    val state by controller.vocab.model.state.collectAsStateWithLifecycle()
    val session = remember {
        val now = startedAt ?: controller.now()
        val current = controller.vocab.model.state.value
        StudySession(
            current.cards, current.plan(now).newWords, now, current.scheduler, current.clock, current.settings.reviewMode,
            seed = now.toEpochMilli().toULong(),
        )
    }
    var questionNumber by remember { mutableIntStateOf(1) }
    var item by remember { mutableStateOf(session.next(startedAt ?: controller.now())) }
    var shownAt by remember { mutableStateOf(controller.now()) }

    fun answer(rating: Rating, mode: ReviewMode) {
        val answered = item ?: return
        val now = controller.vocab.model.now()
        // A question left open while away from the phone shouldn't skew the time estimates.
        val duration = minOf(Duration.between(shownAt, now).toMillis(), 5 * 60 * 1000L).toInt()
        val event = session.answer(answered.copy(mode = mode), rating, now, duration)
        session.cards[answered.headword]?.let { card -> controller.launch { model.record(event, card) } }
        item = session.next(now)
        questionNumber += 1
        shownAt = controller.now()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { AppIcon(R.drawable.ic_close, stringResource(R.string.close)) } },
                title = { if (item != null) RemainingCounts(session.remaining(controller.now())) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val current = item
            if (current == null) {
                SessionSummaryView(session, onDone = onClose)
            } else {
                key(questionNumber) {
                    val entry = state.entry(current.headword) ?: WordEntry(headword = current.headword, translation = "")
                    val seed = SeededRandom.seed(session.seed.toString(), current.headword, questionNumber.toString())
                    QuestionView(current, entry, session.cards[current.headword], seed, ::answer)
                }
            }
        }
    }
}

/** New, learning and review questions left. */
@Composable
private fun RemainingCounts(remaining: StudySession.Remaining) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Count(R.drawable.ic_new_words, Color(0xFF2F6FDB), remaining.new)
        Count(R.drawable.ic_learning, Color(0xFFE08A00), remaining.learning)
        Count(R.drawable.ic_review, GOOD_GREEN, remaining.review)
    }
}

@Composable
private fun Count(icon: Int, color: Color, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AppIcon(icon, null, Modifier.size(18.dp), tint = color)
        Text("$value", style = MaterialTheme.typography.titleMedium)
    }
}

/** One question, in the way its mode asks it. */
@Composable
private fun QuestionView(item: StudyItem, entry: WordEntry, card: Card?, seed: ULong, onAnswer: (Rating, ReviewMode) -> Unit) {
    val controller = LocalWordsController.current
    val question = remember(entry, seed) { controller.vocab.model.state.value.choicePool.question(entry, seed) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when {
            item.kind == StudyItem.Kind.NEW -> NewWordQuestion(entry, question, onAnswer)
            item.mode == ReviewMode.RECOGNITION -> RecognitionQuestion(entry, card ?: Card.of(entry.headword), onAnswer)
            item.mode == ReviewMode.CHOICE -> if (question != null) {
                ChoiceQuestionView(entry, question) { correct -> onAnswer(AnswerGrader.rating(Answer.Choice(correct)), ReviewMode.CHOICE) }
            } else {
                RecognitionQuestion(entry, card ?: Card.of(entry.headword), onAnswer)
            }
            else -> SpellingQuestion(entry) { result -> onAnswer(AnswerGrader.rating(Answer.Spelling(result)), ReviewMode.SPELLING) }
        }
    }
}

/** A new word: its page first, then "I know this" (it skips the learning steps) or a quick check. */
@Composable
private fun NewWordQuestion(entry: WordEntry, question: ChoiceQuestion?, onAnswer: (Rating, ReviewMode) -> Unit) {
    var checking by remember { mutableStateOf(false) }
    if (checking && question != null) {
        ChoiceQuestionView(entry, question) { correct -> onAnswer(AnswerGrader.rating(Answer.Choice(correct)), ReviewMode.CHOICE) }
        return
    }
    Text(stringResource(R.string.new_word).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    WordHeader(entry)
    MeaningsView(entry, Modifier.fillMaxWidth())
    if (entry.forms.isNotEmpty()) FormsView(entry, Modifier.fillMaxWidth())
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { if (question == null) onAnswer(Rating.GOOD, ReviewMode.RECOGNITION) else checking = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.learn_it), style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(
            onClick = { onAnswer(AnswerGrader.rating(Answer.AlreadyKnown), ReviewMode.RECOGNITION) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.i_know_this)) }
    }
}

/** See the word, recall its meaning, then rate how well you knew it. */
@Composable
private fun RecognitionQuestion(entry: WordEntry, card: Card, onAnswer: (Rating, ReviewMode) -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    Spacer(Modifier.height(16.dp))
    WordHeader(entry)
    if (revealed) {
        MeaningsView(entry, Modifier.fillMaxWidth())
        RatingButtons(card) { onAnswer(it, ReviewMode.RECOGNITION) }
    } else {
        Text(stringResource(R.string.remember_meaning), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.show_answer), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Again / Hard / Good / Easy, each with when the word would come back. */
@Composable
private fun RatingButtons(card: Card, onRate: (Rating) -> Unit) {
    val controller = LocalWordsController.current
    val state = controller.vocab.model.state.value
    val now = controller.now()
    val today = state.today(now)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (rating in Rating.entries) {
            val next = state.scheduler.review(card, rating, now, today)
            val color = ratingColor(rating)
            OutlinedButton(
                onClick = { onRate(rating) },
                modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ratingTitle(rating), style = MaterialTheme.typography.titleSmall)
                    Text(intervalText(next.due, now, today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Pick the meaning out of four; afterwards the right one is shown before moving on. */
@Composable
private fun ChoiceQuestionView(entry: WordEntry, question: ChoiceQuestion, onDone: (Boolean) -> Unit) {
    var picked by remember { mutableStateOf<Int?>(null) }
    Spacer(Modifier.height(16.dp))
    WordHeader(entry)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        question.options.forEachIndexed { index, option ->
            val chosen = picked
            val color = when {
                chosen == null -> MaterialTheme.colorScheme.onSurface
                index == question.correctIndex -> GOOD_GREEN
                index == chosen -> ratingColor(Rating.AGAIN)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            OutlinedButton(
                onClick = { if (picked == null) picked = index },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                border = BorderStroke(if (chosen != null && (index == question.correctIndex || index == chosen)) 2.dp else 1.dp, color.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            ) {
                Text(option, modifier = Modifier.weight(1f))
                if (chosen != null && index == question.correctIndex) {
                    AppIcon(R.drawable.ic_check_circle, null, Modifier.size(20.dp), tint = GOOD_GREEN)
                } else if (chosen == index) {
                    AppIcon(R.drawable.ic_wrong, null, Modifier.size(20.dp), tint = ratingColor(Rating.AGAIN))
                }
            }
        }
    }
    val chosen = picked
    if (chosen != null) {
        if (chosen != question.correctIndex) {
            MeaningsView(entry, Modifier.fillMaxWidth())
            WhyWrongButton(entry, picked = question.options[chosen])
        }
        ContinueButton { onDone(question.isCorrect(chosen)) }
    }
}

/** Hear the word and see its meaning, then type it. */
@Composable
private fun SpellingQuestion(entry: WordEntry, onDone: (Answer.SpellingResult) -> Unit) {
    var typed by remember { mutableStateOf("") }
    var usedHint by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Answer.SpellingResult?>(null) }
    val controller = LocalWordsController.current
    LaunchedEffect(entry.headword) { controller.speak(entry.display, Pronunciation.Accent.AMERICAN) }

    fun check() {
        if (result != null || typed.isBlank()) return
        result = if (AnswerGrader.isCorrectSpelling(typed, entry.display)) {
            if (usedHint) Answer.SpellingResult.CORRECT_WITH_HINT else Answer.SpellingResult.CORRECT
        } else {
            Answer.SpellingResult.WRONG
        }
    }

    Text(stringResource(R.string.type_the_word).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    MeaningsView(entry, Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpeakButton(entry.display, Pronunciation.Accent.AMERICAN)
        SpeakButton(entry.display, Pronunciation.Accent.BRITISH)
        Spacer(Modifier.weight(1f))
        if (usedHint) Text(AnswerGrader.hint(entry.display), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
    }
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        label = { Text(stringResource(R.string.spelling)) },
        singleLine = true,
        enabled = result == null,
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { check() }),
        modifier = Modifier.fillMaxWidth(),
    )
    val outcome = result
    if (outcome != null) {
        SpellingResultView(entry, typed, outcome)
        if (outcome == Answer.SpellingResult.WRONG) WhyWrongButton(entry, typed = typed)
        ContinueButton { onDone(outcome) }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { usedHint = true }, enabled = !usedHint) { Text(stringResource(R.string.hint)) }
            OutlinedButton(onClick = { result = Answer.SpellingResult.GAVE_UP }) { Text(stringResource(R.string.i_dont_know)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { check() }, enabled = typed.isNotBlank()) { Text(stringResource(R.string.check)) }
        }
    }
}

@Composable
private fun SpellingResultView(entry: WordEntry, typed: String, result: Answer.SpellingResult) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (result) {
            Answer.SpellingResult.CORRECT, Answer.SpellingResult.CORRECT_WITH_HINT -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppIcon(R.drawable.ic_check_circle, null, Modifier.size(22.dp), tint = GOOD_GREEN)
                Text(stringResource(R.string.correct), style = MaterialTheme.typography.titleMedium, color = GOOD_GREEN)
            }
            Answer.SpellingResult.WRONG -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppIcon(R.drawable.ic_wrong, null, Modifier.size(22.dp), tint = ratingColor(Rating.AGAIN))
                    Text(stringResource(R.string.not_quite), style = MaterialTheme.typography.titleMedium, color = ratingColor(Rating.AGAIN))
                }
                // Missing letters in green, extra ones struck through in red.
                Text(
                    buildAnnotatedString {
                        for (part in AnswerGrader.diff(typed, entry.display)) {
                            when (part) {
                                is DiffPart.Same -> append(part.text)
                                is DiffPart.Missing -> withStyle(SpanStyle(color = GOOD_GREEN, textDecoration = TextDecoration.Underline)) { append(part.text) }
                                is DiffPart.Extra -> withStyle(SpanStyle(color = ratingColor(Rating.AGAIN), textDecoration = TextDecoration.LineThrough)) { append(part.text) }
                            }
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                )
            }
            Answer.SpellingResult.GAVE_UP -> Unit
        }
        WordHeader(entry, large = false)
    }
}

@Composable
private fun ContinueButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.continue_label), style = MaterialTheme.typography.titleMedium)
    }
}

/** The end of a session. */
@Composable
private fun SessionSummaryView(session: StudySession, onDone: () -> Unit) {
    val controller = LocalWordsController.current
    val state by controller.vocab.model.state.collectAsStateWithLifecycle()
    val text = WordsText(LocalContext.current.resources)
    val now = controller.now()
    val today = state.today(now)
    val summary = session.summary
    val streak = VocabStats.streak(state.events, today)
    val waiting = session.waiting(now)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(R.drawable.ic_done_seal, null, Modifier.size(56.dp), tint = GOOD_GREEN)
            Text(stringResource(R.string.session_complete), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Section {
            ValueRow(stringResource(R.string.new_words), "${summary.newWords}")
            RowDivider()
            ValueRow(stringResource(R.string.words_reviewed), "${summary.reviewedWords}")
            RowDivider()
            ValueRow(stringResource(R.string.accuracy), text.percent(summary.accuracy))
            RowDivider()
            ValueRow(stringResource(R.string.time_label), text.minutesAndSeconds(summary.durationMs))
            RowDivider()
            ValueRow(stringResource(R.string.study_streak), pluralDays(streak.days))
            RowDivider()
            ValueRow(stringResource(R.string.reviews_due_tomorrow), "${VocabStats.dueCount(state.cards.values, today + 1, today)}")
        }
        val first = waiting.firstDue
        if (waiting.count > 0 && first != null) {
            Section {
                Text(
                    stringResource(R.string.words_come_back, waiting.count, TimeOfDay.from(first, state.zone).toString()),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        Section {
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.done), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** "5 days" */
@Composable
fun pluralDays(days: Int): String = LocalContext.current.resources.getQuantityString(R.plurals.days_count, days, days)
