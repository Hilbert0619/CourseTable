import Foundation

/// How a focus session is laid out: its length (breaks included) and the Pomodoro rhythm.
public struct FocusSettings: Hashable, Codable, Sendable {
    public var totalMinutes: Int
    /// Off: one straight countdown.
    public var usesBreaks: Bool
    public var focusMinutes: Int
    public var shortBreakMinutes: Int
    public var longBreakMinutes: Int
    /// A long break after every this many focus blocks.
    public var blocksBeforeLongBreak: Int

    public static let totalRange = 15...240
    public static let focusRange = 10...90
    public static let shortBreakRange = 1...30
    public static let longBreakRange = 5...60

    public init(totalMinutes: Int = 120, usesBreaks: Bool = true, focusMinutes: Int = 25, shortBreakMinutes: Int = 5,
                longBreakMinutes: Int = 15, blocksBeforeLongBreak: Int = 4) {
        self.totalMinutes = totalMinutes
        self.usesBreaks = usesBreaks
        self.focusMinutes = focusMinutes
        self.shortBreakMinutes = shortBreakMinutes
        self.longBreakMinutes = longBreakMinutes
        self.blocksBeforeLongBreak = blocksBeforeLongBreak
    }
}

/// One stretch of a focus session.
public struct FocusPhase: Hashable, Codable, Sendable {
    public enum Kind: String, Hashable, Codable, Sendable {
        case focus, shortBreak, longBreak
    }

    public var kind: Kind
    public var seconds: Int
    /// 1 for the first focus block, 2 for the second…; for a break, the block before it.
    public var block: Int

    public init(kind: Kind, seconds: Int, block: Int) {
        self.kind = kind
        self.seconds = seconds
        self.block = block
    }

    public var isBreak: Bool { kind != .focus }
}

/// The phases of a session, filling its length exactly. It never ends with a break: when what's left after a
/// block is too short for a break and at least 5 minutes of focus, the last block takes it; otherwise the last
/// block is shortened so the session ends on time.
public struct FocusPlan: Hashable, Codable, Sendable {
    public var phases: [FocusPhase]

    /// The shortest focus block the plan adds after a break.
    public static let shortestBlockSeconds = 5 * 60

    public init(phases: [FocusPhase]) {
        self.phases = phases
    }

    public init(_ settings: FocusSettings) {
        let total = FocusSettings.totalRange.clamped(settings.totalMinutes) * 60
        guard settings.usesBreaks else {
            self.init(phases: [FocusPhase(kind: .focus, seconds: total, block: 1)])
            return
        }
        let focus = FocusSettings.focusRange.clamped(settings.focusMinutes) * 60
        let shortBreak = FocusSettings.shortBreakRange.clamped(settings.shortBreakMinutes) * 60
        let longBreak = FocusSettings.longBreakRange.clamped(settings.longBreakMinutes) * 60
        let every = max(settings.blocksBeforeLongBreak, 1)
        var phases: [FocusPhase] = []
        var left = total
        var block = 0
        while left > 0 {
            block += 1
            let length = min(focus, left)
            phases.append(FocusPhase(kind: .focus, seconds: length, block: block))
            left -= length
            guard left > 0 else { break }
            let isLong = block % every == 0
            let pause = isLong ? longBreak : shortBreak
            if left < pause + Self.shortestBlockSeconds {
                // Not enough for a break and another block: the last block runs to the end.
                phases[phases.count - 1].seconds += left
                break
            }
            phases.append(FocusPhase(kind: isLong ? .longBreak : .shortBreak, seconds: pause, block: block))
            left -= pause
        }
        self.init(phases: phases)
    }

    public var totalSeconds: Int { phases.reduce(0) { $0 + $1.seconds } }
    public var focusSeconds: Int { phases.filter { !$0.isBreak }.reduce(0) { $0 + $1.seconds } }
    public var blockCount: Int { phases.filter { !$0.isBreak }.count }

    /// When each phase starts, in seconds from the start of the session.
    public var offsets: [Int] {
        var offsets: [Int] = []
        var sum = 0
        for phase in phases {
            offsets.append(sum)
            sum += phase.seconds
        }
        return offsets
    }
}

/// Where a running session stands at some moment.
public struct FocusStatus: Hashable, Sendable {
    /// Index into `FocusPlan.phases`; the last phase once the session is over.
    public var phaseIndex: Int
    public var phase: FocusPhase
    /// Seconds of session time so far (pauses don't count, skipped breaks do).
    public var elapsed: TimeInterval
    public var phaseElapsed: TimeInterval
    public var phaseRemaining: TimeInterval
    /// Focus time so far, without breaks.
    public var focused: TimeInterval
    public var isPaused: Bool
    public var isFinished: Bool
}

/// A focus session in progress: the plan, when it started, and its pauses. Kept as a value so the app, its Live
/// Activity buttons and a relaunch all work from the same saved state.
public struct FocusRun: Hashable, Codable, Sendable {
    public var id: UUID
    public var goal: String
    public var plan: FocusPlan
    public var startedAt: Date
    /// Time spent in pauses that have ended.
    public var pausedSeconds: TimeInterval
    /// The current pause, if paused.
    public var pausedAt: Date?
    /// Break time skipped so far: it counts as elapsed.
    public var skippedSeconds: TimeInterval

    public init(id: UUID = UUID(), goal: String, plan: FocusPlan, startedAt: Date) {
        self.id = id
        self.goal = goal
        self.plan = plan
        self.startedAt = startedAt
        self.pausedSeconds = 0
        self.pausedAt = nil
        self.skippedSeconds = 0
    }

    public var isPaused: Bool { pausedAt != nil }

    /// Session time at `date`: wall time since the start, minus pauses, plus skipped breaks; at most the plan's length.
    public func elapsed(at date: Date) -> TimeInterval {
        let end = pausedAt ?? date
        let raw = end.timeIntervalSince(startedAt) - pausedSeconds + skippedSeconds
        return min(max(raw, 0), TimeInterval(plan.totalSeconds))
    }

    public func status(at date: Date) -> FocusStatus {
        let elapsed = elapsed(at: date)
        let offsets = plan.offsets
        var index = 0
        while index + 1 < plan.phases.count && TimeInterval(offsets[index + 1]) <= elapsed {
            index += 1
        }
        let phase = plan.phases[index]
        let phaseElapsed = min(elapsed - TimeInterval(offsets[index]), TimeInterval(phase.seconds))
        var focused: TimeInterval = 0
        for (offset, item) in zip(offsets, plan.phases) where !item.isBreak {
            focused += min(max(elapsed - TimeInterval(offset), 0), TimeInterval(item.seconds))
        }
        return FocusStatus(
            phaseIndex: index, phase: phase, elapsed: elapsed, phaseElapsed: phaseElapsed,
            phaseRemaining: TimeInterval(phase.seconds) - phaseElapsed, focused: focused, isPaused: isPaused,
            isFinished: elapsed >= TimeInterval(plan.totalSeconds)
        )
    }

    /// When the session ends if nothing changes; `nil` while paused.
    public func endDate(at date: Date) -> Date? {
        guard !isPaused else { return nil }
        return date.addingTimeInterval(TimeInterval(plan.totalSeconds) - elapsed(at: date))
    }

    /// The upcoming switches (into phase `index`, or the end when `index == phases.count`) with their dates,
    /// earliest first, up to `limit`; none while paused. These are the notifications to schedule.
    public func upcomingSwitches(at date: Date, limit: Int = FocusRun.maxAlerts) -> [(index: Int, date: Date)] {
        guard !isPaused else { return [] }
        let elapsed = elapsed(at: date)
        let starts = plan.offsets.map(TimeInterval.init) + [TimeInterval(plan.totalSeconds)]
        var switches: [(index: Int, date: Date)] = []
        for (index, start) in starts.enumerated() where index > 0 && start > elapsed {
            switches.append((index, date.addingTimeInterval(start - elapsed)))
        }
        return Array(switches.prefix(max(limit, 0)))
    }

    /// Notifications kept for a focus session (see `Timetable.maxTodoAlerts`).
    public static let maxAlerts = 10

    public mutating func pause(at date: Date) {
        guard pausedAt == nil else { return }
        pausedAt = date
    }

    public mutating func resume(at date: Date) {
        guard let pausedAt else { return }
        pausedSeconds += max(date.timeIntervalSince(pausedAt), 0)
        self.pausedAt = nil
    }

    /// Ends the current break now; nothing during focus.
    public mutating func skipBreak(at date: Date) {
        let status = status(at: date)
        guard status.phase.isBreak, !status.isFinished else { return }
        skippedSeconds += status.phaseRemaining
    }
}

extension ClosedRange where Bound == Int {
    func clamped(_ value: Int) -> Int { Swift.min(Swift.max(value, lowerBound), upperBound) }
}
