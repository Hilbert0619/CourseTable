package io.github.hilbert0619.coursetable.ai

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hilbert0619.aikit.AIAgentSession
import io.github.hilbert0619.aikit.AIAnswer
import io.github.hilbert0619.aikit.AIError
import io.github.hilbert0619.aikit.AIImage
import io.github.hilbert0619.aikit.AIMessage
import io.github.hilbert0619.aikit.AIPendingCall
import io.github.hilbert0619.aikit.AIStopReason
import io.github.hilbert0619.aikit.AgentChange
import io.github.hilbert0619.aikit.AgentWorld
import io.github.hilbert0619.aikit.AppTools
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.TodoTiming
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.focus.FocusText
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope

/**
 * An Assistant chat with the app's tools (docs/ai-agent-spec.md, iOS `AgentRun`): each round streams through [round];
 * reading tools run at once; changes wait as cards until the user confirms or cancels them, and the next round starts
 * when every call has its result. The screen keeps [context] up to date. Main thread only.
 */
class AgentRun(
    scope: CoroutineScope,
    private val store: AIStore,
    private val text: AgentTexts,
    conversation: AIConversation = AIConversation(),
    usesTools: Boolean,
) {
    enum class Notice {
        /** The provider rejected the tools; the chat goes on without them. */
        TOOLS_UNAVAILABLE,
        ROUND_LIMIT,
        CUT_OFF,
        REFUSED,
    }

    /** Courses to check in the import preview. */
    data class ImportRequest(val id: String, val csv: String)

    /** The app's data, as the screen last saw it. */
    lateinit var context: AgentContext

    var conversation by mutableStateOf(
        // Changes still waiting when a saved chat was closed weren't made.
        conversation.copy(cards = conversation.cards.mapValues { (_, card) -> if (card.state == AgentCard.State.WAITING) card.copy(state = AgentCard.State.CANCELLED) else card }),
    )
        private set

    private var session = AIAgentSession(conversation.messages, if (usesTools) AppTools.all else emptyList())

    /** The conversation so far, as the chat shows it. */
    var messages by mutableStateOf(session.messages.toList())
        private set

    /** The answer streaming in now. */
    val round = AIRun(scope)

    var notice by mutableStateOf<Notice?>(null)
        private set

    var importRequest by mutableStateOf<ImportRequest?>(null)
        private set

    private val changes = mutableMapOf<String, AgentChange>()
    private val undos = mutableStateMapOf<String, AgentUndo>()
    private var withoutThinking = false

    val usesTools: Boolean get() = session.tools.isNotEmpty()
    val isEmpty: Boolean get() = messages.isEmpty() && !round.state.hasContent
    fun canUndo(id: String): Boolean = id in undos

    // Turns

    /** Sends the user's message; changes still waiting are cancelled by it. */
    fun send(message: String, images: List<AIImage> = emptyList(), system: String) {
        round.reset()
        notice = null
        cancelWaiting()
        session.send(message, images, system)
        synced()
        nextRound()
    }

    /** After an error: asks again. */
    fun retry() {
        round.reset()
        nextRound()
    }

    /** Keeps what has arrived so far. */
    fun stop() {
        val answer = round.answer
        round.cancel()
        session.receiveStopped(answer)
        round.reset()
        synced()
    }

    private fun nextRound() {
        if (!session.isWaitingForModel) return
        val request = if (withoutThinking) session.request().withoutThinking else session.request()
        round.start(request, store, onEnd = { finishRound(it) })
    }

    private fun finishRound(answer: AIAnswer) {
        val error = round.state.error
        if (error != null) {
            if (usesTools && error.rejectsTools && session.messages.none { it.toolCalls.isNotEmpty() }) {
                // The model can't use tools: carry on without them.
                session = AIAgentSession(session.messages, emptyList(), session.system)
                notice = Notice.TOOLS_UNAVAILABLE
                retry()
            } else if (!withoutThinking && rejectsThinking(error)) {
                // A thinking block the provider won't take back (e.g. after the model changed): drop them once.
                withoutThinking = true
                retry()
            }
            return // The error stays on screen with Try Again.
        }
        val pending = session.receive(answer)
        round.reset()
        if (answer.stop == AIStopReason.CutOff) notice = Notice.CUT_OFF
        if (answer.stop == AIStopReason.Refused) notice = Notice.REFUSED
        if (session.stoppedAtRoundLimit) notice = Notice.ROUND_LIMIT
        if (pending.isNotEmpty()) {
            val world = context.world()
            for (call in pending) handle(call, world)
        }
        synced()
        nextRound()
    }

    private fun handle(call: AIPendingCall, world: AgentWorld) {
        if (call.needsConfirmation) {
            AppTools.proposal(call, world).fold(
                onSuccess = { change ->
                    changes[call.id] = change
                    setCard(text.proposal(call.id, call.tool.name, change, world.timetable))
                },
                onFailure = { problem ->
                    session.resolve(call.id, problem.message.orEmpty(), isError = true)
                    setCard(AgentCard(call.id, call.tool.name, text.title(call.tool.name), state = AgentCard.State.FAILED))
                },
            )
        } else {
            val result = AppTools.read(call, world)
            session.resolve(call.id, result.content, result.isError)
            setCard(text.lookup(call))
        }
    }

    private fun rejectsThinking(error: AIError): Boolean {
        val message = (error as? AIError.Rejected)?.text?.lowercase() ?: return false
        return message.contains("signature") || message.contains("thinking")
    }

    // Cards

    fun confirm(id: String) {
        val change = changes[id] ?: return
        if (conversation.cards[id]?.state != AgentCard.State.WAITING) return
        if (change is AgentChange.AddCourses) {
            importRequest = ImportRequest(id, change.csv)
            return
        }
        changes.remove(id)
        context.apply(change)?.let { undos[id] = it }
        updateCard(id) { it.copy(state = AgentCard.State.CONFIRMED) }
        session.resolve(id, AppTools.confirmedResult(change))
        synced()
        nextRound()
    }

    fun cancel(id: String) {
        if (conversation.cards[id]?.state != AgentCard.State.WAITING) return
        changes.remove(id)
        updateCard(id) { it.copy(state = AgentCard.State.CANCELLED) }
        session.resolve(id, AIAgentSession.CANCELLED_RESULT)
        synced()
        nextRound()
    }

    /** The import preview added courses. */
    fun imported(id: String, added: List<Course>, skipped: Int) {
        importRequest = null
        if (conversation.cards[id]?.state != AgentCard.State.WAITING) return
        changes.remove(id)
        undos[id] = AgentUndo.DeleteCourses(added.map { it.id })
        updateCard(id) { it.copy(state = AgentCard.State.CONFIRMED, lines = text.imported(added.size, skipped)) }
        session.resolve(id, AppTools.importedResult(added.size, skipped))
        synced()
        nextRound()
    }

    /** The import preview closed without an import: that's a no. */
    fun importClosed() {
        val id = importRequest?.id ?: return
        importRequest = null
        cancel(id)
    }

    fun undo(id: String) {
        val undo = undos.remove(id) ?: return
        context.undo(undo)
        updateCard(id) { it.copy(state = AgentCard.State.UNDONE) }
        synced()
    }

    private fun cancelWaiting() {
        val waiting = conversation.cards.values.filter { it.state == AgentCard.State.WAITING }.map { it.id }
        for (id in waiting) {
            changes.remove(id)
            updateCard(id) { it.copy(state = AgentCard.State.CANCELLED) }
        }
    }

    private fun setCard(card: AgentCard) {
        conversation = conversation.copy(cards = conversation.cards + (card.id to card))
    }

    private fun updateCard(id: String, change: (AgentCard) -> AgentCard) {
        val card = conversation.cards[id] ?: return
        setCard(change(card))
    }

    /** The chat shows the session's messages, and the store keeps them. */
    private fun synced() {
        messages = session.messages.toList()
        conversation = conversation.copy(messages = messages)
        store.saveConversation(conversation)
    }
}

/** What the Assistant's lines and cards say (iOS `AgentCard` extensions), in the app's language. */
class AgentTexts(private val res: Resources) {
    private val locale get() = res.configuration.locales[0]

    /** The name of a tool's action, as the chat shows it. */
    fun title(tool: String): String = when (tool) {
        AppTools.listTodos.name -> res.getString(R.string.agent_looked_todos)
        AppTools.getSchedule.name -> res.getString(R.string.agent_looked_classes)
        AppTools.listCourses.name -> res.getString(R.string.agent_looked_courses)
        AppTools.getVocabOverview.name -> res.getString(R.string.agent_looked_vocab)
        AppTools.getVocabHistory.name -> res.getString(R.string.agent_looked_history)
        AppTools.getHardWords.name -> res.getString(R.string.agent_looked_hard_words)
        AppTools.getWordProgress.name -> res.getString(R.string.agent_looked_word)
        AppTools.getFocusHistory.name -> res.getString(R.string.agent_looked_focus)
        AppTools.addTodo.name -> res.getString(R.string.agent_new_todo)
        AppTools.updateTodo.name -> res.getString(R.string.agent_change_todo)
        AppTools.deleteTodo.name -> res.getString(R.string.agent_delete_todo)
        AppTools.addCourses.name -> res.getString(R.string.agent_add_courses)
        AppTools.startFocus.name -> res.getString(R.string.agent_start_focus)
        else -> tool
    }

    fun lookup(call: AIPendingCall): AgentCard {
        val word = call.arguments.text("word")
        val title = if (call.tool.name == AppTools.getWordProgress.name && word != null) {
            res.getString(R.string.agent_looked_up, word)
        } else {
            title(call.tool.name)
        }
        return AgentCard(call.id, call.tool.name, title, state = AgentCard.State.LOOKED)
    }

    fun proposal(id: String, tool: String, change: AgentChange, timetable: Timetable): AgentCard {
        val lines = when (change) {
            is AgentChange.AddTodo -> listOf(change.todo.title, whenText(change.todo, timetable))
            is AgentChange.DeleteTodo -> listOf(change.todo.title, whenText(change.todo, timetable))
            is AgentChange.UpdateTodo -> buildList {
                add(change.after.title)
                add(whenText(change.after, timetable))
                if (change.before.day != change.after.day || change.before.timing != change.after.timing) {
                    add(res.getString(R.string.agent_was, whenText(change.before, timetable)))
                }
                if (change.before.isDone != change.after.isDone) {
                    add(res.getString(if (change.after.isDone) R.string.agent_mark_done else R.string.agent_mark_not_done))
                }
            }
            is AgentChange.AddCourses -> buildList {
                add(res.getQuantityString(R.plurals.agent_courses_to_add, change.courses, change.courses))
                if (change.duplicates > 0) add(res.getQuantityString(R.plurals.agent_already_there, change.duplicates, change.duplicates))
                if (change.problems > 0) add(res.getQuantityString(R.plurals.agent_lines_unreadable, change.problems, change.problems))
            }
            is AgentChange.StartFocus -> {
                val settings = change.settings
                val length = FocusText(res).duration(settings.totalMinutes * 60)
                // Breaks only come between blocks, so a session no longer than one block has none.
                val rhythm = when {
                    !settings.usesBreaks -> res.getString(R.string.agent_no_breaks)
                    settings.totalMinutes > settings.focusMinutes -> res.getString(R.string.agent_blocks_with_breaks, settings.focusMinutes)
                    else -> null
                }
                listOf(change.goal, listOfNotNull(length, rhythm).joinToString(" · ")).filter { it.isNotEmpty() }
            }
        }
        return AgentCard(id, tool, title(tool), lines, AgentCard.State.WAITING)
    }

    fun imported(added: Int, skipped: Int): List<String> = buildList {
        add(res.getQuantityString(R.plurals.agent_courses_added, added, added))
        if (skipped > 0) add(res.getQuantityString(R.plurals.agent_already_there, skipped, skipped))
    }

    /** "Fri, Oct 16 · 17:30" or "· After Calculus". */
    fun whenText(todo: Todo, timetable: Timetable): String {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEMMMd")
        val day = DateTimeFormatter.ofPattern(pattern, locale).format(java.time.LocalDate.of(todo.day.year, todo.day.month, todo.day.day))
        val timing = when (val timing = todo.timing) {
            TodoTiming.Anytime -> res.getString(R.string.any_time)
            is TodoTiming.At -> timing.time.toString()
            is TodoTiming.AfterClass -> res.getString(R.string.agent_after, timetable.courses.firstOrNull { it.id == timing.courseId }?.name.orEmpty())
        }
        return "$day · $timing"
    }
}

/** A message the user sent: the typed part, and whether text read from a photo went with it. */
fun AIMessage.typedPart(): Pair<String, Boolean> = io.github.hilbert0619.aikit.AIPrompts.typedPart(text)
