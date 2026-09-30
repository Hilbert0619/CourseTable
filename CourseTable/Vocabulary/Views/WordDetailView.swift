import SwiftUI
import CourseKit
import VocabKit

/// A word's page: pronunciation, meanings, English definitions, forms, exam lists and the learner's progress.
struct WordDetailView: View {
    let headword: String
    @Environment(VocabApp.self) private var vocab

    var body: some View {
        let model = vocab.model
        if let entry = model.entry(headword) {
            List {
                Section {
                    WordHeader(entry: entry)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                    MeaningsView(entry: entry)
                }
                if !entry.definition.isEmpty {
                    Section("English") {
                        Text(verbatim: entry.definition)
                            .font(.callout)
                    }
                }
                if !entry.forms.isEmpty {
                    Section("Forms") {
                        FormsView(entry: entry)
                    }
                }
                Section("About the Word") {
                    if !examLists(entry).isEmpty {
                        LabeledContent("Word lists") {
                            Text(verbatim: examLists(entry))
                        }
                    }
                    if entry.collins > 0 {
                        LabeledContent("Collins") {
                            Text(verbatim: String(repeating: "★", count: entry.collins))
                                .foregroundStyle(.orange)
                        }
                    }
                    if entry.oxford {
                        LabeledContent("Oxford 3000") {
                            Image(systemName: "checkmark")
                        }
                    }
                    if let rank = entry.frequencyRank {
                        LabeledContent("Frequency rank") {
                            Text(verbatim: "#\(rank)")
                        }
                    }
                }
                ProgressSection(card: model.cards[entry.headword], model: model)
            }
            .navigationTitle(Text(verbatim: entry.display))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    let favorite = model.isFavorite(entry.headword)
                    Button {
                        model.setFavorite(entry.headword, !favorite)
                    } label: {
                        Image(systemName: favorite ? "star.fill" : "star")
                            .foregroundStyle(favorite ? .yellow : .accentColor)
                    }
                    .accessibilityLabel(favorite ? Text("Remove from Favourites") : Text("Add to Favourites"))
                    .accessibilityIdentifier("favoriteButton")
                }
            }
        } else {
            ContentUnavailableView("Word not found", systemImage: "questionmark.circle")
        }
    }

    /// The exam lists a word belongs to, e.g. `考研 · CET-6`.
    private func examLists(_ entry: WordEntry) -> String {
        let names = ["zk": "中考", "gk": "高考", "cet4": "CET-4", "cet6": "CET-6", "ky": "考研",
                     "toefl": "TOEFL", "ielts": "IELTS", "gre": "GRE"]
        return entry.tags.compactMap { names[$0] }.joined(separator: " · ")
    }
}

/// How the learner is doing with a word, from its card.
private struct ProgressSection: View {
    let card: Card?
    let model: VocabModel

    var body: some View {
        Section {
            if let card, card.state != .new {
                LabeledContent("Status") { stateName(card.state) }
                LabeledContent("Answers") { Text(verbatim: "\(card.reps)") }
                LabeledContent("Forgotten") { Text(verbatim: "\(card.lapses)") }
                if let day = card.dueDay(today: model.today) {
                    LabeledContent("Next review") {
                        Text(model.clock.date(of: day).dateValue, format: .dateTime.month().day())
                    }
                }
                if let recall = model.scheduler.retrievability(of: card, on: model.today) {
                    LabeledContent("Chance of recall today") {
                        Text(verbatim: recall.formatted(.percent.precision(.fractionLength(0))))
                    }
                }
            } else {
                Text("Not learned yet.")
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("Your Progress")
        }
    }

    private func stateName(_ state: CardState) -> Text {
        switch state {
        case .new: Text("New")
        case .learning: Text("Learning")
        case .review: Text("Reviewing")
        case .relearning: Text("Relearning")
        }
    }
}

struct WordSearchView: View {
    @Environment(VocabApp.self) private var vocab
    @State private var query = ""

    var body: some View {
        let model = vocab.model
        let results = query.isEmpty ? [] : model.search.search(query)
        List {
            ForEach(results, id: \.headword) { result in
                NavigationLink(value: WordsRoute.word(result.headword)) {
                    WordRow(entry: model.entry(result.headword), headword: result.headword, note: note(for: result.match))
                }
            }
        }
        .overlay {
            if query.isEmpty {
                ContentUnavailableView("Search Words", systemImage: "magnifyingglass",
                                       description: Text("Type an English word, part of one, or a Chinese meaning."))
            } else if results.isEmpty {
                ContentUnavailableView.search(text: query)
            }
        }
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: Text("abandon, 放弃…"))
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .navigationTitle("Search")
    }

    private func note(for match: WordSearch.Match) -> Text? {
        switch match {
        case .form(let form): Text("Form: \(form)")
        case .similar: Text("Similar spelling")
        case .exact, .prefix, .meaning: nil
        }
    }
}

/// A word and its short meaning, for lists.
struct WordRow: View {
    let entry: WordEntry?
    let headword: String
    var note: Text?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline) {
                Text(verbatim: entry?.display ?? headword)
                    .font(.headline)
                if let note {
                    note
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            if let meaning = entry?.shortMeaning, !meaning.isEmpty {
                Text(verbatim: meaning)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
    }
}

/// Favourites, or the words forgotten most often.
struct WordListView: View {
    enum Kind {
        case favorites
        case hardWords
    }

    let kind: Kind
    @Environment(VocabApp.self) private var vocab

    var body: some View {
        let model = vocab.model
        let words: [String] = switch kind {
        case .favorites: model.favorites.sorted()
        case .hardWords: model.hardWords.map(\.headword)
        }
        List {
            ForEach(words, id: \.self) { word in
                NavigationLink(value: WordsRoute.word(word)) {
                    if kind == .hardWords, let card = model.cards[word] {
                        WordRow(entry: model.entry(word), headword: word, note: Text("Forgotten \(card.lapses) times"))
                    } else {
                        WordRow(entry: model.entry(word), headword: word)
                    }
                }
            }
        }
        .overlay {
            if words.isEmpty {
                switch kind {
                case .favorites:
                    ContentUnavailableView("No Favourites", systemImage: "star",
                                           description: Text("Tap the star on a word's page to keep it here."))
                case .hardWords:
                    ContentUnavailableView("No Hard Words", systemImage: "hand.thumbsup",
                                           description: Text("Words you forget twice or more after learning them appear here."))
                }
            }
        }
        .navigationTitle(kind == .favorites ? Text("Favourites") : Text("Hard Words"))
    }
}
