package io.github.hilbert0619.aikit

import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.TodoTiming
import io.github.hilbert0619.vocabkit.trimmingWhitespaces
import io.github.hilbert0619.vocabkit.trimmingWhitespacesAndNewlines
import kotlinx.serialization.json.JsonObject

/** A to-do the AI read from a note, for the user to check in the editor before saving. */
data class TodoDraft(
    val title: String,
    val day: LocalDate,
    val timing: TodoTiming,
    /** The class the note named when no course has that name (the timing is then "any time" or the clock time). */
    val unknownCourse: String? = null,
) {
    val todo: Todo get() = Todo(title = title, day = day, timing = timing)

    companion object {
        const val MAX_TITLE_LENGTH = 100

        /** Reads the AI's JSON answer (see [AIPrompts.todoDraft]), tolerating code fences and text around it. Throws [AIError.Unreadable]. */
        fun parse(answer: String, today: LocalDate, courses: List<Course>): TodoDraft {
            val start = answer.indexOf('{')
            val end = answer.lastIndexOf('}')
            if (start < 0 || end < 0 || start >= end) throw AIError.Unreadable
            val raw = parseJson(answer.substring(start, end + 1)) as? JsonObject ?: throw AIError.Unreadable
            val read = AIStreamDecoder.Reader()
            val rawTitle = read.string(raw, "title")
            val rawDate = read.string(raw, "date")
            val rawTime = read.string(raw, "time")
            val rawCourse = read.string(raw, "afterCourse")

            val title = (rawTitle ?: "").trimmingWhitespacesAndNewlines().prefixCharacters(MAX_TITLE_LENGTH)
            if (title.isEmpty()) throw AIError.Unreadable
            val day = rawDate?.let { LocalDate.parse(it.trimmingWhitespaces().take(10)) } ?: today
            val time = rawTime?.let { TimeOfDay.parse(it.trimmingWhitespaces()) }

            var draft = TodoDraft(title, day, time?.let { TodoTiming.At(it) } ?: TodoTiming.Anytime)
            val name = rawCourse?.trimmingWhitespacesAndNewlines()
            if (!name.isNullOrEmpty()) {
                val course = match(name, courses)
                draft = if (course != null) draft.copy(timing = TodoTiming.AfterClass(course.id)) else draft.copy(unknownCourse = name)
            }
            return draft
        }

        /**
         * The course with this name; failing that, the one whose name contains it or is contained in it; failing that,
         * an abbreviation ("高数" → "高等数学"). Among several, the shortest name (the closest fit) wins.
         */
        fun match(name: String, courses: List<Course>): Course? {
            val key = name.lowercase()
            val named = courses.filter { it.name.isNotEmpty() }
            named.firstOrNull { it.name.lowercase() == key }?.let { return it }
            val partial = named.filter { it.name.lowercase().contains(key) || key.contains(it.name.lowercase()) }
            partial.minByOrNull { it.name.characterCount }?.let { return it }
            if (key.characterCount < 2) return null
            return named.filter { isAbbreviation(key, it.name.lowercase()) }.minByOrNull { it.name.characterCount }
        }

        /** [short]'s characters appear in [long] in order, starting with its first character. */
        private fun isAbbreviation(short: String, long: String): Boolean {
            if (short.isEmpty() || long.isEmpty() || short[0] != long[0]) return false
            var rest = 0
            for (character in short) {
                val index = long.indexOf(character, rest)
                if (index < 0) return false
                rest = index + 1
            }
            return true
        }
    }
}
