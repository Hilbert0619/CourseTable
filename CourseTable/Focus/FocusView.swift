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
    /// The quick lengths on the plan card, in minutes.
    private static let presets = [25, 50, 90, 120]

    var body: some View {
        let plan = engine.plan
        Form {
            Section {
                FocusPlanCard(plan: plan, totalMinutes: $engine.settings.totalMinutes, presets: Self.presets)
            }

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
                    // The rhythm rarely changes: folded away, its values in the row.
                    DisclosureGroup {
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
                    } label: {
                        LabeledContent("Rhythm") {
                            Text(verbatim: "\(engine.settings.focusMinutes) · \(engine.settings.shortBreakMinutes) · \(engine.settings.longBreakMinutes)")
                                .monospacedDigit()
                        }
                    }
                    .accessibilityIdentifier("focusRhythm")
                }
            } footer: {
                if engine.settings.usesBreaks {
                    Text("Focus block · short break · long break, in minutes.")
                }
            }

            SoundSection(engine: engine)

            WallpaperSection(wallpapers: WallpaperStore.shared)

            FocusHistorySection(engine: engine, planner: planner)
        }
        .paperBackground()
        // Always in reach, above the tab bar; the list fades out under it.
        .safeAreaInset(edge: .bottom) {
            Button {
                engine.start()
            } label: {
                Label("Start Focusing", systemImage: "play.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal, 20)
            .padding(.top, 14)
            .padding(.bottom, 8)
            .background {
                LinearGradient(stops: [.init(color: Theme.paper.opacity(0), location: 0), .init(color: Theme.paper, location: 0.4)],
                               startPoint: .top, endPoint: .bottom)
                    .ignoresSafeArea(edges: .bottom)
                    .allowsHitTesting(false)
            }
            .accessibilityIdentifier("startFocus")
        }
        .onDisappear { engine.stopPreview() }
        // Downloads the next session's wallpaper while you set it up, so it can show at once.
        .task { WallpaperStore.shared.prefetch() }
    }
}

/// The session at a glance: a ring with each focus block and break to scale, the length, when it ends, and quick
/// lengths to pick.
private struct FocusPlanCard: View {
    let plan: FocusPlan
    @Binding var totalMinutes: Int
    let presets: [Int]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 18) {
                PlanRing(plan: plan)
                    .frame(width: 84, height: 84)
                VStack(alignment: .leading, spacing: 4) {
                    Text(verbatim: focusDuration(plan.totalSeconds))
                        .font(.title.weight(.bold))
                        .monospacedDigit()
                    TimelineView(.everyMinute) { context in
                        let end = context.date.addingTimeInterval(TimeInterval(plan.totalSeconds))
                        Text("\(plan.blockCount) focus blocks · \(focusDuration(plan.focusSeconds)) of focus · ends at \(end.formatted(date: .omitted, time: .shortened))")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .accessibilityElement(children: .combine)
            HStack(spacing: 8) {
                ForEach(presets, id: \.self) { minutes in
                    let selected = totalMinutes == minutes
                    Button {
                        totalMinutes = minutes
                    } label: {
                        Text(verbatim: focusDuration(minutes * 60))
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .foregroundStyle(selected ? Color.white : Color.primary)
                            .background(selected ? Color.accentColor : Color.secondary.opacity(0.12), in: Capsule())
                    }
                    .buttonStyle(.borderless)
                    .accessibilityAddTraits(selected ? .isSelected : [])
                    .accessibilityIdentifier("focusPreset-\(minutes)")
                }
            }
        }
        .padding(.vertical, 6)
        .sensoryFeedback(.selection, trigger: totalMinutes)
    }
}

/// The plan as a ring: focus blocks in the accent colour, breaks in green, each to scale.
private struct PlanRing: View {
    let plan: FocusPlan

    var body: some View {
        let total = Double(max(plan.totalSeconds, 1))
        let gap = plan.phases.count > 1 ? 0.012 : 0
        ZStack {
            Circle()
                .stroke(Color.secondary.opacity(0.15), lineWidth: 10)
            ForEach(Array(zip(plan.phases.indices, plan.offsets)), id: \.0) { index, offset in
                let phase = plan.phases[index]
                let start = Double(offset) / total
                let end = Double(offset + phase.seconds) / total
                Circle()
                    .trim(from: start, to: max(end - gap, start + 0.002))
                    .stroke(phase.isBreak ? Color.green : Color.accentColor, style: StrokeStyle(lineWidth: 10, lineCap: .butt))
                    .rotationEffect(.degrees(-90))
            }
            Image(systemName: "timer")
                .font(.title2)
                .foregroundStyle(.secondary)
        }
        .animation(.easeInOut(duration: 0.35), value: plan)
        .accessibilityHidden(true)
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

private struct WallpaperSection: View {
    @Bindable var wallpapers: WallpaperStore

    var body: some View {
        Section {
            Toggle(isOn: $wallpapers.isEnabled) {
                Label("Wallpaper", systemImage: "photo")
            }
            .accessibilityIdentifier("wallpaperToggle")
        } footer: {
            Text("A new photo behind the countdown each session. Photos from Unsplash; each is downloaded once (about 0.5 MB) and kept on this iPhone until you clear it in Settings → Storage.")
        }
    }
}

/// Today's focus time and the latest sessions; swipe one to delete it.
private struct FocusHistorySection: View {
    let engine: FocusEngine
    let planner: PlannerModel

    var body: some View {
        let today = planner.focusedSeconds(on: LocalDate(.now))
        let recent = Array(planner.focusSessions.prefix(5))
        if today > 0 || !recent.isEmpty {
            Section {
                LabeledContent("Focused today", value: focusDuration(today))
                ForEach(recent) { session in
                    FocusSessionRow(session: session)
                }
                .onDelete { offsets in
                    planner.deleteFocusSessions(offsets.map { recent[$0].id })
                }
                NavigationLink {
                    FocusHistoryView(planner: planner)
                } label: {
                    LabeledContent("All Sessions") {
                        Text(verbatim: "\(planner.focusSessionCount)")
                    }
                }
                .accessibilityIdentifier("focusHistoryLink")
            } header: {
                Text("History")
            } footer: {
                Text("Swipe left on a session to delete it.")
            }
        }
    }
}

private struct FocusSessionRow: View {
    let session: FocusSessionSummary
    var showsDay = true

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: session.goal.isEmpty ? String(localized: "Focus") : session.goal)
                Text(verbatim: showsDay ? session.startedAt.formatted(date: .abbreviated, time: .shortened)
                                        : session.startedAt.formatted(date: .omitted, time: .shortened))
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

/// Every focus session by day, to look back on or delete (one by one, or all).
private struct FocusHistoryView: View {
    let planner: PlannerModel
    @State private var confirmingClear = false

    var body: some View {
        // Reading the count redraws the list after each delete (the model updates it whenever it saves).
        let sessions = planner.focusSessionCount == 0 ? [] : planner.allFocusSessions()
        let days = Dictionary(grouping: sessions) { LocalDate($0.startedAt) }.sorted { $0.key > $1.key }
        List {
            ForEach(days, id: \.key) { day, daySessions in
                Section {
                    ForEach(daySessions) { session in
                        FocusSessionRow(session: session, showsDay: false)
                    }
                    .onDelete { offsets in
                        planner.deleteFocusSessions(offsets.map { daySessions[$0].id })
                    }
                } header: {
                    HStack {
                        Text(verbatim: day.date().formatted(date: .abbreviated, time: .omitted))
                        Spacer()
                        Text(verbatim: focusDuration(daySessions.reduce(0) { $0 + $1.focusedSeconds }))
                    }
                }
            }
            if !sessions.isEmpty {
                Section {
                    Button("Clear History", role: .destructive) { confirmingClear = true }
                        .accessibilityIdentifier("clearFocusHistory")
                }
            }
        }
        .paperBackground()
        .overlay {
            if sessions.isEmpty {
                ContentUnavailableView("No Focus Sessions", systemImage: "timer",
                                       description: Text("Finished and stopped sessions appear here."))
            }
        }
        .navigationTitle("Focus History")
        .toolbar {
            if !sessions.isEmpty { EditButton() }
        }
        .confirmationDialog("Delete all \(sessions.count) focus sessions?", isPresented: $confirmingClear, titleVisibility: .visible) {
            Button("Clear History", role: .destructive) { planner.deleteAllFocusSessions() }
        } message: {
            Text("Today's focus time starts again from zero. This can't be undone.")
        }
    }
}

/// The session in progress: the phase, a ring with the time left, and the controls.
private struct RunningFocusView: View {
    @Bindable var engine: FocusEngine
    let run: FocusRun
    @State private var confirmingStop = false
    @State private var fullScreen = false
    @Environment(\.colorScheme) private var colorScheme
    private var wallpapers: WallpaperStore { WallpaperStore.shared }

    var body: some View {
        Form {
            Section {
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    let status = run.status(at: context.date)
                    let onPhoto = wallpapers.shown != nil
                    // On a photo: white, and a light green for breaks; otherwise the app's colours.
                    let tint = status.phase.isBreak ? (onPhoto ? Color(red: 0.62, green: 0.95, blue: 0.72) : Color.green)
                                                    : (onPhoto ? Color.white : Color.accentColor)
                    VStack(spacing: 16) {
                        Text(verbatim: phaseTitle(status.phase))
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(tint)
                        ZStack {
                            Circle()
                                .stroke(onPhoto ? Color.white.opacity(0.25) : Color.secondary.opacity(0.2), lineWidth: 14)
                            Circle()
                                .trim(from: 0, to: status.phaseElapsed / max(TimeInterval(status.phase.seconds), 1))
                                .stroke(tint, style: StrokeStyle(lineWidth: 14, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                            VStack(spacing: 4) {
                                Text(verbatim: clock(status.phaseRemaining))
                                    .font(.system(size: 52, weight: .semibold, design: .rounded).monospacedDigit())
                                if status.isPaused {
                                    Text("Paused")
                                        .font(.subheadline)
                                        .foregroundStyle(onPhoto ? AnyShapeStyle(Color.white.opacity(0.85)) : AnyShapeStyle(.secondary))
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
                            .foregroundStyle(onPhoto ? AnyShapeStyle(Color.white.opacity(0.85)) : AnyShapeStyle(.secondary))
                            .multilineTextAlignment(.center)
                        if status.phase.isBreak && !status.isPaused {
                            Button("Skip Break") { engine.skipBreak() }
                                .buttonStyle(.bordered)
                        }
                        if let shown = wallpapers.shown {
                            WallpaperCredit(shown: shown, isLoading: wallpapers.isLoading) { wallpapers.showAnother() }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .shadow(color: .black.opacity(onPhoto ? 0.35 : 0), radius: 5, y: 1)
                    // Light text and controls on the photo, whatever the system appearance.
                    .environment(\.colorScheme, onPhoto ? .dark : colorScheme)
                    // A tap at each switch between focus and break, while the app is open.
                    .sensoryFeedback(.impact(weight: .medium), trigger: status.phase)
                }
                // The card draws its own background (with the row's usual insets): a List row background isn't
                // redrawn when the photo arrives, so the photo only showed after the row was rebuilt.
                .padding(.horizontal, 20)
                .padding(.vertical, 11)
                .background { CountdownCardBackground() }
                .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }

            Section {
                HStack(spacing: 12) {
                    Button {
                        engine.togglePause()
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: run.isPaused ? "play.fill" : "pause.fill")
                            run.isPaused ? Text("Resume") : Text("Pause")
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("pauseFocus")
                    Button {
                        confirmingStop = true
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "stop.fill")
                            Text("Stop")
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)
                    }
                    .buttonStyle(.bordered)
                    .tint(.red)
                    .accessibilityIdentifier("stopFocus")
                    Button {
                        fullScreen = true
                    } label: {
                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                            .padding(.vertical, 4)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityLabel(Text("Full Screen"))
                    .accessibilityIdentifier("fullScreenFocus")
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }

            SoundSection(engine: engine)
        }
        .paperBackground()
        .fullScreenCover(isPresented: $fullScreen) {
            FullScreenFocusView(engine: engine)
        }
        .confirmationDialog("Stop this focus session?", isPresented: $confirmingStop, titleVisibility: .visible) {
            Button("Stop Session", role: .destructive) { engine.stop() }
        } message: {
            Text("The time you've focused so far is kept in the history.")
        }
        // The session's wallpaper, also after a relaunch.
        .onAppear { wallpapers.restore() }
    }

    private func phaseTitle(_ phase: FocusPhase) -> String { run.phaseTitle(phase) }
    private func summary(_ status: FocusStatus, now: Date) -> String { run.summary(status, now: now) }
    private func clock(_ seconds: TimeInterval) -> String { focusClock(seconds) }
}

private extension FocusRun {
    func phaseTitle(_ phase: FocusPhase) -> String {
        switch phase.kind {
        case .focus:
            plan.blockCount > 1 ? String(localized: "Focus · Block \(phase.block) of \(plan.blockCount)") : String(localized: "Focus")
        case .shortBreak: String(localized: "Short break")
        case .longBreak: String(localized: "Long break")
        }
    }

    /// "1 hr, 50 min focused · ends at 21:30"
    func summary(_ status: FocusStatus, now: Date) -> String {
        let focused = String(localized: "\(focusDuration(Int(status.focused))) focused")
        guard let end = endDate(at: now) else { return focused }
        return "\(focused) · \(String(localized: "ends at \(end.formatted(date: .omitted, time: .shortened))"))"
    }
}

/// "24:59" or "1:05:00"
private func focusClock(_ seconds: TimeInterval) -> String {
    let total = Int(seconds.rounded(.up))
    let (hours, minutes, rest) = (total / 3600, total / 60 % 60, total % 60)
    return hours > 0 ? String(format: "%d:%02d:%02d", hours, minutes, rest) : String(format: "%d:%02d", minutes, rest)
}

/// Full screen while focusing: the photo edge to edge (or a deep ink gradient), the countdown large, Pause and a way
/// back. The screen stays on; it closes by itself when the session ends.
private struct FullScreenFocusView: View {
    @Bindable var engine: FocusEngine
    @Environment(\.dismiss) private var dismiss
    private var wallpapers: WallpaperStore { WallpaperStore.shared }

    var body: some View {
        ZStack {
            Group {
                if let shown = wallpapers.shown {
                    WallpaperBackground(shown: shown)
                } else {
                    LinearGradient(colors: [Color(red: 0.13, green: 0.15, blue: 0.20), Color(red: 0.05, green: 0.06, blue: 0.08)],
                                   startPoint: .top, endPoint: .bottom)
                }
            }
            .ignoresSafeArea()

            if let run = engine.run {
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    let status = run.status(at: context.date)
                    VStack(spacing: 12) {
                        HStack {
                            Spacer()
                            Button {
                                dismiss()
                            } label: {
                                Image(systemName: "arrow.down.right.and.arrow.up.left")
                                    .font(.title3)
                                    .padding(10)
                            }
                            .accessibilityLabel(Text("Exit Full Screen"))
                            .accessibilityIdentifier("exitFullScreenFocus")
                        }
                        Spacer()
                        Text(verbatim: run.phaseTitle(status.phase))
                            .font(.title3.weight(.semibold))
                        Text(verbatim: focusClock(status.phaseRemaining))
                            .font(.system(size: 92, weight: .semibold, design: .rounded).monospacedDigit())
                            .minimumScaleFactor(0.5)
                            .lineLimit(1)
                        if status.isPaused {
                            Text("Paused")
                                .font(.headline)
                                .opacity(0.85)
                        }
                        if !run.goal.isEmpty {
                            Text(verbatim: run.goal)
                                .font(.title3)
                        }
                        Text(verbatim: run.summary(status, now: context.date))
                            .font(.subheadline)
                            .opacity(0.85)
                            .multilineTextAlignment(.center)
                        Spacer()
                        Button {
                            engine.togglePause()
                        } label: {
                            Label {
                                run.isPaused ? Text("Resume") : Text("Pause")
                            } icon: {
                                Image(systemName: run.isPaused ? "play.fill" : "pause.fill")
                            }
                            .font(.headline)
                                .padding(.horizontal, 28)
                                .padding(.vertical, 6)
                        }
                        .buttonStyle(.bordered)
                        .tint(.white)
                        if let shown = wallpapers.shown {
                            WallpaperCredit(shown: shown, isLoading: wallpapers.isLoading) { wallpapers.showAnother() }
                        }
                    }
                    .padding(.horizontal, 28)
                    .padding(.vertical, 12)
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.4), radius: 6, y: 1)
                }
            }
        }
        .environment(\.colorScheme, .dark)
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
        .onChange(of: engine.run == nil) { _, ended in
            if ended { dismiss() }
        }
    }
}

/// The countdown card's background: the session's photo, or the plain row colour. It reads the store itself, so it
/// redraws as soon as the photo arrives.
private struct CountdownCardBackground: View {
    var body: some View {
        if let shown = WallpaperStore.shared.shown {
            WallpaperBackground(shown: shown)
        } else {
            Color(uiColor: .secondarySystemGroupedBackground)
        }
    }
}

/// The countdown card's photo: its average colour until it loads, placed so its subject stays in view, and
/// darkened towards the bottom so the white text reads on any photo.
private struct WallpaperBackground: View {
    let shown: WallpaperStore.Shown

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                if let rgb = shown.wallpaper.rgb {
                    Color(red: rgb.red, green: rgb.green, blue: rgb.blue)
                } else {
                    Color.gray
                }
                if let image = shown.image {
                    let frame = FocusWallpaperLayout.frame(imageSize: image.size, in: proxy.size, focusY: shown.wallpaper.focusY)
                    Image(uiImage: image)
                        .resizable()
                        .frame(width: frame.width, height: frame.height)
                        .offset(x: frame.minX, y: frame.minY)
                        .transition(.opacity)
                }
                LinearGradient(colors: [.black.opacity(0.18), .black.opacity(0.38), .black.opacity(0.55)],
                               startPoint: .top, endPoint: .bottom)
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
            .clipped()
            .animation(.easeInOut(duration: 0.5), value: shown)
        }
        .accessibilityHidden(true)
    }
}

/// "Photo by … on Unsplash" (opens the photo's page) and a button for another wallpaper.
private struct WallpaperCredit: View {
    let shown: WallpaperStore.Shown
    let isLoading: Bool
    let another: () -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        HStack(spacing: 12) {
            let wallpaper = shown.wallpaper
            if !wallpaper.author.isEmpty {
                Button {
                    if let page = wallpaper.sourceURL { openURL(page) }
                } label: {
                    if wallpaper.sourceURL?.host()?.hasSuffix("unsplash.com") == true {
                        Text("Photo by \(wallpaper.author) on Unsplash")
                    } else {
                        Text("Photo by \(wallpaper.author)")
                    }
                }
                .lineLimit(1)
                .disabled(wallpaper.sourceURL == nil)
            }
            Spacer(minLength: 0)
            Button(action: another) {
                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                } else {
                    Image(systemName: "shuffle")
                }
            }
            .disabled(isLoading)
            .accessibilityLabel(Text("Another Wallpaper"))
            .accessibilityIdentifier("anotherWallpaper")
        }
        .buttonStyle(.borderless)
        .font(.caption)
        .foregroundStyle(.white.opacity(0.85))
        .tint(.white)
        .padding(.top, 4)
    }
}

#Preview {
    FocusView()
        .environment(PlannerModel.preview())
}
