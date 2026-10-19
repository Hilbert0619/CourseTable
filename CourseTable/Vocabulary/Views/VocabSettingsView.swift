import SwiftUI
import CourseKit
import VocabKit

/// Study settings, sync, and the account (sign out, delete).
struct VocabSettingsView: View {
    @Environment(VocabApp.self) private var vocab
    @State private var confirmingSignOut = false
    @State private var confirmingDelete = false
    @State private var working = false
    @State private var error: String?

    var body: some View {
        let model = vocab.model
        let settings = model.settings
        Form {
            Section {
                Stepper(value: binding(\.dailyNewWords), in: 0...200, step: 5) {
                    LabeledContent("New words a day") {
                        Text(verbatim: "\(settings.dailyNewWords)")
                    }
                }
                .accessibilityIdentifier("dailyNewWords")
                Picker("Review questions", selection: binding(\.reviewMode)) {
                    Text("Recall and rate").tag(SessionMode.recognition)
                    Text("Multiple choice").tag(SessionMode.choice)
                    Text("Spelling").tag(SessionMode.spelling)
                    Text("Mixed").tag(SessionMode.mixed)
                }
                Picker("New day starts at", selection: binding(\.dayStart)) {
                    ForEach(0..<7) { hour in
                        Text(verbatim: TimeOfDay(hour: hour, minute: 0).description).tag(TimeOfDay(hour: hour, minute: 0))
                    }
                }
            } header: {
                Text("Study")
            } footer: {
                Text("Study late at night and it still counts for that evening until the new day starts.")
            }

            Section {
                Toggle("Exam date", isOn: Binding(
                    get: { settings.examDate != nil },
                    set: { on in
                        model.updateSettings { $0.examDate = on ? ($0.examDate ?? Self.defaultExamDate()) : nil }
                    }
                ))
                if let examDate = settings.examDate {
                    DatePicker("Date", selection: Binding(
                        get: { examDate.dateValue },
                        set: { date in model.updateSettings { $0.examDate = LocalDate(date) } }
                    ), in: Date.now..., displayedComponents: .date)
                }
            } header: {
                Text("Exam")
            } footer: {
                Text("The Words home screen counts down to it and estimates the pace you need.")
            }

            Section {
                VStack(alignment: .leading) {
                    LabeledContent("Target recall") {
                        Text(verbatim: settings.desiredRetention.formatted(.percent.precision(.fractionLength(0))))
                    }
                    Slider(value: binding(\.desiredRetention), in: 0.80...0.95, step: 0.01)
                }
            } header: {
                Text("Advanced")
            } footer: {
                Text("How likely you should be to remember a word when it comes back. Higher means more reviews. 90% suits most learners.")
            }

            Section("Sync") {
                SyncStatusRow()
            }

            Section {
                if let account = vocab.account {
                    LabeledContent("Signed in as") {
                        Text(verbatim: account.email)
                    }
                }
                Button("Sign Out") {
                    confirmingSignOut = true
                }
                .disabled(working)
                Button("Delete Account…", role: .destructive) {
                    confirmingDelete = true
                }
                .disabled(working)
            } header: {
                Text("Account")
            } footer: {
                Text("Signing out removes your words from this iPhone; they stay in your account. Deleting the account removes all of your answers, lists and settings for good.")
            }

            if let error {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                }
            }

            Section {
                Text("考研词汇 from ECDICT by skywind3000, MIT licence. Pronunciation uses the voices on your iPhone.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } header: {
                Text("About")
            }
        }
        .paperBackground()
        .navigationTitle("Study Settings")
        .disabled(working)
        // Upload changed settings when leaving the screen (not on every tap of the stepper).
        .onDisappear {
            Task { await vocab.syncNow() }
        }
        .confirmationDialog("Sign out?", isPresented: $confirmingSignOut, titleVisibility: .visible) {
            Button("Sign Out", role: .destructive) {
                Task {
                    working = true
                    await vocab.signOut()
                    working = false
                }
            }
        } message: {
            if model.unsentCount > 0 {
                Text("\(model.unsentCount) answers haven't been uploaded yet. They'll be lost if you sign out while offline.")
            }
        }
        .confirmationDialog("Delete your account?", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("Delete Account", role: .destructive) {
                Task {
                    working = true
                    defer { working = false }
                    do {
                        try await vocab.deleteAccount()
                    } catch {
                        self.error = error.localizedDescription
                    }
                }
            }
        } message: {
            Text("All of your answers, lists and settings will be deleted from the server and this iPhone. This can't be undone.")
        }
    }

    /// A binding to one setting that saves the change.
    private func binding<Value>(_ keyPath: WritableKeyPath<VocabSettings, Value>) -> Binding<Value> {
        Binding(
            get: { vocab.model.settings[keyPath: keyPath] },
            set: { value in vocab.model.updateSettings { $0[keyPath: keyPath] = value } }
        )
    }

    /// Late December, when the national 考研 exam is usually held.
    private static func defaultExamDate() -> LocalDate {
        let today = LocalDate(.now)
        let year = today.month == 12 && today.day > 20 ? today.year + 1 : today.year
        return LocalDate(year: year, month: 12, day: 20)
    }
}
