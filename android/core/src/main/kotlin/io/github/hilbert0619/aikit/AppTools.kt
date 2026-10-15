package io.github.hilbert0619.aikit

import io.github.hilbert0619.coursekit.CourseCSV
import io.github.hilbert0619.coursekit.CourseImporter
import io.github.hilbert0619.coursekit.FocusSettings
import io.github.hilbert0619.coursekit.ImportProblem
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.SemesterStatus
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.TodoTiming
import io.github.hilbert0619.coursekit.planningImport
import io.github.hilbert0619.coursekit.resolve
import io.github.hilbert0619.coursekit.uuidString
import io.github.hilbert0619.vocabkit.Card
import io.github.hilbert0619.vocabkit.CardState
import io.github.hilbert0619.vocabkit.DailyPlan
import io.github.hilbert0619.vocabkit.DayStats
import io.github.hilbert0619.vocabkit.ExamCountdown
import io.github.hilbert0619.vocabkit.Headword
import io.github.hilbert0619.vocabkit.Rating
import io.github.hilbert0619.vocabkit.ReviewEvent
import io.github.hilbert0619.vocabkit.Scheduler
import io.github.hilbert0619.vocabkit.VocabStats
import io.github.hilbert0619.vocabkit.WordEntry
import java.time.Instant
import java.util.UUID

/**
 * The app's tools for the Assistant (docs/ai-agent-spec.md §3): their definitions, what the reading tools answer, and
 * the changes the others propose. Everything here is plain logic over a snapshot of the app's data ([AgentWorld]); the
 * app runs it, shows the proposals and applies what the user confirms. Texts are for the model, in English; the app
 * shows its own words.
 */
object AppTools {
    // Definitions

    val listTodos = AITool(
        "list_todos",
        "List the user's to-dos (reminders) between two dates with their IDs, timing and whether they're done. " +
            "Defaults: from today, for 7 days.",
        listOf(
            AIToolParameter("from", AIToolParameter.Kind.Date, "First day"),
            AIToolParameter("to", AIToolParameter.Kind.Date, "Last day, at most 31 days after from"),
        ),
        AITool.Effect.READS,
    )

    val addTodo = AITool(
        "add_todo",
        "Propose a new to-do. A to-do with a time or after a class is a reminder: the app notifies the user then " +
            "(after a class: when that class ends). The user confirms it on a card before it is added.",
        listOf(
            AIToolParameter(
                "title", AIToolParameter.Kind.Text(TodoDraft.MAX_TITLE_LENGTH),
                "What to do, short, in the user's language, without the date or time", isRequired = true,
            ),
            AIToolParameter("date", AIToolParameter.Kind.Date, "The day", isRequired = true),
            AIToolParameter("time", AIToolParameter.Kind.Time, "A clock time; leave out for any time of day or with afterCourse"),
            AIToolParameter(
                "afterCourse", AIToolParameter.Kind.Text(60),
                "A course name from the timetable: due when its class on that date ends. Not with time",
            ),
        ),
        AITool.Effect.CHANGES,
    )

    val updateTodo = AITool(
        "update_todo",
        "Propose changes to one to-do: its title, date, timing (time, afterCourse, or anyTime) or done (tick it " +
            "off or reopen it). Get the ID from list_todos. The user confirms on a card.",
        listOf(
            AIToolParameter("id", AIToolParameter.Kind.Text(40), "The to-do's ID from list_todos", isRequired = true),
            AIToolParameter("title", AIToolParameter.Kind.Text(TodoDraft.MAX_TITLE_LENGTH), "New title"),
            AIToolParameter("date", AIToolParameter.Kind.Date, "New day"),
            AIToolParameter("time", AIToolParameter.Kind.Time, "New clock time"),
            AIToolParameter("afterCourse", AIToolParameter.Kind.Text(60), "New class to follow (a course name)"),
            AIToolParameter("anyTime", AIToolParameter.Kind.Boolean, "true: no particular time any more"),
            AIToolParameter("done", AIToolParameter.Kind.Boolean, "true: tick it off; false: not done yet"),
        ),
        AITool.Effect.CHANGES,
    )

    val deleteTodo = AITool(
        "delete_todo",
        "Propose deleting one to-do. Get the ID from list_todos. The user confirms on a card.",
        listOf(AIToolParameter("id", AIToolParameter.Kind.Text(40), "The to-do's ID from list_todos", isRequired = true)),
        AITool.Effect.CHANGES,
    )

    val getSchedule = AITool(
        "get_schedule",
        "The user's classes between two dates with times and rooms, and the teaching week. Defaults: today and the " +
            "next 6 days.",
        listOf(
            AIToolParameter("from", AIToolParameter.Kind.Date, "First day"),
            AIToolParameter("to", AIToolParameter.Kind.Date, "Last day, at most 14 days after from"),
        ),
        AITool.Effect.READS,
    )

    val listCourses = AITool(
        "list_courses",
        "Every course in the timetable as CSV in the import format (name,teacher,room,weekday,periods,weeks), the " +
            "class period times and the term.",
        effect = AITool.Effect.READS,
    )

    val addCourses = AITool(
        "add_courses",
        "Propose adding courses to the timetable, written as CSV in the import format. The user checks them in the " +
            "import preview (clashes, duplicates, lines that can't be read) before anything is saved. Header row: " +
            "name,teacher,room,weekday,periods,weeks (Chinese headers work too). weekday: 1-7 (1 = Monday) or 周一…周日; " +
            "periods: like 1-2; weeks: like 1-16, 1-15 odd, 2-16 even, or \"1-8,10-16\" (quote a field that has commas). " +
            "One line per weekly meeting: a course that meets twice a week has two lines.",
        listOf(AIToolParameter("csv", AIToolParameter.Kind.Text(20000), "The CSV text with its header row", isRequired = true)),
        AITool.Effect.CHANGES,
    )

    val getVocabOverview = AITool(
        "get_vocab_overview",
        "The learner's vocabulary progress in the Words tab: today's plan, streak, words learned and mastered of " +
            "the book, words due, exam countdown and the pace needed, recent accuracy and study time.",
        effect = AITool.Effect.READS,
    )

    val getVocabHistory = AITool(
        "get_vocab_history",
        "Vocabulary study day by day for the last days: answers, new words, accuracy and minutes.",
        listOf(AIToolParameter("days", AIToolParameter.Kind.Integer(1..90), "How many days, counting today (default 14)")),
        AITool.Effect.READS,
    )

    val getHardWords = AITool(
        "get_hard_words",
        "The words the learner forgets most, with their meanings, times forgotten, last answer and today's chance " +
            "of recalling them.",
        listOf(AIToolParameter("limit", AIToolParameter.Kind.Integer(1..50), "How many words (default 20)")),
        AITool.Effect.READS,
    )

    val getWordProgress = AITool(
        "get_word_progress",
        "One word's learning record: state, answers, times forgotten, next review, chance of recall, last answers.",
        listOf(AIToolParameter("word", AIToolParameter.Kind.Text(60), "The English word", isRequired = true)),
        AITool.Effect.READS,
    )

    val startFocus = AITool(
        "start_focus",
        "Propose starting a focus session (the Focus tab's Pomodoro timer) now. The user confirms on a card.",
        listOf(
            AIToolParameter("minutes", AIToolParameter.Kind.Integer(FocusSettings.TOTAL_RANGE), "Total length including breaks", isRequired = true),
            AIToolParameter("goal", AIToolParameter.Kind.Text(60), "What the user will work on, in their language"),
            AIToolParameter("breaks", AIToolParameter.Kind.Boolean, "false: one straight countdown; leave out to keep the user's setting"),
        ),
        AITool.Effect.CHANGES,
    )

    val getFocusHistory = AITool(
        "get_focus_history",
        "Focus sessions of the last days: minutes per day, each session's goal, start, focused minutes and whether " +
            "it was finished, and the session running now, if any.",
        listOf(AIToolParameter("days", AIToolParameter.Kind.Integer(1..31), "How many days, counting today (default 7)")),
        AITool.Effect.READS,
    )

    /** Every tool, in the order the model sees them. */
    val all: List<AITool> = listOf(
        listTodos, addTodo, updateTodo, deleteTodo, getSchedule, listCourses, addCourses,
        getVocabOverview, getVocabHistory, getHardWords, getWordProgress, startFocus, getFocusHistory,
    )

    /** The longest result a reading tool returns; longer lists end with "…and N more". */
    const val MAX_RESULT_CHARACTERS = 4000

    private val WEEKDAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    internal fun dayName(day: LocalDate): String = "${WEEKDAY_NAMES[day.weekday.rawValue - 1]} $day"

    /** [lines] under [header], cut to [MAX_RESULT_CHARACTERS] with a count of what was left out. */
    internal fun list(header: String, lines: List<String>, empty: String): String {
        if (lines.isEmpty()) return empty
        val text = StringBuilder(header)
        var count = header.characterCount
        for ((index, line) in lines.withIndex()) {
            val more = "\n…and ${lines.size - index} more"
            val length = line.characterCount
            if (count + length + 1 + more.characterCount > MAX_RESULT_CHARACTERS) return text.toString() + more
            text.append('\n').append(line)
            count += length + 1
        }
        return text.toString()
    }

    // Running calls

    /** The answer of a reading tool, or an error the model can act on. */
    fun read(call: AIPendingCall, world: AgentWorld): AIToolResult {
        val arguments = call.arguments
        fun result(outcome: Result<String>): AIToolResult = outcome.fold(
            onSuccess = { AIToolResult(call.id, it) },
            onFailure = { AIToolResult(call.id, it.message.orEmpty(), isError = true) },
        )
        return when (call.tool.name) {
            listTodos.name -> result(todoList(arguments, world))
            getSchedule.name -> result(schedule(arguments, world))
            listCourses.name -> result(Result.success(courseList(world.timetable, world.today)))
            getFocusHistory.name -> result(Result.success(focusHistory(world.focus, world.today, arguments.int("days") ?: 7)))
            getVocabOverview.name, getVocabHistory.name, getHardWords.name, getWordProgress.name -> {
                val vocab = world.vocab ?: return result(Result.failure(AIToolError(NOT_SIGNED_IN)))
                when (call.tool.name) {
                    getVocabOverview.name -> result(Result.success(vocabOverview(vocab)))
                    getVocabHistory.name -> result(Result.success(vocabHistory(vocab, arguments.int("days") ?: 14)))
                    getHardWords.name -> result(Result.success(hardWords(vocab, arguments.int("limit") ?: 20)))
                    else -> result(Result.success(wordProgress(vocab, arguments.text("word") ?: "")))
                }
            }
            else -> result(Result.failure(AIToolError("${call.tool.name} isn't a reading tool.")))
        }
    }

    const val NOT_SIGNED_IN = "The user isn't signed in to Words (the vocabulary account), so there are no learning numbers. " +
        "They can sign in on the Words tab."

    /** The change a changing tool proposes, or why it can't be proposed (for the model to fix or explain). */
    fun proposal(call: AIPendingCall, world: AgentWorld): Result<AgentChange> {
        val arguments = call.arguments
        return when (call.tool.name) {
            addTodo.name -> newTodo(arguments, world).map { AgentChange.AddTodo(it) }
            updateTodo.name -> changedTodo(arguments, world).map { AgentChange.UpdateTodo(it.first, it.second) }
            deleteTodo.name -> todo(arguments.text("id") ?: "", world.todos).map { AgentChange.DeleteTodo(it) }
            addCourses.name -> courses(arguments.text("csv") ?: "", world.timetable)
            startFocus.name -> focusSession(arguments, world)
            else -> Result.failure(AIToolError("${call.tool.name} doesn't change anything."))
        }
    }

    /**
     * What the model hears after the user confirmed [change] ([detail] adds what the app knows only then, such as the
     * number of courses imported).
     */
    fun confirmedResult(change: AgentChange, detail: String? = null): String {
        val text = when (change) {
            is AgentChange.AddTodo -> "Added the to-do (id ${shortID(change.todo.id)})."
            is AgentChange.UpdateTodo -> "Saved the changes."
            is AgentChange.DeleteTodo -> "Deleted the to-do."
            is AgentChange.AddCourses -> "The user imported the courses."
            is AgentChange.StartFocus -> "The focus session started."
        }
        return listOfNotNull(text, detail).joinToString(" ")
    }

    // To-dos

    /** A to-do's ID as the model sees it: the first 8 hex digits of its UUID. */
    fun shortID(id: UUID): String = id.uuidString.take(8).lowercase()

    /** The to-do with this ID (short or full, any case, with or without `#`). */
    internal fun todo(text: String, todos: List<Todo>): Result<Todo> {
        val key = text.trim(::isWhitespaceInSet).lowercase().replace("#", "").replace("id ", "")
        val matches = todos.filter { it.id.uuidString.lowercase().startsWith(key) }
        if (key.characterCount < 4 || matches.size != 1) {
            return Result.failure(AIToolError("No to-do has the id \"$text\". Call list_todos for the current ids."))
        }
        return Result.success(matches[0])
    }

    internal fun timingText(todo: Todo, timetable: Timetable): String = when (val timing = todo.timing) {
        TodoTiming.Anytime -> "any time"
        is TodoTiming.At -> "at ${timing.time}"
        is TodoTiming.AfterClass -> {
            val name = timetable.courses.firstOrNull { it.id == timing.courseId }?.name ?: "a deleted course"
            val session = timetable.resolve(todo).session
            if (session != null) "after $name (ends ${session.end})" else "after $name (no class that day)"
        }
    }

    internal fun todoLine(todo: Todo, timetable: Timetable): String {
        var line = "id ${shortID(todo.id)} · ${dayName(todo.day)} · ${timingText(todo, timetable)} · ${todo.title}"
        if (todo.isDone) line += " · done"
        return line
    }

    internal fun range(arguments: AIToolArguments, today: LocalDate, defaultDays: Int, maxDays: Int): Result<Pair<LocalDate, LocalDate>> {
        val from = arguments.date("from") ?: today
        val to = arguments.date("to") ?: from.adding(defaultDays - 1)
        if (from > to) return Result.failure(AIToolError("\"to\" is before \"from\"."))
        if (from.daysTo(to) >= maxDays) return Result.failure(AIToolError("Ask for at most $maxDays days at a time."))
        return Result.success(from to to)
    }

    internal fun todoList(arguments: AIToolArguments, world: AgentWorld): Result<String> =
        range(arguments, world.today, 7, 31).map { (from, to) ->
            // Earliest first; within a day, by time (anytime and unknown classes last), then as created.
            val inRange = world.todos.withIndex().filter { it.value.day >= from && it.value.day <= to }
            val sorted = inRange.sortedWith { a, b ->
                val dueA = world.timetable.resolve(a.value).due
                val dueB = world.timetable.resolve(b.value).due
                when {
                    a.value.day != b.value.day -> a.value.day.compareTo(b.value.day)
                    dueA != null && dueB != null && dueA != dueB -> dueA.compareTo(dueB)
                    dueA != null && dueB == null -> -1
                    dueA == null && dueB != null -> 1
                    else -> a.index.compareTo(b.index)
                }
            }.map { it.value }
            val earlierOpen = world.todos.count { it.day < from && !it.isDone && it.day >= from.adding(-60) }
            var text = list(
                "To-dos from ${dayName(from)} to ${dayName(to)} (${sorted.size}):",
                sorted.map { todoLine(it, world.timetable) },
                empty = "No to-dos from ${dayName(from)} to ${dayName(to)}.",
            )
            if (earlierOpen > 0) {
                text += "\n$earlierOpen unfinished to-do${if (earlierOpen == 1) "" else "s"} on earlier days (ask with an earlier \"from\")."
            }
            text
        }

    /**
     * Checks a course name and that its class meets on [day]. A course that meets twice a week is two courses with one
     * name; the one meeting that day is picked.
     */
    internal fun classTiming(name: String, day: LocalDate, timetable: Timetable): Result<TodoTiming> {
        val match = TodoDraft.match(name, timetable.courses)
        if (match == null) {
            val names = timetable.courses.map { it.name }.toSortedSet().toList()
            return Result.failure(
                AIToolError(
                    if (names.isEmpty()) {
                        "The timetable has no courses, so a to-do can't follow a class. Use a time instead."
                    } else {
                        "No course is called \"$name\". Courses: ${names.joinToString(", ")}."
                    },
                ),
            )
        }
        val ids = timetable.courses.filter { it.name.lowercase() == match.name.lowercase() }.map { it.id }.toSet()
        val session = timetable.sessions(day).firstOrNull { it.course.id in ids }
        if (session == null) {
            val near = (-7..7).map { day.adding(it) }.filter { date -> timetable.sessions(date).any { it.course.id in ids } }.map(::dayName)
            val days = if (near.isEmpty()) "it has no classes within a week of that day" else "its classes near then: ${near.joinToString(", ")}"
            return Result.failure(AIToolError("${match.name} has no class on ${dayName(day)}; $days. Pick one of those days or a time."))
        }
        return Result.success(TodoTiming.AfterClass(session.course.id))
    }

    internal fun newTodo(arguments: AIToolArguments, world: AgentWorld): Result<Todo> {
        val title = arguments.text("title")
        val day = arguments.date("date")
        if (title == null || day == null) return Result.failure(AIToolError("title and date are needed."))
        if (day < world.today) {
            return Result.failure(AIToolError("${dayName(day)} is before today (${dayName(world.today)}); to-dos are for today or later."))
        }
        if (arguments.has("time") && arguments.has("afterCourse")) {
            return Result.failure(AIToolError("Give either time or afterCourse, not both."))
        }
        var timing: TodoTiming = TodoTiming.Anytime
        val time = arguments.time("time")
        val name = arguments.text("afterCourse")
        if (time != null) {
            timing = TodoTiming.At(time)
        } else if (name != null) {
            timing = classTiming(name, day, world.timetable).getOrElse { return Result.failure(it) }
        }
        return Result.success(Todo(title = title, day = day, timing = timing))
    }

    internal fun changedTodo(arguments: AIToolArguments, world: AgentWorld): Result<Pair<Todo, Todo>> {
        val found = todo(arguments.text("id") ?: "", world.todos).getOrElse { return Result.failure(it) }
        val timings = listOf("time", "afterCourse", "anyTime").filter(arguments::has)
        if (timings.size > 1) return Result.failure(AIToolError("Give only one of time, afterCourse and anyTime."))
        var after = found
        arguments.text("title")?.let { after = after.copy(title = it) }
        arguments.date("date")?.let { after = after.copy(day = it) }
        arguments.bool("done")?.let { after = after.copy(isDone = it) }
        val time = arguments.time("time")
        val name = arguments.text("afterCourse")
        val timing = after.timing
        if (time != null) {
            after = after.copy(timing = TodoTiming.At(time))
        } else if (arguments.bool("anyTime") == true) {
            after = after.copy(timing = TodoTiming.Anytime)
        } else if (name != null) {
            after = after.copy(timing = classTiming(name, after.day, world.timetable).getOrElse { return Result.failure(it) })
        } else if (timing is TodoTiming.AfterClass && after.day != found.day) {
            // Moved to another day: follow the same course's class that day (maybe its other weekly meeting).
            val courseName = world.timetable.courses.firstOrNull { it.id == timing.courseId }?.name ?: ""
            val moved = classTiming(courseName, after.day, world.timetable).getOrNull()
                ?: return Result.failure(
                    AIToolError(
                        "The to-do follows ${courseName.ifEmpty { "a class" }}, which has no class on ${dayName(after.day)}. " +
                            "Give a time, anyTime, or another afterCourse too.",
                    ),
                )
            after = after.copy(timing = moved)
        }
        if (after == found) return Result.failure(AIToolError("That doesn't change anything."))
        return Result.success(found to after)
    }

    // Timetable

    internal fun termLine(timetable: Timetable, today: LocalDate): String {
        val semester = timetable.semester
        val term = "The term starts ${dayName(semester.startDate)} and has ${semester.totalWeeks} weeks"
        return when (val status = semester.status(today)) {
            is SemesterStatus.NotStarted -> "$term; it begins in ${status.daysUntilStart} days."
            is SemesterStatus.InProgress -> "$term; today is in week ${status.week}."
            SemesterStatus.Finished -> "$term; it has ended."
        }
    }

    internal fun schedule(arguments: AIToolArguments, world: AgentWorld): Result<String> =
        range(arguments, world.today, 7, 14).map { (from, to) ->
            val lines = mutableListOf<String>()
            var day = from
            while (day <= to) {
                for (session in world.timetable.sessions(day)) {
                    var line = "${dayName(day)} ${session.start}–${session.end} ${session.course.name}"
                    val details = listOf(session.course.room, session.course.teacher).filter { it.isNotEmpty() }
                    if (details.isNotEmpty()) line += " (${details.joinToString(", ")})"
                    line += " · week ${session.week}"
                    lines += line
                }
                day = day.adding(1)
            }
            val header = "${termLine(world.timetable, world.today)}\nClasses from ${dayName(from)} to ${dayName(to)}:"
            list(header, lines, empty = "$header\nNone.")
        }

    internal fun courseList(timetable: Timetable, today: LocalDate): String {
        val periods = timetable.periods.periods.mapIndexed { index, period -> "${index + 1} ${period.start}–${period.end}" }
        val header = "${termLine(timetable, today)}\nClass periods: ${periods.joinToString(", ")}."
        if (timetable.courses.isEmpty()) return "$header\nThe timetable has no courses yet."
        val csv = CourseCSV.export(timetable.courses).replace("\r\n", "\n").trim('\n', '\r', '\u000B', '\u000C', '\u0085', ' ', ' ')
        val rows = csv.split('\n')
        return list("$header\nCourses (${rows.size - 1}):\n${rows[0]}", rows.drop(1), empty = header)
    }

    internal fun courses(csv: String, timetable: Timetable): Result<AgentChange> {
        val parsed = CourseImporter(timetable).parse(csv)
        val plan = timetable.planningImport(parsed.courses)
        val problems = parsed.problems.take(8).map(::problemText)
        if (plan.added.isEmpty()) {
            val message = when {
                parsed.courses.size == 1 -> "That course is already in the timetable; nothing to add."
                parsed.courses.isNotEmpty() -> "All ${parsed.courses.size} courses are already in the timetable; nothing to add."
                else -> {
                    var text = "No course could be read from the CSV."
                    if (problems.isNotEmpty()) text += " Problems:\n" + problems.joinToString("\n")
                    text + "\nUse the header name,teacher,room,weekday,periods,weeks and one line per weekly meeting."
                }
            }
            return Result.failure(AIToolError(message))
        }
        return Result.success(AgentChange.AddCourses(csv, plan.added.size, plan.duplicates.size, parsed.problems.size))
    }

    /** What the importer couldn't read, for the model. */
    fun problemText(problem: ImportProblem): String {
        val detail = when (val kind = problem.kind) {
            is ImportProblem.Kind.MissingColumns -> "the header row is missing ${kind.columns.joinToString(", ") { it.rawValue }}"
            is ImportProblem.Kind.MissingValue -> "missing ${kind.column.rawValue}"
            is ImportProblem.Kind.InvalidValue -> "can't read the ${kind.column.rawValue} \"${kind.text}\""
            is ImportProblem.Kind.PeriodsOutOfRange -> "periods \"${kind.text}\" are outside 1–${kind.periodsPerDay}"
        }
        return "Line ${problem.line}: $detail"
    }

    /** The summary the model hears after the user's import. */
    fun importedResult(added: Int, skipped: Int): String {
        var text = "The user imported $added course${if (added == 1) "" else "s"}"
        if (skipped > 0) text += "; $skipped already in the timetable were skipped"
        return "$text."
    }

    // Focus

    internal fun focusSession(arguments: AIToolArguments, world: AgentWorld): Result<AgentChange> {
        world.focus.running?.let { running ->
            val goal = if (running.goal.isEmpty()) "" else " (${running.goal})"
            return Result.failure(
                AIToolError(
                    "A focus session$goal is already running until ${TimeOfDay.from(running.endsAt)}. The user can stop it on " +
                        "the Focus tab first.",
                ),
            )
        }
        var settings = world.focus.settings
        arguments.int("minutes")?.let { settings = settings.copy(totalMinutes = it) }
        arguments.bool("breaks")?.let { settings = settings.copy(usesBreaks = it) }
        return Result.success(AgentChange.StartFocus(arguments.text("goal") ?: "", settings))
    }

    internal fun minutes(seconds: Int): Int = (seconds + 30) / 60

    internal fun focusHistory(focus: FocusSnapshot, today: LocalDate, days: Int): String {
        val first = today.adding(-(days - 1))
        val sessions = focus.sessions.filter { LocalDate.from(it.startedAt) >= first }.sortedBy { it.startedAt }
        val lines = mutableListOf<String>()
        focus.running?.let { running ->
            val goal = if (running.goal.isEmpty()) "" else " on \"${running.goal}\""
            lines += "Running now$goal: started ${TimeOfDay.from(running.startedAt)}, ends ${TimeOfDay.from(running.endsAt)}" +
                "${if (running.isPaused) ", paused" else ""}."
        }
        val s = focus.settings
        lines += "Setup: ${s.totalMinutes} minutes" +
            "${if (s.usesBreaks) ", ${s.focusMinutes}-minute blocks with ${s.shortBreakMinutes}-minute breaks" else ", no breaks"}."
        var day = first
        var total = 0
        while (day <= today) {
            val daySessions = sessions.filter { LocalDate.from(it.startedAt) == day }
            val focused = daySessions.sumOf { it.focusedSeconds }
            total += focused
            if (daySessions.isEmpty()) {
                lines += "${dayName(day)}: no focus sessions"
            } else {
                val items = daySessions.map { session ->
                    val goal = if (session.goal.isEmpty()) "no goal" else "\"${session.goal}\""
                    "${TimeOfDay.from(session.startedAt)} $goal ${minutes(session.focusedSeconds)} min${if (session.finished) "" else " (stopped early)"}"
                }
                lines += "${dayName(day)}: ${minutes(focused)} min focused — ${items.joinToString("; ")}"
            }
            day = day.adding(1)
        }
        return list("Focus, last $days day${if (days == 1) "" else "s"} (${minutes(total)} min in all):", lines, empty = "")
    }

    // Vocabulary

    /** `1 day`, `2 days`. */
    internal fun count(number: Int, word: String): String = "$number $word${if (number == 1) "" else "s"}"

    internal fun percent(value: Double?): String = value?.let { "${(it * 100).roundSwift()}%" } ?: "—"

    /** Swift `rounded()`: halves away from zero. */
    private fun Double.roundSwift(): Long = if (this >= 0) kotlin.math.floor(this + 0.5).toLong() else -kotlin.math.floor(-this + 0.5).toLong()

    internal fun meaning(entry: WordEntry?): String {
        if (entry == null) return ""
        val line = entry.translation.split('\n').firstOrNull { it.isNotEmpty() } ?: ""
        return if (line.characterCount > 40) line.prefixCharacters(40) + "…" else line
    }

    internal fun vocabOverview(vocab: VocabSnapshot): String {
        val plan = vocab.plan
        val progress = VocabStats.progress(vocab.book, vocab.cards, vocab.today)
        val streak = VocabStats.streak(vocab.events, vocab.today)
        val week = VocabStats.series(vocab.events, vocab.today, 7)
        val month = VocabStats.series(vocab.events, vocab.today, 30)
        fun summary(days: List<DayStats>): String {
            val answers = days.sumOf { it.answers }
            val correct = days.sumOf { it.correct }
            val minutes = days.sumOf { it.durationMs } / 60000
            val studied = days.count { it.studied }
            return "${count(studied, "day")} studied, ${count(answers, "answer")}, " +
                "${percent(if (answers == 0) null else correct.toDouble() / answers)} right, $minutes min"
        }
        val lines = mutableListOf(
            "Book: ${vocab.bookName}, ${progress.total} words. Learned ${progress.learned}, mastered ${progress.mastered} " +
                "(stable for 30+ days), not seen yet ${plan.unseenInBook}.",
            "Today: new words ${plan.newDone} of ${plan.newTarget}, reviews done ${plan.reviewsDone}, reviews left " +
                "${plan.reviewsRemaining}${plan.estimatedMinutes?.let { ", about $it min of work left" } ?: ""}.",
            "Streak: ${streak.days} day${if (streak.days == 1) "" else "s"}${if (streak.includesToday) " (including today)" else " (not studied yet today)"}.",
            "Last 7 days: ${summary(week)}.",
            "Last 30 days: ${summary(month)}.",
            "Settings: ${vocab.newWordsPerDay} new words a day, target recall ${percent(vocab.desiredRetention)}.",
            "Hard words (forgotten ${VocabStats.HARD_WORD_LAPSES}+ times): ${VocabStats.hardWords(vocab.cards.values).size}.",
        )
        val exam = vocab.exam
        lines += if (exam != null) {
            "Exam on ${dayName(exam.examDate)}: ${count(exam.daysLeft, "day")} left, ${count(exam.wordsLeft, "word")} not seen yet, " +
                "needs ${count(exam.newWordsPerDayNeeded, "new word")} a day to see them all (now ${vocab.newWordsPerDay})."
        } else {
            "No exam date set (Words → Settings)."
        }
        return lines.joinToString("\n")
    }

    internal fun vocabHistory(vocab: VocabSnapshot, days: Int): String {
        val series = VocabStats.series(vocab.events, vocab.today, days)
        val lines = series.reversed().map { stats ->
            val date = dayName(LocalDate(daysSinceEpoch = stats.day))
            if (!stats.studied) {
                "$date: no study"
            } else {
                "$date: ${count(stats.answers, "answer")}, ${count(stats.newWords, "new word")}, ${stats.reviewedWords} reviewed, " +
                    "${percent(stats.accuracy)} right, ${stats.durationMs / 60000} min"
            }
        }
        return list("Vocabulary study, last $days day${if (days == 1) "" else "s"}, newest first:", lines, empty = "")
    }

    internal fun lastAnswers(vocab: VocabSnapshot): Map<String, ReviewEvent> {
        val last = mutableMapOf<String, ReviewEvent>()
        for (event in vocab.events) {
            val known = last[event.headword]
            if (known == null || known.reviewedAt <= event.reviewedAt) last[event.headword] = event
        }
        return last
    }

    internal fun ratingName(rating: Rating): String = when (rating) {
        Rating.AGAIN -> "Again (forgot)"
        Rating.HARD -> "Hard"
        Rating.GOOD -> "Good"
        Rating.EASY -> "Easy"
    }

    internal fun hardWords(vocab: VocabSnapshot, limit: Int): String {
        val cards = VocabStats.hardWords(vocab.cards.values).take(limit)
        val last = lastAnswers(vocab)
        val lines = cards.map { card ->
            val parts = mutableListOf(card.headword)
            val meaning = meaning(vocab.entries[card.headword])
            if (meaning.isNotEmpty()) parts += meaning
            parts += "forgotten ${count(card.lapses, "time")}"
            last[card.headword]?.let { event ->
                parts += "last answer ${ratingName(event.rating)} on ${LocalDate(daysSinceEpoch = event.studyDay)}"
            }
            parts += "recall chance today ${percent(vocab.scheduler.retrievability(card, vocab.today))}"
            parts.joinToString(" · ")
        }
        return list(
            "Hardest words (${cards.size}):", lines,
            empty = "No hard words yet: no word has been forgotten ${VocabStats.HARD_WORD_LAPSES} or more times after learning it.",
        )
    }

    internal fun wordProgress(vocab: VocabSnapshot, word: String): String {
        val key = Headword.key(word)
        val entry = vocab.entries[key]
        val inBook = key in vocab.book
        val card = vocab.cards[key]
        if (card == null || card.state == CardState.NEW) {
            val known = if (entry == null) "isn't in the dictionary on this device" else if (inBook) "is in the book" else "isn't in the current book"
            return "\"$word\" $known and hasn't been studied yet."
        }
        val events = vocab.events.filter { it.headword == key }.sortedBy { it.reviewedAt }
        val state = when (card.state) {
            CardState.NEW -> "new"
            CardState.LEARNING -> "being learned"
            CardState.REVIEW -> "learned"
            CardState.RELEARNING -> "being relearned after a lapse"
        }
        val lines = mutableListOf(
            "${card.headword}${entry?.let { " — ${meaning(it)}" } ?: ""}${if (inBook) "" else " (not in the current book)"}",
            "State: $state. Answers: ${events.size}, forgotten after learning: ${count(card.lapses, "time")}.",
        )
        card.firstReviewDay?.let { lines += "First studied: ${LocalDate(daysSinceEpoch = it)}." }
        card.dueDay(vocab.today)?.let { due ->
            lines += if (due <= vocab.today) "Due for review today." else "Next review: ${LocalDate(daysSinceEpoch = due)}."
        }
        lines += "Recall chance today: ${percent(vocab.scheduler.retrievability(card, vocab.today))}."
        card.memory?.stability?.let { lines += "Memory stability: ${count(it.roundSwift().toInt(), "day")}." }
        val recent = events.takeLast(5).map { "${LocalDate(daysSinceEpoch = it.studyDay)} ${ratingName(it.rating)}" }
        if (recent.isNotEmpty()) lines += "Last answers: ${recent.joinToString(", ")}."
        return lines.joinToString("\n")
    }
}

/** `CharacterSet.whitespaces` */
private fun isWhitespaceInSet(char: Char): Boolean = io.github.hilbert0619.vocabkit.isWhitespaceInSet(char)

// The world the tools see

/** What the tools read, taken when a call runs. */
data class AgentWorld(
    val today: LocalDate,
    val now: TimeOfDay,
    val timetable: Timetable,
    val todos: List<Todo>,
    val focus: FocusSnapshot,
    /** `null` when the user isn't signed in to Words. */
    val vocab: VocabSnapshot?,
)

data class FocusSnapshot(
    /** The user's focus setup, which a new session starts from. */
    val settings: FocusSettings,
    /** Ended sessions, any order. */
    val sessions: List<Session> = emptyList(),
    val running: Running? = null,
) {
    data class Session(val goal: String, val startedAt: Instant, val focusedSeconds: Int, val finished: Boolean)
    data class Running(val goal: String, val startedAt: Instant, val endsAt: Instant, val isPaused: Boolean)
}

class VocabSnapshot(
    val bookName: String,
    /** The book's word keys. */
    val book: List<String>,
    /** Dictionary entries by word key (the book's and any others on the device). */
    val entries: Map<String, WordEntry>,
    val cards: Map<String, Card>,
    val events: List<ReviewEvent>,
    val plan: DailyPlan,
    val exam: ExamCountdown?,
    val newWordsPerDay: Int,
    val desiredRetention: Double,
    /** Today's study day. */
    val today: Int,
    val scheduler: Scheduler,
)

/** A change a tool call proposes, for the user to confirm. */
sealed interface AgentChange {
    data class AddTodo(val todo: Todo) : AgentChange
    data class UpdateTodo(val before: Todo, val after: Todo) : AgentChange
    data class DeleteTodo(val todo: Todo) : AgentChange

    /** Courses to check in the import preview: the CSV and what the importer read from it now. */
    data class AddCourses(val csv: String, val courses: Int, val duplicates: Int, val problems: Int) : AgentChange
    data class StartFocus(val goal: String, val settings: FocusSettings) : AgentChange
}
