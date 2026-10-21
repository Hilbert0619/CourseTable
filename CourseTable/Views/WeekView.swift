import SwiftUI
import SwiftData
import UIKit
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
    @AppStorage(TimetableAppearance.blockStyleKey) private var blockStyle = TimetableAppearance.defaultBlockStyle
    @AppStorage(BackgroundPhoto.blockOpacityKey) private var blockOpacity = BackgroundPhoto.defaultBlockOpacity
    @AppStorage(AppAppearance.styleKey) private var appStyle = AppAppearance.defaultStyle
    @AppStorage(AppBackground.key) private var background = ""

    private var today: LocalDate { LocalDate(.now) }
    /// What the day row and the period times sit on: frosted glass, the paper over a picture, or nothing.
    private var labelSurface: LabelSurface {
        if appStyle == .glass { return .frosted }
        return AppBackground.resolve(background, style: appStyle) == .paper ? .none : .paper
    }
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
                    DayHeader(layout: layout, semester: timetable.semester, week: week, today: today, surface: labelSurface)
                    Divider()
                    ScrollView {
                        // Each minute: the current-time line and the class in progress.
                        TimelineView(.everyMinute) { context in
                            WeekGrid(
                                layout: layout,
                                timetable: timetable,
                                week: week,
                                today: today,
                                now: Moment(context.date),
                                blockStyle: blockStyle,
                                blockOpacity: labelSurface == .none ? 1 : blockOpacity,
                                glass: appStyle == .glass,
                                labelSurface: labelSurface,
                                showsAllWeeks: placing,
                                onSelectCourse: { editorTarget = .edit($0) },
                                onSelectSlot: { day, period in selectSlot(day, period, templates: templates) }
                            )
                        }
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
                            .modifier(HintSurface(glass: appStyle == .glass))
                            .padding(.bottom, 24)
                        }
                    }
                }
            }
            .background { AppBackdrop(style: appStyle) }
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

/// Behind the day row and the period times, so they stay readable on a picture.
private enum LabelSurface {
    case none
    /// The paper, mostly opaque (the classic look on a picture).
    case paper
    /// Frosted glass (the glass look).
    case frosted

    @ViewBuilder
    var fill: some View {
        switch self {
        case .none: Color.clear
        case .paper: Theme.paper.opacity(0.82)
        case .frosted: FrostedFill()
        }
    }
}

/// The empty timetable's hint: glass, or the regular material.
private struct HintSurface: ViewModifier {
    let glass: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if glass {
            content.glassCard(cornerRadius: 16)
        } else {
            content.background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }
}

private struct DayHeader: View {
    let layout: GridLayout
    let semester: Semester
    let week: Int
    let today: LocalDate
    var surface = LabelSurface.none

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
                // Not into the navigation bar above (a colour background goes into the safe area by default).
                .background(isToday ? Color.accentColor.opacity(0.12) : Color.clear, ignoresSafeAreaEdges: [])
            }
        }
        // Not into the navigation bar above.
        .background { surface.fill }
        .clipped()
    }
}

private struct WeekGrid: View {
    let layout: GridLayout
    let timetable: Timetable
    let week: Int
    let today: LocalDate
    let now: Moment
    let blockStyle: CourseBlockStyle
    let blockOpacity: Double
    /// Course blocks of glass.
    let glass: Bool
    /// Behind the period times.
    let labelSurface: LabelSurface
    /// Also show courses that don't meet in `week`, dimmed (used while placing courses).
    let showsAllWeeks: Bool
    let onSelectCourse: (Course) -> Void
    let onSelectSlot: (Weekday, Int) -> Void

    var body: some View {
        let periodCount = timetable.periods.count
        let current = timetable.currentAndNext(at: now).current
        ZStack(alignment: .topLeading) {
            if labelSurface != .none {
                labelSurface.fill
                    .frame(width: GridLayout.timeColumnWidth, height: CGFloat(periodCount) * GridLayout.rowHeight)
            }
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
                    let isCurrent = placement.isInWeek && current?.course.id == placement.course.id
                        && current?.day == timetable.semester.date(of: placement.weekday, inWeek: week)
                    CourseBlock(course: placement.course, style: blockStyle, isCurrent: isCurrent, fillOpacity: blockOpacity, glass: glass)
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

            // The current time in today's column, like Calendar's red line.
            if now.day == today, let x = layout.x(for: today.weekday),
               timetable.semester.date(of: today.weekday, inWeek: week) == today,
               let position = timetable.periods.gridPosition(of: now.time) {
                NowLine(width: layout.columnWidth)
                    .offset(x: x, y: CGFloat(position) * GridLayout.rowHeight - NowLine.dotSize / 2)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
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

/// A course on the grid: soft (a tint, same-hue text and a stripe in the course's colour) or solid (the colour with
/// white text), of glass in the glass look, and outlined while the class is on.
struct CourseBlock: View {
    let course: Course
    var style: CourseBlockStyle = TimetableAppearance.defaultBlockStyle
    var isCurrent = false
    /// Below 1 when a picture is behind the timetable (the text and stripe stay solid).
    var fillOpacity: Double = 1
    /// Glass tinted with the block's colour.
    var glass = false
    /// Small type: day columns are only ~70 points wide (~50 with the weekend shown), and English words wider than
    /// that get broken mid-word. It grows with Dynamic Type, but only so far.
    @ScaledMetric(relativeTo: .caption2) private var nameSize: CGFloat = 11
    @ScaledMetric(relativeTo: .caption2) private var roomSize: CGFloat = 10

    var body: some View {
        let colors = CoursePalette.block(course.colorIndex, style: style)
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        let leading: CGFloat = style == .soft ? 6 : 3
        GeometryReader { proxy in
            let width = proxy.size.width - leading - 3
            let room = "@\(course.room)"
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: course.name)
                    .font(.system(size: Self.fitting(course.name, preferred: min(nameSize, 14), width: width, weight: .semibold),
                                  weight: .semibold))
                    .lineLimit(5)
                if !course.room.isEmpty {
                    Text(verbatim: room)
                        .font(.system(size: Self.fitting(room, preferred: min(roomSize, 12), width: width, weight: .regular)))
                        .lineLimit(2)
                }
            }
            .foregroundStyle(colors.text)
            .padding(.vertical, 3)
            .padding(.leading, leading)
            .padding(.trailing, 3)
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
        .modifier(BlockSurface(fill: colors.fill, stripe: style == .soft ? course.color : nil,
                               // Solid blocks carry white text: they keep more of their colour.
                               opacity: glass ? fillOpacity * (style == .soft ? 0.75 : 0.95) : fillOpacity, glass: glass))
        .overlay {
            if isCurrent {
                shape.strokeBorder(style == .soft ? course.color : Color.primary.opacity(0.7), lineWidth: 2)
            }
        }
        .shadow(color: isCurrent ? course.color.opacity(0.45) : .clear, radius: 6)
        .accessibilityElement(children: .combine)
        .accessibilityValue(isCurrent ? Text("Now") : Text(verbatim: ""))
    }
}

/// A course block's fill and stripe: on the grid, or as tinted glass.
private struct BlockSurface: ViewModifier {
    let fill: Color
    let stripe: Color?
    let opacity: Double
    let glass: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        if glass {
            content
                .overlay(alignment: .leading) { stripeView }
                .clipShape(shape)
                .glassCard(tint: fill.opacity(opacity), cornerRadius: 6)
        } else {
            content.background {
                shape.fill(fill.opacity(opacity))
                    .overlay(alignment: .leading) { stripeView }
                    .clipShape(shape)
            }
        }
    }

    @ViewBuilder
    private var stripeView: some View {
        if let stripe {
            Rectangle().fill(stripe).frame(width: 3)
        }
    }
}

extension CourseBlock {
    /// The largest size up to `preferred` (at least 8) at which every Latin word of `text` fits `width`, so a long
    /// English word shrinks instead of breaking mid-word. Chinese wraps between characters anyway.
    static func fitting(_ text: String, preferred: CGFloat, width: CGFloat, weight: UIFont.Weight) -> CGFloat {
        let words = text.split(whereSeparator: \.isWhitespace).filter { $0.unicodeScalars.allSatisfy(\.isASCII) }
        guard width > 0, !words.isEmpty else { return preferred }
        let font = UIFont.systemFont(ofSize: preferred, weight: weight)
        let widest = words.map { (String($0) as NSString).size(withAttributes: [.font: font]).width }.max() ?? 0
        guard widest > width else { return preferred }
        // Text width grows in proportion to the size; half-point steps, a little room to spare.
        return max(8, ((preferred * width / widest) * 2).rounded(.down) / 2 - 0.5)
    }
}

/// The current time across today's column: a dot at the left and a line.
private struct NowLine: View {
    static let dotSize: CGFloat = 8
    let width: CGFloat

    var body: some View {
        HStack(spacing: 0) {
            Circle().frame(width: Self.dotSize, height: Self.dotSize)
            Rectangle().frame(height: 2)
        }
        .foregroundStyle(.red)
        .frame(width: width, height: Self.dotSize)
        .offset(x: -Self.dotSize / 2)
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
