import SwiftUI
import VocabKit

/// A study session (docs/vocabulary-spec.md §7): learning steps that are due, then due reviews, then new
/// words, until nothing is left for now. Every answer is saved straight away, so closing early loses nothing.
struct StudySessionView: View {
    @Environment(VocabApp.self) private var vocab
    @Environment(\.dismiss) private var dismiss
    @State private var session: StudySession?
    @State private var item: StudyItem?
    @State private var questionNumber = 0
    @State private var shownAt = Date.now
    @State private var finished = false

    private var model: VocabModel { vocab.model }

    var body: some View {
        NavigationStack {
            Group {
                if let item, let session {
                    QuestionView(
                        item: item,
                        entry: model.entry(item.headword) ?? WordEntry(headword: item.headword, translation: ""),
                        card: session.cards[item.headword],
                        seed: SeededRandom.seed(String(session.seed), item.headword, String(questionNumber)),
                        onAnswer: answer
                    )
                    .id(questionNumber)
                } else if finished, let session {
                    SessionSummaryView(summary: session.summary, waiting: session.waiting(at: .now)) {
                        dismiss()
                    }
                } else {
                    ProgressView()
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel(Text("Close"))
                    .accessibilityIdentifier("closeSession")
                }
                ToolbarItem(placement: .principal) {
                    if let session, !finished {
                        RemainingView(remaining: session.remaining(at: .now))
                    }
                }
            }
        }
        .task { start() }
        .onDisappear {
            Task { await vocab.syncNow() }
        }
    }

    private func start() {
        guard session == nil else { return }
        let plan = model.plan()
        session = StudySession(
            cards: model.cards, newWords: plan.newWords, now: .now, scheduler: model.scheduler, clock: model.clock,
            mode: model.settings.reviewMode, seed: UInt64(Date.now.timeIntervalSince1970 * 1000)
        )
        showNext()
    }

    private func showNext() {
        guard let session else { return }
        if let upcoming = session.next(at: .now) {
            item = upcoming
            questionNumber += 1
            shownAt = .now
        } else {
            item = nil
            finished = true
        }
    }

    /// Saves the answer to the current question, asked in `mode`, and moves on.
    private func answer(_ rating: Rating, mode: ReviewMode) {
        guard var current = session, var answered = item else { return }
        answered.mode = mode
        let now = Date.now
        // A question left open while away from the phone shouldn't skew the time estimates.
        let duration = min(Int(now.timeIntervalSince(shownAt) * 1000), 5 * 60 * 1000)
        let event = current.answer(answered, rating: rating, at: now, durationMs: duration)
        if let card = current.cards[answered.headword] {
            model.record(event, card: card)
        }
        session = current
        showNext()
    }
}

/// New, learning and review questions left.
private struct RemainingView: View {
    let remaining: (new: Int, review: Int, learning: Int)

    var body: some View {
        HStack(spacing: 12) {
            count(remaining.new, "sparkles", .blue)
            count(remaining.learning, "arrow.triangle.2.circlepath", .orange)
            count(remaining.review, "clock.arrow.circlepath", .green)
        }
        .font(.subheadline.monospacedDigit())
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("remaining")
    }

    private func count(_ value: Int, _ symbol: String, _ color: Color) -> some View {
        Label {
            Text(verbatim: "\(value)")
        } icon: {
            Image(systemName: symbol)
                .foregroundStyle(color)
        }
    }
}

/// One question, in the way its mode asks it.
private struct QuestionView: View {
    let item: StudyItem
    let entry: WordEntry
    let card: Card?
    let seed: UInt64
    let onAnswer: (Rating, ReviewMode) -> Void
    @Environment(VocabApp.self) private var vocab

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                switch (item.kind, item.mode) {
                case (.new, _):
                    NewWordQuestion(entry: entry, question: question, onAnswer: onAnswer)
                case (_, .recognition):
                    RecognitionQuestion(entry: entry, card: card ?? Card(headword: entry.headword), onAnswer: onAnswer)
                case (_, .choice):
                    if let question {
                        ChoiceQuestionView(entry: entry, question: question) { correct in
                            onAnswer(AnswerGrader.rating(for: .choice(correct: correct)), .choice)
                        }
                    } else {
                        RecognitionQuestion(entry: entry, card: card ?? Card(headword: entry.headword), onAnswer: onAnswer)
                    }
                case (_, .spelling):
                    SpellingQuestion(entry: entry) { result in
                        onAnswer(AnswerGrader.rating(for: .spelling(result)), .spelling)
                    }
                }
            }
            .padding()
            .frame(maxWidth: .infinity)
        }
    }

    private var question: ChoiceQuestion? {
        vocab.model.choicePool.question(for: entry, seed: seed)
    }
}

/// A new word: its page first, then "I know this" (it skips the learning steps) or a quick check.
private struct NewWordQuestion: View {
    let entry: WordEntry
    let question: ChoiceQuestion?
    let onAnswer: (Rating, ReviewMode) -> Void
    @State private var checking = false

    var body: some View {
        if checking, let question {
            ChoiceQuestionView(entry: entry, question: question) { correct in
                onAnswer(AnswerGrader.rating(for: .choice(correct: correct)), .choice)
            }
        } else {
            Text("New word")
                .font(.caption.bold())
                .foregroundStyle(.blue)
                .textCase(.uppercase)
            WordHeader(entry: entry)
            MeaningsView(entry: entry)
                .frame(maxWidth: .infinity, alignment: .leading)
            if !entry.forms.isEmpty {
                FormsView(entry: entry)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            VStack(spacing: 12) {
                Button {
                    if question == nil {
                        onAnswer(.good, .recognition)
                    } else {
                        checking = true
                    }
                } label: {
                    Text("Learn It")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("learnWord")
                Button {
                    onAnswer(AnswerGrader.rating(for: .alreadyKnown), .recognition)
                } label: {
                    Text("I Know This")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("iKnowThis")
            }
            .controlSize(.large)
        }
    }
}

/// See the word, recall its meaning, then rate how well you knew it.
private struct RecognitionQuestion: View {
    let entry: WordEntry
    let card: Card
    let onAnswer: (Rating, ReviewMode) -> Void
    @Environment(VocabApp.self) private var vocab
    @State private var revealed = false

    var body: some View {
        WordHeader(entry: entry)
            .padding(.top, 24)
        if revealed {
            MeaningsView(entry: entry)
                .frame(maxWidth: .infinity, alignment: .leading)
            RatingButtons(card: card) { rating in
                onAnswer(rating, .recognition)
            }
        } else {
            Text("Do you remember what it means?")
                .foregroundStyle(.secondary)
            Button {
                revealed = true
            } label: {
                Text("Show Answer")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .accessibilityIdentifier("showAnswer")
        }
    }
}

/// Again / Hard / Good / Easy, each with when the word would come back.
private struct RatingButtons: View {
    let card: Card
    let onRate: (Rating) -> Void
    @Environment(VocabApp.self) private var vocab

    var body: some View {
        let model = vocab.model
        let now = Date.now
        let today = model.clock.day(of: now)
        HStack(spacing: 8) {
            ForEach(Rating.allCases, id: \.self) { rating in
                let next = model.scheduler.review(card, rating: rating, at: now, day: today)
                Button {
                    onRate(rating)
                } label: {
                    VStack(spacing: 2) {
                        Text(rating.title)
                            .font(.headline)
                        IntervalText(due: next.due, from: now, today: today)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, minHeight: 52)
                }
                .buttonStyle(.bordered)
                .tint(rating.color)
                .accessibilityIdentifier("rating-\(rating.rawValue)")
            }
        }
    }
}

/// Pick the meaning out of four; afterwards the right one is shown before moving on.
private struct ChoiceQuestionView: View {
    let entry: WordEntry
    let question: ChoiceQuestion
    let onDone: (Bool) -> Void
    @State private var picked: Int?

    var body: some View {
        WordHeader(entry: entry)
            .padding(.top, 24)
        VStack(spacing: 10) {
            ForEach(Array(question.options.enumerated()), id: \.offset) { index, option in
                Button {
                    if picked == nil { picked = index }
                } label: {
                    HStack {
                        Text(verbatim: option)
                            .multilineTextAlignment(.leading)
                        Spacer()
                        if picked != nil, index == question.correctIndex {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                        } else if picked == index {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(.red)
                        }
                    }
                    .padding(.vertical, 6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.bordered)
                .tint(tint(for: index))
                .accessibilityIdentifier("choice-\(index)")
            }
        }
        if let picked {
            if picked != question.correctIndex {
                MeaningsView(entry: entry)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            ContinueButton {
                onDone(question.isCorrect(picked))
            }
        }
    }

    private func tint(for index: Int) -> Color? {
        guard let picked else { return nil }
        if index == question.correctIndex { return .green }
        return index == picked ? .red : .gray
    }
}

/// Hear the word and see its meaning, then type it.
private struct SpellingQuestion: View {
    let entry: WordEntry
    let onDone: (Answer.SpellingResult) -> Void
    @State private var typed = ""
    @State private var usedHint = false
    @State private var result: Answer.SpellingResult?
    @FocusState private var focused: Bool

    var body: some View {
        Text("Type the word")
            .font(.caption.bold())
            .foregroundStyle(.secondary)
            .textCase(.uppercase)
            .padding(.top, 16)
        MeaningsView(entry: entry)
            .frame(maxWidth: .infinity, alignment: .leading)
        HStack {
            SpeakButton(text: entry.display, accent: .american, label: "US")
            SpeakButton(text: entry.display, accent: .british, label: "UK")
            Spacer()
            if usedHint {
                Text(verbatim: AnswerGrader.hint(for: entry.display))
                    .font(.title3.monospaced())
                    .accessibilityIdentifier("spellingHint")
            }
        }
        TextField("Spelling", text: $typed)
            .font(.title2)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .textFieldStyle(.roundedBorder)
            .focused($focused)
            .disabled(result != nil)
            .onSubmit(check)
            .accessibilityIdentifier("spellingField")
        if let result {
            SpellingResultView(entry: entry, typed: typed, result: result)
            ContinueButton { onDone(result) }
        } else {
            HStack {
                Button("Hint") { usedHint = true }
                    .disabled(usedHint)
                    .accessibilityIdentifier("spellingHintButton")
                Button("I Don't Know") { result = .gaveUp }
                    .accessibilityIdentifier("spellingGiveUp")
                Spacer()
                Button("Check", action: check)
                    .buttonStyle(.borderedProminent)
                    .disabled(typed.trimmingCharacters(in: .whitespaces).isEmpty)
                    .accessibilityIdentifier("checkSpelling")
            }
            .buttonStyle(.bordered)
        }
    }

    private func check() {
        guard result == nil, !typed.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        focused = false
        if AnswerGrader.isCorrectSpelling(typed, of: entry.display) {
            result = usedHint ? .correctWithHint : .correct
        } else {
            result = .wrong
        }
    }
}

private struct SpellingResultView: View {
    let entry: WordEntry
    let typed: String
    let result: Answer.SpellingResult

    var body: some View {
        VStack(spacing: 8) {
            switch result {
            case .correct, .correctWithHint:
                Label("Correct", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
                    .font(.headline)
            case .wrong:
                Label("Not quite", systemImage: "xmark.circle.fill")
                    .foregroundStyle(.red)
                    .font(.headline)
                difference
                    .font(.title2.monospaced())
                    .accessibilityIdentifier("spellingDiff")
            case .gaveUp:
                EmptyView()
            }
            WordHeader(entry: entry, large: false)
        }
    }

    /// What was typed against the word: missing letters in green, extra ones struck through in red.
    private var difference: Text {
        AnswerGrader.diff(typed: typed, word: entry.display).reduce(Text(verbatim: "")) { text, part in
            switch part {
            case .same(let letters): text + Text(verbatim: letters)
            case .missing(let letters): text + Text(verbatim: letters).foregroundStyle(.green).underline()
            case .extra(let letters): text + Text(verbatim: letters).foregroundStyle(.red).strikethrough()
            }
        }
    }
}

private struct ContinueButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("Continue")
                .font(.headline)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .accessibilityIdentifier("continue")
    }
}

/// Inflected forms: past tense, plural and so on.
struct FormsView: View {
    let entry: WordEntry

    var body: some View {
        let forms = entry.forms
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(forms.enumerated()), id: \.offset) { _, form in
                HStack(spacing: 6) {
                    formName(form.kind)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(verbatim: form.word)
                        .font(.callout)
                }
            }
        }
    }

    private func formName(_ kind: WordForm.Kind) -> Text {
        switch kind {
        case .past: Text("Past")
        case .pastParticiple: Text("Past participle")
        case .presentParticiple: Text("-ing form")
        case .thirdPerson: Text("3rd person")
        case .comparative: Text("Comparative")
        case .superlative: Text("Superlative")
        case .plural: Text("Plural")
        case .lemma: Text("Base form")
        }
    }
}

/// The end of a session.
private struct SessionSummaryView: View {
    let summary: SessionSummary
    let waiting: (count: Int, firstDue: Date?)
    let onDone: () -> Void
    @Environment(VocabApp.self) private var vocab

    var body: some View {
        let model = vocab.model
        let today = model.today
        let streak = VocabStats.streak(model.events, today: today)
        List {
            Section {
                VStack(spacing: 8) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(.green)
                    Text("Session Complete")
                        .font(.title2.bold())
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .accessibilityIdentifier("sessionSummary")
            }
            Section {
                LabeledContent("New words") { Text(verbatim: "\(summary.newWords)") }
                LabeledContent("Words reviewed") { Text(verbatim: "\(summary.reviewedWords)") }
                LabeledContent("Accuracy") {
                    Text(verbatim: summary.accuracy.map { $0.formatted(.percent.precision(.fractionLength(0))) } ?? "–")
                }
                LabeledContent("Time") {
                    Text(Duration.milliseconds(summary.durationMs), format: .units(allowed: [.minutes, .seconds], width: .abbreviated))
                }
                LabeledContent("Study streak") { Text("\(streak.days) days") }
                LabeledContent("Reviews due tomorrow") {
                    Text(verbatim: "\(VocabStats.dueCount(model.cards.values, by: today + 1, today: today))")
                }
            }
            if waiting.count > 0, let first = waiting.firstDue {
                Section {
                    Text("\(waiting.count) words come back from \(first, format: .dateTime.hour().minute()). Open Words again then to finish them.")
                }
            }
            Section {
                Button(action: onDone) {
                    Text("Done")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                }
                .accessibilityIdentifier("finishSession")
            }
        }
    }
}
