package io.github.hilbert0619.coursetable.ai

import io.github.hilbert0619.aikit.get
import io.github.hilbert0619.aikit.parseJson
import io.github.hilbert0619.aikit.stringValue
import io.github.hilbert0619.coursekit.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * How [DemoAITransport] answers a request (iOS `DemoAITransport.reply`): canned text, or with tools a few scripted
 * conversations that call them the way a model would — a reminder (looks at tomorrow's classes, then proposes a to-do
 * after the first one), vocabulary progress (two lookups, then an analysis), a focus session, and courses from a
 * timetable photo.
 */
object DemoAgent {
    sealed interface Reply {
        data class Text(val text: String) : Reply
        data class Calls(val calls: List<Pair<String, String>>) : Reply
    }

    fun reply(body: String): Reply {
        val json = parseJson(body) as? JsonObject ?: return Reply.Text("")
        val all = promptText(json)
        val messages = (json["messages"] as? JsonArray).orEmpty()
        val userIndex = messages.indexOfLast { it["role"]?.stringValue == "user" }
        if (json["tools"] == null || userIndex < 0) return Reply.Text(DemoAnswers.answer(all))
        val user = text(messages[userIndex])
        val lower = user.lowercase()
        // The tools called since the user's message, and their latest results by tool name.
        val names = mutableMapOf<String, String>()
        val called = mutableListOf<String>()
        val results = mutableMapOf<String, String>()
        for (message in messages.drop(userIndex + 1)) {
            for (call in (message["tool_calls"] as? JsonArray).orEmpty()) {
                val id = call["id"]?.stringValue ?: continue
                val name = call["function"]?.get("name")?.stringValue ?: continue
                names[id] = name
                called += name
            }
            if (message["role"]?.stringValue == "tool") {
                val name = message["tool_call_id"]?.stringValue?.let(names::get)
                if (name != null) results[name] = message["content"]?.stringValue.orEmpty()
            }
        }
        val system = messages.firstOrNull { it["role"]?.stringValue == "system" }?.let(::text).orEmpty()
        val chinese = system.contains("The user's language: Simplified Chinese")
        val today = Regex("Today is (\\d{4}-\\d{2}-\\d{2})").find(system)?.groupValues?.get(1)?.let(LocalDate::parse)
            ?: LocalDate.from(java.time.Instant.now())
        val tomorrow = today.adding(1).toString()

        return when {
            user.contains("Call the ping tool") ->
                if (called.isEmpty()) Reply.Calls(listOf("ping" to "{}")) else Reply.Text("OK")
            user.contains("[image]") || user.contains("<photo_text>") -> when {
                called.isEmpty() -> Reply.Calls(listOf("add_courses" to arguments("csv" to DemoAnswers.PHOTO_CSV)))
                results["add_courses"].orEmpty().startsWith("The user imported") ->
                    Reply.Text(if (chinese) "好了，课程已经加进课程表。" else "Done — the courses are in your timetable now.")
                else -> Reply.Text(if (chinese) "好的，没有添加课程。" else "OK, I didn't add any courses.")
            }
            lower.contains("remind") || user.contains("提醒") -> {
                val title = if (chinese) "交论文" else "Hand in the paper"
                when {
                    called.isEmpty() -> Reply.Calls(listOf("get_schedule" to arguments("from" to tomorrow, "to" to tomorrow)))
                    called.last() == "get_schedule" -> {
                        val course = firstCourse(results["get_schedule"].orEmpty())
                        Reply.Calls(
                            listOf(
                                "add_todo" to if (course != null) {
                                    arguments("title" to title, "date" to tomorrow, "afterCourse" to course)
                                } else {
                                    arguments("title" to title, "date" to tomorrow, "time" to "17:30")
                                },
                            ),
                        )
                    }
                    results["add_todo"].orEmpty().startsWith("Added") ->
                        Reply.Text(if (chinese) "好了，明天会提醒你交论文。" else "Done — I'll remind you to hand in the paper tomorrow.")
                    else -> Reply.Text(if (chinese) "好的，没有添加。" else "OK, I didn't add it.")
                }
            }
            lower.contains("focus") || user.contains("专注") ->
                if (called.isEmpty()) {
                    Reply.Calls(listOf("start_focus" to arguments("minutes" to 25, "goal" to if (chinese) "高数复习" else "Calculus review")))
                } else if (results["start_focus"].orEmpty().startsWith("The focus session started")) {
                    Reply.Text(if (chinese) "开始了，加油！该休息时会提醒你。" else "Started — good luck! I'll let you know when it's time for a break.")
                } else {
                    Reply.Text(if (chinese) "好的，先不开始。" else "OK, not now.")
                }
            lower.contains("word") || lower.contains("progress") || user.contains("单词") || user.contains("进度") ->
                if (called.isEmpty()) {
                    Reply.Calls(listOf("get_vocab_overview" to "{}", "get_hard_words" to arguments("limit" to 5)))
                } else {
                    Reply.Text(if (chinese) PROGRESS_ZH else PROGRESS_EN)
                }
            else -> Reply.Text(DemoAnswers.answer(all))
        }
    }

    /** A message's text: its content, or the text parts of its content (a picture as "[image]"). */
    private fun text(message: JsonElement): String {
        val content = message["content"]
        content?.stringValue?.let { return it }
        return (content as? JsonArray).orEmpty().joinToString("\n") { part ->
            part["text"]?.stringValue ?: if (part["type"]?.stringValue == "image_url") "[image]" else ""
        }
    }

    /** The system prompt and messages of a request body, which tell the requests apart. */
    private fun promptText(json: JsonObject): String =
        (listOf(json["system"]?.stringValue.orEmpty()) + (json["messages"] as? JsonArray).orEmpty().map(::text)).joinToString("\n")

    private fun arguments(vararg values: Pair<String, Any>): String = JsonObject(
        values.sortedBy { it.first }.associate { (key, value) ->
            key to when (value) {
                is Number -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        },
    ).toString()

    /** The course of the first line in `get_schedule`'s answer, e.g. `Fri 2026-10-16 08:00–09:40 Data Structures (C305, Zhao) · week 7`. */
    private fun firstCourse(schedule: String): String? {
        val pattern = Regex("\\d{2}:\\d{2}–\\d{2}:\\d{2} (.+?)(?: \\(| · )")
        return schedule.lines().firstNotNullOfOrNull { pattern.find(it)?.groupValues?.get(1) }
    }

    private val PROGRESS_EN = """
        Here's how your words are going:

        - **Streak:** you've studied on most days lately — keep it going.
        - **Accuracy:** most answers are right; the misses cluster on a few hard words.
        - **Hard words:** review them once more tonight and use each one in a sentence.
        - **Exam pace:** at your current number of new words a day you'll see the whole book before the exam.

        **Next step:** finish today's reviews first, then the new words.
    """.trimIndent()

    private val PROGRESS_ZH = """
        你的单词学习情况：

        - **连续学习：** 最近大部分日子都学了，继续保持。
        - **正确率：** 大多数回答正确，错误集中在几个难词上。
        - **难词：** 今晚再复习一遍，每个词造一个句子。
        - **考试进度：** 按现在每天的新词数量，考试前能学完整本词书。

        **下一步：** 先做完今天的复习，再学新词。
    """.trimIndent()
}
