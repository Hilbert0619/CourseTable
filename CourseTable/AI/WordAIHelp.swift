import SwiftUI
import AIKit
import VocabKit

/// The word page's AI help: Explain, Memory aid, Compare, Collocations. Finished answers are saved on the
/// device, so opening one again is instant and free; "Ask Again" gets a fresh one.
struct WordAISection: View {
    let entry: WordEntry
    @Environment(AIStore.self) private var ai
    @State private var kind: WordHelpKind?
    @State private var run = AIRun()

    var body: some View {
        Section {
            if ai.isSetUp {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(WordHelpKind.allCases, id: \.self) { item in
                            Button {
                                open(item)
                            } label: {
                                Label { title(item) } icon: { Image(systemName: symbol(item)) }
                                    .font(.subheadline)
                            }
                            .buttonStyle(.bordered)
                            .tint(kind == item ? Color.accentColor : Color.secondary)
                            .accessibilityIdentifier("aiHelp-\(item.rawValue)")
                        }
                    }
                    .padding(.vertical, 2)
                }
                if let kind, run.hasContent {
                    AIAnswerView(run: run) { ask(kind) }
                    if !run.isRunning && run.error == nil {
                        Button {
                            ask(kind)
                        } label: {
                            Label("Ask Again", systemImage: "arrow.clockwise")
                        }
                        .accessibilityIdentifier("aiAskAgain")
                    }
                }
            } else {
                AISetupLink()
            }
        } header: {
            Text("AI Help")
        } footer: {
            if !ai.isSetUp {
                Text("Explanations, memory aids, comparisons and collocations from an AI provider you choose, with your own key.")
            }
        }
        .onDisappear { run.cancel() }
    }

    private var language: AIAnswerLanguage { AIStore.answerLanguage }

    private func cacheKey(_ kind: WordHelpKind) -> String {
        "word|\(entry.headword)|\(kind.rawValue)|\(language.rawValue)"
    }

    private func open(_ item: WordHelpKind) {
        kind = item
        if let saved = ai.cachedAnswer(cacheKey(item)) {
            run.show(saved)
        } else {
            ask(item)
        }
    }

    private func ask(_ item: WordHelpKind) {
        let key = cacheKey(item)
        run.start(AIPrompts.wordHelp(item, entry: entry, language: language), store: ai) { [ai, run] answer in
            ai.saveAnswer(answer.text, model: run.model, for: key)
        }
    }

    private func title(_ kind: WordHelpKind) -> Text {
        switch kind {
        case .explain: Text("Explain")
        case .memory: Text("Memory Aid")
        case .compare: Text("Compare")
        case .collocations: Text("Collocations")
        }
    }

    private func symbol(_ kind: WordHelpKind) -> String {
        switch kind {
        case .explain: "text.magnifyingglass"
        case .memory: "brain.head.profile"
        case .compare: "arrow.left.arrow.right"
        case .collocations: "link"
        }
    }
}

/// "Why?" after a wrong answer in a study session: the AI explains the difference. Hidden without AI.
struct WhyWrongButton: View {
    let entry: WordEntry
    /// The meaning picked instead of the right one (multiple choice).
    var picked: String?
    /// The misspelling (spelling questions).
    var typed: String?
    @Environment(AIStore.self) private var ai
    @State private var showing = false

    var body: some View {
        if ai.isSetUp {
            Button {
                showing = true
            } label: {
                Label("Why?", systemImage: "sparkles")
            }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("aiWhy")
            .sheet(isPresented: $showing) {
                WhyWrongSheet(entry: entry, picked: picked, typed: typed)
            }
        }
    }
}

private struct WhyWrongSheet: View {
    let entry: WordEntry
    let picked: String?
    let typed: String?
    @Environment(AIStore.self) private var ai
    @Environment(\.dismiss) private var dismiss
    @State private var run = AIRun()

    var body: some View {
        NavigationStack {
            ScrollView {
                AIAnswerView(run: run) { ask() }
                    .padding()
            }
            .navigationTitle(Text(verbatim: entry.display))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        // Once: the sheet's task can run again when the sheet is laid out anew.
        .task { if !run.hasContent { ask() } }
        .onDisappear { run.cancel() }
    }

    private func ask() {
        run.start(AIPrompts.whyWrong(entry: entry, picked: picked, typed: typed, language: AIStore.answerLanguage),
                  store: ai)
    }
}
