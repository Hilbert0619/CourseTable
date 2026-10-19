import SwiftUI
import AIKit
import VocabKit

/// Long-sentence analysis (长难句): paste or scan a sentence; the AI breaks it down and translates it, and the book's
/// words in it are listed for one-tap lookup.
struct SentenceAnalysisView: View {
    @Environment(AIStore.self) private var ai
    @Environment(VocabApp.self) private var vocab
    @AppStorage("ai.sentenceDraft") private var text = ""
    @State private var run = AIRun()
    @State private var analysed = ""
    @FocusState private var editing: Bool

    static let maxLength = 2000

    var body: some View {
        Form {
            if !ai.isSetUp {
                Section { AISetupLink() }
            }
            Section {
                TextField("Paste an English sentence or passage", text: $text, axis: .vertical)
                    .lineLimit(4...12)
                    .focused($editing)
                    .accessibilityIdentifier("sentenceField")
                TextScanButtons { scanned in
                    text = scanned
                }
            } header: {
                Text("Sentence")
            } footer: {
                Text("Check scanned text before analysing it: the camera can misread letters.")
            }
            Section {
                Button {
                    analyse()
                } label: {
                    Label("Analyse", systemImage: "sparkles")
                }
                .disabled(!ai.isSetUp || trimmed.isEmpty || trimmed.count > Self.maxLength || run.isRunning)
                .accessibilityIdentifier("analyseSentence")
                if trimmed.count > Self.maxLength {
                    Text("Too long: up to \(Self.maxLength) characters.")
                        .font(.footnote)
                        .foregroundStyle(.orange)
                }
            }
            if run.hasContent {
                Section {
                    AIAnswerView(run: run) { analyse() }
                }
                let words = SentenceWords.entries(in: analysed) { vocab.model.entry($0) }
                if !words.isEmpty {
                    Section {
                        ForEach(words, id: \.headword) { entry in
                            NavigationLink(value: WordsRoute.word(entry.headword)) {
                                LabeledContent {
                                    Text(verbatim: entry.shortMeaning)
                                        .lineLimit(1)
                                } label: {
                                    Text(verbatim: entry.display)
                                }
                            }
                        }
                    } header: {
                        Text("Words from Your Book")
                    } footer: {
                        Text("Open a word to see it in full or add it to your favourites.")
                    }
                }
            }
        }
        .paperBackground()
        .navigationTitle("Long Sentences")
        .onDisappear { run.cancel() }
    }

    private var trimmed: String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    private func analyse() {
        editing = false
        analysed = trimmed
        run.start(AIPrompts.sentenceAnalysis(trimmed, language: AIStore.answerLanguage), store: ai)
    }
}

/// Essay feedback (作文): pick the exam task, paste or scan the essay, and get a mark, corrections and a better
/// version.
struct EssayFeedbackView: View {
    @Environment(AIStore.self) private var ai
    @AppStorage("ai.essayTask") private var task = EssayTask.english1Long
    @AppStorage("ai.essayPrompt") private var prompt = ""
    @AppStorage("ai.essayDraft") private var essay = ""
    @State private var run = AIRun()
    @FocusState private var editing: Bool

    static let maxLength = 6000

    var body: some View {
        Form {
            if !ai.isSetUp {
                Section { AISetupLink() }
            }
            Section {
                Picker("Task", selection: $task) {
                    ForEach(EssayTask.allCases, id: \.self) { task in
                        title(task).tag(task)
                    }
                }
                TextField("The task's instructions (optional)", text: $prompt, axis: .vertical)
                    .lineLimit(1...6)
                    .focused($editing)
            } header: {
                Text("Task")
            }
            Section {
                TextField("Your essay", text: $essay, axis: .vertical)
                    .lineLimit(8...30)
                    .focused($editing)
                    .accessibilityIdentifier("essayField")
                TextScanButtons { scanned in
                    essay = essay.isEmpty ? scanned : essay + "\n" + scanned
                }
            } header: {
                Text("Essay")
            } footer: {
                Text("\(wordCount) words")
            }
            Section {
                Button {
                    mark()
                } label: {
                    Label("Mark My Essay", systemImage: "sparkles")
                }
                .disabled(!ai.isSetUp || wordCount < 20 || essay.count > Self.maxLength || run.isRunning)
                .accessibilityIdentifier("markEssay")
            } footer: {
                Text("The score is an AI estimate, not an official mark.")
            }
            if run.hasContent {
                Section {
                    AIAnswerView(run: run) { mark() }
                }
            }
        }
        .paperBackground()
        .navigationTitle("Writing Feedback")
        .onDisappear { run.cancel() }
    }

    private var wordCount: Int {
        essay.split { !$0.isLetter && $0 != "'" && $0 != "-" }.count
    }

    private func mark() {
        editing = false
        run.start(AIPrompts.essayFeedback(essay, task: task, prompt: prompt.trimmingCharacters(in: .whitespacesAndNewlines),
                                          language: AIStore.answerLanguage),
                  store: ai)
    }

    private func title(_ task: EssayTask) -> Text {
        switch task {
        case .english1Short: Text("English I · Short (letter or notice)")
        case .english1Long: Text("English I · Long (picture essay)")
        case .english2Short: Text("English II · Short (letter or notice)")
        case .english2Long: Text("English II · Long (chart essay)")
        }
    }
}
