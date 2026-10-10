package io.github.hilbert0619.coursetable.data

import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseWeeks
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Semester
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.TodoTiming
import io.github.hilbert0619.coursekit.WeekParity

/** Sample courses and to-dos for previews and screenshots (iOS: SampleCourses, PlannerModel.insertDemoData). */
object DemoData {
    /** A handful of courses on [today]'s weekday and the next, so previews always show something. */
    fun courses(today: LocalDate, chinese: Boolean = false): List<Course> {
        val day = today.weekday
        val tomorrow = today.adding(1).weekday
        val weeks = CourseWeeks(1, 18)
        fun name(english: String, chineseName: String) = if (chinese) chineseName else english
        return listOf(
            Course(name = name("Advanced Mathematics", "高等数学"), teacher = name("Li", "李老师"), room = "A101",
                weekday = day, startPeriod = 1, weeks = weeks, colorIndex = 0),
            Course(name = name("College English", "大学英语"), teacher = name("Wang", "王老师"), room = "B204",
                weekday = day, startPeriod = 3, weeks = weeks, colorIndex = 5),
            Course(name = name("Physics Lab", "物理实验"), room = name("Lab 3", "实验楼3"), weekday = day, startPeriod = 5,
                periodCount = 3, weeks = CourseWeeks(1, 18, WeekParity.ODD), colorIndex = 3),
            Course(name = name("Data Structures", "数据结构"), teacher = name("Zhao", "赵老师"), room = "C305",
                weekday = tomorrow, startPeriod = 1, weeks = weeks, colorIndex = 7),
            Course(name = name("Linear Algebra", "线性代数"), teacher = name("Chen", "陈老师"), room = "A203",
                weekday = tomorrow, startPeriod = 5, weeks = weeks, colorIndex = 4),
        )
    }

    /** A term in its fifth week with [courses]. */
    fun timetable(today: LocalDate, chinese: Boolean = false): Timetable =
        Timetable(Semester(today.adding(-28)), courses = courses(today, chinese))

    /**
     * A few to-dos for [today]: one after the first class of the day, one at a time, one for any time, one done.
     */
    fun todos(today: LocalDate, courses: List<Course>, chinese: Boolean = false): List<Todo> {
        val first = courses.filter { it.weekday == today.weekday }.minByOrNull { it.startPeriod }
        val todos = mutableListOf(
            Todo(title = if (chinese) "取快递" else "Pick up the package", day = today, timing = TodoTiming.At(TimeOfDay(12, 10))),
            Todo(title = if (chinese) "买牛奶" else "Buy milk", day = today),
            Todo(
                title = if (chinese) "还图书馆的书" else "Return the library book", day = today,
                timing = TodoTiming.At(TimeOfDay(7, 30)), isDone = true,
            ),
        )
        if (first != null) {
            todos.add(0, Todo(title = if (chinese) "交论文" else "Hand in the paper", day = today, timing = TodoTiming.AfterClass(first.id)))
        }
        return todos
    }
}
