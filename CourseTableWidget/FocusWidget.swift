import AppIntents
import SwiftUI
import WidgetKit
import CourseKit

/// Focus on the Home Screen: today's focus time and streak with a Start button, or the session in progress with
/// Pause and Stop. The buttons run in the app (`StartFocusIntent`, `ToggleFocusPauseIntent`, `StopFocusIntent`).
struct FocusEntry: TimelineEntry, Sendable {
    let date: Date
    /// `nil` until the app has written a planner snapshot.
    let state: FocusWidgetState?
}

struct FocusProvider: TimelineProvider {
    func placeholder(in context: Context) -> FocusEntry {
        .preview
    }

    func getSnapshot(in context: Context, completion: @escaping (FocusEntry) -> Void) {
        if context.isPreview {
            completion(.preview)
        } else {
            completion(Self.entries(from: .now, planner: SnapshotStore.readPlanner()).first ?? .preview)
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<FocusEntry>) -> Void) {
        // An entry at each switch between focus and break and at the end, the last at midnight. A pause or a new
        // session comes from the app, which reloads the widget.
        completion(Timeline(entries: Self.entries(from: .now, planner: SnapshotStore.readPlanner()), policy: .atEnd))
    }

    static func entries(from now: Date, planner: PlannerSnapshot?) -> [FocusEntry] {
        guard let planner else { return [FocusEntry(date: now, state: nil)] }
        return planner.focusTimelineDates(from: now).map { FocusEntry(date: $0, state: planner.focusState(at: $0)) }
    }
}

struct FocusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetKinds.focus, provider: FocusProvider()) { entry in
            FocusWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Focus")
        .description("Today's focus time and streak. Start a session right from the Home Screen.")
        .supportedFamilies([.systemSmall])
    }
}

struct FocusWidgetView: View {
    let entry: FocusEntry

    var body: some View {
        Group {
            if let state = entry.state {
                if let run = state.run {
                    RunningFocusView(state: FocusActivityAttributes.ContentState(run, at: entry.date), goal: run.goal)
                } else {
                    IdleFocusView(state: state)
                }
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    Image(systemName: "timer")
                        .font(.title2)
                        .foregroundStyle(.indigo)
                        .widgetAccentable()
                    Text("Open Course Table to start focusing.")
                        .font(.footnote)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

/// Today's time, the streak and Start.
private struct IdleFocusView: View {
    let state: FocusWidgetState

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Label("Focus", systemImage: "timer")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.indigo)
                .widgetAccentable()
            Spacer(minLength: 0)
            Text(verbatim: Self.duration(state.todaySeconds))
                .font(.system(.title, design: .rounded).weight(.semibold))
                .minimumScaleFactor(0.6)
                .lineLimit(1)
            Text("Focused today")
                .font(.caption)
                .foregroundStyle(.secondary)
            if state.streakDays > 0 {
                Label("\(state.streakDays) days in a row", systemImage: "flame.fill")
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.orange)
                    .lineLimit(1)
                    .padding(.top, 1)
            }
            Spacer(minLength: 6)
            Button(intent: StartFocusIntent()) {
                Label("\(StartFocusIntent.minutes) min", systemImage: "play.fill")
                    .font(.caption.weight(.semibold))
                    .frame(maxWidth: .infinity)
            }
            .modifier(PrimaryWidgetButton(color: .indigo))
            .buttonBorderShape(.capsule)
            .accessibilityLabel(Text("Start a 25-minute focus session"))
        }
    }

    /// "0 min", "45 min", "1 hr, 20 min", in the system's words.
    static func duration(_ seconds: Int) -> String {
        let minutes = seconds / 60
        guard minutes >= 60 else { return String(localized: "\(minutes) min") }
        return Duration.seconds(minutes * 60).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated))
    }
}

/// The phase, the time left in it, Pause / Resume and Stop.
private struct RunningFocusView: View {
    let state: FocusActivityAttributes.ContentState
    let goal: String

    private var color: Color { state.isBreak ? .green : .indigo }

    private var phaseName: String {
        if state.isBreak {
            return state.phase == FocusPhase.Kind.longBreak.rawValue ? String(localized: "Long break") : String(localized: "Short break")
        }
        return state.blockCount > 1 ? String(localized: "Focus \(state.block)/\(state.blockCount)") : String(localized: "Focus")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Label {
                Text(verbatim: phaseName)
            } icon: {
                Image(systemName: state.isPaused ? "pause.fill" : state.isBreak ? "cup.and.saucer.fill" : "brain.head.profile")
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(color)
            .lineLimit(1)
            .widgetAccentable()
            if !goal.isEmpty {
                Text(verbatim: goal)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            timer
                .font(.system(size: 34, weight: .semibold, design: .rounded).monospacedDigit())
                .minimumScaleFactor(0.6)
                .lineLimit(1)
            (state.isPaused ? Text("Paused") : Text("Left in this phase"))
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer(minLength: 6)
            HStack(spacing: 8) {
                Button(intent: ToggleFocusPauseIntent()) {
                    Image(systemName: state.isPaused ? "play.fill" : "pause.fill")
                        .font(.footnote.weight(.bold))
                        .frame(maxWidth: .infinity)
                }
                .modifier(PrimaryWidgetButton(color: color))
                .accessibilityLabel(state.isPaused ? Text("Resume") : Text("Pause"))
                Button(intent: StopFocusIntent()) {
                    Image(systemName: "stop.fill")
                        .font(.footnote.weight(.bold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(Color.secondary)
                .accessibilityLabel(Text("Stop"))
            }
            .buttonBorderShape(.capsule)
        }
    }

    /// Counts down to the end of the phase; stands still while paused.
    private var timer: Text {
        if let remaining = state.pausedRemaining {
            let total = Int(remaining.rounded(.up))
            return Text(verbatim: String(format: "%d:%02d", total / 60, total % 60))
        }
        return Text(timerInterval: min(state.phaseStart, state.phaseEnd)...state.phaseEnd, countsDown: true, showsHours: false)
    }
}

/// A filled button in full colour. On the Clear and Tinted Home Screen iOS tints a filled button's background and
/// its label alike (a blank capsule in the 0.7.2 screenshots), so there it's a bordered one.
private struct PrimaryWidgetButton: ViewModifier {
    @Environment(\.widgetRenderingMode) private var renderingMode
    let color: Color

    func body(content: Content) -> some View {
        if renderingMode == .fullColor {
            content
                .buttonStyle(.borderedProminent)
                .tint(color)
        } else {
            content
                .buttonStyle(.bordered)
                .widgetAccentable()
        }
    }
}

extension FocusEntry {
    /// Sample content for the widget gallery and previews.
    static var preview: FocusEntry {
        FocusEntry(date: .now, state: FocusWidgetState(todaySeconds: 50 * 60, streakDays: 4, run: nil, status: nil))
    }
}

#Preview(as: .systemSmall) {
    FocusWidget()
} timeline: {
    FocusEntry.preview
    FocusEntry(date: .now, state: FocusWidgetState(
        todaySeconds: 600, streakDays: 1,
        run: FocusRun(goal: "Calculus review", plan: FocusPlan(FocusSettings(totalMinutes: 60)), startedAt: .now.addingTimeInterval(-420)),
        status: nil
    ))
    FocusEntry(date: .now, state: nil)
}
