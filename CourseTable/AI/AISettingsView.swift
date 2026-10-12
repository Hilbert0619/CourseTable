import SwiftUI
import AIKit

/// Settings → AI: the user's connections (provider, key, model), which one AI help uses, this month's usage,
/// and what is sent where.
struct AISettingsView: View {
    @Environment(AIStore.self) private var ai
    @State private var editing: EditorTarget?
    @State private var confirmingClear = false

    enum EditorTarget: Identifiable {
        case new
        case edit(AIConnection)

        var id: String {
            switch self {
            case .new: "new"
            case .edit(let connection): connection.id.uuidString
            }
        }
    }

    var body: some View {
        List {
            Section {
                ForEach(ai.connections) { connection in
                    Button {
                        editing = .edit(connection)
                    } label: {
                        ConnectionRow(connection: connection, isDefault: connection.id == ai.defaultConnection?.id,
                                      usage: ai.usage(of: connection))
                    }
                    .tint(.primary)
                    .swipeActions(edge: .leading) {
                        if connection.id != ai.defaultConnection?.id {
                            Button {
                                ai.makeDefault(connection)
                            } label: {
                                Label("Use for AI Help", systemImage: "checkmark.circle")
                            }
                            .tint(.green)
                        }
                    }
                }
                .onDelete { offsets in
                    for index in offsets { ai.delete(ai.connections[index]) }
                }
                Button {
                    editing = .new
                } label: {
                    Label("Add Connection", systemImage: "plus")
                }
                .accessibilityIdentifier("aiAddConnection")
            } header: {
                Text("Connections")
            } footer: {
                if ai.connections.count > 1 {
                    Text("AI help uses the connection with the check mark. Swipe right on another to use it instead.")
                }
            }

            Section {
                Text("Your questions go straight from this iPhone to the provider you chose, under their terms, and are paid from your account with them. CourseTable's server never sees them. Keys stay in this iPhone's Keychain.")
                Text("Each feature sends only what it needs: a word and its dictionary entry, a sentence you paste, or a to-do note with your course names.")
                Text("AI answers can be wrong. The dictionary still decides what counts as a correct answer.")
            } header: {
                Text("Privacy")
            }
            .font(.footnote)
            .foregroundStyle(.secondary)

            if ai.savedAnswerCount > 0 || !ai.conversations.isEmpty {
                Section {
                    Button("Delete Saved Answers", role: .destructive) { confirmingClear = true }
                } footer: {
                    Text("\(ai.savedAnswerCount) answers and \(ai.conversations.count) Assistant chats are saved on this iPhone, so opening them again is free.")
                }
            }
        }
        .navigationTitle("AI")
        .sheet(item: $editing) { target in
            switch target {
            case .new: AIConnectionEditor(connection: nil)
            case .edit(let connection): AIConnectionEditor(connection: connection)
            }
        }
        .confirmationDialog("Delete all saved AI answers and Assistant chats?", isPresented: $confirmingClear, titleVisibility: .visible) {
            Button("Delete Saved Answers", role: .destructive) { ai.clearSavedAnswers() }
        }
    }
}

private struct ConnectionRow: View {
    let connection: AIConnection
    let isDefault: Bool
    let usage: AIUsageTotal

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: connection.name)
                Text(verbatim: connection.model)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if usage.requests > 0 {
                    Text("This month: \(usage.requests) requests · \(usage.inputTokens + usage.outputTokens) tokens")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            if isDefault {
                Image(systemName: "checkmark")
                    .foregroundStyle(Color.accentColor)
                    .accessibilityLabel(Text("Used for AI help"))
            }
        }
        .contentShape(Rectangle())
    }
}

/// Adds or edits a connection: provider, key, model (typed or loaded from the provider), address, and a test.
struct AIConnectionEditor: View {
    let connection: AIConnection?
    @Environment(AIStore.self) private var ai
    @Environment(\.dismiss) private var dismiss
    @State private var providerID = "deepseek"
    @State private var name = ""
    @State private var baseURL = ""
    @State private var model = ""
    @State private var key = ""
    @State private var models: [String] = []
    @State private var loadingModels = false
    @State private var modelsError: AIError?
    @State private var test = AIRun()
    @State private var testedOK = false
    @State private var loaded = false
    /// A new connection's ID, the same for its test and its save.
    @State private var newID = UUID()

    private var provider: AIProvider? { AIProvider.preset(id: providerID) }
    private var hasStoredKey: Bool { connection.map(ai.hasKey) ?? false }

    /// The connection as currently filled in.
    private var draft: AIConnection {
        AIConnection(id: connection?.id ?? newID, providerID: providerID,
                     name: name.trimmingCharacters(in: .whitespaces).isEmpty ? (provider?.name ?? "AI") : name,
                     format: provider?.format ?? .openAI, baseURL: baseURL,
                     model: model.trimmingCharacters(in: .whitespaces))
    }

    /// The key to use: the one typed, or the stored one.
    private var effectiveKey: String? {
        let typed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        if !typed.isEmpty { return typed }
        return connection.flatMap(ai.key(for:))
    }

    private var canSave: Bool {
        draft.normalizedBaseURL != nil && !draft.model.isEmpty
            && (!(provider?.needsKey ?? true) || effectiveKey != nil)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Provider", selection: $providerID) {
                        Section("No VPN needed in mainland China") { providerOptions(.china) }
                        Section("International") { providerOptions(.international) }
                        Section("Other") {
                            providerOptions(.local)
                            providerOptions(.custom)
                        }
                    }
                    .pickerStyle(.navigationLink)
                    .disabled(connection != nil)
                    .accessibilityIdentifier("aiProvider")
                    TextField("Name", text: $name, prompt: Text(verbatim: provider?.name ?? ""))
                    if let provider, let page = provider.keyPage, let url = URL(string: page) {
                        let providerName = provider.name
                        Link(destination: url) {
                            Label("Get a Key from \(providerName)", systemImage: "arrow.up.right.square")
                        }
                    }
                }

                if provider?.needsKey ?? true {
                    Section {
                        SecureField(text: $key, prompt: hasStoredKey ? Text("Saved — type to replace") : Text("API key")) {
                            Text("API key")
                        }
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .accessibilityIdentifier("aiKeyField")
                    } header: {
                        Text("API Key")
                    } footer: {
                        Text("Stored only in this iPhone's Keychain, never synced or exported.")
                    }
                }

                Section {
                    TextField("Model", text: $model)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("aiModelField")
                    if !models.isEmpty {
                        Picker("Available models", selection: $model) {
                            ForEach(models, id: \.self) { Text(verbatim: $0).tag($0) }
                            if !models.contains(model) { Text(verbatim: model).tag(model) }
                        }
                        .pickerStyle(.navigationLink)
                    }
                    Button {
                        loadModels()
                    } label: {
                        HStack {
                            Text("Load Models from the Provider")
                            if loadingModels {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .disabled(loadingModels || draft.normalizedBaseURL == nil)
                    if let modelsError {
                        AIErrorView(error: modelsError)
                            .font(.footnote)
                    }
                } header: {
                    Text("Model")
                } footer: {
                    if let suggested = provider?.models, !suggested.isEmpty {
                        let list = suggested.joined(separator: ", ")
                        Text("Suggested: \(list)")
                    }
                }

                Section {
                    TextField("Address", text: $baseURL)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("aiAddressField")
                } header: {
                    Text("Address")
                } footer: {
                    if providerID == "local" {
                        Text("Your computer's address on this Wi-Fi with the server's port and /v1, e.g. http://192.168.1.2:11434/v1 for Ollama or :1234/v1 for LM Studio.")
                    } else if draft.isInsecure {
                        Text("Plain http sends the key unencrypted. Use it only for a computer on your own network.")
                    } else if draft.normalizedBaseURL == nil {
                        Text("Enter the provider's OpenAI-compatible base URL, ending before /chat/completions.")
                    }
                }

                Section {
                    Button {
                        runTest()
                    } label: {
                        Label("Test Connection", systemImage: "bolt.horizontal")
                    }
                    .disabled(!canSave || test.isRunning)
                    .accessibilityIdentifier("aiTest")
                    if test.isRunning {
                        ProgressView()
                    } else if let error = test.error {
                        AIErrorView(error: error)
                    } else if testedOK {
                        Label("Connected. The model answered.", systemImage: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                            .accessibilityIdentifier("aiTestOK")
                    }
                }

                if let connection {
                    Section {
                        Button("Delete Connection", role: .destructive) {
                            ai.delete(connection)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(connection == nil ? Text("New Connection") : Text(verbatim: connection?.name ?? ""))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        ai.save(draft, key: key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : key)
                        dismiss()
                    }
                    .disabled(!canSave)
                    .accessibilityIdentifier("aiSave")
                }
            }
            .onAppear(perform: load)
            .onChange(of: providerID) { _, _ in
                guard connection == nil, let provider else { return }
                baseURL = provider.baseURL
                model = provider.defaultModel
                models = []
                modelsError = nil
                testedOK = false
                test.reset()
            }
            .onDisappear { test.cancel() }
        }
    }

    private func providerOptions(_ region: AIProvider.Region) -> some View {
        ForEach(AIProvider.presets.filter { $0.region == region }) { provider in
            Text(verbatim: provider.name).tag(provider.id)
        }
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        if let connection {
            providerID = connection.providerID
            name = connection.name
            baseURL = connection.baseURL
            model = connection.model
        } else if let provider {
            baseURL = provider.baseURL
            model = provider.defaultModel
        }
    }

    private func loadModels() {
        loadingModels = true
        modelsError = nil
        let request: AIHTTPRequest
        do {
            request = try draft.modelsRequest(key: effectiveKey)
        } catch {
            modelsError = error as? AIError ?? .unreadable
            loadingModels = false
            return
        }
        Task {
            do {
                let data = try await ai.transport.data(for: request)
                models = try AIConnection.modelIDs(from: data)
                if model.isEmpty, let first = models.first { model = first }
            } catch {
                modelsError = error as? AIError ?? .unreadable
            }
            loadingModels = false
        }
    }

    private func runTest() {
        testedOK = false
        test.start(AIPrompts.connectionTest, store: ai, connection: draft, key: effectiveKey) { _ in
            testedOK = true
        }
    }
}
