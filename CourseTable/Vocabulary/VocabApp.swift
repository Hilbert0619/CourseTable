import Foundation
import SwiftData
import AVFoundation
import VocabKit

/// The Words tab's coordinator: who is signed in, the vocabulary store and model, syncing, and the
/// email links that open the app. One instance lives for the app's lifetime.
@MainActor
@Observable
final class VocabApp {
    enum AuthState: Equatable {
        case starting
        case signedOut
        case signedIn(Account)
    }

    private(set) var auth: AuthState = .starting
    /// A password-reset link opened the app: ask for a new password.
    var needsNewPassword = false
    /// Something to tell the learner after an email link opened the app.
    var linkMessage: String?
    /// The vocabulary store couldn't be opened, so this session keeps words in memory only.
    let storeProblem: String?

    let backend: any VocabBackend
    let model: VocabModel
    let sync: SyncEngine

    init(backend: any VocabBackend, container: ModelContainer, storeProblem: String? = nil) {
        self.backend = backend
        self.storeProblem = storeProblem
        model = VocabModel(context: container.mainContext)
        sync = SyncEngine(backend: backend, model: model)
        model.load()
    }

    /// The app's instance: the store on disk and the Supabase backend, or, for UI tests, an in-memory
    /// store and the demo backend.
    static func makeForLaunch() -> VocabApp {
        let backend: any VocabBackend = UITestLaunch.isActive
            ? DemoBackend(signedIn: UITestLaunch.wantsVocabDemo, history: UITestLaunch.wantsVocabDemo,
                          reviewMode: UITestLaunch.vocabReviewMode.flatMap(SessionMode.init(rawValue:)) ?? .mixed)
            : SupabaseBackend()
        do {
            return VocabApp(backend: backend, container: try VocabStore.makeContainer(inMemory: UITestLaunch.isActive))
        } catch {
            // Keep the timetable usable: study from memory this session and say why.
            let container = try! VocabStore.makeContainer(inMemory: true)
            return VocabApp(backend: backend, container: container, storeProblem: error.localizedDescription)
        }
    }

    /// For SwiftUI previews: the demo learner, in memory.
    static func preview() -> VocabApp {
        VocabApp(backend: DemoBackend(signedIn: true, history: true), container: try! VocabStore.makeContainer(inMemory: true))
    }

    var account: Account? {
        if case .signedIn(let account) = auth { account } else { nil }
    }

    /// Restores the session stored on this device, then syncs.
    func start() async {
        guard auth == .starting else { return }
        if let account = await backend.restoreAccount() {
            auth = .signedIn(account)
            await sync.sync(for: account)
        } else {
            auth = .signedOut
        }
    }

    func syncNow() async {
        guard let account else { return }
        await sync.sync(for: account)
    }

    func signIn(email: String, password: String) async throws {
        let account = try await backend.signIn(email: Self.clean(email), password: password)
        auth = .signedIn(account)
        await sync.sync(for: account)
    }

    /// `true` if the account must be confirmed with the emailed link first.
    func signUp(email: String, password: String) async throws -> Bool {
        switch try await backend.signUp(email: Self.clean(email), password: password) {
        case .signedIn(let account):
            auth = .signedIn(account)
            await sync.sync(for: account)
            return false
        case .confirmationSent:
            return true
        }
    }

    func sendPasswordReset(email: String) async throws {
        try await backend.sendPasswordReset(email: Self.clean(email))
    }

    /// Handles a URL that opened the app; sign-in email links sign the learner in.
    func open(_ url: URL) async {
        let link = AuthLink(url)
        guard link != .notAuthLink else { return }
        do {
            let account = try await backend.openLink(url)
            auth = .signedIn(account)
            switch link {
            case .passwordRecovery: needsNewPassword = true
            case .signUpConfirmed: linkMessage = String(localized: "Your email is confirmed. You're signed in.")
            default: break
            }
            await sync.sync(for: account)
        } catch {
            linkMessage = error.localizedDescription
        }
    }

    func setNewPassword(_ password: String) async throws {
        try await backend.updatePassword(password)
        needsNewPassword = false
    }

    /// Uploads what it can, signs out and clears this device's vocabulary data.
    func signOut() async {
        await syncNow()
        await backend.signOut()
        model.wipe()
        auth = .signedOut
    }

    /// Deletes the account and all of its data on the server, then everything here.
    func deleteAccount() async throws {
        try await backend.deleteAccount()
        model.wipe()
        auth = .signedOut
    }

    private static func clean(_ email: String) -> String {
        email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }
}

/// Reads words aloud with the voices on the device (no network, no audio files).
@MainActor
final class Pronunciation {
    static let shared = Pronunciation()

    enum Accent {
        case american
        case british
    }

    private let synthesizer = AVSpeechSynthesizer()

    func speak(_ text: String, accent: Accent = .american) {
        guard !UITestLaunch.isActive else { return }
        // Playback, so words are heard even with the ring/silent switch on silent.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        synthesizer.stopSpeaking(at: .immediate)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: accent == .american ? "en-US" : "en-GB")
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate * 0.9
        synthesizer.speak(utterance)
    }
}
