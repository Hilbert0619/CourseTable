import SwiftUI
import CourseKit

struct WeekView: View {
    let timetable: Timetable
    /// `nil` follows the current week.
    @State private var selectedWeek: Int?
    @State private var editorTarget: EditorTarget?

    private var today: LocalDate { LocalDate(.now) }
    private var currentWeek: Int { timetable.semester.displayWeek(on: today) }
    private var week: Int { min(selectedWeek ?? currentWeek, timetable.semester.totalWeeks) }

    private var visibleDays: [Weekday] {
        timetable.hasWeekendCourses ? Weekday.allCases : Array(Weekday.allCases.prefix(5))
    }

    var body: some View {
        NavigationStack {
            GeometryReader { proxy in
                let layout = GridLayout(width: proxy.size.width, days: visibleDays)
                VStack(spacing: 0) {
                    DayHeader(layout: layout, semester: timetable.semester, week: week, today: today)
                    Divider()
                    ScrollView {
                        WeekGrid(
                            layout: layout,
                            timetable: timetable,
                            week: week,
                            today: today,
                            onSelectCourse: { editorTarget = .edit($0) },
                            onSelectSlot: { editorTarget = .new(weekday: $0, period: $1) }
                        )
                    }
                    .overlay(alignment: .bottom) {
                        if timetable.courses.isEmpty {
                            Text("Tap an empty slot or + to add a course")
                                .font(.callout)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .background(.regularMaterial, in: Capsule())
                                .padding(.bottom, 24)
                        }
                    }
                }
            }
            .simultaneousGesture(
                DragGesture(minimumDistance: 30).onEnded { value in
                    let dx = value.translation.width
                    guard abs(dx) > 60, abs(dx) > abs(value.translation.height) * 1.5 else { return }
                    step(dx < 0 ? 1 : -1)
                }
            )
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) { weekSwitcher }
                ToolbarItem(placement: .topBarLeading) {
                    if week != currentWeek {
                        Button("This Week") { selectedWeek = nil }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        editorTarget = .new(weekday: today.weekday, period: 1)
                    } label: {
                        Label("Add Course", systemImage: "plus")
                    }
                }
            }
            .sheet(item: $editorTarget) { target in
                CourseEditorView(timetable: timetable, target: target)
            }
        }
    }

    private var weekSwitcher: some View {
        HStack(spacing: 2) {
            Button { step(-1) } label: {
                Image(systemName: "chevron.left").accessibilityLabel("Previous Week")
            }
            .disabled(week <= 1)

            Menu {
                ForEach(timetable.semester.weeks, id: \.self) { number in
                    Button { selectedWeek = number } label: {
                        if number == week {
                            Label("Week \(number)", systemImage: "checkmark")
                        } else {
                            Text("Week \(number)")
                        }
                    }
                }
            } label: {
                VStack(spacing: 0) {
                    Text("Week \(week)").font(.headline)
                    if week == timetable.semester.status(on: today).week {
                        Text("This week").font(.caption2).foregroundStyle(.secondary)
                    }
                }
                .frame(minWidth: 90)
            }
            .foregroundStyle(.primary)

            Button { step(1) } label: {
                Image(systemName: "chevron.right").accessibilityLabel("Next Week")
            }
            .disabled(week >= timetable.semester.totalWeeks)
        }
    }

    private func step(_ delta: Int) {
        selectedWeek = min(max(week + delta, 1), timetable.semester.totalWeeks)
    }
}

/// Column and row geometry shared by the header and the grid.
private struct GridLayout {
    static let timeColumnWidth: CGFloat = 40
    static let rowHeight: CGFloat = 60

    let days: [Weekday]
    let columnWidth: CGFloat

    init(width: CGFloat, days: [Weekday]) {
        self.days = days
        columnWidth = max((width - Self.timeColumnWidth) / CGFloat(max(days.count, 1)), 24)
    }

    var totalWidth: CGFloat { Self.timeColumnWidth + columnWidth * CGFloat(days.count) }

    func x(for weekday: Weekday) -> CGFloat? {
        days.firstIndex(of: weekday).map { Self.timeColumnWidth + CGFloat($0) * columnWidth }
    }

    func y(forPeriod period: Int) -> CGFloat {
        CGFloat(period - 1) * Self.rowHeight
    }
}

private struct DayHeader: View {
    let layout: GridLayout
    let semester: Semester
    let week: Int
    let today: LocalDate

    var body: some View {
        HStack(spacing: 0) {
            Text(semester.date(of: .monday, inWeek: week).date().formatted(.dateTime.month(.abbreviated)))
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.secondary)
                .frame(width: GridLayout.timeColumnWidth)
            ForEach(layout.days) { day in
                let date = semester.date(of: day, inWeek: week)
                let isToday = date == today
                VStack(spacing: 2) {
                    Text(verbatim: day.shortName)
                        .font(.caption.weight(.semibold))
                    Text(verbatim: "\(date.month)/\(date.day)")
                        .font(.caption2)
                }
                .foregroundStyle(isToday ? Color.accentColor : Color.primary)
                .frame(width: layout.columnWidth, height: 40)
                .background(isToday ? Color.accentColor.opacity(0.12) : Color.clear)
            }
        }
    }
}

private struct WeekGrid: View {
    let layout: GridLayout
    let timetable: Timetable
    let week: Int
    let today: LocalDate
    let onSelectCourse: (Course) -> Void
    let onSelectSlot: (Weekday, Int) -> Void

    var body: some View {
        let periodCount = timetable.periods.count
        ZStack(alignment: .topLeading) {
            ForEach(0..<periodCount, id: \.self) { index in
                PeriodLabel(number: index + 1, period: timetable.periods.periods[index])
                    .frame(width: GridLayout.timeColumnWidth, height: GridLayout.rowHeight)
                    .offset(y: layout.y(forPeriod: index + 1))
            }

            ForEach(layout.days) { day in
                let x = layout.x(for: day) ?? 0
                let isToday = timetable.semester.date(of: day, inWeek: week) == today
                ForEach(0..<periodCount, id: \.self) { index in
                    Rectangle()
                        .fill(isToday ? Color.accentColor.opacity(0.06) : Color.clear)
                        .overlay(alignment: .bottom) {
                            Rectangle().fill(Color.secondary.opacity(0.15)).frame(height: 0.5)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { onSelectSlot(day, index + 1) }
                        .frame(width: layout.columnWidth, height: GridLayout.rowHeight)
                        .offset(x: x, y: layout.y(forPeriod: index + 1))
                        .accessibilityHidden(true)
                }
            }

            ForEach(timetable.gridPlacements(inWeek: week)) { placement in
                if let x = layout.x(for: placement.weekday) {
                    let laneWidth = layout.columnWidth / CGFloat(placement.laneCount)
                    CourseBlock(course: placement.course)
                        .onTapGesture { onSelectCourse(placement.course) }
                        .frame(
                            width: laneWidth - 3,
                            height: CGFloat(placement.periods.count) * GridLayout.rowHeight - 3
                        )
                        .offset(
                            x: x + CGFloat(placement.lane) * laneWidth + 1.5,
                            y: layout.y(forPeriod: placement.periods.lowerBound) + 1.5
                        )
                        .accessibilityAddTraits(.isButton)
                }
            }
        }
        .frame(width: layout.totalWidth, height: CGFloat(periodCount) * GridLayout.rowHeight, alignment: .topLeading)
        .clipped()
    }
}

private struct PeriodLabel: View {
    let number: Int
    let period: ClassPeriod

    var body: some View {
        VStack(spacing: 1) {
            Text(verbatim: "\(number)")
                .font(.caption.weight(.semibold))
            Text(verbatim: period.start.description)
            Text(verbatim: period.end.description)
        }
        .font(.system(size: 9).monospacedDigit())
        .foregroundStyle(.secondary)
    }
}

struct CourseBlock: View {
    let course: Course

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: course.name)
                .font(.caption.weight(.semibold))
                .lineLimit(4)
            if !course.room.isEmpty {
                Text(verbatim: "@\(course.room)")
                    .font(.caption2)
                    .lineLimit(2)
            }
        }
        .foregroundStyle(.white)
        .padding(4)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(course.color, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    let today = LocalDate(.now)
    let timetable = Timetable(
        semester: Semester(startingOn: today.adding(days: -28)),
        courses: SampleCourses.all(today: today)
    )
    return WeekView(timetable: timetable)
}
