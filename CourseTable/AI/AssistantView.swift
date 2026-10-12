import SwiftUI
import SwiftData
import AIKit
import CourseKit
import VocabKit

/// The Assistant: a chat with the user's AI connection, kept on the device. The week's classes and the hard words
/// are shared only when switched on. The study tools are linked from an empty chat, so they work without a
/// Words account too.
struct AssistantView: View {
    @Environment(AIStore.self) private var ai
    @Environment(VocabApp.self) private var vocab
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \CourseRecord.createdAt) private var courseRecords: [CourseRecord]
    @Query private var settingsRecords: [SettingsRecord]
    @AppStorage("ai.includeSchedule") private var includeSchedule = false
    @AppStorage("ai.includeHardWords") private var includeHardWords = false
    @State private var conversation = AIConversation()
    @State private var run = AIRun()
    @State private var input = ""
    @State private var showingHistory = false
    @FocusState private var inputFocused: Bool

    static let maxInput = 4000

    var body: some View {
        NavigationStack {
            Group {
                if ai.isSetUp {
                    chat
                } else {
                    List {
                        Section {
                            AISetupLink()
                        } footer: {
                            Text("Ask about your studies, English or anything else, using an AI provider you choose with your own key.")
                        }
                    }
                }
            }
            .navigationTitle("Assistant")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                if ai.isSetUp {
                    ToolbarItemGroup(placement: .primaryAction) {
                        Menu {
                            Toggle(isOn: $includeSchedule) {
                                Label("Share This Week's Classes", systemImage: "calendar")
                            }
                            Toggle(isOn: $includeHardWords) {
                                Label("Share My Hard Words", systemImage: "exclamationmark.bubble")
                            }
                            Divider()
                            Button {
                                showingHistory = true
                            } label: {
                                Label("Earlier Chats", systemImage: "clock.arrow.circlepath")
                            }
                            .disabled(ai.conversations.isEmpty)
                        } label: {
                            Image(systemName: "ellipsis.circle")
                        }
                        .accessibilityLabel(Text("Options"))
                        .accessibilityIdentifier("assistantOptions")
                        Button {
                            newChat()
                        } label: {
                            Image(systemName: "square.and.pencil")
                        }
                        .accessibilityLabel(Text("New Chat"))
                        .disabled(conversation.messages.isEmpty && !run.hasContent)
                    }
                }
            }
            .navigationDestination(for: WordsRoute.self) { $0.destination }
            .sheet(isPresented: $showingHistory) {
                ChatHistoryView { picked in
                    run.reset()
                    conversation = picked
                }
            }
        }
        .onDisappear { run.cancel() }
    }

    private var chat: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                if conversation.messages.isEmpty && !run.hasContent {
                    welcome
                }
                ForEach(Array(conversation.messages.enumerated()), id: \.offset) { _, message in
                    MessageBubble(message: message)
                }
                if run.hasContent {
                    AIAnswerView(run: run) { send(retrying: true) }
                        .padding(12)
                        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 14))
                }
            }
            .padding()
        }
        // An empty chat starts at the top; a conversation follows its latest message.
        .defaultScrollAnchor(conversation.messages.isEmpty && !run.hasContent ? .top : .bottom)
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom) { inputBar }
    }

    private var welcome: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Ask about your studies, English, planning or anything else.", systemImage: "sparkles")
                .foregroundStyle(.secondary)
            if includeSchedule || includeHardWords {
                Text(sharingSummary)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            NavigationLink(value: WordsRoute.sentences) {
                Label("Long Sentences", systemImage: "text.alignleft")
            }
            .accessibilityIdentifier("assistantSentences")
            NavigationLink(value: WordsRoute.essay) {
                Label("Writing Feedback", systemImage: "pencil.and.list.clipboard")
            }
            .accessibilityIdentifier("assistantEssay")
        }
        .padding(.vertical, 8)
    }

    private var sharingSummary: LocalizedStringKey {
        switch (includeSchedule, includeHardWords) {
        case (true, true): "This chat can see this week's classes and your hard words."
        case (true, false): "This chat can see this week's classes."
        default: "This chat can see your hard words."
        }
    }

    private var inputBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField("Message", text: $input, axis: .vertical)
                .lineLimit(1...6)
                .focused($inputFocused)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(.background.secondary, in: RoundedRectangle(cornerRadius: 18))
                .accessibilityIdentifier("assistantInput")
            if run.isRunning {
                Button {
                    stop()
                } label: {
                    Image(systemName: "stop.circle.fill")
                        .font(.title)
                }
                .accessibilityLabel(Text("Stop"))
            } else {
                Button {
                    send()
                } label: {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.title)
                }
                .disabled(trimmedInput.isEmpty || trimmedInput.count > Self.maxInput)
                .accessibilityLabel(Text("Send"))
                .accessibilityIdentifier("assistantSend")
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(.bar)
    }

    private var trimmedInput: String { input.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Sends the typed message, or (`retrying`) asks again for the last one after an error.
    private func send(retrying: Bool = false) {
        if !retrying {
            guard !trimmedInput.isEmpty else { return }
            conversation.messages.append(AIMessage(.user, trimmedInput))
            input = ""
            ai.saveConversation(conversation)
        }
        guard conversation.messages.last?.role == .user else { return }
        let request = AIRequest(system: systemPrompt(), messages: conversation.messages)
        run.start(request, store: ai, onEnd: { answer in
            // After an error the partial answer stays on screen with "Try Again".
            let text = answer.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty, run.error == nil else { return }
            conversation.messages.append(AIMessage(.assistant, text))
            ai.saveConversation(conversation)
            run.reset()
        })
    }

    /// Keeps what has arrived so far.
    private func stop() {
        let text = run.answer.text.trimmingCharacters(in: .whitespacesAndNewlines)
        run.cancel()
        if !text.isEmpty {
            conversation.messages.append(AIMessage(.assistant, text))
            ai.saveConversation(conversation)
        }
        run.reset()
    }

    private func newChat() {
        run.reset()
        conversation = AIConversation()
        input = ""
    }

    private func systemPrompt() -> String {
        let today = LocalDate(.now)
        var schedule: String?
        if includeSchedule {
            let timetable = Persistence.timetable(settings: settingsRecords.first, courses: courseRecords)
            schedule = AIPrompts.weekSchedule(timetable, around: today)
        }
        var hardWords: [String]?
        if includeHardWords {
            hardWords = Array(vocab.model.hardWords.prefix(20).map(\.headword))
        }
        return AIPrompts.assistant(language: AIStore.answerLanguage, today: today, schedule: schedule, weakWords: hardWords)
    }
}

private struct MessageBubble: View {
    let message: AIMessage

    var body: some View {
        switch message.role {
        case .user:
            HStack {
                Spacer(minLength: 40)
                Text(verbatim: message.text)
                    .textSelection(.enabled)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .foregroundStyle(.white)
                    .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 16))
            }
        case .assistant:
            MarkdownText(text: message.text)
                .padding(12)
                .background(.background.secondary, in: RoundedRectangle(cornerRadius: 14))
                .accessibilityIdentifier("assistantReply")
        }
    }
}

/// Earlier Assistant chats, newest first.
private struct ChatHistoryView: View {
    let open: (AIConversation) -> Void
    @Environment(AIStore.self) private var ai
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(ai.conversations) { conversation in
                    Button {
                        open(conversation)
                        dismiss()
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(verbatim: conversation.title)
                                .lineLimit(1)
                            Text(conversation.updated, format: .dateTime.month().day().hour().minute())
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .tint(.primary)
                }
                .onDelete { offsets in
                    for id in offsets.map({ ai.conversations[$0].id }) { ai.deleteConversation(id) }
                }
            }
            .navigationTitle("Earlier Chats")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
