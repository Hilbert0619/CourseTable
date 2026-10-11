package io.github.hilbert0619.coursetable.ui.words

import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.words.Pronunciation
import io.github.hilbert0619.vocabkit.Due
import io.github.hilbert0619.vocabkit.Rating
import io.github.hilbert0619.vocabkit.WordEntry
import io.github.hilbert0619.vocabkit.WordForm
import io.github.hilbert0619.vocabkit.backend.VocabAuthError
import io.github.hilbert0619.vocabkit.store.SyncEngine
import io.github.hilbert0619.vocabkit.store.VocabApp
import java.text.NumberFormat
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What the Words screens work with: the Words app, a scope for work that must finish even when a screen closes
 * (saving an answer, syncing), the voice, and the current time.
 */
class WordsController(
    val vocab: VocabApp,
    val scope: CoroutineScope,
    private val speaker: (String, Pronunciation.Accent) -> Boolean,
    val now: () -> Instant,
) {
    fun launch(block: suspend VocabApp.() -> Unit): Job = scope.launch { block(vocab) }

    /** `false` if the phone has no English voice. */
    fun speak(text: String, accent: Pronunciation.Accent): Boolean = speaker(text, accent)
}

val LocalWordsController = staticCompositionLocalOf<WordsController> { error("No WordsController") }

/** The word, its phonetic spelling and buttons to hear it (American and British voices). */
@Composable
fun WordHeader(entry: WordEntry, large: Boolean = true, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val size = if (large) 40.sp else 28.sp
        BasicText(
            entry.display,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = size, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = size * 0.5f, maxFontSize = size),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (entry.phonetic.isNotEmpty()) {
                Text("/${entry.phonetic}/", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SpeakButton(entry.display, Pronunciation.Accent.AMERICAN)
            SpeakButton(entry.display, Pronunciation.Accent.BRITISH)
        }
    }
}

@Composable
fun SpeakButton(text: String, accent: Pronunciation.Accent) {
    val controller = LocalWordsController.current
    val context = LocalContext.current
    val noVoice = stringResource(R.string.no_english_voice)
    val description = stringResource(if (accent == Pronunciation.Accent.AMERICAN) R.string.american_pronunciation else R.string.british_pronunciation)
    FilledTonalButton(
        onClick = { if (!controller.speak(text, accent)) Toast.makeText(context, noVoice, Toast.LENGTH_LONG).show() },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        AppIcon(R.drawable.ic_volume_up, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (accent == Pronunciation.Accent.AMERICAN) "US" else "UK", style = MaterialTheme.typography.labelLarge)
    }
}

/** The Chinese meanings, one line per part of speech. */
@Composable
fun MeaningsView(entry: WordEntry, modifier: Modifier = Modifier) {
    val senses = entry.senses
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (senses.isEmpty()) {
            Text(stringResource(R.string.no_meaning), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (sense in senses) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val tag = sense.partOfSpeechTag ?: sense.label?.let { "[$it]" }
                if (tag != null) {
                    Text(
                        tag,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = if (sense.partOfSpeechTag != null) FontStyle.Italic else FontStyle.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(sense.meanings.joinToString("，"), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Inflected forms: past tense, plural and so on. */
@Composable
fun FormsView(entry: WordEntry, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (form in entry.forms) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formName(form.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(form.word, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun formName(kind: WordForm.Kind): String = stringResource(
    when (kind) {
        WordForm.Kind.PAST -> R.string.form_past
        WordForm.Kind.PAST_PARTICIPLE -> R.string.form_past_participle
        WordForm.Kind.PRESENT_PARTICIPLE -> R.string.form_ing
        WordForm.Kind.THIRD_PERSON -> R.string.form_third_person
        WordForm.Kind.COMPARATIVE -> R.string.form_comparative
        WordForm.Kind.SUPERLATIVE -> R.string.form_superlative
        WordForm.Kind.PLURAL -> R.string.form_plural
        WordForm.Kind.LEMMA -> R.string.form_base
    },
)

/** How long until a word comes back: minutes in learning steps, otherwise days, months or years. */
@Composable
fun intervalText(due: Due?, from: Instant, today: Int): String = when (due) {
    is Due.Moment -> stringResource(R.string.interval_minutes, maxOf(1, Math.round((due.instant.epochSecond - from.epochSecond) / 60.0).toInt()))
    is Due.Day -> {
        val days = maxOf(1, due.day - today)
        when {
            days < 60 -> stringResource(R.string.interval_days, days)
            days < 365 -> stringResource(R.string.interval_months, days / 30)
            else -> stringResource(R.string.interval_years, "%.1f".format(LocalContext.current.resources.configuration.locales[0], days / 365.0))
        }
    }
    null -> "–"
}

/** A word and its short meaning, for lists. */
@Composable
fun WordRow(entry: WordEntry?, headword: String, note: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry?.display ?: headword, style = MaterialTheme.typography.titleMedium)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val meaning = entry?.shortMeaning.orEmpty()
        if (meaning.isNotEmpty()) {
            Text(meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ratingTitle(rating: Rating): String = stringResource(
    when (rating) {
        Rating.AGAIN -> R.string.rating_again
        Rating.HARD -> R.string.rating_hard
        Rating.GOOD -> R.string.rating_good
        Rating.EASY -> R.string.rating_easy
    },
)

fun ratingColor(rating: Rating): Color = when (rating) {
    Rating.AGAIN -> Color(0xFFD93B30)
    Rating.HARD -> Color(0xFFE08A00)
    Rating.GOOD -> GOOD_GREEN
    Rating.EASY -> Color(0xFF2F6FDB)
}

val GOOD_GREEN = Color(0xFF2E9E4F)

/** Texts for the Words tab's numbers and errors. */
class WordsText(private val res: Resources) {
    private val locale get() = res.configuration.locales[0]

    /** "89%" / "89%" */
    fun percent(value: Double?): String = value?.let { NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }.format(it) } ?: "–"

    /** "3 min, 20 sec" for a session. */
    fun minutesAndSeconds(milliseconds: Int): String {
        val seconds = (milliseconds + 500) / 1000
        val measures = buildList {
            if (seconds >= 60) add(Measure(seconds / 60, MeasureUnit.MINUTE))
            if (seconds % 60 > 0 || seconds < 60) add(Measure(seconds % 60, MeasureUnit.SECOND))
        }
        return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).formatMeasures(*measures.toTypedArray())
    }

    /** A sign-in or sync problem in words the learner can act on. */
    fun error(error: Throwable): String = when (error) {
        VocabAuthError.InvalidCredentials -> res.getString(R.string.auth_invalid_credentials)
        VocabAuthError.EmailNotConfirmed -> res.getString(R.string.auth_email_not_confirmed)
        VocabAuthError.WeakPassword -> res.getString(R.string.auth_weak_password)
        VocabAuthError.InvalidEmail -> res.getString(R.string.auth_invalid_email)
        VocabAuthError.RateLimited -> res.getString(R.string.auth_rate_limited)
        VocabAuthError.EmailNotAllowed -> res.getString(R.string.auth_email_not_allowed)
        VocabAuthError.LinkExpired -> res.getString(R.string.auth_link_expired)
        VocabAuthError.Offline -> res.getString(R.string.auth_offline)
        is VocabAuthError.Other -> error.text
        else -> error.message ?: error.toString()
    }

    /** Why the last sync stopped. */
    fun syncError(phase: SyncEngine.Phase.Failed): String =
        if (phase.cause == VocabAuthError.Offline) res.getString(R.string.sync_offline) else error(phase.cause)
}
