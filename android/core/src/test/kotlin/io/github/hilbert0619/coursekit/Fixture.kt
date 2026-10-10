package io.github.hilbert0619.coursekit

/** Term starts Monday 2026-08-31; the week of 2026-09-21 is week 4 (even), 2026-09-28 week 5 (odd). */
object Fixture {
    val semester = Semester(LocalDate(2026, 8, 31), totalWeeks = 18)

    val calculus = Course(
        name = "Calculus", teacher = "Li", room = "A101", weekday = Weekday.MONDAY, startPeriod = 1, periodCount = 2,
        weeks = CourseWeeks(1, 16),
    )
    val physics = Course(
        name = "Physics", room = "B204", weekday = Weekday.MONDAY, startPeriod = 3, periodCount = 2,
        weeks = CourseWeeks(1, 16, WeekParity.ODD),
    )
    val english = Course(
        name = "English", room = "C3", weekday = Weekday.MONDAY, startPeriod = 3, periodCount = 2,
        weeks = CourseWeeks(2, 16, WeekParity.EVEN),
    )
    val lab = Course(name = "Lab", weekday = Weekday.THURSDAY, startPeriod = 5, periodCount = 3, weeks = CourseWeeks(4, 12))
    val seminar = Course(name = "Seminar", weekday = Weekday.FRIDAY, startPeriod = 9, periodCount = 2, weeks = CourseWeeks(1, 18))

    val timetable = Timetable(semester, courses = listOf(seminar, lab, english, physics, calculus))

    fun day(text: String): LocalDate = LocalDate.parse(text)!!
    fun at(day: String, time: String): Moment = Moment(LocalDate.parse(day)!!, TimeOfDay.parse(time)!!)
    fun time(text: String): TimeOfDay = TimeOfDay.parse(text)!!
}
