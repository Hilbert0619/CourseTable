import SwiftUI
import VocabKit

/// The Words tab: sign-in until there is an account, then the learner's home.
struct WordsTab: View {
    @Environment(VocabApp.self) private var vocab
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        @Bindable var vocab = vocab
        NavigationStack {
            switch vocab.auth {
            case .starting:
                ProgressView()
            case .signedOut:
                SignInView()
            case .signedIn:
                WordsHomeView()
            }
        }
        .task { await vocab.start() }
        // Coming back to the app uploads answers given offline and fetches other devices' answers.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await vocab.syncNow() } }
        }
        .sheet(isPresented: $vocab.needsNewPassword) {
            NewPasswordView()
        }
        .alert("Words", isPresented: Binding(
            get: { vocab.linkMessage != nil },
            set: { if !$0 { vocab.linkMessage = nil } }
        )) {
            Button("OK") { vocab.linkMessage = nil }
        } message: {
            Text(verbatim: vocab.linkMessage ?? "")
        }
    }
}

struct SignInView: View {
    @Environment(VocabApp.self) private var vocab

    enum Mode: Hashable {
        case signIn
        case signUp
    }

    @State private var mode = Mode.signIn
    @State private var email = ""
    @State private var password = ""
    @State private var working = false
    @State private var error: String?
    @State private var notice: String?

    private var canSubmit: Bool {
        email.contains("@") && password.count >= (mode == .signUp ? 8 : 1) && !working
    }

    var body: some View {
        Form {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Image(systemName: "character.book.closed.fill")
                        .font(.largeTitle)
                        .foregroundStyle(.tint)
                    Text("Learn the 考研 words")
                        .font(.title2.bold())
                    Text("Daily new words and reviews scheduled for you, synced across your devices. Studying works offline too.")
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 8)
            }

            Section {
                Picker("Account", selection: $mode) {
                    Text("Sign In").tag(Mode.signIn)
                    Text("Create Account").tag(Mode.signUp)
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("authMode")

                TextField("Email", text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .accessibilityIdentifier("email")
                SecureField("Password", text: $password)
                    .textContentType(mode == .signUp ? .newPassword : .password)
                    .accessibilityIdentifier("password")
            } footer: {
                if mode == .signUp {
                    Text("At least 8 characters. We'll email you a link: open it on this iPhone to finish.")
                }
            }

            Section {
                Button {
                    Task { await submit() }
                } label: {
                    HStack {
                        Spacer()
                        if working {
                            ProgressView()
                        } else {
                            mode == .signIn ? Text("Sign In") : Text("Create Account")
                        }
                        Spacer()
                    }
                }
                .disabled(!canSubmit)
                .accessibilityIdentifier("submitAuth")

                if mode == .signIn {
                    Button("Forgot password?") {
                        Task { await resetPassword() }
                    }
                    .disabled(!email.contains("@") || working)
                }
            }

            if let error {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                }
            }
            if let notice {
                Section {
                    Label(notice, systemImage: "envelope")
                        .accessibilityIdentifier("authNotice")
                }
            }
        }
        .navigationTitle("Words")
        .onChange(of: mode) {
            error = nil
            notice = nil
        }
    }

    private func submit() async {
        working = true
        error = nil
        notice = nil
        defer { working = false }
        do {
            switch mode {
            case .signIn:
                try await vocab.signIn(email: email, password: password)
            case .signUp:
                // The emailed link opens the app and signs the new account in.
                if try await vocab.signUp(email: email, password: password) {
                    notice = String(localized: "Check your inbox: tap the link in the email on this iPhone to confirm your account.")
                }
            }
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func resetPassword() async {
        working = true
        error = nil
        notice = nil
        defer { working = false }
        do {
            try await vocab.sendPasswordReset(email: email)
            notice = String(localized: "We sent a password reset link. Open it on this iPhone to choose a new password.")
        } catch {
            self.error = error.localizedDescription
        }
    }
}

/// Shown after a password-reset link opened the app.
struct NewPasswordView: View {
    @Environment(VocabApp.self) private var vocab
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var working = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    SecureField("New password", text: $password)
                        .textContentType(.newPassword)
                } footer: {
                    Text("At least 8 characters.")
                }
                if let error {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Choose a New Password")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task {
                            working = true
                            defer { working = false }
                            do {
                                try await vocab.setNewPassword(password)
                                dismiss()
                            } catch {
                                self.error = error.localizedDescription
                            }
                        }
                    }
                    .disabled(password.count < 8 || working)
                }
            }
        }
    }
}
