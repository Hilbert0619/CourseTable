import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

/// A class as a Live Activity: in the Dynamic Island and on the Lock Screen from the reminder time until
/// the class ends. Before the start it counts down to it; once the class has started (the content's stale
/// date) it shows "Until 09:40" with a progress bar. Timers update on their own: the app sends no updates.
struct ClassActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ClassActivityAttributes.self) { context in
            ClassActivityLockScreenView(context: context)
        } dynamicIsland: { context in
            let info = ClassActivityInfo(context: context)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: info.courseName)
                            .font(.headline)
                            .foregroundStyle(info.color)
                            .lineLimit(1)
                        if !info.details.isEmpty {
                            Text(verbatim: info.details)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 2) {
                        info.caption
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        info.timer(showsHours: true)
                            .font(.title3.monospacedDigit().weight(.semibold))
                            .multilineTextAlignment(.trailing)
                            .frame(maxWidth: 90, alignment: .trailing)
                    }
                    .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack(spacing: 12) {
                        info.when
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        if info.started {
                            info.progress
                        } else {
                            Spacer()
                        }
                        CloseActivityButton(signature: info.signature)
                    }
                    .padding(.horizontal, 4)
                }
            } compactLeading: {
                Text(verbatim: info.courseName)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(info.color)
                    .lineLimit(1)
                    .frame(maxWidth: 64, alignment: .leading)
            } compactTrailing: {
                // Minutes only ("72:30"), so a long class fits.
                info.timer(showsHours: false)
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .multilineTextAlignment(.trailing)
                    .frame(width: 48, alignment: .trailing)
            } minimal: {
                info.ring
            }
            .keylineTint(info.color)
        }
    }
}

/// What the views show, worked out once from the activity's context.
private struct ClassActivityInfo {
    let attributes: ClassActivityAttributes
    let state: ClassActivityAttributes.ContentState
    /// The class has started: the stale date (its start) has passed.
    let started: Bool

    init(context: ActivityViewContext<ClassActivityAttributes>) {
        attributes = context.attributes
        state = context.state
        started = context.isStale
    }

    var signature: String { attributes.signature }
    var courseName: String { attributes.courseName }
    var details: String { attributes.details }
    var color: Color { CoursePalette.color(attributes.colorIndex) }

    /// Before the start: from when it appeared to the start; then the class itself.
    private var interval: ClosedRange<Date> {
        started ? state.start...max(state.start, state.end) : min(attributes.showDate, state.start)...state.start
    }

    var caption: Text {
        started ? Text("Ends in") : Text("Starts in")
    }

    /// "08:00–09:40" before the start, then "Until 09:40".
    var when: Text {
        started ? Text("Until \(attributes.endTime)") : Text(verbatim: attributes.times)
    }

    /// Counts down to the start, then to the end.
    func timer(showsHours: Bool) -> Text {
        Text(timerInterval: interval, countsDown: true, showsHours: showsHours)
    }

    var progress: some View {
        ProgressView(timerInterval: interval, countsDown: false) {
            EmptyView()
        } currentValueLabel: {
            EmptyView()
        }
        .tint(color)
    }

    var ring: some View {
        ProgressView(timerInterval: interval, countsDown: !started) {
            EmptyView()
        } currentValueLabel: {
            EmptyView()
        }
        .progressViewStyle(.circular)
        .tint(color)
    }
}

struct ClassActivityLockScreenView: View {
    let context: ActivityViewContext<ClassActivityAttributes>

    var body: some View {
        let info = ClassActivityInfo(context: context)
        HStack(alignment: .center, spacing: 12) {
            RoundedRectangle(cornerRadius: 3)
                .fill(info.color)
                .frame(width: 6)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: info.courseName)
                    .font(.headline)
                    .lineLimit(1)
                HStack(spacing: 6) {
                    info.when
                    if !info.details.isEmpty {
                        Text(verbatim: "· \(info.details)")
                    }
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                if info.started {
                    info.progress
                }
            }
            Spacer(minLength: 4)
            VStack(alignment: .trailing, spacing: 2) {
                info.caption
                    .font(.caption)
                    .foregroundStyle(.secondary)
                info.timer(showsHours: true)
                    .font(.title2.monospacedDigit().weight(.semibold))
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 110, alignment: .trailing)
            }
            CloseActivityButton(signature: info.signature)
        }
        .padding(16)
        .activitySystemActionForegroundColor(info.color)
    }
}

/// Ends the activity: Live Activities can't remove themselves at a set time without a server.
private struct CloseActivityButton: View {
    let signature: String

    var body: some View {
        Button(intent: EndClassActivityIntent(signature: signature)) {
            Image(systemName: "xmark")
                .font(.caption.weight(.bold))
                .frame(width: 28, height: 28)
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.circle)
        .tint(Color.secondary)
        .accessibilityLabel(Text("Close"))
    }
}
