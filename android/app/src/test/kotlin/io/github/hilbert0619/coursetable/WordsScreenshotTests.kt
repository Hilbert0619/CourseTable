package io.github.hilbert0619.coursetable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursetable.ui.LocalNowSource
import io.github.hilbert0619.coursetable.ui.NowSource
import io.github.hilbert0619.coursetable.ui.theme.StudyHourTheme
import io.github.hilbert0619.coursetable.ui.words.BooksScreen
import io.github.hilbert0619.coursetable.ui.words.LocalWordsController
import io.github.hilbert0619.coursetable.ui.words.StudySessionScreen
import io.github.hilbert0619.coursetable.ui.words.VocabSettingsScreen
import io.github.hilbert0619.coursetable.ui.words.VocabStatsScreen
import io.github.hilbert0619.coursetable.ui.words.WordDetailScreen
import io.github.hilbert0619.coursetable.ui.words.WordListImportScreen
import io.github.hilbert0619.coursetable.ui.words.WordSearchScreen
import io.github.hilbert0619.coursetable.ui.words.WordsController
import io.github.hilbert0619.coursetable.ui.words.WordsTab
import io.github.hilbert0619.vocabkit.ReviewMode
import io.github.hilbert0619.vocabkit.SeededRandom
import io.github.hilbert0619.vocabkit.SessionMode
import io.github.hilbert0619.vocabkit.StudyItem
import io.github.hilbert0619.vocabkit.StudySession
import io.github.hilbert0619.vocabkit.backend.DemoBackend
import io.github.hilbert0619.vocabkit.store.InMemoryVocabStore
import io.github.hilbert0619.vocabkit.store.VocabApp
import io.github.hilbert0619.vocabkit.store.VocabModel
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Words tab with the demo learner (two weeks of answers, 30 words of the 考研 list) at a fixed moment, Monday
 * 2026-09-28 21:00 in Shanghai, rendered on the JVM. Nothing reaches the network.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class WordsScreenshotTests {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Moment(LocalDate(2026, 9, 28), TimeOfDay(21, 0)).toInstant(zone)
    private val clock = Clock.fixed(now, zone)

    /** The Words app with the demo backend, started (signed in and synced) before anything is drawn. */
    private fun words(signedIn: Boolean = true, history: Boolean = true, mode: SessionMode = SessionMode.MIXED): WordsController {
        val app = VocabApp(DemoBackend(signedIn, history, mode, now, zone), VocabModel(InMemoryVocabStore(), clock, Dispatchers.Unconfined))
        runBlocking { app.start() }
        return WordsController(app, CoroutineScope(Dispatchers.Unconfined), { _, _ -> true }, clock::instant)
    }

    private fun show(controller: WordsController, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalNowSource provides NowSource(clock, ticks = false), LocalWordsController provides controller) {
                StudyHourTheme { content() }
            }
        }
    }

    private fun capture() = compose.onRoot().captureRoboImage()

    @Test
    fun signIn() {
        val controller = words(signedIn = false, history = false)
        show(controller) { WordsTab(controller) }
        compose.onNodeWithText("Learn the 考研 words").assertExists()
        capture()
    }

    @Test
    fun home() {
        val controller = words()
        show(controller) { WordsTab(controller) }
        compose.onNodeWithText("考研词汇 (ECDICT)").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN-night")
    fun homeChineseDark() {
        val controller = words()
        show(controller) { WordsTab(controller) }
        compose.onNodeWithText("今天").assertExists()
        capture()
    }

    @Test
    fun newWord() {
        val controller = words(history = false)
        show(controller) { StudySessionScreen(onClose = {}) }
        compose.onNodeWithText("I Know This").assertExists()
        capture()
    }

    @Test
    fun recallAndRate() {
        val controller = words(mode = SessionMode.RECOGNITION)
        show(controller) { StudySessionScreen(onClose = {}) }
        compose.onNodeWithText("Show Answer").performClick()
        compose.onNodeWithText("Again").assertExists()
        capture()
    }

    @Test
    fun multipleChoiceAnswered() {
        val controller = words(mode = SessionMode.CHOICE)
        // The question the screen asks first, made the same way, so a wrong option can be picked.
        val state = controller.vocab.model.state.value
        val session = StudySession(state.cards, state.plan(now).newWords, now, state.scheduler, state.clock, state.settings.reviewMode, seed = now.toEpochMilli().toULong())
        val item = session.next(now)!!
        check(item.mode == ReviewMode.CHOICE && item.kind != StudyItem.Kind.NEW) { "The first question is $item" }
        val question = state.choicePool.question(state.entry(item.headword)!!, SeededRandom.seed(session.seed.toString(), item.headword, "1"))!!
        show(controller) { StudySessionScreen(onClose = {}) }
        compose.onNodeWithText(question.options[(question.correctIndex + 1) % question.options.size]).performClick()
        compose.onNodeWithText("Continue").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun spellingWrongChinese() {
        val controller = words(mode = SessionMode.SPELLING)
        show(controller) { StudySessionScreen(onClose = {}) }
        compose.onNode(hasSetTextAction()).performTextInput("acommodate")
        compose.onNodeWithText("检查").performClick()
        capture()
    }

    @Test
    fun wordPage() {
        val controller = words()
        show(controller) { WordDetailScreen("abandon", onBack = {}) }
        compose.onNodeWithText("Your Progress").assertExists()
        capture()
    }

    @Test
    fun search() {
        val controller = words()
        show(controller) { WordSearchScreen(onOpen = {}, onBack = {}, initialQuery = "acc") }
        compose.onNodeWithText("accommodate").assertExists()
        capture()
    }

    @Test
    fun books() {
        val controller = words()
        show(controller) { BooksScreen(onBack = {}) }
        compose.onNodeWithText("Import a Word List…").assertExists()
        capture()
    }

    @Test
    fun importPreview() {
        val controller = words()
        show(controller) { WordListImportScreen(onDismiss = {}, initialText = "abandon\nability 能力\nacquire\tv. 获得；学到\ntenacious adj. 顽强的\nqwertyuiop\nabandon") }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Words to import"))
        capture()
    }

    @Test
    fun settings() {
        val controller = words()
        show(controller) { VocabSettingsScreen(onBack = {}) }
        compose.onNodeWithText("New words a day").assertExists()
        capture()
    }

    @Test
    fun statistics() {
        val controller = words()
        show(controller) { VocabStatsScreen(onBack = {}) }
        compose.onNodeWithText("Words a Day, Last 30 Days").assertExists()
        capture()
    }
}
