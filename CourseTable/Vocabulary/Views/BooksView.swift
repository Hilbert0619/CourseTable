import SwiftUI
import UniformTypeIdentifiers
import CourseKit
import VocabKit

/// The system word lists and the learner's own; choose the one to study, import, rename or delete.
struct BooksView: View {
    @Environment(VocabApp.self) private var vocab
    @State private var importing = false
    @State private var renaming: BookRecord?
    @State private var newName = ""

    var body: some View {
        let model = vocab.model
        let current = model.currentBook?.id
        List {
            Section {
                ForEach(model.books.filter(\.isSystem)) { book in
                    row(book, isCurrent: book.id == current)
                }
            } header: {
                Text("Word Lists")
            } footer: {
                Text("考研词汇 comes from ECDICT (MIT licence): the entries it tags as 考研, most frequent first.")
            }
            Section {
                ForEach(model.books.filter { !$0.isSystem }) { book in
                    row(book, isCurrent: book.id == current)
                        .swipeActions {
                            Button("Delete", role: .destructive) {
                                model.deleteBook(book)
                                Task { await vocab.syncNow() }
                            }
                            Button("Rename") {
                                newName = book.name
                                renaming = book
                            }
                            .tint(.orange)
                        }
                }
                Button {
                    importing = true
                } label: {
                    Label("Import a Word List…", systemImage: "square.and.arrow.down")
                }
                .accessibilityIdentifier("importWordList")
            } header: {
                Text("My Lists")
            } footer: {
                Text("Paste or open a list with one word per line, or a CSV file with a word column. Meanings are filled in from the dictionary.")
            }
        }
        .navigationTitle("Books")
        .sheet(isPresented: $importing) {
            WordListImportView()
        }
        .alert("Rename List", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("Name", text: $newName)
            Button("Cancel", role: .cancel) { renaming = nil }
            Button("Save") {
                if let renaming { model.renameBook(renaming, to: newName) }
                renaming = nil
                Task { await vocab.syncNow() }
            }
        }
    }

    private func row(_ book: BookRecord, isCurrent: Bool) -> some View {
        Button {
            vocab.model.updateSettings { $0.bookID = book.id }
            Task { await vocab.syncNow() }
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: book.name)
                        .foregroundStyle(.primary)
                    Text("\(book.wordCount) words")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if isCurrent {
                    Image(systemName: "checkmark")
                        .foregroundStyle(.tint)
                        .accessibilityLabel(Text("Studying"))
                }
            }
        }
        .accessibilityIdentifier("book")
    }
}

/// Paste or open a word list, check the preview, and save it as a new book.
struct WordListImportView: View {
    @Environment(VocabApp.self) private var vocab
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var name = ""
    @State private var openingFile = false
    @State private var fileError: String?

    var body: some View {
        let model = vocab.model
        let preview = text.isEmpty ? nil : WordListImport.preview(text) { model.words[$0] != nil }
        NavigationStack {
            Form {
                Section {
                    TextField("List name", text: $name)
                        .accessibilityIdentifier("listName")
                }
                Section {
                    TextEditor(text: $text)
                        .font(.body.monospaced())
                        .frame(minHeight: 160)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("wordListText")
                    Button {
                        openingFile = true
                    } label: {
                        Label("Open a File…", systemImage: "doc")
                    }
                    Button {
                        text = String(localized: "abandon\nability 能力\nacquire\tv. 获得；学到\n# lines starting with # are skipped\ntenacious adj. 顽强的")
                        if name.isEmpty { name = String(localized: "My words") }
                    } label: {
                        Label("Insert Example", systemImage: "text.badge.plus")
                    }
                    .accessibilityIdentifier("insertWordExample")
                } header: {
                    Text("Words")
                } footer: {
                    Text("One word per line, optionally followed by a meaning; or CSV with a header row (word, meaning, phonetic, pos, example).")
                }
                if let fileError {
                    Section {
                        Label(fileError, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(.red)
                    }
                }
                if let preview {
                    previewSection(preview)
                }
            }
            .navigationTitle("Import a Word List")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .accessibilityIdentifier("cancel")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Import") {
                        guard let preview, let owner = vocab.account?.id else { return }
                        let listName = name.trimmingCharacters(in: .whitespacesAndNewlines)
                        let book = model.importBook(named: listName.isEmpty ? String(localized: "My words") : listName,
                                                    words: preview.words, owner: owner)
                        model.updateSettings { $0.bookID = book.id }
                        Task { await vocab.syncNow() }
                        dismiss()
                    }
                    .disabled((preview?.words.isEmpty ?? true) || vocab.account == nil)
                    .accessibilityIdentifier("importWords")
                }
            }
            .fileImporter(isPresented: $openingFile, allowedContentTypes: [.plainText, .commaSeparatedText, .tabSeparatedText]) { result in
                load(result)
            }
        }
    }

    private func previewSection(_ preview: WordListPreview) -> some View {
        Section {
            LabeledContent("Words to import") { Text(verbatim: "\(preview.words.count)") }
            LabeledContent("Meaning from the dictionary") { Text(verbatim: "\(preview.matchedCount)") }
            LabeledContent("Your own meaning") { Text(verbatim: "\(preview.customCount)") }
            if !preview.duplicates.isEmpty {
                LabeledContent("Repeated, skipped") { Text(verbatim: "\(preview.duplicates.count)") }
            }
            if !preview.unknown.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Not in the dictionary and no meaning given, skipped:")
                    Text(verbatim: preview.unknown.prefix(20).map(\.display).joined(separator: ", "))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            if !preview.unreadable.isEmpty {
                let lines = preview.unreadable.prefix(20).map(String.init).joined(separator: ", ")
                Text("Lines that aren't a word: \(lines)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("Preview")
        }
    }

    private func load(_ result: Result<URL, any Error>) {
        fileError = nil
        do {
            let url = try result.get()
            let accessing = url.startAccessingSecurityScopedResource()
            defer { if accessing { url.stopAccessingSecurityScopedResource() } }
            let data = try Data(contentsOf: url)
            guard data.count <= ImportedText.maxBytes, let decoded = ImportedText.decode(data) else {
                fileError = String(localized: "This file can't be read as text (UTF-8, UTF-16 or GB18030, at most 1 MB).")
                return
            }
            text = decoded
            if name.isEmpty { name = url.deletingPathExtension().lastPathComponent }
        } catch {
            fileError = error.localizedDescription
        }
    }
}
