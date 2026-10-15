package io.github.hilbert0619.coursetable.ai

import io.github.hilbert0619.coursekit.LocalDate

/** The canned answers of [DemoAITransport], chosen by what the request asks (iOS `DemoAITransport.answer(for:)`). */
object DemoAnswers {
    fun answer(body: String): String {
        val chinese = body.contains("简体中文")
        return when {
            body.contains("single word OK") -> "OK"
            body.contains("You read a university timetable") ->
                "```csv\n$PHOTO_CSV\n```\n```periods\n1,08:00,08:45\n2,08:50,09:35\n3,09:55,10:40\n4,10:45,11:30\n" +
                    "5,14:00,14:45\n6,14:50,15:35\n7,15:55,16:40\n8,16:45,17:30\n```"
            body.contains("What colour fills this picture") -> "Red"
            body.contains("one to-do item") -> todoAnswer(body)
            body.contains("long-sentence") -> if (chinese) SENTENCE_ZH else SENTENCE_EN
            body.contains("exam marker") -> if (chinese) ESSAY_ZH else ESSAY_EN
            body.contains("study assistant in CourseTable") ->
                if (body.contains("The user's language: Simplified Chinese")) CHAT_ZH else CHAT_EN
            body.contains("I spelled it like this") -> if (chinese) SPELLING_ZH else SPELLING_EN
            body.contains("got this word wrong") -> if (chinese) WHY_ZH else WHY_EN
            else -> if (chinese) EXPLAIN_ZH else EXPLAIN_EN
        }
    }

    /**
     * After the first class from tomorrow on in the prompt's class list (lines like `Tue 2026-10-13 08:00–09:40 Calculus
     * (A101)`), or tomorrow at 17:30 without one. The request body is JSON, so its line breaks are `\n` escapes.
     */
    private fun todoAnswer(body: String): String {
        val prompt = body.replace("\\n", "\n")
        val today = Regex("Today is (\\d{4}-\\d{2}-\\d{2})").find(prompt)?.groupValues?.get(1)?.let(LocalDate::parse)
            ?: LocalDate.from(java.time.Instant.now())
        val tomorrow = today.adding(1)
        val list = prompt.substringAfterLast("Classes in the next 7 days:\n", "")
        for (line in list.split("\n").takeWhile { it.isNotEmpty() }) {
            val parts = line.split(" ")
            val day = parts.getOrNull(1)?.let(LocalDate::parse) ?: continue
            if (parts.size < 4 || day < tomorrow) continue
            var name = parts.drop(3).joinToString(" ")
            if (name.endsWith(")") && name.contains(" (")) name = name.substring(0, name.lastIndexOf(" ("))
            return """{"title": "交论文", "date": "$day", "time": null, "afterCourse": "$name"}"""
        }
        return """{"title": "交论文", "date": "$tomorrow", "time": "17:30", "afterCourse": null}"""
    }

    /** What the sample timetable picture holds. */
    const val PHOTO_CSV = "课程,教师,教室,星期,节次,周次\n" +
        "线性代数,,C305,1,1-2,1-16\n" +
        "线性代数,,C305,5,1-2,1-16\n" +
        "中国近代史,,D102,2,3-4,1-16\n" +
        "体育,,操场,4,5-6,1-16"

    private const val SPELLING_EN = "You left out letters in the middle. Split it into syllables: **a·ban·don** — each one has its own vowel.\n\n" +
        "**Tip:** say the three syllables slowly, then spell one syllable at a time."
    private const val SPELLING_ZH = "你漏掉了中间的字母。按音节拆开记：**a·ban·don** —— 每个音节都有一个元音。\n\n" +
        "**小技巧：** 先慢慢读出三个音节，再一个音节一个音节地拼写。"
    private const val WHY_EN = "**abandon** means to leave someone or something for good; **adopt**, which you picked, means to take " +
        "something on — almost the opposite.\n\n**Tip:** think *a-BAND-on*: walking out on your band for good."
    private const val WHY_ZH = "**abandon** 指彻底放弃、离开（人、地方或计划），带有“不再回来”的意味；你选的 **adopt** 是“采用、收养”，方向正好相反。\n\n" +
        "**小技巧：** *a-band-on* —— 把乐队（band）丢下不管 → 放弃。"

    private val EXPLAIN_EN = """
        ## Core meaning
        **abandon** /əˈbændən/ means to leave or give up *completely*, usually for good and often irresponsibly.

        ## In the exam
        - **abandon a plan / an idea** — give it up
        - **abandon oneself to** — let a feeling take over
        - noun **with abandon** — wildly, without restraint

        ## Examples
        1. The government had to *abandon* its plan to raise taxes.
           政府不得不放弃加税的计划。
        2. They *abandoned* the car and walked the rest of the way.
           他们弃车步行走完了剩下的路。
    """.trimIndent()

    private val EXPLAIN_ZH = """
        ## 核心含义
        **abandon** /əˈbændən/ 指*彻底*放弃或离开，强调不再回来，常带有不负责任的意味。

        ## 考研常见用法
        - **abandon a plan / an idea**：放弃计划、想法
        - **abandon oneself to**：沉溺于（某种情绪）
        - 名词 **with abandon**：放纵地、尽情地

        ## 例句
        1. The government had to *abandon* its plan to raise taxes.
           政府不得不放弃加税的计划。
        2. They *abandoned* the car and walked the rest of the way.
           他们弃车步行走完了剩下的路。
    """.trimIndent()

    private val CHAT_EN = """
        Here's a simple plan for this week:

        1. **Mornings (30 min):** new words and reviews in the Words tab.
        2. **After classes:** one long sentence from a past paper each day.
        3. **Weekend:** one essay, then check it with *Writing Feedback*.

        Short, regular sessions beat long cramming.
    """.trimIndent()

    private val CHAT_ZH = """
        这周可以这样安排：

        1. **每天早上（30 分钟）：** 在“单词”里学新词、做复习。
        2. **下课后：** 每天分析一个真题长难句。
        3. **周末：** 写一篇作文，再用“作文批改”检查。

        短而规律的学习，比临时突击更有效。
    """.trimIndent()

    private val SENTENCE_EN = """
        ## Main clause
        **The study** (subject) **suggests** (verb) **that…** (object clause)

        ## Clauses and modifiers
        - *that people who sleep less are more likely to abandon their goals* — object clause of **suggests**
        - *who sleep less* — relative clause modifying **people**

        ## Translation
        这项研究表明，睡眠较少的人更有可能放弃自己的目标。

        ## Key words
        - **abandon** — 放弃
        - **be likely to** — 很可能
    """.trimIndent()

    private val SENTENCE_ZH = """
        ## 主干
        **The study**（主语）**suggests**（谓语）**that…**（宾语从句）

        ## 从句与修饰成分
        - *that people who sleep less are more likely to abandon their goals*：**suggests** 的宾语从句
        - *who sleep less*：定语从句，修饰 **people**

        ## 译文
        这项研究表明，睡眠较少的人更有可能放弃自己的目标。

        ## 重点词汇
        - **abandon**：放弃
        - **be likely to**：很可能
    """.trimIndent()

    private val ESSAY_EN = """
        ## Score
        Band 3 (estimated **12/20**): clear ideas, some errors.

        ## Errors
        1. "people *is* busy" → "people **are** busy"
        2. "more and more *peoples*" → "more and more **people**"

        ## A better version
        As is vividly depicted in the picture, …
    """.trimIndent()

    private val ESSAY_ZH = """
        ## 评分
        第三档（估计 **12/20**）：观点清楚，有一些语言错误。

        ## 错误
        1. "people *is* busy" → "people **are** busy"（主谓一致）
        2. "more and more *peoples*" → "more and more **people**"

        ## 改进版本
        As is vividly depicted in the picture, …
    """.trimIndent()
}
