/// Where a course block sits in the weekly grid.
///
/// Clashing courses on the same day share the column side by side: `lane` is the block's position
/// (0-based) and `laneCount` how many lanes its group of overlapping blocks needs.
public struct GridPlacement: Hashable, Identifiable, Sendable {
    public var course: Course
    public var lane: Int
    public var laneCount: Int

    public var id: Course.ID { course.id }
    public var weekday: Weekday { course.weekday }
    public var periods: ClosedRange<Int> { course.periods }
}

extension Timetable {
    /// Block placements for the weekly grid of teaching week `week`.
    public func gridPlacements(inWeek week: Int) -> [GridPlacement] {
        let byDay = Dictionary(grouping: courses(inWeek: week), by: \.weekday)
        return Weekday.allCases.flatMap { Self.layOut(byDay[$0] ?? []) }
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
