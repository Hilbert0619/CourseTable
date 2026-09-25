/// Where a course block sits in the weekly grid.
///
/// Clashing courses on the same day share the column side by side: `lane` is the block's position
/// (0-based) and `laneCount` how many lanes its group of overlapping blocks needs.
public struct GridPlacement: Hashable, Identifiable, Sendable {
    public var course: Course
    public var lane: Int
    public var laneCount: Int
    /// Whether the course meets in the week shown; `false` only when other weeks are included.
    public var isInWeek = true

    public var id: Course.ID { course.id }
    public var weekday: Weekday { course.weekday }
    public var periods: ClosedRange<Int> { course.periods }
}

extension Timetable {
    /// Block placements for the weekly grid of teaching week `week`.
    ///
    /// With `includingOtherWeeks`, every course that meets at some point in the term is laid out,
    /// and those not meeting in `week` have `isInWeek == false` (shown dimmed).
    public func gridPlacements(inWeek week: Int, includingOtherWeeks: Bool = false) -> [GridPlacement] {
        let shown = includingOtherWeeks ? courses.filter { !$0.weeks.all.isEmpty } : courses(inWeek: week)
        let byDay = Dictionary(grouping: shown, by: \.weekday)
        return Weekday.allCases.flatMap { Self.layOut(byDay[$0] ?? []) }.map { placement in
            var placement = placement
            placement.isInWeek = placement.course.meets(inWeek: week)
            return placement
        }
    }

    /// Greedy interval colouring, then each cluster of overlapping blocks shares one lane count.
    static func layOut(_ dayCourses: [Course]) -> [GridPlacement] {
        let sorted = dayCourses.sorted(by: slotOrder)
        var placements: [GridPlacement] = []
        var cluster: [GridPlacement] = []
        var laneEnds: [Int] = []  // last period occupied in each lane of the current cluster
        var clusterEnd = Int.min

        func closeCluster() {
            let lanes = laneEnds.count
            placements += cluster.map { var p = $0; p.laneCount = lanes; return p }
            cluster.removeAll()
            laneEnds.removeAll()
        }

        for course in sorted {
            if course.startPeriod > clusterEnd { closeCluster() }
            let lane = laneEnds.firstIndex { $0 < course.startPeriod } ?? laneEnds.count
            if lane == laneEnds.count { laneEnds.append(course.endPeriod) } else { laneEnds[lane] = course.endPeriod }
            cluster.append(GridPlacement(course: course, lane: lane, laneCount: 1))
            clusterEnd = max(clusterEnd, course.endPeriod)
        }
        closeCluster()
        return placements
    }
}
