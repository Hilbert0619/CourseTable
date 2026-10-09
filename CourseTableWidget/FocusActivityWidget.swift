import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

/// A running focus session in the Dynamic Island and on the Lock Screen: the phase, the time left in it and,
/// like the system timer, Pause/Resume and Stop.
struct FocusActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FocusActivityAttributes.self) { context in
            FocusLockScreenView(info: FocusInfo(context: context))
        } dynamicIsland: { context in
            let info = FocusInfo(context: context)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label {
                        Text(verbatim: info.shortPhaseName)
                    } icon: {
                        Image(systemName: info.symbol)
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(info.color)
                    .lineLimit(1)
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    info.timer
                        .font(.title2.monospacedDigit().weight(.semibold))
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 110, alignment: .trailing)
                        .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 8) {
                        if !info.goal.isEmpty {
                            Text(verbatim: info.goal)
                                .font(.headline)
                                .lineLimit(1)
                        }
                        HStack(spacing: 10) {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(verbatim: info.detail)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                                info.progress
                            }
                            FocusButtons(info: info, size: 30)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 4)
                }
            } compactLeading: {
                Image(systemName: info.symbol)
                    .foregroundStyle(info.color)
            } compactTrailing: {
                info.timer
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .multilineTextAlignment(.trailing)
                    .frame(width: 48, alignment: .trailing)
                    .foregroundStyle(info.isPaused ? Color.secondary : info.color)
            } minimal: {
                if info.isPaused {
                    Image(systemName: "pause.fill")
                        .foregroundStyle(info.color)
                } else {
                    info.ring
                }
            }
            .keylineTint(info.color)
        }
    }
}

/// What the views show, worked out once from the activity's context.
private struct FocusInfo {
    let goal: String
    let state: FocusActivityAttributes.ContentState

    init(context: ActivityViewContext<FocusActivityAttributes>) {
        goal = context.attributes.goal
        state = context.state
    }

    var isPaused: Bool { state.isPaused }
    var color: Color { state.isBreak ? .green : .indigo }
    var symbol: String { isPaused ? "pause.fill" : state.isBreak ? "cup.and.saucer.fill" : "brain.head.profile" }

    var phaseName: String {
        if state.isBreak {
            return state.phase == "longBreak" ? String(localized: "Long break") : String(localized: "Short break")
        }
        return state.blockCount > 1 ? String(localized: "Focus · Block \(state.block) of \(state.blockCount)") : String(localized: "Focus")
    }

    /// "Focus 2/5", "Short break", "Long break": fits beside the camera.
    var shortPhaseName: String {
        if state.isBreak { return phaseName }
        return state.blockCount > 1 ? String(localized: "Focus \(state.block)/\(state.blockCount)") : String(localized: "Focus")
    }

    /// "Paused" or "Ends at 21:30".
    var detail: String {
        isPaused ? String(localized: "Paused")
            : String(localized: "Session ends at \(state.sessionEnd.formatted(date: .omitted, time: .shortened))")
    }

    private var interval: ClosedRange<Date> { min(state.phaseStart, state.phaseEnd)...state.phaseEnd }

    /// Counts down to the end of the phase; stands still while paused.
    var timer: Text {
        if let remaining = state.pausedRemaining {
            let total = Int(remaining.rounded(.up))
            return Text(verbatim: String(format: "%d:%02d", total / 60, total % 60))
        }
        return Text(timerInterval: interval, countsDown: true, showsHours: false)
    }

    var progress: some View {
        Group {
            if let remaining = state.pausedRemaining {
                let length = max(state.phaseEnd.timeIntervalSince(state.phaseStart), 1)
                ProgressView(value: max(length - remaining, 0), total: length)
            } else {
                ProgressView(timerInterval: interval, countsDown: false) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
            }
        }
        .tint(color)
    }

    var ring: some View {
        ProgressView(timerInterval: interval, countsDown: true) {
            EmptyView()
        } currentValueLabel: {
            EmptyView()
        }
        .progressViewStyle(.circular)
        .tint(color)
    }
}

private struct FocusLockScreenView: View {
    let info: FocusInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Image(systemName: info.symbol)
                    .foregroundStyle(info.color)
                Text(verbatim: info.goal.isEmpty ? info.phaseName : info.goal)
                    .font(.headline)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                info.timer
                    .font(.title2.monospacedDigit().weight(.semibold))
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 96, alignment: .trailing)
            }
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(verbatim: "\(info.shortPhaseName) · \(info.detail)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                    info.progress
                }
                FocusButtons(info: info, size: 26)
            }
        }
        .padding(16)
        .activitySystemActionForegroundColor(info.color)
    }
}

/// Pause/Resume and Stop.
private struct FocusButtons: View {
    let info: FocusInfo
    let size: CGFloat

    var body: some View {
        HStack(spacing: 8) {
            Button(intent: ToggleFocusPauseIntent()) {
                Image(systemName: info.isPaused ? "play.fill" : "pause.fill")
                    .font(size > 32 ? .body.weight(.bold) : .caption.weight(.bold))
                    .frame(width: size, height: size)
            }
            .tint(info.color)
            .accessibilityLabel(info.isPaused ? Text("Resume") : Text("Pause"))
            Button(intent: StopFocusIntent()) {
                Image(systemName: "xmark")
                    .font(size > 32 ? .body.weight(.bold) : .caption.weight(.bold))
                    .frame(width: size, height: size)
            }
            .tint(Color.secondary)
            .accessibilityLabel(Text("Stop"))
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.circle)
    }
}
