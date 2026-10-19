import SwiftUI
import VocabKit

/// Places reachable from the Words home screen.
enum WordsRoute: Hashable {
    case search
    case favorites
    case hardWords
    case statistics
    case books
    case settings
    case word(String)
    case sentences
    case essay

    /// The screen for this place (also used by the Assistant, whose study tools link to words).
    @MainActor @ViewBuilder
    var destination: some View {
        switch self {
        case .search: WordSearchView()
        case .favorites: WordListView(kind: .favorites)
        case .hardWords: WordListView(kind: .hardWords)
        case .statistics: VocabStatsView()
        case .books: BooksView()
        case .settings: VocabSettingsView()
        case .word(let headword): WordDetailView(headword: headword)
        case .sentences: SentenceAnalysisView()
        case .essay: EssayFeedbackView()
        }
    }
}

struct WordsHomeView: View {
    @Environment(VocabApp.self) private var vocab
    @State private var studying = false
    @State private var showingAssistant = false

    private var model: VocabModel { vocab.model }

    var body: some View {
        let plan = model.plan()
        List {
            if let problem = vocab.storeProblem {
                Section {
                    Label("Words can't be saved on this iPhone right now: \(problem)", systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                }
            }
            todaySection(plan)
            if let countdown = model.examCountdown() {
                examSection(countdown)
            }
            bookSection
            Section {
                NavigationLink(value: WordsRoute.search) {
                    Label("Search", systemImage: "magnifyingglass")
                }
                .accessibilityIdentifier("searchLink")
                NavigationLink(value: WordsRoute.favorites) {
                    LabeledContent {
                        Text(verbatim: "\(model.favorites.count)")
                    } label: {
                        Label("Favourites", systemImage: "star")
                    }
                }
                .accessibilityIdentifier("favoritesLink")
                NavigationLink(value: WordsRoute.hardWords) {
                    LabeledContent {
                        Text(verbatim: "\(model.hardWords.count)")
                    } label: {
                        Label("Hard Words", systemImage: "exclamationmark.bubble")
                    }
                }
                .accessibilityIdentifier("hardWordsLink")
                NavigationLink(value: WordsRoute.statistics) {
                    Label("Statistics", systemImage: "chart.bar")
                }
                .accessibilityIdentifier("statisticsLink")
            }
            Section {
                NavigationLink(value: WordsRoute.sentences) {
                    Label("Long Sentences", systemImage: "text.alignleft")
                }
                .accessibilityIdentifier("sentencesLink")
                NavigationLink(value: WordsRoute.essay) {
                    Label("Writing Feedback", systemImage: "pencil.and.list.clipboard")
                }
                .accessibilityIdentifier("essayLink")
            } header: {
                Text("AI Tools")
            }
            Section {
                SyncStatusRow()
            }
        }
        .paperBackground()
        .navigationTitle("Words")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    showingAssistant = true
                } label: {
                    Label("Assistant", systemImage: "sparkles")
                }
                .accessibilityIdentifier("wordsAssistantButton")
            }
            ToolbarItem(placement: .primaryAction) {
                NavigationLink(value: WordsRoute.settings) {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel(Text("Study Settings"))
                .accessibilityIdentifier("wordsSettings")
            }
        }
        .refreshable { await vocab.syncNow() }
        .navigationDestination(for: WordsRoute.self) { $0.destination }
        .fullScreenCover(isPresented: $studying) {
            StudySessionView()
        }
        .sheet(isPresented: $showingAssistant) {
            AssistantView()
        }
    }

    private func todaySection(_ plan: DailyPlan) -> some View {
        let streak = VocabStats.streak(model.events, today: model.today)
        let countdown = model.examCountdown()
        return Section {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 18) {
                    TodayRing(progress: plan.progress, isComplete: plan.isComplete)
                        .frame(width: 84, height: 84)
                    VStack(spacing: 6) {
                        StatLine(label: "New words", value: "\(plan.newDone)/\(plan.newDone + plan.newRemaining)")
                        StatLine(label: "Reviews left", value: "\(plan.reviewsRemaining)")
                        StatLine(label: "Reviewed", value: "\(plan.reviewsDone)")
                    }
                }
                if streak.days > 0 || countdown != nil {
                    HStack(spacing: 8) {
                        if streak.days > 0 {
                            Chip(systemImage: "flame.fill", tint: .orange) { Text("\(streak.days)-day streak") }
                        }
                        if let countdown {
                            Chip(systemImage: "calendar", tint: .accentColor) { Text("\(countdown.daysLeft) days to the exam") }
                        }
                    }
                }
                if let minutes = plan.estimatedMinutes, !plan.isComplete {
                    Text("About \(minutes) min left, going by your pace so far.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                if plan.isComplete {
                    Label("Done for today", systemImage: "checkmark.seal.fill")
                        .foregroundStyle(.green)
                        .font(.headline)
                } else {
                    Button {
                        studying = true
                    } label: {
                        Group {
                            if plan.newDone + plan.reviewsDone == 0 { Text("Start") } else { Text("Continue") }
                        }
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(model.bookWords.isEmpty && plan.reviewsRemaining == 0)
                    .accessibilityIdentifier("startStudy")
                }
            }
            .padding(.vertical, 6)
        } header: {
            Text("Today")
        }
    }

    /// Today's progress as a ring, with the percentage (a check mark once done).
    private struct TodayRing: View {
        let progress: Double
        let isComplete: Bool

        var body: some View {
            ZStack {
                Circle()
                    .stroke(Color.secondary.opacity(0.15), lineWidth: 10)
                if progress > 0 {
                    Circle()
                        .trim(from: 0, to: progress)
                        .stroke(isComplete ? Color.green : Color.accentColor, style: StrokeStyle(lineWidth: 10, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                }
                if isComplete {
                    Image(systemName: "checkmark")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.green)
                } else {
                    Text(verbatim: "\(Int((progress * 100).rounded()))%")
                        .font(.headline.monospacedDigit())
                }
            }
            .animation(.easeInOut(duration: 0.4), value: progress)
            .accessibilityElement()
            .accessibilityLabel(Text("Today's progress"))
            .accessibilityValue(Text(verbatim: "\(Int((progress * 100).rounded()))%"))
        }
    }

    private struct StatLine: View {
        let label: LocalizedStringKey
        let value: String

        var body: some View {
            HStack(alignment: .firstTextBaseline) {
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer(minLength: 8)
                Text(verbatim: value)
                    .font(.headline.monospacedDigit())
            }
        }
    }

    private struct Chip<Content: View>: View {
        let systemImage: String
        let tint: Color
        @ViewBuilder let content: () -> Content

        var body: some View {
            // Not a Label: in a list it keeps a wide column for the icon.
            HStack(spacing: 5) {
                Image(systemName: systemImage)
                    .foregroundStyle(tint)
                content()
            }
            .font(.footnote.weight(.medium))
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(tint.opacity(0.12), in: Capsule())
        }
    }

    private func examSection(_ countdown: ExamCountdown) -> some View {
        Section {
            LabeledContent("Days to the exam") {
                Text(verbatim: "\(countdown.daysLeft)")
                    .monospacedDigit()
            }
            LabeledContent("Words not started") {
                Text(verbatim: "\(countdown.wordsLeft)")
                    .monospacedDigit()
            }
            LabeledContent("New words a day to finish") {
                Text(verbatim: "\(countdown.newWordsPerDayNeeded)")
                    .monospacedDigit()
            }
        } header: {
            Text("Exam")
        } footer: {
            Text("An estimate from the words left and the days left; it doesn't change your daily target.")
        }
    }

    private var bookSection: some View {
        Section {
            NavigationLink(value: WordsRoute.books) {
                if let book = model.currentBook {
                    let progress = VocabStats.progress(book: model.bookWords, cards: model.cards, today: model.today)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(verbatim: book.name)
                            .font(.headline)
                        if let download = vocab.sync.download {
                            ProgressView(value: Double(download.done), total: Double(max(download.total, 1))) {
                                Text("Downloading words…")
                                    .font(.caption)
                            }
                        } else if model.bookWords.isEmpty {
                            Text("Not downloaded yet. Pull down to sync.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else {
                            Text("Learned \(progress.learned) of \(progress.total) · mastered \(progress.mastered)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                } else {
                    Text("Choose a Book")
                }
            }
            .accessibilityIdentifier("currentBook")
        } header: {
            Text("Book")
        }
    }
}

/// When the last sync happened, or what went wrong, with a button to sync now.
struct SyncStatusRow: View {
    @Environment(VocabApp.self) private var vocab

    var body: some View {
        let sync = vocab.sync
        HStack {
            switch sync.phase {
            case .syncing:
                ProgressView()
                Text("Syncing…")
                    .foregroundStyle(.secondary)
            case .failed(let message):
                Image(systemName: "exclamationmark.icloud")
                    .foregroundStyle(.orange)
                Text(verbatim: message)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            case .idle:
                Image(systemName: "checkmark.icloud")
                    .foregroundStyle(.secondary)
                if let last = sync.lastSync {
                    Text("Synced \(last, format: .relative(presentation: .named))")
                        .foregroundStyle(.secondary)
                } else {
                    Text("Not synced yet")
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            if sync.phase != .syncing {
                Button("Sync Now") {
                    Task { await vocab.syncNow() }
                }
                .buttonStyle(.borderless)
            }
        }
        .font(.subheadline)
        if vocab.model.unsentCount > 0 {
            Text("\(vocab.model.unsentCount) answers waiting to upload")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}
