package io.github.hilbert0619.aikit

import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseWeeks
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TodoTiming
import io.github.hilbert0619.coursekit.UuidSerializer
import io.github.hilbert0619.coursekit.Weekday
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

/**
 * The AI cases in `spec/fixtures/ai-*.json`, which the Swift package's tests read too
 * (`CourseKit/Tests/AIKitTests/AISharedFixtureTests.swift`): both platforms must parse streams, map errors and read to-do
 * drafts the same way. The expected values come from the iOS implementation.
 */
class AISharedFixtureTests {
    @Serializable
    class StreamCase(
        val name: String,
        val format: AIWireFormat,
        val lines: List<String>,
        val lineEnding: String? = null,
        val text: String,
        val stop: String? = null,
        val inputTokens: Int? = null,
        val outputTokens: Int? = null,
        val ended: Boolean? = null,
        val sawThinking: Boolean? = null,
        val error: String? = null,
    ) {
        val bytes: ByteArray get() = lines.joinToString(lineEnding ?: "\n").toByteArray()
    }

    @Serializable
    class StreamsFile(val streams: List<StreamCase>)

    @Serializable
    class ErrorCase(val name: String, val status: Int, val body: String, val kind: String, val message: String? = null)

    @Serializable
    class ErrorsFile(val errors: List<ErrorCase>)

    @Serializable
    class DraftCases(val today: String, val courses: List<NamedCourse>, val cases: List<Case>) {
        @Serializable
        class NamedCourse(@Serializable(with = UuidSerializer::class) val id: UUID, val name: String)

        @Serializable
        class Case(
            val name: String,
            val answer: String,
            val title: String? = null,
            val date: String? = null,
            val timing: String? = null,
            val unknownCourse: String? = null,
            val error: Boolean? = null,
        )
    }

    @Test
    fun streamsDecodeTheSameInAnyChunkSize() {
        val mismatches = AIFixtures.Mismatches()
        for (stream in AIFixtures.decode<StreamsFile>("ai-streams.json").streams) {
            for (size in listOf(1, 3, 7, 4096)) {
                val what = "${stream.name} (chunks of $size)"
                val run = AIFixtures.run(stream.format, stream.bytes, size)
                mismatches.check("$what: text", stream.text, run.answer.text)
                if (stream.error != null) {
                    mismatches.check("$what: error", stream.error, run.error?.let(AIFixtures::kind))
                    continue
                }
                mismatches.check("$what: error", null, run.error?.let(AIFixtures::kind))
                mismatches.check("$what: stop", stream.stop, AIFixtures.stop(run.answer.stop))
                mismatches.check("$what: input tokens", stream.inputTokens, run.answer.inputTokens)
                mismatches.check("$what: output tokens", stream.outputTokens, run.answer.outputTokens)
                mismatches.check("$what: ended", stream.ended, run.answer.hasEnded)
                mismatches.check("$what: saw thinking", stream.sawThinking, run.sawThinking)
                mismatches.check("$what: still thinking", false, run.answer.isThinking)
            }
        }
        mismatches.assertNone()
    }

    @Test
    fun errorResponsesMapToKinds() {
        val mismatches = AIFixtures.Mismatches()
        for (item in AIFixtures.decode<ErrorsFile>("ai-errors.json").errors) {
            val error = AIError.from(item.status, item.body.toByteArray())
            mismatches.check("${item.name}: kind", item.kind, AIFixtures.kind(error))
            if (item.message != null) mismatches.check("${item.name}: message", item.message, error.providerMessage)
        }
        mismatches.assertNone()
    }

    @Test
    fun todoDraftsAreReadTheSameWay() {
        val file = AIFixtures.decode<DraftCases>("ai-todo-drafts.json")
        val today = LocalDate.parse(file.today)!!
        val courses = file.courses.map { Course(id = it.id, name = it.name, weekday = Weekday.MONDAY, startPeriod = 1, weeks = CourseWeeks(1, 16)) }
        val mismatches = AIFixtures.Mismatches()
        for (item in file.cases) {
            val draft = runCatching { TodoDraft.parse(item.answer, today, courses) }
            if (item.error == true) {
                mismatches.check("${item.name}: unreadable", AIError.Unreadable, draft.exceptionOrNull())
                continue
            }
            val value = draft.getOrNull()
            if (value == null) {
                mismatches.check("${item.name}: parsed", null, draft.exceptionOrNull())
                continue
            }
            mismatches.check("${item.name}: title", item.title, value.title)
            mismatches.check("${item.name}: date", item.date, value.day.toString())
            val timing = when (val timing = value.timing) {
                TodoTiming.Anytime -> "anytime"
                is TodoTiming.At -> "at ${timing.time}"
                is TodoTiming.AfterClass -> "after ${courses.firstOrNull { it.id == timing.courseId }?.name ?: "?"}"
            }
            mismatches.check("${item.name}: timing", item.timing, timing)
            mismatches.check("${item.name}: unknown course", item.unknownCourse, value.unknownCourse)
        }
        mismatches.assertNone()
    }
}

/** Reading the shared files and running streams, for the AI fixture tests. */
object AIFixtures {
    private val directory = File(System.getProperty("fixtures.dir") ?: error("The test task sets fixtures.dir"))

    inline fun <reified T> decode(name: String): T = AIKitJson.decodeFromString(kotlinx.serialization.serializer<T>(), text(name))

    fun text(name: String): String = File(directory, name).readText()

    /** Collects every mismatch, so one run shows them all. */
    class Mismatches {
        val lines = mutableListOf<String>()
        fun <T> check(what: String, expected: T, actual: T) {
            if (expected != actual) lines += "$what:\n  expected $expected\n  got      $actual"
        }
        fun assertNone() = assertTrue(lines.isEmpty(), lines.joinToString("\n"))
    }

    class Run(val answer: AIAnswer, val error: AIError?, val sawThinking: Boolean)

    /** Runs a stream through the parser in chunks of [size] bytes. */
    fun run(format: AIWireFormat, bytes: ByteArray, size: Int): Run {
        val parser = SSEParser()
        val decoder = AIStreamDecoder(format)
        val answer = AIAnswer()
        var sawThinking = false
        val events = mutableListOf<SSEEvent>()
        var offset = 0
        while (offset < bytes.size) {
            events += parser.feed(bytes, offset, minOf(size, bytes.size - offset))
            offset += size
        }
        events += parser.finish()
        try {
            for (event in events) {
                for (item in decoder.decode(event)) {
                    if (item == AIStreamEvent.Thinking) sawThinking = true
                    answer.apply(item)
                }
            }
        } catch (error: AIError) {
            return Run(answer, error, sawThinking)
        }
        return Run(answer, null, sawThinking)
    }

    fun kind(error: AIError): String = when (error) {
        is AIError.WrongKey -> "wrongKey"
        is AIError.NoCredit -> "noCredit"
        is AIError.NotFound -> "notFound"
        is AIError.RateLimited -> "rateLimited"
        is AIError.Busy -> "busy"
        is AIError.Rejected -> "rejected"
        is AIError.Network -> "network"
        AIError.BadAddress -> "badAddress"
        AIError.NoModel -> "noModel"
        AIError.Unreadable -> "unreadable"
        AIError.Empty -> "empty"
    }

    fun stop(reason: AIStopReason?): String? = when (reason) {
        AIStopReason.Complete -> "complete"
        AIStopReason.CutOff -> "cutOff"
        AIStopReason.Refused -> "refused"
        AIStopReason.ToolUse -> "toolUse"
        is AIStopReason.Other -> reason.reason
        null -> null
    }
}
