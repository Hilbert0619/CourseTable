package io.github.hilbert0619.coursekit

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class CSVTests {
    @Test
    fun detectsDelimiter() {
        assertEquals('\t', CSV.detectDelimiter("name\tweekday\n"))
        assertEquals(',', CSV.detectDelimiter("\n\nname,weekday,periods\n"))
        assertEquals(';', CSV.detectDelimiter("name;weekday;periods"))
        assertEquals('，', CSV.detectDelimiter("课程，星期，节次"))
        assertEquals(';', CSV.detectDelimiter("a;b,c;d"))
        assertEquals(',', CSV.detectDelimiter("just one column"))
        assertEquals(',', CSV.detectDelimiter(""))
    }

    @Test
    fun readsQuotedFieldsAndLineEndings() {
        val text = "a,\"b,c\",\"say \"\"hi\"\"\"\r\n1, \"two\" ,3\r4\n"
        assertEquals(
            listOf(
                CSV.Record(1, listOf("a", "b,c", "say \"hi\"")),
                CSV.Record(2, listOf("1", "two ", "3")),
                CSV.Record(3, listOf("4")),
            ),
            CSV.records(text, ','),
        )
    }

    @Test
    fun lineBreakInsideQuotesKeepsLineNumbers() {
        val records = CSV.records("name,room\n\"Lab\nPart 2\",A1\nMath,B2", ',')
        assertEquals(listOf(1, 2, 4), records.map { it.line })
        assertEquals(listOf("Lab\nPart 2", "A1"), records[1].fields)
        assertEquals(listOf("Math", "B2"), records[2].fields)
    }

    @Test
    fun strayQuoteInsideFieldIsText() {
        assertEquals(listOf(CSV.Record(1, listOf("5\" screen", "x"))), CSV.records("5\" screen,x", ','))
    }

    @Test
    fun emptyAndTrailingFields() {
        assertTrue(CSV.records("", ',').isEmpty())
        assertEquals(listOf(CSV.Record(1, listOf("a", "", ""))), CSV.records("a,,\n", ','))
        assertEquals(listOf(CSV.Record(1, listOf("unterminated"))), CSV.records("\"unterminated", ','))
    }

    @Test
    fun escapeRoundTrips() {
        val fields = listOf("plain", "a,b", "quote \"x\"", "two\nlines", "")
        val line = fields.joinToString(",") { CSV.escape(it) }
        assertEquals("plain,\"a,b\",\"quote \"\"x\"\"\",\"two\nlines\",", line)
        assertEquals(fields, CSV.records(line, ',').first().fields)
    }
}

class FieldParsingTests {
    @ParameterizedTest
    @MethodSource("weekdayCases")
    fun weekdays(text: String, expected: Weekday) {
        assertEquals(expected, CourseImporter.parseWeekday(text))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "0", "8", "t", "s", "Funday", "周八", "星期", "1.5"])
    fun invalidWeekdays(text: String) {
        assertNull(CourseImporter.parseWeekday(text))
    }

    @ParameterizedTest
    @MethodSource("periodCases")
    fun periods(text: String, expected: List<IntRange>) {
        assertEquals(expected, CourseImporter.parsePeriods(text, PeriodSchedule.STANDARD))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "abc", "2-1", "1-2-3", "-3", "periods", "06:00-07:00", "09:40-08:00", "8:00"])
    fun invalidPeriods(text: String) {
        assertNull(CourseImporter.parsePeriods(text, PeriodSchedule.STANDARD))
    }

    @ParameterizedTest
    @MethodSource("weekCases")
    fun weeks(text: String, expected: List<CourseWeeks>) {
        assertEquals(expected, CourseImporter.parseWeeks(text, totalWeeks = 18))
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc", "0-3", "5-3", "1-31", "2 odd", "单双", "1-16单双", "1-2-3", "1-16x"])
    fun invalidWeeks(text: String) {
        assertNull(CourseImporter.parseWeeks(text, totalWeeks = 18))
    }

    @Test
    fun weekSegments() {
        assertEquals(emptyList(), CourseWeeks.segments(emptySet()))
        assertEquals(listOf(CourseWeeks(4, 4)), CourseWeeks.segments(setOf(4)))
        assertEquals(
            listOf(CourseWeeks(1, 3), CourseWeeks(5, 9, WeekParity.ODD)),
            CourseWeeks.segments(setOf(1, 2, 3, 5, 7, 9)),
        )
        assertEquals(
            listOf(CourseWeeks(2, 6, WeekParity.EVEN), CourseWeeks(7, 8)),
            CourseWeeks.segments(setOf(2, 4, 6, 7, 8)),
        )
        // Segments always cover exactly the input weeks.
        val weeks = setOf(1, 2, 4, 6, 9, 10, 11, 13, 15, 18)
        assertEquals(weeks, CourseWeeks.segments(weeks).flatMap { it.all }.toSet())
    }

    @Test
    fun headerMatching() {
        assertEquals(ImportColumn.NAME, ImportColumn.matching("Course Name"))
        assertEquals(ImportColumn.WEEKDAY, ImportColumn.matching("day_of_week"))
        assertEquals(ImportColumn.ROOM, ImportColumn.matching("上课地点"))
        assertEquals(ImportColumn.WEEKS, ImportColumn.matching(" 周次 "))
        assertEquals(ImportColumn.ROOM, ImportColumn.matching("Ｒｏｏｍ"))
        assertNull(ImportColumn.matching("notes"))
    }

    companion object {
        @JvmStatic
        fun weekdayCases(): List<Arguments> = listOf(
            "1" to Weekday.MONDAY, "7" to Weekday.SUNDAY, "Mon" to Weekday.MONDAY, "monday" to Weekday.MONDAY,
            "Tue." to Weekday.TUESDAY, "TUES" to Weekday.TUESDAY, "th" to Weekday.THURSDAY, "Thurs" to Weekday.THURSDAY,
            "sa" to Weekday.SATURDAY, "Sun" to Weekday.SUNDAY, "周一" to Weekday.MONDAY, "星期三" to Weekday.WEDNESDAY,
            "礼拜五" to Weekday.FRIDAY, "六" to Weekday.SATURDAY, "周日" to Weekday.SUNDAY, "星期天" to Weekday.SUNDAY,
            "周7" to Weekday.SUNDAY, "２" to Weekday.TUESDAY, " 周 二 " to Weekday.TUESDAY,
        ).map { (text, weekday) -> Arguments.of(text, weekday) }

        @JvmStatic
        fun periodCases(): List<Arguments> = listOf(
            "3" to listOf(3..3), "1-2" to listOf(1..2), "第1-2节" to listOf(1..2), "1~2节" to listOf(1..2),
            "１－２" to listOf(1..2), "1,2" to listOf(1..2), "1、2,5" to listOf(1..2, 5..5), "3 - 5" to listOf(3..5),
            "11-12" to listOf(11..12), "08:00-09:40" to listOf(1..2), "8:00 ~ 9:35" to listOf(1..2),
            "14:00–17:40" to listOf(5..8), "08:00-08:45" to listOf(1..1), "0-1" to listOf(0..1), "13" to listOf(13..13),
        ).map { (text, runs) -> Arguments.of(text, runs) }

        @JvmStatic
        fun weekCases(): List<Arguments> = listOf(
            "" to listOf(CourseWeeks(1, 18)),
            "1-16" to listOf(CourseWeeks(1, 16)),
            "1-16周" to listOf(CourseWeeks(1, 16)),
            "第1-16周" to listOf(CourseWeeks(1, 16)),
            "１－１６周" to listOf(CourseWeeks(1, 16)),
            "1 - 16" to listOf(CourseWeeks(1, 16)),
            "Weeks 1-16" to listOf(CourseWeeks(1, 16)),
            "1-15单" to listOf(CourseWeeks(1, 15, WeekParity.ODD)),
            "1-16(单)" to listOf(CourseWeeks(1, 15, WeekParity.ODD)),
            "1-16周（单）" to listOf(CourseWeeks(1, 15, WeekParity.ODD)),
            "2-16双周" to listOf(CourseWeeks(2, 16, WeekParity.EVEN)),
            "1-16 even" to listOf(CourseWeeks(2, 16, WeekParity.EVEN)),
            "3-15 odd" to listOf(CourseWeeks(3, 15, WeekParity.ODD)),
            "单周" to listOf(CourseWeeks(1, 17, WeekParity.ODD)),
            "even" to listOf(CourseWeeks(2, 18, WeekParity.EVEN)),
            "1-8,10-16" to listOf(CourseWeeks(1, 8), CourseWeeks(10, 16)),
            "1-8、10-16周" to listOf(CourseWeeks(1, 8), CourseWeeks(10, 16)),
            "1,3,5,7" to listOf(CourseWeeks(1, 7, WeekParity.ODD)),
            "1-7单,8-12" to listOf(CourseWeeks(1, 7, WeekParity.ODD), CourseWeeks(8, 12)),
            "5" to listOf(CourseWeeks(5, 5)),
            "1-30" to listOf(CourseWeeks(1, 30)),
        ).map { (text, weeks) -> Arguments.of(text, weeks) }
    }
}

class CourseImporterTests {
    private val importer = CourseImporter(PeriodSchedule.STANDARD, totalWeeks = 18)

    @Test
    fun englishAndChineseExamplesMatch() {
        val english = importer.parse(CourseCSV.ENGLISH_EXAMPLE)
        val chinese = importer.parse(CourseCSV.CHINESE_EXAMPLE)
        assertTrue(english.problems.isEmpty())
        assertTrue(chinese.problems.isEmpty())
        assertEquals(4, english.rowCount)
        // "1-8,10-16" becomes two courses.
        assertEquals(5, english.courses.size)
        assertEquals(
            listOf("Advanced Mathematics", "College English", "Physics Lab", "Data Structures", "Data Structures"),
            english.courses.map { it.name },
        )
        assertEquals(CourseWeeks(1, 15, WeekParity.ODD), english.courses[2].weeks)
        assertEquals(5..7, english.courses[2].periods)
        assertEquals(Weekday.THURSDAY, english.courses[2].weekday)
        assertEquals(CourseWeeks(10, 16), english.courses[4].weeks)

        fun slots(courses: List<Course>) = courses.map { "${it.weekday.rawValue} ${it.periods} ${it.weeks.all}" }
        assertEquals(slots(english.courses), slots(chinese.courses))
        assertEquals("高等数学", chinese.courses[0].name)
        assertEquals("李老师", chinese.courses[0].teacher)
        assertEquals("A101", chinese.courses[0].room)
    }

    @Test
    fun headerColumnsInAnyOrderWithExtraColumns() {
        val result = importer.parse(
            """
            Weeks,Notes,Periods,Day,Course,Room
            1-16,bring calculator,3-4,Wed,Calculus,A1
            ,,1,Fri,Seminar,
            """.trimIndent(),
        )
        assertTrue(result.problems.isEmpty())
        assertEquals(2, result.courses.size)
        val calculus = result.courses[0]
        assertEquals("Calculus", calculus.name)
        assertEquals("A1", calculus.room)
        assertEquals("", calculus.teacher)
        assertEquals(Weekday.WEDNESDAY, calculus.weekday)
        assertEquals(3..4, calculus.periods)
        assertEquals(CourseWeeks(1, 18), result.courses[1].weeks)
        assertEquals(1, result.courses[1].periodCount)
    }

    @Test
    fun headerlessRowsUseDefaultOrder() {
        val result = importer.parse("Calculus,Li,A101,1,1-2,1-16\nPhysics,,B2,3,3-4,")
        assertTrue(result.problems.isEmpty())
        assertEquals(2, result.rowCount)
        assertEquals(listOf("Calculus", "Physics"), result.courses.map { it.name })
        assertEquals("Li", result.courses[0].teacher)
        assertEquals(Weekday.WEDNESDAY, result.courses[1].weekday)
        assertEquals(CourseWeeks(1, 18), result.courses[1].weeks)
    }

    @Test
    fun spreadsheetPasteWithChineseHeader() {
        val result = importer.parse("课程名称\t任课教师\t上课地点\t星期\t节次\t周次\r\n线性代数\t陈\tA203\t星期二\t第5-6节\t2-16双周\r\n")
        assertTrue(result.problems.isEmpty())
        assertEquals(
            listOf(
                Course(
                    id = result.courses.firstOrNull()?.id ?: UUID.randomUUID(), name = "线性代数", teacher = "陈", room = "A203",
                    weekday = Weekday.TUESDAY, startPeriod = 5, periodCount = 2, weeks = CourseWeeks(2, 16, WeekParity.EVEN),
                ),
            ),
            result.courses,
        )
    }

    @Test
    fun timeRangesMapToPeriods() {
        val result = importer.parse("name,weekday,time\nEvening Class,Thu,19:00-20:40")
        assertTrue(result.problems.isEmpty())
        assertEquals(9..10, result.courses.firstOrNull()?.periods)
    }

    @Test
    fun missingRequiredColumnStopsImport() {
        val result = importer.parse("name,room,weeks\nCalculus,A1,1-16")
        assertTrue(result.courses.isEmpty())
        assertEquals(
            listOf(ImportProblem(1, ImportProblem.Kind.MissingColumns(listOf(ImportColumn.WEEKDAY, ImportColumn.PERIODS)))),
            result.problems,
        )
    }

    @Test
    fun badRowsAreReportedAndOthersStillImport() {
        val result = importer.parse(
            """
            name,weekday,periods,weeks
            Calculus,Mon,1-2,1-16

            # comment lines are skipped
            Oops,Funday,1-2,1-16
            ,Tue,3-4,
            Late,Fri,13-14,
            Physics,Wed,x,1-16 odd
            Chemistry,Thu,,99
            English,Fri,3-4,1-16
            """.trimIndent(),
        )
        assertEquals(listOf("Calculus", "English"), result.courses.map { it.name })
        assertEquals(7, result.rowCount)
        assertEquals(
            listOf(
                ImportProblem(5, ImportProblem.Kind.InvalidValue(ImportColumn.WEEKDAY, "Funday")),
                ImportProblem(6, ImportProblem.Kind.MissingValue(ImportColumn.NAME)),
                ImportProblem(7, ImportProblem.Kind.PeriodsOutOfRange("13-14", periodsPerDay = 12)),
                ImportProblem(8, ImportProblem.Kind.InvalidValue(ImportColumn.PERIODS, "x")),
                ImportProblem(9, ImportProblem.Kind.MissingValue(ImportColumn.PERIODS)),
                ImportProblem(9, ImportProblem.Kind.InvalidValue(ImportColumn.WEEKS, "99")),
            ),
            result.problems,
        )
    }

    @Test
    fun shortRowsReportMissingValues() {
        val result = importer.parse("Calculus,Li")
        assertTrue(result.courses.isEmpty())
        assertEquals(
            listOf(
                ImportProblem(1, ImportProblem.Kind.MissingValue(ImportColumn.WEEKDAY)),
                ImportProblem(1, ImportProblem.Kind.MissingValue(ImportColumn.PERIODS)),
            ),
            result.problems,
        )
    }

    @Test
    fun splitPeriodsAndWeeksMakeSeveralCourses() {
        val result = importer.parse("name,weekday,periods,weeks\nLab,Mon,\"1,2,5\",\"1-4,9-12\"")
        assertTrue(result.problems.isEmpty())
        assertEquals(
            listOf("1..2 1-4", "1..2 9-12", "5..5 1-4", "5..5 9-12"),
            result.courses.map { "${it.periods} ${it.weeks.first}-${it.weeks.last}" },
        )
    }

    @Test
    fun ignoresByteOrderMarkBlankInputAndCommentsOnly() {
        assertEquals(1, importer.parse("﻿name,weekday,periods\nMath,1,1").courses.size)
        assertEquals(CourseImportResult(), importer.parse(""))
        assertEquals(CourseImportResult(), importer.parse("\n  \n# nothing here\n"))
        assertEquals(0, importer.parse("name,weekday,periods\n").rowCount)
    }

    @Test
    fun usesTimetableSettings() {
        val short = Timetable(
            Semester(LocalDate(2026, 9, 7), totalWeeks = 10),
            periods = PeriodSchedule(PeriodSchedule.STANDARD.periods.take(4)),
        )
        val result = CourseImporter(short).parse("name,weekday,periods\nMath,1,5")
        assertEquals(listOf(ImportProblem(2, ImportProblem.Kind.PeriodsOutOfRange("5", periodsPerDay = 4))), result.problems)
        assertEquals(CourseWeeks(1, 10), CourseImporter(short).parse("name,weekday,periods\nMath,1,1").courses.firstOrNull()?.weeks)
    }
}

class ImportPlanTests {
    private val timetable = Fixture.timetable

    @Test
    fun skipsCoursesAlreadyInTimetableOrRepeated() {
        val copy = Fixture.calculus.copy(id = UUID.randomUUID(), colorIndex = 7)
        val newCourse = Course(name = "Biology", weekday = Weekday.TUESDAY, startPeriod = 1, weeks = CourseWeeks(1, 16))
        val plan = timetable.planningImport(listOf(copy, newCourse, newCourse))
        assertEquals(listOf("Biology"), plan.added.map { it.name })
        assertEquals(2, plan.duplicates.size)
        assertEquals(timetable.courses.size + 1, plan.timetable.courses.size)
        assertTrue(!plan.replacesExisting)
    }

    @Test
    fun weeksCompareByMeetingWeeks() {
        var sameMeetings = Fixture.physics.copy(id = UUID.randomUUID(), weeks = CourseWeeks(1, 15, WeekParity.ODD)) // weeks 1–16 odd
        assertTrue(sameMeetings.isSameClass(Fixture.physics))
        sameMeetings = sameMeetings.copy(room = "Other")
        assertTrue(!sameMeetings.isSameClass(Fixture.physics))
    }

    @Test
    fun coloursFollowCourseNames() {
        val calculusTuesday = Fixture.calculus.copy(id = UUID.randomUUID(), weekday = Weekday.TUESDAY)
        val existing = Timetable(Fixture.semester, courses = listOf(Fixture.calculus.copy(colorIndex = 3)))
        val biology = Course(name = "Biology", weekday = Weekday.FRIDAY, startPeriod = 1, weeks = CourseWeeks(1, 16))
        val biologyLab = biology.copy(id = UUID.randomUUID(), startPeriod = 5)
        val chemistry = Course(name = "Chemistry", weekday = Weekday.FRIDAY, startPeriod = 3, weeks = CourseWeeks(1, 16))

        val plan = existing.planningImport(listOf(calculusTuesday, biology, biologyLab, chemistry))
        assertEquals(listOf(3, 0, 0, 1), plan.added.map { it.colorIndex })
    }

    @Test
    fun replacingDropsExistingCourses() {
        val biology = Course(name = "Calculus", weekday = Weekday.FRIDAY, startPeriod = 1, weeks = CourseWeeks(1, 16))
        val plan = timetable.planningImport(listOf(Fixture.calculus, biology), replacingExisting = true)
        assertTrue(plan.replacesExisting)
        assertTrue(plan.duplicates.isEmpty())
        assertEquals(2, plan.added.size)
        assertEquals(plan.added, plan.timetable.courses)
        assertEquals(listOf(0, 0), plan.added.map { it.colorIndex })
        assertEquals(timetable.semester, plan.timetable.semester)
    }

    @Test
    fun planTimetableShowsClashes() {
        val clashing = Course(name = "Makeup", weekday = Weekday.MONDAY, startPeriod = 2, periodCount = 2, weeks = CourseWeeks(5, 5))
        val plan = timetable.planningImport(listOf(clashing))
        assertEquals(listOf("Calculus", "Physics"), plan.timetable.clashes(plan.added[0]).map { it.name })
    }
}

class CourseCSVTests {
    @Test
    fun exportsInSlotOrderWithEscaping() {
        val course = Course(
            name = "Lab, Part 1", teacher = "Zhao \"Z\"", room = "", weekday = Weekday.THURSDAY, startPeriod = 5,
            periodCount = 1, weeks = CourseWeeks(3, 3),
        )
        val csv = CourseCSV.export(listOf(Fixture.seminar, course, Fixture.english))
        assertEquals(
            "name,teacher,room,weekday,periods,weeks\r\n" +
                "English,,C3,1,3-4,2-16 even\r\n" +
                "\"Lab, Part 1\",\"Zhao \"\"Z\"\"\",,4,5,3\r\n" +
                "Seminar,,,5,9-10,1-18\r\n",
            csv,
        )
    }

    @Test
    fun exportImportRoundTrip() {
        val original = Fixture.timetable.courses.sortedWith(Timetable.SLOT_ORDER)
        val result = CourseImporter(Fixture.timetable).parse(CourseCSV.export(original))
        assertTrue(result.problems.isEmpty())
        assertEquals(original.size, result.courses.size)
        for ((imported, course) in result.courses.zip(original)) assertTrue(imported.isSameClass(course))
    }

    @Test
    fun exportDataHasByteOrderMarkAndDecodes() {
        val data = CourseCSV.exportData(listOf(Fixture.calculus))
        assertContentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), data.copyOfRange(0, 3))
        assertEquals(CourseCSV.export(listOf(Fixture.calculus)), ImportedText.decode(data))
    }
}

class ImportedTextTests {
    @Test
    fun decodesUnicodeEncodings() {
        val text = "课程,星期\n高等数学,周一"
        assertEquals(text, ImportedText.decode(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, ImportedText.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)))

        val utf16LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, ImportedText.decode(utf16LE))
        val utf16BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)
        assertEquals(text, ImportedText.decode(utf16BE))
        assertEquals("", ImportedText.decode(ByteArray(0)))
    }

    /** As on Apple platforms (the Swift package tests this only there): not UTF-8, so GB18030. */
    @Test
    fun invalidUTF8IsReadAsGB18030() {
        // "高等" in GB18030.
        assertEquals("高等", ImportedText.decode(byteArrayOf(0xB8.toByte(), 0xDF.toByte(), 0xB5.toByte(), 0xC8.toByte())))
    }
}
