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
                // Like the system timer: the button on the left, the time on the right, and the class
                // below at full width, so long course names fit.
                DynamicIslandExpandedRegion(.leading) {
                    CloseActivityButton(signature: info.signature, size: 40)
                        .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 0) {
                        info.caption
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        info.timer
                            .font(.title2.monospacedDigit().weight(.semibold))
                            .multilineTextAlignment(.trailing)
                            .frame(maxWidth: 100, alignment: .trailing)
                    }
                    .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(verbatim: info.courseName)
                            .font(.headline)
                            .foregroundStyle(info.color)
                            .lineLimit(1)
                        info.whenAndWhere
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        if info.started {
                            info.progress
                                .padding(.top, 2)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 4)
                }
            } compactLeading: {
                Text(verbatim: info.courseName)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(info.color)
                    .lineLimit(1)
                    .frame(maxWidth: 88, alignment: .leading)
            } compactTrailing: {
                info.timer
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
    /// The class has started: the stale date (its start) has passed, or the activity began after it.
    let started: Bool

    init(context: ActivityViewContext<ClassActivityAttributes>) {
        attributes = context.attributes
        state = context.state
        started = context.isStale || context.state.start <= .now
    }

    var signature: String { attributes.signature }
    var courseName: String { attributes.courseName }
    var color: Color { CoursePalette.color(attributes.colorIndex) }

    /// Before the start: from when it appeared to the start; then the class itself.
    private var interval: ClosedRange<Date> {
        started ? state.start...max(state.start, state.end) : min(attributes.showDate, state.start)...state.start
    }

    var caption: Text {
        started ? Text("Ends in") : Text("Starts in")
    }

    /// "08:00–09:40" before the start, then "Until 09:40".
    var when: String {
        started ? String(localized: "Until \(attributes.endTime)") : attributes.times
    }

    /// Room and teacher, e.g. "A101 · Li"; may be empty.
    var details: String { attributes.details }

    /// Both on one line: "08:00–09:40 · A101 · Li", then "Until 09:40 · A101 · Li".
    var whenAndWhere: Text {
        Text(verbatim: [when, details].filter { !$0.isEmpty }.joined(separator: " · "))
    }

    /// Counts down to the start, then to the end, in minutes ("72:30") so a long class fits.
    var timer: Text {
        Text(timerInterval: interval, countsDown: true, showsHours: false)
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
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                // As tall as the text beside it (the stack has a fixed height).
                RoundedRectangle(cornerRadius: 3)
                    .fill(info.color)
                    .frame(width: 6)
                    .frame(maxHeight: .infinity)
                VStack(alignment: .leading, spacing: 3) {
                    Text(verbatim: info.courseName)
                        .font(.headline)
                        .lineLimit(2)
                    Group {
                        Text(verbatim: info.when)
                        if !info.details.isEmpty {
                            Text(verbatim: info.details)
                        }
                    }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .trailing, spacing: 0) {
                    info.caption
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    info.timer
                        .font(.title2.monospacedDigit().weight(.semibold))
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 84, alignment: .trailing)
                }
                CloseActivityButton(signature: info.signature, size: 28)
            }
            .fixedSize(horizontal: false, vertical: true)
            if info.started {
                info.progress
            }
        }
        .padding(16)
        .activitySystemActionForegroundColor(info.color)
    }
}

/// Ends the activity: Live Activities can't remove themselves at a set time without a server.
private struct CloseActivityButton: View {
    let signature: String
    let size: CGFloat

    var body: some View {
        Button(intent: EndClassActivityIntent(signature: signature)) {
            Image(systemName: "xmark")
                .font(size > 32 ? .body.weight(.bold) : .caption.weight(.bold))
                .frame(width: size, height: size)
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.circle)
        .tint(Color.secondary)
        .accessibilityLabel(Text("Close"))
    }
}
