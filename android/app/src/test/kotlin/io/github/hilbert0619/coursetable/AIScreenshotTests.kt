package io.github.hilbert0619.coursetable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hilbert0619.aikit.AIAnswer
import io.github.hilbert0619.aikit.AIConnection
import io.github.hilbert0619.aikit.AIProvider
import io.github.hilbert0619.aikit.AIStreamEvent
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursetable.ai.AIStore
import io.github.hilbert0619.coursetable.ai.DemoAITransport
import io.github.hilbert0619.coursetable.ai.DemoAnswers
import io.github.hilbert0619.coursetable.ai.MemoryAIKeys
import io.github.hilbert0619.coursetable.ui.LocalNowSource
import io.github.hilbert0619.coursetable.ui.NowSource
import io.github.hilbert0619.coursetable.ui.ai.AIConnectionEditor
import io.github.hilbert0619.coursetable.ui.ai.AISettingsScreen
import io.github.hilbert0619.coursetable.ui.ai.LocalAI
import io.github.hilbert0619.coursetable.ui.theme.StudyHourTheme
import io.github.hilbert0619.coursetable.ui.words.LocalWordsController
import io.github.hilbert0619.coursetable.ui.words.StudySessionScreen
import io.github.hilbert0619.coursetable.ui.words.WordDetailScreen
import io.github.hilbert0619.coursetable.ui.words.WordsController
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
 * AI help (Settings → AI Help, the connection editor, the word page's help, "Why?") with a store in memory and the demo
 * transport's canned answers. Nothing reaches the network.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AIScreenshotTests {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Moment(LocalDate(2026, 9, 28), TimeOfDay(21, 0)).toInstant(zone)
    private val clock = Clock.fixed(now, zone)

    /** One DeepSeek connection, answered at once by the demo transport, with some use this month. */
    private fun store(connected: Boolean = true): AIStore {
        val store = AIStore(null, null, DemoAITransport(pieceDelayMillis = 0), MemoryAIKeys(), clock = clock::instant, zone = zone)
        if (connected) {
            val deepSeek = AIConnection(AIProvider.preset("deepseek")!!)
            store.save(deepSeek, "demo-key")
            repeat(12) { store.record(AIAnswer().apply { apply(AIStreamEvent.Usage(900, 350)) }, deepSeek) }
        }
        return store
    }

    private fun words(mode: io.github.hilbert0619.vocabkit.SessionMode = SessionMode.MIXED): WordsController {
        val app = VocabApp(DemoBackend(true, true, mode, now, zone), VocabModel(InMemoryVocabStore(), clock, Dispatchers.Unconfined))
        runBlocking { app.start() }
        return WordsController(app, CoroutineScope(Dispatchers.Unconfined), { _, _ -> true }, clock::instant)
    }

    private fun show(store: AIStore?, controller: WordsController? = null, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalNowSource provides NowSource(clock, ticks = false), LocalAI provides store) {
                if (controller != null) {
                    CompositionLocalProvider(LocalWordsController provides controller) { StudyHourTheme { content() } }
                } else {
                    StudyHourTheme { content() }
                }
            }
        }
    }

    private fun capture() = compose.onRoot().captureRoboImage()

    @Test
    fun aiSettings() {
        val store = store()
        show(store) { AISettingsScreen(store, onBack = {}) }
        compose.onNodeWithText("DeepSeek").assertExists()
        compose.onNodeWithText("This month: 12 requests · 15000 tokens").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun aiSettingsChineseEmpty() {
        val store = store(connected = false)
        show(store) { AISettingsScreen(store, onBack = {}) }
        compose.onNodeWithText("添加连接").assertExists()
        capture()
    }

    @Test
    fun newConnectionEditor() {
        val store = store(connected = false)
        show(store) { AIConnectionEditor(store, null, onDismiss = {}, inDialog = false) }
        compose.onNodeWithText("deepseek-v4-flash").assertExists()
        // No key yet: nothing to test or save.
        compose.onNodeWithText("Test Connection").assertExists()
        capture()
    }

    @Test
    fun connectionEditorAfterTheTest() {
        val store = store()
        val connection = store.defaultConnection!!
        show(store) { AIConnectionEditor(store, connection, onDismiss = {}, inDialog = false) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Test Connection"))
        compose.onNodeWithText("Test Connection").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Tools work: the model called the test tool.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Connected. The model answered.").assertExists()
        capture()
    }

    @Test
    fun wordPageAIHelp() {
        val store = store()
        store.saveAnswer(DemoAnswers.answer("explain"), "deepseek-v4-flash", "word|abandon|explain|english")
        show(store, words()) { WordDetailScreen("abandon", onBack = {}) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Explain"))
        compose.onNodeWithText("Explain").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Ask Again"))
        compose.onNodeWithText("AI-generated · deepseek-v4-flash").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun wordPageAIHelpChinese() {
        val store = store()
        show(store, words()) { WordDetailScreen("abandon", onBack = {}) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("搭配"))
        compose.onNodeWithText("讲解").performClick()
        // Not saved yet: asked through the demo transport.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("重新提问").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("重新提问"))
        capture()
    }

    @Test
    fun wordPageWithoutAI() {
        val store = store(connected = false)
        show(store, words()) { WordDetailScreen("abandon", onBack = {}) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Set Up AI Help…"))
        capture()
    }

    @Test
    fun whyAfterAWrongChoice() {
        val controller = words(SessionMode.CHOICE)
        val state = controller.vocab.model.state.value
        val session = StudySession(state.cards, state.plan(now).newWords, now, state.scheduler, state.clock, state.settings.reviewMode, seed = now.toEpochMilli().toULong())
        val item = session.next(now)!!
        check(item.kind != StudyItem.Kind.NEW)
        val question = state.choicePool.question(state.entry(item.headword)!!, SeededRandom.seed(session.seed.toString(), item.headword, "1"))!!
        show(store(), controller) { StudySessionScreen(onClose = {}) }
        compose.onNodeWithText(question.options[(question.correctIndex + 1) % question.options.size]).performClick()
        compose.onNodeWithText("Why?").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Tip:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(isRoot()).onLast().captureRoboImage()
    }
}
