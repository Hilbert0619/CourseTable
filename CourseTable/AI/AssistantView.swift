import SwiftUI
import SwiftData
import PhotosUI
import AIKit
import CourseKit
import VocabKit

/// The Assistant: a chat with the user's AI connection, kept on the device. When the model can call tools it
/// looks at the user's to-dos, classes, focus sessions and vocabulary numbers by itself, and suggests changes
/// as cards the user confirms (docs/ai-agent-spec.md). The week's classes and the hard words can also be shared
/// up front. The study tools are linked from an empty chat, so they work without a Words account too.
struct AssistantView: View {
    /// Sent as soon as the chat opens (e.g. "Analyse My Progress").
    var startMessage: String?

    @Environment(AIStore.self) private var ai
    @Environment(VocabApp.self) private var vocab
    @Environment(PlannerModel.self) private var planner
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \CourseRecord.createdAt) private var courseRecords: [CourseRecord]
    @Query private var settingsRecords: [SettingsRecord]
    @AppStorage("ai.includeSchedule") private var includeSchedule = false
    @AppStorage("ai.includeHardWords") private var includeHardWords = false
    @State private var run = AgentRun(usesTools: false)
    @State private var started = false
    @State private var input = ""
    @State private var attachments: [Attachment] = []
    @State private var readingPhoto = false
    @State private var photoProblem = false
    @State private var showingHistory = false
    @FocusState private var inputFocused: Bool

    static let maxInput = 4000
    static let maxAttachments = 3

    init(startMessage: String? = nil) {
        self.startMessage = startMessage
    }

    /// A picture for the model, or the text the phone read from one when the model can't read pictures.
    enum Attachment: Hashable {
        case image(Data)
        case photoText(String)
    }

    private var context: AgentContext {
        AgentContext(planner: planner, vocab: vocab, modelContext: modelContext)
    }

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
                        .disabled(run.isEmpty)
                    }
                }
            }
            .navigationDestination(for: WordsRoute.self) { $0.destination }
            .sheet(isPresented: $showingHistory) {
                ChatHistoryView { picked in
                    run.round.cancel()
                    run = AgentRun(conversation: picked, usesTools: usesTools)
                }
            }
            .sheet(item: $run.importRequest, onDismiss: importClosed) { request in
                ImportView(timetable: context.timetable(), text: request.csv) { added, skipped in
                    run.imported(request.id, added: added, skipped: skipped, store: ai, context: context)
                }
            }
        }
        .onAppear(perform: start)
        .onDisappear { run.round.cancel() }
    }

    private var usesTools: Bool { ai.defaultConnection?.usesTools ?? false }

    private func start() {
        guard !started else { return }
        started = true
        run = AgentRun(usesTools: usesTools)
        if let startMessage, ai.isSetUp { send(startMessage) }
    }

    // MARK: Chat

    private var chat: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                if run.isEmpty {
                    welcome
                }
                ForEach(rows) { row in
                    switch row {
                    case .user(_, let message):
                        UserBubble(message: message)
                    case .text(_, let text):
                        MarkdownText(text: text)
                            .padding(12)
                            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 14))
                            .accessibilityIdentifier("assistantReply")
                    case .card(let card):
                        AgentCardView(card: card, canUndo: run.canUndo(card.id),
                                      confirm: { run.confirm(card.id, store: ai, context: context) },
                                      cancel: { run.cancel(card.id, store: ai, context: context) },
                                      undo: { run.undo(card.id, store: ai, context: context) })
                    }
                }
                if run.round.hasContent {
                    AIAnswerView(run: run.round) { run.retry(store: ai, context: context) }
                        .padding(12)
                        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 14))
                }
                if let notice = run.notice {
                    noticeView(notice)
                }
            }
            .padding()
        }
        // An empty chat starts at the top; a conversation follows its latest message.
        .defaultScrollAnchor(run.isEmpty ? .top : .bottom)
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom) { inputBar }
    }

    /// One row of the chat: what the user wrote, the assistant's text, or a tool call.
    private enum Row: Identifiable {
        case user(Int, AIMessage)
        case text(Int, String)
        case card(AgentCard)

        var id: String {
            switch self {
            case .user(let index, _): "user-\(index)"
            case .text(let index, _): "text-\(index)"
            case .card(let card): "card-\(card.id)"
            }
        }
    }

    private var rows: [Row] {
        var rows: [Row] = []
        for (index, message) in run.messages.enumerated() {
            switch message.role {
            case .user:
                if !message.text.isEmpty || !message.images.isEmpty { rows.append(.user(index, message)) }
            case .assistant:
                if !message.text.isEmpty { rows.append(.text(index, message.text)) }
                for call in message.toolCalls {
                    if let card = run.conversation.cards[call.id] { rows.append(.card(card)) }
                }
            }
        }
        return rows
    }

    private var welcome: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Ask about your studies, English, planning or anything else.", systemImage: "sparkles")
                .foregroundStyle(.secondary)
            if run.usesTools, let provider = ai.defaultConnection?.name {
                Text("The Assistant can look at your to-dos, classes, focus sessions and vocabulary progress to answer, sending what it looks at to \(provider). It suggests changes as cards; nothing changes until you tap Confirm.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 8) {
                    suggestion("Remind me to hand in the paper after class tomorrow", id: "suggestReminder")
                    suggestion("How are my words going?", id: "suggestProgress")
                    suggestion("Start a 25-minute focus session", id: "suggestFocus")
                }
            } else if includeSchedule || includeHardWords {
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

    private func suggestion(_ text: LocalizedStringResource, id: String) -> some View {
        Button {
            send(String(localized: text))
        } label: {
            Text(text)
                .font(.callout)
                .multilineTextAlignment(.leading)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(.background.secondary, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }

    private var sharingSummary: LocalizedStringKey {
        switch (includeSchedule, includeHardWords) {
        case (true, true): "This chat can see this week's classes and your hard words."
        case (true, false): "This chat can see this week's classes."
        default: "This chat can see your hard words."
        }
    }

    @ViewBuilder
    private func noticeView(_ notice: AgentRun.Notice) -> some View {
        let text: Text = switch notice {
        case .toolsUnavailable: Text("This model can't use the app's tools, so this chat goes on without them. Pick another model in Settings → AI Help.")
        case .roundLimit: Text("The Assistant stopped after too many steps. Ask again to go on.")
        case .cutOff: Text("The answer was cut off by the provider's length limit.")
        case .refused: Text("The provider declined to answer this.")
        }
        Label { text } icon: { Image(systemName: "info.circle") }
            .font(.footnote)
            .foregroundStyle(.secondary)
            .accessibilityIdentifier("assistantNotice")
    }

    // MARK: Input

    private var inputBar: some View {
        VStack(alignment: .leading, spacing: 6) {
            if !attachments.isEmpty || readingPhoto || photoProblem {
                attachmentRow
            }
            HStack(alignment: .bottom, spacing: 8) {
                AttachPhotoButton { data in attach(data) }
                    .disabled(attachments.count >= Self.maxAttachments || readingPhoto)
                TextField("Message", text: $input, axis: .vertical)
                    .lineLimit(1...6)
                    .focused($inputFocused)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(.background.secondary, in: RoundedRectangle(cornerRadius: 18))
                    .accessibilityIdentifier("assistantInput")
                if run.round.isRunning {
                    Button {
                        run.stop(store: ai)
                    } label: {
                        Image(systemName: "stop.circle.fill")
                            .font(.title)
                    }
                    .accessibilityLabel(Text("Stop"))
                } else {
                    Button {
                        send(trimmedInput)
                    } label: {
                        Image(systemName: "arrow.up.circle.fill")
                            .font(.title)
                    }
                    .disabled((trimmedInput.isEmpty && attachments.isEmpty) || trimmedInput.count > Self.maxInput || readingPhoto)
                    .accessibilityLabel(Text("Send"))
                    .accessibilityIdentifier("assistantSend")
                }
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(.bar)
    }

    private var attachmentRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(attachments.enumerated()), id: \.offset) { index, attachment in
                    HStack(spacing: 6) {
                        switch attachment {
                        case .image(let data):
                            if let image = UIImage(data: data) {
                                Image(uiImage: image)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 36, height: 36)
                                    .clipShape(RoundedRectangle(cornerRadius: 6))
                            }
                            Text("Photo")
                        case .photoText:
                            Image(systemName: "text.viewfinder")
                            Text("Text from a photo")
                        }
                        Button {
                            attachments.remove(at: index)
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(.secondary)
                        }
                        .accessibilityLabel(Text("Remove"))
                    }
                    .font(.footnote)
                    .padding(6)
                    .background(.background.secondary, in: RoundedRectangle(cornerRadius: 10))
                    .accessibilityIdentifier("assistantAttachment")
                }
                if readingPhoto {
                    Label { Text("Reading the text in the photo…") } icon: { ProgressView() }
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else if photoProblem {
                    Text("No text found in the photo.")
                        .font(.footnote)
                        .foregroundStyle(.orange)
                }
            }
        }
    }

    private var trimmedInput: String { input.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// A picture goes to a model that reads pictures; for any other, the phone reads its text first.
    private func attach(_ data: Data) {
        photoProblem = false
        guard let jpeg = PhotoPrep.jpeg(data) else { return }
        if ai.defaultConnection?.readsImages == true {
            attachments.append(.image(jpeg))
            return
        }
        readingPhoto = true
        Task {
            let boxes = await TextRecognizer.boxes(in: jpeg)
            readingPhoto = false
            if boxes.isEmpty {
                photoProblem = true
            } else {
                attachments.append(.photoText(TimetablePhoto.ocrText(boxes)))
            }
        }
    }

    private func send(_ typed: String) {
        let texts = attachments.compactMap { attachment -> String? in
            if case .photoText(let text) = attachment { return AIPrompts.photoText(text) }
            return nil
        }
        let images = attachments.compactMap { attachment -> AIImage? in
            if case .image(let data) = attachment { return AIImage(data: data) }
            return nil
        }
        let text = ([typed] + texts).filter { !$0.isEmpty }.joined(separator: "\n\n")
        guard !text.isEmpty || !images.isEmpty else { return }
        // A new connection (or a changed tools switch) applies from a fresh chat on.
        if run.isEmpty && run.usesTools != usesTools { run = AgentRun(usesTools: usesTools) }
        input = ""
        attachments = []
        photoProblem = false
        run.send(text, images: images, system: systemPrompt(), store: ai, context: context)
    }

    private func newChat() {
        run.round.cancel()
        run = AgentRun(usesTools: usesTools)
        input = ""
        attachments = []
    }

    private func importClosed() {
        // The sheet closed: without an import, the proposal was turned down.
        for (id, card) in run.conversation.cards where card.state == .waiting && card.tool == AppTools.addCourses.name {
            run.importClosed(id, store: ai, context: context)
        }
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
        return AIPrompts.assistant(language: AIStore.answerLanguage, today: today, now: TimeOfDay(.now), schedule: schedule,
                                   weakWords: hardWords, usesTools: run.usesTools)
    }
}

/// Picks a photo to send. UI tests (`-aiDemoPhoto`) get a drawn sample timetable instead.
private struct AttachPhotoButton: View {
    let onPhoto: (Data) -> Void
    @State private var item: PhotosPickerItem?

    var body: some View {
        Group {
            if UITestLaunch.wantsAIDemoPhoto {
                Button {
                    if let data = SampleTimetablePicture.data() { onPhoto(data) }
                } label: {
                    icon
                }
            } else {
                PhotosPicker(selection: $item, matching: .images) { icon }
            }
        }
        .accessibilityLabel(Text("Add a Photo"))
        .accessibilityIdentifier("assistantAttach")
        .onChange(of: item) { _, picked in
            guard let picked else { return }
            Task {
                let data = try? await picked.loadTransferable(type: Data.self)
                item = nil
                if let data { onPhoto(data) }
            }
        }
    }

    private var icon: some View {
        Image(systemName: "photo.badge.plus")
            .font(.title2)
    }
}

private struct UserBubble: View {
    let message: AIMessage

    var body: some View {
        let parts = AIPrompts.typedPart(of: message.text)
        HStack {
            Spacer(minLength: 40)
            VStack(alignment: .trailing, spacing: 6) {
                if !message.images.isEmpty || parts.hasPhotoText {
                    HStack(spacing: 6) {
                        ForEach(Array(message.images.enumerated()), id: \.offset) { _, image in
                            if let picture = UIImage(data: image.data) {
                                Image(uiImage: picture)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 72, height: 72)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                            } else {
                                Label("Photo", systemImage: "photo")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        if parts.hasPhotoText {
                            Label("Text from a photo", systemImage: "text.viewfinder")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                if !parts.text.isEmpty {
                    Text(verbatim: parts.text)
                        .textSelection(.enabled)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .foregroundStyle(.white)
                        .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 16))
                }
            }
        }
    }
}

/// A tool call in the chat: a line for a lookup, a card with Confirm / Cancel for a change.
struct AgentCardView: View {
    let card: AgentCard
    let canUndo: Bool
    let confirm: () -> Void
    let cancel: () -> Void
    let undo: () -> Void

    var body: some View {
        switch card.state {
        case .looked:
            Label { Text(verbatim: card.title) } icon: { Image(systemName: "checkmark.circle") }
                .font(.footnote)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("agentLookup")
        case .failed:
            Label { Text("\(card.title): not possible") } icon: { Image(systemName: "xmark.circle") }
                .font(.footnote)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("agentFailed")
        default:
            proposal
        }
    }

    private var icon: String {
        switch card.tool {
        case AppTools.addTodo.name: "plus.circle.fill"
        case AppTools.updateTodo.name: "pencil.circle.fill"
        case AppTools.deleteTodo.name: "trash.circle.fill"
        case AppTools.addCourses.name: "calendar.badge.plus"
        case AppTools.startFocus.name: "timer"
        default: "sparkles"
        }
    }

    private var proposal: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label { Text(verbatim: card.title).font(.headline) } icon: { Image(systemName: icon) }
            ForEach(Array(card.lines.enumerated()), id: \.offset) { index, line in
                Text(verbatim: line)
                    .font(index == 0 ? .body : .subheadline)
                    .foregroundStyle(index == 0 ? .primary : .secondary)
            }
            switch card.state {
            case .waiting:
                HStack {
                    Button("Cancel", role: .cancel, action: cancel)
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("agentCancel")
                    Button(action: confirm) {
                        card.tool == AppTools.addCourses.name ? Text("Review…") : Text("Confirm")
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("agentConfirm")
                }
                .padding(.top, 2)
            case .confirmed:
                HStack {
                    Label("Done", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                        .accessibilityIdentifier("agentDone")
                    if canUndo {
                        Spacer()
                        Button("Undo", action: undo)
                            .buttonStyle(.borderless)
                            .accessibilityIdentifier("agentUndo")
                    }
                }
                .font(.subheadline)
            case .cancelled:
                Text("Cancelled")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            case .undone:
                Text("Undone")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            case .looked, .failed:
                EmptyView()
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.accentColor.opacity(0.5)))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("agentCard")
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
