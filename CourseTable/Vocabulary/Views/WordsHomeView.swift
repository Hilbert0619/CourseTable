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
}

struct WordsHomeView: View {
    @Environment(VocabApp.self) private var vocab
    @State private var studying = false

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
                NavigationLink(value: WordsRoute.hardWords) {
                    LabeledContent {
                        Text(verbatim: "\(model.hardWords.count)")
                    } label: {
                        Label("Hard Words", systemImage: "exclamationmark.bubble")
                    }
                }
                NavigationLink(value: WordsRoute.statistics) {
                    Label("Statistics", systemImage: "chart.bar")
                }
                .accessibilityIdentifier("statisticsLink")
            }
            Section {
                SyncStatusRow()
            }
        }
        .navigationTitle("Words")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                NavigationLink(value: WordsRoute.settings) {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel(Text("Study Settings"))
                .accessibilityIdentifier("wordsSettings")
            }
        }
        .refreshable { await vocab.syncNow() }
        .navigationDestination(for: WordsRoute.self) { route in
            switch route {
            case .search: WordSearchView()
            case .favorites: WordListView(kind: .favorites)
            case .hardWords: WordListView(kind: .hardWords)
            case .statistics: VocabStatsView()
            case .books: BooksView()
            case .settings: VocabSettingsView()
            case .word(let headword): WordDetailView(headword: headword)
            }
        }
        .fullScreenCover(isPresented: $studying) {
            StudySessionView()
        }
    }

    private func todaySection(_ plan: DailyPlan) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("New words")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(verbatim: "\(plan.newDone)/\(plan.newDone + plan.newRemaining)")
                            .font(.title2.monospacedDigit().bold())
                    }
                    Spacer()
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Reviews left")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(verbatim: "\(plan.reviewsRemaining)")
                            .font(.title2.monospacedDigit().bold())
                    }
                    Spacer()
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Reviewed")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(verbatim: "\(plan.reviewsDone)")
                            .font(.title2.monospacedDigit().bold())
                    }
                }
                ProgressView(value: plan.progress)
                    .tint(plan.isComplete ? .green : .accentColor)
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
