import SwiftUI
import CourseKit

/// Settings → Storage: the records and downloaded files the app keeps besides the timetable, and clearing the
/// ones you pick (docs/todos-focus-spec.md §7). The timetable, unfinished to-dos, Words progress and AI
/// connections are never cleared here.
struct StorageView: View {
    @Environment(PlannerModel.self) private var planner
    @Environment(AIStore.self) private var ai
    @State private var selection: Set<Item> = []
    @State private var wallpaperCount = 0
    @State private var wallpaperBytes = 0
    @State private var otherBytes = 0
    @State private var backgroundBytes = 0
    @State private var confirming = false
    @State private var cleared = false

    enum Item: String, CaseIterable, Identifiable {
        case focusHistory, completedTodos, assistantChats, savedAnswers, aiUsage, wallpapers, timetableBackground, otherFiles

        var id: String { rawValue }

        static let records: [Item] = [.focusHistory, .completedTodos, .assistantChats, .savedAnswers, .aiUsage]
        static let files: [Item] = [.wallpapers, .timetableBackground, .otherFiles]

        var title: String {
            switch self {
            case .focusHistory: String(localized: "Focus History")
            case .completedTodos: String(localized: "Completed To-Dos")
            case .assistantChats: String(localized: "Assistant Chats")
            case .savedAnswers: String(localized: "Saved AI Answers")
            case .aiUsage: String(localized: "AI Usage Counts")
            case .wallpapers: String(localized: "Focus Wallpapers")
            case .timetableBackground: String(localized: "Timetable Background")
            case .otherFiles: String(localized: "Other Cached Files")
            }
        }

        var symbol: String {
            switch self {
            case .focusHistory: "timer"
            case .completedTodos: "checklist.checked"
            case .assistantChats: "bubble.left.and.bubble.right"
            case .savedAnswers: "sparkles"
            case .aiUsage: "chart.bar"
            case .wallpapers: "photo.on.rectangle"
            case .timetableBackground: "photo"
            case .otherFiles: "doc"
            }
        }
    }

    var body: some View {
        Form {
            Section {
                ForEach(Item.records) { row($0) }
            } header: {
                Text("Records")
            } footer: {
                Text("Your timetable, unfinished to-dos, Words progress and AI connections are never cleared here.")
            }

            Section {
                ForEach(Item.files) { row($0) }
            } header: {
                Text("Downloaded Files")
            } footer: {
                Text("Downloaded again when needed — except the timetable background, which is your choice.")
            }

        }
        .paperBackground()
        // Always in reach above the tab bar, like Start Focusing; the list fades out under it.
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 8) {
                if cleared {
                    Label("Cleared.", systemImage: "checkmark.circle")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Button(role: .destructive) {
                    confirming = true
                } label: {
                    Text("Clear Selected")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .disabled(selection.isEmpty)
                .accessibilityIdentifier("clearSelectedStorage")
            }
            .padding(.horizontal, 20)
            .padding(.top, 14)
            .padding(.bottom, 8)
            .background {
                LinearGradient(stops: [.init(color: Theme.paper.opacity(0), location: 0), .init(color: Theme.paper, location: 0.4)],
                               startPoint: .top, endPoint: .bottom)
                    .ignoresSafeArea(edges: .bottom)
                    .allowsHitTesting(false)
            }
        }
        .navigationTitle("Storage")
        .toolbar {
            let clearable = Set(Item.allCases.filter { !isEmpty($0) })
            Button {
                selection = selection == clearable ? [] : clearable
            } label: {
                selection == clearable && !clearable.isEmpty ? Text("Deselect All") : Text("Select All")
            }
            .disabled(clearable.isEmpty)
        }
        .confirmationDialog("Clear the selected data?", isPresented: $confirming, titleVisibility: .visible) {
            Button("Clear", role: .destructive) { clear() }
        } message: {
            let titles = Item.allCases.filter(selection.contains).map(\.title)
            Text("\(titles.formatted(.list(type: .and))) will be deleted from this iPhone. This can't be undone.")
        }
        .onAppear(perform: measure)
    }

    private func row(_ item: Item) -> some View {
        let selected = selection.contains(item)
        return Button {
            if selected { selection.remove(item) } else { selection.insert(item) }
            cleared = false
        } label: {
            HStack(spacing: 12) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(selected ? Color.accentColor : Color.secondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: item.title)
                        .foregroundStyle(isEmpty(item) ? Color.secondary : Color.primary)
                    Text(verbatim: detail(item))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: item.symbol)
                    .foregroundStyle(.secondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(isEmpty(item))
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("storage-\(item.rawValue)")
    }

    private func count(_ item: Item) -> Int {
        switch item {
        case .focusHistory: planner.focusSessionCount
        case .completedTodos: planner.completedTodoCount
        case .assistantChats: ai.conversations.count
        case .savedAnswers: ai.savedAnswerCount
        case .aiUsage: ai.countedRequests
        case .wallpapers: wallpaperBytes
        case .timetableBackground: backgroundBytes
        case .otherFiles: otherBytes
        }
    }

    private func isEmpty(_ item: Item) -> Bool { count(item) == 0 }

    private func detail(_ item: Item) -> String {
        let n = count(item)
        return switch item {
        case .focusHistory: String(localized: "\(n) sessions")
        case .completedTodos: String(localized: "\(n) to-dos")
        case .assistantChats: String(localized: "\(n) chats")
        case .savedAnswers: String(localized: "\(n) answers")
        case .aiUsage: String(localized: "\(n) requests counted")
        case .wallpapers: String(localized: "\(wallpaperCount) photos · \(Self.size(wallpaperBytes))")
        case .timetableBackground: Self.size(backgroundBytes)
        case .otherFiles: Self.size(otherBytes)
        }
    }

    private static func size(_ bytes: Int) -> String {
        Int64(bytes).formatted(.byteCount(style: .file))
    }

    private func measure() {
        let usage = WallpaperStore.shared.cacheUsage()
        wallpaperCount = usage.count
        wallpaperBytes = usage.bytes
        otherBytes = URLCache.shared.currentDiskUsage + FolderUsage.bytes(in: .temporaryDirectory)
        backgroundBytes = TimetableBackground.shared.bytes
    }

    private func clear() {
        for item in selection {
            switch item {
            case .focusHistory: planner.deleteAllFocusSessions()
            case .completedTodos: planner.deleteCompletedTodos()
            case .assistantChats: ai.clearConversations()
            case .savedAnswers: ai.clearAnswers()
            case .aiUsage: ai.clearUsage()
            case .wallpapers: WallpaperStore.shared.clearCache()
            case .timetableBackground: TimetableBackground.shared.clear()
            case .otherFiles:
                URLCache.shared.removeAllCachedResponses()
                FolderUsage.removeContents(of: .temporaryDirectory)
            }
        }
        selection = []
        cleared = true
        measure()
    }
}
