import SwiftUI
import CourseKit

/// The Focus tab: set up a session (goal, length, Pomodoro rhythm, sound), run it, and see today's focus time.
struct FocusView: View {
    @Environment(PlannerModel.self) private var planner
    private var engine: FocusEngine { FocusEngine.shared }

    var body: some View {
        NavigationStack {
            Group {
                if let run = engine.run {
                    RunningFocusView(engine: engine, run: run)
                } else {
                    FocusSetupView(engine: engine, planner: planner)
                }
            }
            .navigationTitle("Focus")
        }
    }
}

/// "2 hr, 30 min" in the app's language.
func focusDuration(_ seconds: Int) -> String {
    Duration.seconds(seconds).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated))
}

private struct FocusSetupView: View {
    @Bindable var engine: FocusEngine
    let planner: PlannerModel

    private static let lengths = Array(stride(from: FocusSettings.totalRange.lowerBound, through: FocusSettings.totalRange.upperBound, by: 5))

    var body: some View {
        let plan = engine.plan
        Form {
            Section {
                TextField("Goal, e.g. Calculus review", text: $engine.goal)
                    .accessibilityIdentifier("focusGoal")
                Picker("Length", selection: $engine.settings.totalMinutes) {
                    ForEach(Self.lengths, id: \.self) { minutes in
                        Text(verbatim: focusDuration(minutes * 60)).tag(minutes)
                    }
                }
            }

            Section {
                Toggle("Breaks (Pomodoro)", isOn: $engine.settings.usesBreaks)
                if engine.settings.usesBreaks {
                    Stepper(value: $engine.settings.focusMinutes, in: FocusSettings.focusRange, step: 5) {
                        LabeledContent("Focus block", value: focusDuration(engine.settings.focusMinutes * 60))
                    }
                    Stepper(value: $engine.settings.shortBreakMinutes, in: FocusSettings.shortBreakRange) {
                        LabeledContent("Short break", value: focusDuration(engine.settings.shortBreakMinutes * 60))
                    }
                    Stepper(value: $engine.settings.longBreakMinutes, in: FocusSettings.longBreakRange, step: 5) {
                        LabeledContent("Long break", value: focusDuration(engine.settings.longBreakMinutes * 60))
                    }
                    Stepper(value: $engine.settings.blocksBeforeLongBreak, in: 2...8) {
                        Text("Long break after every \(engine.settings.blocksBeforeLongBreak) blocks")
                    }
                }
            } footer: {
                TimelineView(.everyMinute) { context in
                    let end = context.date.addingTimeInterval(TimeInterval(plan.totalSeconds))
                    Text("\(plan.blockCount) focus blocks · \(focusDuration(plan.focusSeconds)) of focus · ends at \(end.formatted(date: .omitted, time: .shortened))")
                }
            }

            SoundSection(engine: engine)

            Section {
                Button {
                    engine.start()
                } label: {
                    Label("Start Focusing", systemImage: "play.fill")
                        .frame(maxWidth: .infinity)
                        .font(.headline)
                }
                .buttonStyle(.borderedProminent)
                .listRowInsets(EdgeInsets())
                .accessibilityIdentifier("startFocus")
            }

            FocusHistorySection(engine: engine, planner: planner)
        }
        .onDisappear { engine.stopPreview() }
    }
}

private struct SoundSection: View {
    @Bindable var engine: FocusEngine

    var body: some View {
        Section {
            Picker("Background Sound", selection: $engine.sound) {
                ForEach(FocusSound.available) { sound in
                    Label(sound.name, systemImage: sound.symbol).tag(sound)
                }
            }
            if engine.sound != .none {
                HStack {
                    Image(systemName: "speaker.fill")
                        .foregroundStyle(.secondary)
                    Slider(value: $engine.volume, in: 0...1)
                        .accessibilityLabel(Text("Volume"))
                    Image(systemName: "speaker.wave.3.fill")
                        .foregroundStyle(.secondary)
                }
                if engine.run == nil {
                    Button {
                        engine.togglePreview()
                    } label: {
                        engine.previewing ? Text("Stop Preview") : Text("Preview Sound")
                    }
                }
            }
        } footer: {
            if engine.run == nil {
                Text("The sound keeps playing when you leave the app or lock your iPhone, and mixes with music. Recordings from Freesound, in the public domain (CC0).")
            }
        }
    }
}

/// Today's focus time and the latest sessions.
private struct FocusHistorySection: View {
    let engine: FocusEngine
    let planner: PlannerModel

    var body: some View {
        let today = planner.focusedSeconds(on: LocalDate(.now))
        let recent = Array(planner.focusSessions.prefix(5))
        if today > 0 || !recent.isEmpty {
            Section("History") {
                LabeledContent("Focused today", value: focusDuration(today))
                ForEach(recent) { session in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(verbatim: session.goal.isEmpty ? String(localized: "Focus") : session.goal)
                            Text(verbatim: session.startedAt.formatted(date: .abbreviated, time: .shortened))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(verbatim: focusDuration(session.focusedSeconds))
                            .monospacedDigit()
                        Image(systemName: session.finished ? "checkmark.circle.fill" : "stop.circle")
                            .foregroundStyle(session.finished ? Color.green : Color.secondary)
                            .accessibilityLabel(session.finished ? Text("Finished") : Text("Stopped"))
                    }
                }
            }
        }
    }
}

/// The session in progress: the phase, a ring with the time left, and the controls.
private struct RunningFocusView: View {
    @Bindable var engine: FocusEngine
    let run: FocusRun
    @State private var confirmingStop = false

    var body: some View {
        Form {
            Section {
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    let status = run.status(at: context.date)
                    VStack(spacing: 16) {
                        Text(verbatim: phaseTitle(status.phase))
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(status.phase.isBreak ? Color.green : Color.accentColor)
                        ZStack {
                            Circle()
                                .stroke(Color.secondary.opacity(0.2), lineWidth: 14)
                            Circle()
                                .trim(from: 0, to: status.phaseElapsed / max(TimeInterval(status.phase.seconds), 1))
                                .stroke(status.phase.isBreak ? Color.green : Color.accentColor,
                                        style: StrokeStyle(lineWidth: 14, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                            VStack(spacing: 4) {
                                Text(verbatim: clock(status.phaseRemaining))
                                    .font(.system(size: 52, weight: .semibold, design: .rounded).monospacedDigit())
                                if status.isPaused {
                                    Text("Paused")
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .frame(width: 240, height: 240)
                        .padding(.vertical, 4)
                        if !run.goal.isEmpty {
                            Text(verbatim: run.goal)
                                .font(.headline)
                        }
                        Text(verbatim: summary(status, now: context.date))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
            }

            Section {
                HStack(spacing: 12) {
                    Button {
                        engine.togglePause()
                    } label: {
                        Label {
                            run.isPaused ? Text("Resume") : Text("Pause")
                        } icon: {
                            Image(systemName: run.isPaused ? "play.fill" : "pause.fill")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("pauseFocus")
                    Button(role: .destructive) {
                        confirmingStop = true
                    } label: {
                        Label("Stop", systemImage: "stop.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    if run.status(at: context.date).phase.isBreak && !run.isPaused {
                        Button("Skip Break") { engine.skipBreak() }
                    }
                }
            }

            SoundSection(engine: engine)
        }
        .confirmationDialog("Stop this focus session?", isPresented: $confirmingStop, titleVisibility: .visible) {
            Button("Stop Session", role: .destructive) { engine.stop() }
        } message: {
            Text("The time you've focused so far is kept in the history.")
        }
    }

    private func phaseTitle(_ phase: FocusPhase) -> String {
        switch phase.kind {
        case .focus:
            run.plan.blockCount > 1 ? String(localized: "Focus · Block \(phase.block) of \(run.plan.blockCount)") : String(localized: "Focus")
        case .shortBreak: String(localized: "Short break")
        case .longBreak: String(localized: "Long break")
        }
    }

    /// "1 hr, 50 min focused · ends at 21:30"
    private func summary(_ status: FocusStatus, now: Date) -> String {
        let focused = String(localized: "\(focusDuration(Int(status.focused))) focused")
        guard let end = run.endDate(at: now) else { return focused }
        return "\(focused) · \(String(localized: "ends at \(end.formatted(date: .omitted, time: .shortened))"))"
    }

    /// "24:59" or "1:05:00"
    private func clock(_ seconds: TimeInterval) -> String {
        let total = Int(seconds.rounded(.up))
        let (hours, minutes, rest) = (total / 3600, total / 60 % 60, total % 60)
        return hours > 0 ? String(format: "%d:%02d:%02d", hours, minutes, rest) : String(format: "%d:%02d", minutes, rest)
    }
}

#Preview {
    FocusView()
        .environment(PlannerModel.preview())
}
