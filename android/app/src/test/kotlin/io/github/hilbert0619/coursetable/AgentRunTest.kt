package io.github.hilbert0619.coursetable

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hilbert0619.aikit.AIAnswerLanguage
import io.github.hilbert0619.aikit.AIConnection
import io.github.hilbert0619.aikit.AIError
import io.github.hilbert0619.aikit.AIHTTPRequest
import io.github.hilbert0619.aikit.AIPrompts
import io.github.hilbert0619.aikit.AIProvider
import io.github.hilbert0619.aikit.AIStopReason
import io.github.hilbert0619.aikit.AIStreamEvent
import io.github.hilbert0619.aikit.AIWireFormat
import io.github.hilbert0619.aikit.AppTools
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.Semester
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.TodoTiming
import io.github.hilbert0619.coursetable.ai.AIStore
import io.github.hilbert0619.coursetable.ai.AITransport
import io.github.hilbert0619.coursetable.ai.AgentCard
import io.github.hilbert0619.coursetable.ai.AgentContext
import io.github.hilbert0619.coursetable.ai.AgentRun
import io.github.hilbert0619.coursetable.ai.AgentTexts
import io.github.hilbert0619.coursetable.ai.DemoAITransport
import io.github.hilbert0619.coursetable.ai.DemoAnswers
import io.github.hilbert0619.coursetable.ai.MemoryAIKeys
import io.github.hilbert0619.coursetable.data.DemoData
import io.github.hilbert0619.coursetable.focus.FocusActions
import io.github.hilbert0619.coursetable.focus.FocusState
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.AppData
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Assistant conversations with tools ([AgentRun]) through the demo transport's scripted tool calls, against the demo
 * timetable on Monday 2026-09-28 at 21:00 (Robolectric, for the cards' texts).
 */
@RunWith(AndroidJUnit4::class)
class AgentRunTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate(2026, 9, 28)
    private val clock = Clock.fixed(Moment(today, TimeOfDay(21, 0)).toInstant(zone), zone)
    private val timetable = Timetable(Semester(today.adding(-28)), courses = DemoData.courses(today))

    /** What the tools changed. */
    private class Recorder : AppActions by AppActions.None {
        val saved = mutableListOf<Todo>()
        val deleted = mutableListOf<UUID>()
        val deletedCourses = mutableListOf<UUID>()
        override fun saveTodo(todo: Todo) {
            saved += todo
        }
        override fun deleteTodo(id: UUID) {
            deleted += id
        }
        override fun deleteCourse(id: UUID) {
            deletedCourses += id
        }
    }

    private class FocusRecorder : FocusActions by FocusActions.None {
        var started = false
        var goal = ""
        override fun setGoal(goal: String) {
            this.goal = goal
        }
        override fun start() {
            started = true
        }
    }

    private val actions = Recorder()
    private val focus = FocusRecorder()

    private fun TestScope.agent(transport: AITransport = DemoAITransport(pieceDelayMillis = 0), usesTools: Boolean = true): AgentRun {
        val store = AIStore(null, null, transport, MemoryAIKeys(), clock::instant, zone)
        store.save(AIConnection(AIProvider.preset("deepseek")!!), "k")
        return AgentRun(this, store, AgentTexts(application.resources), usesTools = usesTools).also {
            it.context = AgentContext(AppData(timetable, emptyList(), emptyList()), actions, FocusState(), focus, vocab = null, clock = clock)
        }
    }

    private val system = AIPrompts.assistant(AIAnswerLanguage.ENGLISH, today, TimeOfDay(21, 0), null, null, usesTools = true)

    private fun AgentRun.cards(): List<AgentCard> = messages.flatMap { it.toolCalls }.mapNotNull { conversation.cards[it.id] }

    @Test
    fun aReminderIsProposedConfirmedAndUndone() = runTest {
        val run = agent()
        run.send("Remind me to hand in the paper after class tomorrow", system = system)
        advanceUntilIdle()
        // It looked at tomorrow's classes, then proposed a to-do after the first one.
        val cards = run.cards()
        assertEquals(listOf(AppTools.getSchedule.name, AppTools.addTodo.name), cards.map { it.tool })
        assertEquals(AgentCard.State.LOOKED, cards[0].state)
        val proposal = cards[1]
        assertEquals(AgentCard.State.WAITING, proposal.state)
        assertEquals("New To-Do", proposal.title)
        assertEquals("Hand in the paper", proposal.lines[0])
        assertTrue(proposal.lines[1], proposal.lines[1].contains("After "))
        assertTrue(actions.saved.isEmpty())

        run.confirm(proposal.id)
        advanceUntilIdle()
        val todo = actions.saved.single()
        assertEquals(today.adding(1), todo.day)
        assertTrue(todo.timing is TodoTiming.AfterClass)
        assertEquals(AgentCard.State.CONFIRMED, run.conversation.cards[proposal.id]?.state)
        assertEquals("Done — I'll remind you to hand in the paper tomorrow.", run.messages.last().text)

        assertTrue(run.canUndo(proposal.id))
        run.undo(proposal.id)
        assertEquals(listOf(todo.id), actions.deleted)
        assertEquals(AgentCard.State.UNDONE, run.conversation.cards[proposal.id]?.state)
    }

    @Test
    fun aCancelledFocusSessionDoesntStart() = runTest {
        val run = agent()
        run.send("Start a 25-minute focus session", system = system)
        advanceUntilIdle()
        val card = run.cards().single()
        assertEquals("Start Focus", card.title)
        assertEquals("Calculus review", card.lines[0])
        run.cancel(card.id)
        advanceUntilIdle()
        assertFalse(focus.started)
        assertEquals(AgentCard.State.CANCELLED, run.conversation.cards[card.id]?.state)
        assertEquals("OK, not now.", run.messages.last().text)

        // Confirmed, it starts with the goal.
        run.send("Start a 25-minute focus session", system = system)
        advanceUntilIdle()
        run.confirm(run.cards().last().id)
        advanceUntilIdle()
        assertTrue(focus.started)
        assertEquals("Calculus review", focus.goal)
    }

    @Test
    fun coursesFromAPhotoGoThroughTheImportPreview() = runTest {
        val run = agent()
        run.send("Add these courses\n\n" + AIPrompts.photoText("[x 0.10 y 0.10 w 0.10 h 0.02] 线性代数"), system = system)
        advanceUntilIdle()
        val card = run.cards().single()
        assertEquals(AppTools.addCourses.name, card.tool)
        run.confirm(card.id)
        assertEquals(DemoAnswers.PHOTO_CSV, run.importRequest?.csv)
        val added = listOf(Course(name = "线性代数", weekday = io.github.hilbert0619.coursekit.Weekday.MONDAY, startPeriod = 1, weeks = io.github.hilbert0619.coursekit.CourseWeeks(1, 16)))
        run.imported(card.id, added, skipped = 1)
        advanceUntilIdle()
        assertNull(run.importRequest)
        assertEquals(listOf("1 course added", "1 already in your timetable"), run.conversation.cards[card.id]?.lines)
        assertEquals("Done — the courses are in your timetable now.", run.messages.last().text)
        run.undo(card.id)
        assertEquals(added.map { it.id }, actions.deletedCourses)
    }

    @Test
    fun aModelWithoutToolsCarriesOnWithout() = runTest {
        var calls = 0
        val transport = object : AITransport {
            override fun stream(request: AIHTTPRequest, format: AIWireFormat): Flow<AIStreamEvent> = flow {
                calls += 1
                val body = String(request.body!!)
                if (body.contains("\"tools\"")) throw AIError.Rejected(400, "This model does not support function calling")
                emit(AIStreamEvent.Text("Hello"))
                emit(AIStreamEvent.Stop(AIStopReason.Complete))
                emit(AIStreamEvent.End)
            }

            override suspend fun data(request: AIHTTPRequest) = ByteArray(0)
        }
        val run = agent(transport)
        run.send("Hi", system = system)
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(AgentRun.Notice.TOOLS_UNAVAILABLE, run.notice)
        assertFalse(run.usesTools)
        assertEquals("Hello", run.messages.last().text)
    }

    @Test
    fun withoutAWordsAccountTheLookupSaysSo() = runTest {
        val run = agent()
        run.send("How are my words going?", system = system)
        advanceUntilIdle()
        assertEquals(
            listOf("Looked at your vocabulary progress", "Looked at your hard words"),
            run.cards().map { it.title },
        )
        val results = run.messages.flatMap { it.toolResults }
        assertTrue(results.all { it.isError && it.content == AppTools.NOT_SIGNED_IN })
    }
}
