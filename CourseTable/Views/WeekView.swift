import SwiftUI
import SwiftData
import CourseKit

struct WeekView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \CourseTemplateRecord.createdAt) private var templateRecords: [CourseTemplateRecord]

    let timetable: Timetable
    /// `nil` follows the current week.
    @State private var selectedWeek: Int?
    @State private var editorTarget: EditorTarget?
    @State private var importing = false
    @State private var editingCourseList = false
    /// Placing mode: pick a course from the tray, then tap empty slots to add it there.
    @State private var placing = false
    @State private var placementID: CourseTemplate.ID?
    @State private var placementLength = 2
    @State private var placementParity: WeekParity = .every
    @State private var placedCount = 0
    /// While placing, also show Saturday and Sunday so weekend classes can be placed.
    @State private var placingOnWeekend = false

    private var today: LocalDate { LocalDate(.now) }
    private var currentWeek: Int { timetable.semester.displayWeek(on: today) }
    private var week: Int { min(selectedWeek ?? currentWeek, timetable.semester.totalWeeks) }

    private var visibleDays: [Weekday] {
        timetable.hasWeekendCourses || (placing && placingOnWeekend) ? Weekday.allCases : Array(Weekday.allCases.prefix(5))
    }

    var body: some View {
        let templates = timetable.placementTemplates(saved: templateRecords.map(\.template))
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
                            showsAllWeeks: placing,
                            onSelectCourse: { editorTarget = .edit($0) },
                            onSelectSlot: { day, period in selectSlot(day, period, templates: templates) }
                        )
                    }
                    .overlay(alignment: .bottom) {
                        if timetable.courses.isEmpty && !placing {
                            VStack(spacing: 6) {
                                Text("Tap an empty slot or + to add a course")
                                HStack(spacing: 16) {
                                    Button("Type Course List…") { startPlacing(templates: templates) }
                                    Button("Import Courses…") { importing = true }
                                }
                                .fontWeight(.semibold)
                            }
                            .font(.callout)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .padding(.bottom, 24)
                        }
                    }
                }
            }
            // Swiping the grid changes the week. Attached before the tray is added below, so
            // scrolling the tray's course chips doesn't.
            .simultaneousGesture(
                DragGesture(minimumDistance: 30).onEnded { value in
                    let dx = value.translation.width
                    guard abs(dx) > 60, abs(dx) > abs(value.translation.height) * 1.5 else { return }
                    step(dx < 0 ? 1 : -1)
                }
            )
            .safeAreaInset(edge: .bottom) {
                if placing {
                    PlacementTray(
                        templates: templates,
                        savedIDs: Set(templateRecords.map(\.id)),
                        selection: $placementID,
                        length: $placementLength,
                        parity: $placementParity,
                        showsWeekend: timetable.hasWeekendCourses ? nil : $placingOnWeekend,
                        periodsPerDay: timetable.periods.count,
                        onAddCourses: { editingCourseList = true },
                        onRemove: { removeTemplate($0) }
                    )
                }
            }
            .sensoryFeedback(.success, trigger: placedCount)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) { weekSwitcher }
                ToolbarItem(placement: .topBarLeading) {
                    if week != currentWeek {
                        Button("This Week") { selectedWeek = nil }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if placing {
                        Button("Done") { placing = false }
                            .fontWeight(.semibold)
                    } else {
                        Menu {
                            Button {
                                editorTarget = .new(weekday: today.weekday, period: 1)
                            } label: {
                                Label("New Course", systemImage: "square.and.pencil")
                            }
                            Button {
                                startPlacing(templates: templates)
                            } label: {
                                Label("Place Courses", systemImage: "hand.tap")
                            }
                            Button {
                                importing = true
                            } label: {
                                Label("Import Courses…", systemImage: "square.and.arrow.down")
                            }
                            .accessibilityIdentifier("importCourses")
                        } label: {
                            Label("Add Course", systemImage: "plus")
                        }
                        .accessibilityIdentifier("addMenu")
                    }
                }
            }
            .sheet(item: $editorTarget) { target in
                CourseEditorView(timetable: timetable, target: target)
            }
            .sheet(isPresented: $importing) {
                ImportView(timetable: timetable)
            }
            .sheet(isPresented: $editingCourseList) {
                CourseListView(timetable: timetable) { added in
                    if let first = added.first {
                        placementID = first.id
                    }
                }
            }
        }
    }

    /// Enters placing mode with a course selected, opening the course list if there's nothing to place.
    private func startPlacing(templates: [CourseTemplate]) {
        placing = true
        if !templates.contains(where: { $0.id == placementID }) {
            placementID = templates.first?.id
        }
        if templates.isEmpty {
            editingCourseList = true
        }
    }

    /// In placing mode with a course selected, adds it at the tapped slot; otherwise opens a new course there.
    private func selectSlot(_ day: Weekday, _ period: Int, templates: [CourseTemplate]) {
        guard placing, let template = templates.first(where: { $0.id == placementID }) else {
            editorTarget = .new(weekday: day, period: period)
            return
        }
        let course = timetable.placing(template, on: day, at: period, periodCount: placementLength, parity: placementParity)
        context.insert(CourseRecord(course))
        try? context.save()
        placedCount += 1
    }

    private func removeTemplate(_ id: CourseTemplate.ID) {
        for record in templateRecords where record.id == id {
            context.delete(record)
        }
        try? context.save()
        if placementID == id {
            placementID = nil
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
            .accessibilityIdentifier("weekMenu")

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
    /// Also show courses that don't meet in `week`, dimmed (used while placing courses).
    let showsAllWeeks: Bool
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

            ForEach(timetable.gridPlacements(inWeek: week, includingOtherWeeks: showsAllWeeks)) { placement in
                if let x = layout.x(for: placement.weekday) {
                    let laneWidth = layout.columnWidth / CGFloat(placement.laneCount)
                    CourseBlock(course: placement.course)
                        .opacity(placement.isInWeek ? 1 : 0.35)
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
                        .accessibilityIdentifier("courseBlock")
                }
            }
        }
        .frame(width: layout.totalWidth, height: CGFloat(periodCount) * GridLayout.rowHeight, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("weekGrid")
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
        // Small type and padding: day columns are only ~70 points wide (~50 with the weekend shown),
        // and English words wider than that get broken mid-word.
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: course.name)
                .font(.system(size: 11, weight: .semibold))
                .lineLimit(5)
            if !course.room.isEmpty {
                Text(verbatim: "@\(course.room)")
                    .font(.system(size: 10))
                    .lineLimit(2)
            }
        }
        .foregroundStyle(.white)
        .padding(3)
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
        .modelContainer(Persistence.previewContainer())
}

/// Bottom panel while placing: choose a course, set its length and weeks, then tap empty slots.
private struct PlacementTray: View {
    let templates: [CourseTemplate]
    /// Templates from the course list (removable), as opposed to ones taken from placed courses.
    let savedIDs: Set<CourseTemplate.ID>
    @Binding var selection: CourseTemplate.ID?
    @Binding var length: Int
    @Binding var parity: WeekParity
    /// `nil` when the weekend is always shown because there are weekend classes.
    let showsWeekend: Binding<Bool>?
    let periodsPerDay: Int
    let onAddCourses: () -> Void
    let onRemove: (CourseTemplate.ID) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Group {
                    if let selected = templates.first(where: { $0.id == selection }) {
                        Text("Tap empty slots to place \(selected.name).")
                    } else if templates.isEmpty {
                        Text("Add your courses first, then tap empty slots to place them.")
                    } else {
                        Text("Choose a course, then tap empty slots to place it.")
                    }
                }
                .foregroundStyle(.secondary)
                Spacer(minLength: 8)
                if let showsWeekend {
                    Toggle("Weekend", isOn: showsWeekend)
                        .toggleStyle(.button)
                        .accessibilityIdentifier("weekendToggle")
                }
            }
            .font(.footnote)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(templates) { template in
                        let chip = TemplateChip(template: template, isSelected: template.id == selection) {
                            selection = template.id == selection ? nil : template.id
                        }
                        if savedIDs.contains(template.id) {
                            chip.contextMenu {
                                Button("Remove from List", role: .destructive) { onRemove(template.id) }
                            }
                        } else {
                            chip
                        }
                    }
                    Button(action: onAddCourses) {
                        Label("Add Courses…", systemImage: "plus")
                            .font(.subheadline)
                            .padding(.horizontal, 12)
                            .frame(minHeight: 40)
                            .background(Color.secondary.opacity(0.12), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.vertical, 2)
            }
            .accessibilityIdentifier("courseChips")

            Stepper(value: $length, in: 1...max(periodsPerDay, 1)) {
                Text("Length: \(length) periods")
                    .font(.subheadline)
            }
            Picker("Repeats", selection: $parity) {
                ForEach(WeekParity.allCases, id: \.self) { parity in
                    Text(verbatim: parity.displayName).tag(parity)
                }
            }
            .pickerStyle(.segmented)
        }
        .padding()
        .background(.bar)
    }
}

private struct TemplateChip: View {
    let template: CourseTemplate
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Circle()
                    .fill(template.color)
                    .frame(width: 10, height: 10)
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: template.name)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    if !template.room.isEmpty {
                        Text(verbatim: template.room)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
            }
            .padding(.horizontal, 12)
            .frame(minHeight: 40)
            .background(isSelected ? template.color.opacity(0.25) : Color.secondary.opacity(0.12), in: Capsule())
            .overlay {
                Capsule().strokeBorder(isSelected ? template.color : Color.clear, lineWidth: 2)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("courseChip")
    }
}
