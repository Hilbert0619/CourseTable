package io.github.hilbert0619.aikit

import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.FocusSettings
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Semester
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.TodoTiming
import io.github.hilbert0619.coursekit.uuidString
import io.github.hilbert0619.vocabkit.CardState
import io.github.hilbert0619.vocabkit.DailyPlan
import io.github.hilbert0619.vocabkit.ExamCountdown
import io.github.hilbert0619.vocabkit.Rating
import io.github.hilbert0619.vocabkit.ReviewEvent
import io.github.hilbert0619.vocabkit.ReviewMode
import io.github.hilbert0619.vocabkit.Scheduler
import io.github.hilbert0619.vocabkit.StudyClock
import io.github.hilbert0619.vocabkit.WordEntry
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The agent cases in `spec/fixtures/ai-tool-*.json`, `ai-agent-tools.json` and `ai-timetable-photo.json`, which the
 * Swift package's tests read too (`CourseKit/Tests/AIKitTests/AIAgentFixtureTests.swift`).
 */
class AIAgentFixtureTests {
    /** A tool as the fixtures write it. */
    @Serializable
    class FixtureTool(val name: String, val description: String, val effect: String, val parameters: List<Parameter>) {
        @Serializable
        class Parameter(
            val name: String,
            val type: String,
            val max: Int? = null,
            val min: Int? = null,
            val choices: List<String>? = null,
            val description: String,
            val required: Boolean? = null,
        )

        val tool: AITool
            get() = AITool(
                name, description,
                parameters.map { parameter ->
                    val kind = when (parameter.type) {
                        "text" -> AIToolParameter.Kind.Text(parameter.max ?: 100)
                        "date" -> AIToolParameter.Kind.Date
                        "time" -> AIToolParameter.Kind.Time
                        "integer" -> AIToolParameter.Kind.Integer((parameter.min ?: 0)..(parameter.max ?: 100))
                        "boolean" -> AIToolParameter.Kind.Boolean
                        else -> AIToolParameter.Kind.Choice(parameter.choices.orEmpty())
                    }
                    AIToolParameter(parameter.name, kind, parameter.description, parameter.required ?: false)
                },
                if (effect == "reads") AITool.Effect.READS else AITool.Effect.CHANGES,
            )
    }

    @Serializable
    class ToolStreamCase(
        val name: String,
        val format: AIWireFormat,
        val lines: List<String>,
        val text: String,
        val reasoning: String,
        val stop: String? = null,
        val toolCalls: List<AIToolCall>,
        val thinking: List<AIThinkingBlock>,
        val inputTokens: Int? = null,
        val outputTokens: Int? = null,
        val ended: Boolean,
        val sawThinking: Boolean,
    )

    @Serializable
    class ToolStreamsFile(val streams: List<ToolStreamCase>)

    @Serializable
    class RequestCase(
        val name: String,
        val connection: Connection,
        val system: String,
        val tools: List<FixtureTool>,
        val messages: List<AIMessage>,
        val body: JsonElement,
    ) {
        @Serializable
        class Connection(val providerID: String, val format: AIWireFormat, val baseURL: String, val model: String)
    }

    @Serializable
    class RequestsFile(val requests: List<RequestCase>)

    @Serializable
    class ArgumentCases(val tool: FixtureTool, val cases: List<Case>) {
        @Serializable
        class Case(val name: String, val arguments: String, val values: Map<String, JsonElement>? = null, val error: String? = null)
    }

    @Test
    fun toolCallStreamsDecodeTheSameInAnyChunkSize() {
        val mismatches = AIFixtures.Mismatches()
        for (item in AIFixtures.decode<ToolStreamsFile>("ai-tool-streams.json").streams) {
            for (size in listOf(1, 5, 64, 4096)) {
                val what = "${item.name} (chunks of $size)"
                val run = AIFixtures.run(item.format, item.lines.joinToString("\n").toByteArray(), size)
                val answer = run.answer
                mismatches.check("$what: error", null, run.error)
                mismatches.check("$what: text", item.text, answer.text)
                mismatches.check("$what: reasoning", item.reasoning, answer.reasoning)
                mismatches.check("$what: stop", item.stop, AIFixtures.stop(answer.stop))
                mismatches.check("$what: tool calls", item.toolCalls, answer.toolCalls)
                mismatches.check("$what: thinking", item.thinking, answer.thinkingBlocks)
                mismatches.check("$what: input tokens", item.inputTokens, answer.inputTokens)
                mismatches.check("$what: output tokens", item.outputTokens, answer.outputTokens)
                mismatches.check("$what: ended", item.ended, answer.hasEnded)
                mismatches.check("$what: saw thinking", item.sawThinking, run.sawThinking)
            }
        }
        mismatches.assertNone()
    }

    @Test
    fun requestBodiesWithToolsAndPictures() {
        val mismatches = AIFixtures.Mismatches()
        for (item in AIFixtures.decode<RequestsFile>("ai-tool-requests.json").requests) {
            val connection = AIConnection(
                providerID = item.connection.providerID, name = "Test", format = item.connection.format,
                baseURL = item.connection.baseURL, model = item.connection.model,
            )
            val request = AIRequest(item.system, item.messages, tools = item.tools.map { it.tool })
            val http = connection.chatRequest(request, "k")
            val body = parseJson(String(http.body!!, Charsets.UTF_8))
            mismatches.check(item.name, item.body.jsonText, body?.jsonText)
        }
        mismatches.assertNone()
    }

    @Test
    fun argumentsAreCheckedAndNormalised() {
        val file = AIFixtures.decode<ArgumentCases>("ai-tool-arguments.json")
        val tool = file.tool.tool
        val mismatches = AIFixtures.Mismatches()
        for (item in file.cases) {
            tool.validate(item.arguments).fold(
                onSuccess = { arguments ->
                    mismatches.check("${item.name}: error", item.error, null)
                    mismatches.check("${item.name}: values", item.values, arguments.values)
                },
                onFailure = { problem -> mismatches.check("${item.name}: error", item.error, problem.message) },
            )
        }
        mismatches.assertNone()
    }

    // App tools

    @Serializable
    class AgentToolCases(val world: World, val cases: List<Case>) {
        @Serializable
        class World(
            val today: LocalDate,
            val now: TimeOfDay,
            val semester: Semester,
            val courses: List<Course>,
            val todos: List<Todo>,
            val focus: Focus,
            val vocab: Vocab,
        ) {
            @Serializable
            class Focus(val settings: FocusSettings, val sessions: List<Session>) {
                @Serializable
                class Session(val goal: String, val at: String, val focusedSeconds: Int, val finished: Boolean)
            }

            @Serializable
            class Vocab(
                val bookName: String,
                val book: List<String>,
                val entries: Map<String, String>,
                val newWordsPerDay: Int,
                val examDate: LocalDate,
                val events: List<Event>,
            ) {
                @Serializable
                class Event(val word: String, val rating: Int, val at: String, val durationMs: Int)
            }
        }

        @Serializable
        class Case(
            val name: String,
            val tool: String,
            val arguments: String,
            val result: String? = null,
            val error: String? = null,
            val proposal: JsonElement? = null,
            val running: Boolean? = null,
            val signedOut: Boolean? = null,
        )

        fun agentWorld(running: Boolean, signedOut: Boolean): AgentWorld {
            val timetable = Timetable(world.semester, courses = world.courses)
            val sessions = world.focus.sessions.map { FocusSnapshot.Session(it.goal, date(it.at), it.focusedSeconds, it.finished) }
            val runningSession = if (running) {
                FocusSnapshot.Running("英语阅读", world.today.toInstant(TimeOfDay(10, 0)), world.today.toInstant(TimeOfDay(11, 0)), isPaused = false)
            } else {
                null
            }
            var vocab: VocabSnapshot? = null
            if (!signedOut) {
                val clock = StudyClock()
                val scheduler = Scheduler()
                val events = world.vocab.events.map { event ->
                    val at = date(event.at)
                    ReviewEvent(
                        headword = event.word, rating = Rating.of(event.rating)!!, mode = ReviewMode.RECOGNITION, reviewedAt = at,
                        studyDay = clock.day(at), durationMs = event.durationMs,
                    )
                }
                val cards = scheduler.replay(events)
                val now = world.today.toInstant(world.now)
                val book = world.vocab.book
                val unseen = book.count { (cards[it]?.state ?: CardState.NEW) == CardState.NEW }
                vocab = VocabSnapshot(
                    bookName = world.vocab.bookName, book = book,
                    entries = world.vocab.entries.mapValues { (key, translation) -> WordEntry(headword = key, display = key, translation = translation) },
                    cards = cards, events = events,
                    plan = DailyPlan(cards, events, book, world.vocab.newWordsPerDay, now, clock),
                    exam = ExamCountdown.of(world.vocab.examDate, clock.day(now), unseen),
                    newWordsPerDay = world.vocab.newWordsPerDay, desiredRetention = 0.9, today = clock.day(now), scheduler = scheduler,
                )
            }
            return AgentWorld(
                world.today, world.now, timetable, world.todos, FocusSnapshot(world.focus.settings, sessions, runningSession), vocab,
            )
        }

        companion object {
            /** `YYYY-MM-DD HH:mm` in the current time zone. */
            fun date(text: String): Instant {
                val (day, time) = text.split(" ")
                return LocalDate.parse(day)!!.toInstant(TimeOfDay.parse(time)!!)
            }
        }
    }

    private fun timing(timing: TodoTiming): String = when (timing) {
        TodoTiming.Anytime -> "anytime"
        is TodoTiming.At -> "at ${timing.time}"
        is TodoTiming.AfterClass -> "after ${timing.courseId.uuidString}"
    }

    private fun describe(todo: Todo): Map<String, Any> =
        mapOf("title" to todo.title, "date" to todo.day.toString(), "timing" to timing(todo.timing), "done" to todo.isDone)

    private fun describe(change: AgentChange): JsonElement = when (change) {
        is AgentChange.AddTodo -> json(mapOf("add" to describe(change.todo)))
        is AgentChange.UpdateTodo -> json(mapOf("update" to describe(change.after) + ("id" to change.after.id.uuidString)))
        is AgentChange.DeleteTodo -> json(mapOf("delete" to change.todo.id.uuidString))
        is AgentChange.AddCourses -> json(mapOf("courses" to mapOf("courses" to change.courses, "duplicates" to change.duplicates, "problems" to change.problems)))
        is AgentChange.StartFocus -> json(
            mapOf("focus" to mapOf("goal" to change.goal, "totalMinutes" to change.settings.totalMinutes, "usesBreaks" to change.settings.usesBreaks)),
        )
    }

    @Test
    fun toolsAnswerAndProposeTheSameWay() {
        val file = AIFixtures.decode<AgentToolCases>("ai-agent-tools.json")
        val mismatches = AIFixtures.Mismatches()
        for (item in file.cases) {
            val world = file.agentWorld(item.running ?: false, item.signedOut ?: false)
            val session = AIAgentSession(tools = AppTools.all)
            session.send("test", system = "")
            val answer = AIAnswer()
            answer.apply(AIStreamEvent.ToolCall(0, "c1", item.tool))
            answer.apply(AIStreamEvent.ToolArguments(0, item.arguments))
            val pending = session.receive(answer)
            val outcome: Map<String, Any?>
            val call = pending.firstOrNull()
            if (call != null) {
                outcome = if (call.needsConfirmation) {
                    AppTools.proposal(call, world).fold(
                        onSuccess = { mapOf("proposal" to describe(it)) },
                        onFailure = { mapOf("error" to it.message) },
                    )
                } else {
                    val result = AppTools.read(call, world)
                    if (result.isError) mapOf("error" to result.content) else mapOf("result" to result.content)
                }
            } else {
                val result = session.messages.last().toolResults.first()
                mismatches.check("${item.name}: an error result", true, result.isError)
                outcome = mapOf("error" to result.content)
            }
            val expected = mutableMapOf<String, Any?>()
            item.result?.let { expected["result"] = it }
            item.error?.let { expected["error"] = it }
            item.proposal?.let { expected["proposal"] = it }
            mismatches.check(item.name, json(expected), json(outcome))
        }
        mismatches.assertNone()
    }

    @Test
    fun confirmedChangesAreToldToTheModel() {
        val todo = Todo(id = UUID.fromString("ABCDEF12-0000-0000-0000-000000000000"), title = "x", day = LocalDate(2026, 10, 16))
        assertEquals("Added the to-do (id abcdef12).", AppTools.confirmedResult(AgentChange.AddTodo(todo)))
        assertEquals("The user imported 3 courses; 1 already in the timetable were skipped.", AppTools.importedResult(3, 1))
        assertEquals("The user imported 1 course.", AppTools.importedResult(1, 0))
    }

    @Test
    fun longListsAreCut() {
        val lines = (1..500).map { "line $it with some words in it" }
        val text = AppTools.list("Header:", lines, empty = "none")
        assertTrue(text.length <= AppTools.MAX_RESULT_CHARACTERS)
        assertTrue(text.startsWith("Header:\nline 1 "))
        assertTrue(Regex("\n…and \\d+ more$").containsMatchIn(text))
        assertEquals("none", AppTools.list("Header:", emptyList(), empty = "none"))
    }

    @Test
    fun everyToolHasAUsableSchema() {
        val names = AppTools.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
        for (tool in AppTools.all) {
            assertEquals("object", tool.schema["type"]?.stringValue, tool.name)
            assertTrue(Regex("^[a-z_]{1,64}$").matches(tool.name))
            assertTrue(tool.description.length < 1024)
        }
    }

    // Photos

    @Serializable
    class PhotoCases(val ocr: List<OCRCase>, val answers: List<AnswerCase>) {
        @Serializable
        class OCRCase(val name: String, val boxes: List<Box>, val text: String) {
            @Serializable
            class Box(val text: String, val x: Double, val y: Double, val width: Double, val height: Double)
        }

        @Serializable
        class AnswerCase(
            val name: String,
            val answer: String,
            val csv: String? = null,
            val periods: List<List<String>>? = null,
            val none: Boolean? = null,
        )
    }

    @Test
    fun timetablePhotosGiveLinesCSVAndPeriods() {
        val file = AIFixtures.decode<PhotoCases>("ai-timetable-photo.json")
        val mismatches = AIFixtures.Mismatches()
        for (item in file.ocr) {
            val boxes = item.boxes.map { TimetablePhoto.TextBox(it.text, it.x, it.y, it.width, it.height) }
            mismatches.check(item.name, item.text, TimetablePhoto.ocrText(boxes))
        }
        for (item in file.answers) {
            val reading = TimetablePhoto.reading(item.answer)
            if (item.none == true) {
                mismatches.check("${item.name}: nothing", null, reading)
                continue
            }
            mismatches.check("${item.name}: csv", item.csv, reading?.csv)
            mismatches.check(
                "${item.name}: periods", item.periods,
                reading?.periods?.periods?.map { listOf(it.start.toString(), it.end.toString()) },
            )
        }
        mismatches.assertNone()
    }
}
