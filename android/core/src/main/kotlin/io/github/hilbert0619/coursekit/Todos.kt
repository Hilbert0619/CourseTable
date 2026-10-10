package io.github.hilbert0619.coursekit

import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** When a to-do is due. */
@Serializable(with = TodoTimingSerializer::class)
sealed interface TodoTiming {
    /** Some time that day; no notification. */
    data object Anytime : TodoTiming

    /** At a time of its day. */
    data class At(val time: TimeOfDay) : TodoTiming

    /** When that course's class ends on its day. */
    data class AfterClass(val courseId: UUID) : TodoTiming
}

/** Something to do on a given day, e.g. "Pick up the package" at 12:00 or after Calculus. */
@Serializable
data class Todo(
    @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    val title: String,
    val day: LocalDate,
    val timing: TodoTiming = TodoTiming.Anytime,
    val isDone: Boolean = false,
)

/** A to-do with its time worked out from the timetable. */
data class ResolvedTodo(
    val todo: Todo,
    /** The class it follows, when that class meets on the to-do's day. */
    val session: ClassSession?,
    /** When it's due: its time, or the end of its class. `null` for "anytime" and for a class that doesn't meet that day. */
    val due: Moment?,
) {
    val id: UUID get() = todo.id

    /** Tied to a class that doesn't meet on its day, e.g. after the timetable was edited. */
    val isMissingItsClass: Boolean get() = todo.timing is TodoTiming.AfterClass && session == null
}

/** One day's classes and to-dos, in the order they happen. */
data class DayAgenda(
    val day: LocalDate,
    /** To-dos without a time, and those whose class doesn't meet that day. */
    val anytime: List<ResolvedTodo>,
    /** Classes and timed to-dos by time; at the same time, a to-do comes before a class. */
    val items: List<Item>,
) {
    sealed interface Item {
        val id: String

        /** A class, with the to-dos due when it ends. */
        data class Session(val session: ClassSession, val after: List<ResolvedTodo>) : Item {
            override val id: String get() = "session-${session.id}"
        }

        /** A to-do at a time. */
        data class TodoItem(val todo: ResolvedTodo) : Item {
            override val id: String get() = "todo-${todo.id.uuidString}"
        }
    }

    val isEmpty: Boolean get() = anytime.isEmpty() && items.isEmpty()
}

fun Timetable.resolve(todo: Todo): ResolvedTodo = when (val timing = todo.timing) {
    TodoTiming.Anytime -> ResolvedTodo(todo, null, null)
    is TodoTiming.At -> ResolvedTodo(todo, null, Moment(todo.day, timing.time))
    is TodoTiming.AfterClass -> {
        val session = sessions(todo.day).firstOrNull { it.course.id == timing.courseId }
        ResolvedTodo(todo, session, session?.endMoment)
    }
}

/** The classes of [day] with its to-dos ([todos] may hold other days too; they keep their order). */
fun Timetable.agenda(day: LocalDate, todos: List<Todo>): DayAgenda {
    val resolved = todos.filter { it.day == day }.map(::resolve)
    val anytime = resolved.filter { it.due == null }

    data class Entry(val time: TimeOfDay, val rank: Int, val item: DayAgenda.Item)

    val entries = mutableListOf<Entry>()
    resolved.forEachIndexed { index, todo ->
        if (todo.due != null && todo.session == null) entries += Entry(todo.due.time, index, DayAgenda.Item.TodoItem(todo))
    }
    sessions(day).forEachIndexed { index, session ->
        val after = resolved.filter { it.session?.id == session.id }
        entries += Entry(session.start, resolved.size + index, DayAgenda.Item.Session(session, after))
    }
    val items = entries.sortedWith(compareBy<Entry>({ it.time }, { it.rank })).map { it.item }
    return DayAgenda(day, anytime, items)
}

/** The unfinished to-dos due after [after], earliest first, up to [limit]: the ones to notify. */
fun Timetable.upcomingTodoAlerts(after: Moment, todos: List<Todo>, limit: Int = Timetable.MAX_TODO_ALERTS): List<ResolvedTodo> =
    todos.filter { !it.isDone }.map(::resolve)
        .filter { (it.due ?: after) > after }
        .sortedBy { it.due!! }
        .take(maxOf(limit, 0))

/** The unfinished to-dos due when [session] ends, in their order. */
fun Timetable.todosAfter(session: ClassSession, todos: List<Todo>): List<Todo> = todos.filter { todo ->
    val timing = todo.timing
    !todo.isDone && todo.day == session.day && timing is TodoTiming.AfterClass && timing.courseId == session.course.id
}

/**
 * Swift's `Codable` form of the enum: `{"anytime":{}}`, `{"at":{"_0":"12:00"}}`, `{"afterClass":{"courseID":"…"}}`.
 */
object TodoTimingSerializer : KSerializer<TodoTiming> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: TodoTiming) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("TodoTiming is only written as JSON")
        val element = when (value) {
            TodoTiming.Anytime -> JsonObject(mapOf("anytime" to JsonObject(emptyMap())))
            is TodoTiming.At -> JsonObject(mapOf("at" to JsonObject(mapOf("_0" to JsonPrimitive(value.time.toString())))))
            is TodoTiming.AfterClass ->
                JsonObject(mapOf("afterClass" to JsonObject(mapOf("courseID" to JsonPrimitive(value.courseId.uuidString)))))
        }
        json.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): TodoTiming {
        val json = decoder as? JsonDecoder ?: throw SerializationException("TodoTiming is only read from JSON")
        val element = json.decodeJsonElement().jsonObject
        val (case, payload) = element.entries.singleOrNull() ?: throw SerializationException("Invalid to-do timing $element")
        return when (case) {
            "anytime" -> TodoTiming.Anytime
            "at" -> {
                val text = payload.jsonObject["_0"]?.jsonPrimitive?.content
                TodoTiming.At(text?.let(TimeOfDay::parse) ?: throw SerializationException("Invalid time in $element"))
            }
            "afterClass" -> {
                val text = payload.jsonObject["courseID"]?.jsonPrimitive?.content ?: throw SerializationException("No course in $element")
                TodoTiming.AfterClass(UUID.fromString(text))
            }
            else -> throw SerializationException("Unknown to-do timing \"$case\"")
        }
    }
}
