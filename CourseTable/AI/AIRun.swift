import Foundation
import Observation
import AIKit

/// One request to the AI and its answer as it streams in.
@MainActor @Observable
final class AIRun {
    private(set) var answer = AIAnswer()
    private(set) var error: AIError?
    private(set) var isRunning = false
    /// The model answering, for the "AI-generated" label.
    private(set) var model = ""
    private var task: Task<Void, Never>?
    /// Counts requests; events from an older one are ignored.
    private var generation = 0

    /// The longest conversation sent, in characters (older turns are dropped first).
    static let maxCharacters = 60_000

    /// Asks `request` through the default connection (or `connection` with `key`, e.g. a connection being edited).
    /// `onComplete` gets the answer when it finished normally (not cut off, refused or failed); `onEnd` gets it
    /// whenever the stream ends without being cancelled, whatever happened.
    func start(_ request: AIRequest, store: AIStore, connection: AIConnection? = nil, key: String? = nil,
               onComplete: (@MainActor (AIAnswer) -> Void)? = nil, onEnd: (@MainActor (AIAnswer) -> Void)? = nil) {
        cancel()
        answer = AIAnswer()
        error = nil
        guard let connection = connection ?? store.defaultConnection else { return }
        model = connection.model
        let http: AIHTTPRequest
        do {
            http = try connection.chatRequest(request.trimmed(maxCharacters: Self.maxCharacters),
                                              key: key ?? store.key(for: connection))
        } catch {
            self.error = error as? AIError ?? .unreadable
            return
        }
        isRunning = true
        generation += 1
        let current = generation
        let stream = store.transport.stream(http, format: connection.format)
        task = Task { [weak self] in
            var failure: AIError?
            do {
                for try await event in stream {
                    // A replaced request may still deliver a few events; only the latest one's count.
                    guard let self, self.generation == current else { break }
                    self.answer.apply(event)
                }
            } catch {
                failure = error as? AIError ?? .network(message: error.localizedDescription)
            }
            guard let self, self.generation == current, !Task.isCancelled else { return }
            isRunning = false
            store.record(answer, for: connection)
            if let failure {
                self.error = failure
            } else if answer.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && answer.stop != .refused {
                self.error = .empty
            } else if answer.stop != .cutOff && answer.stop != .refused {
                onComplete?(answer)
            }
            onEnd?(answer)
        }
    }

    /// Shows an answer saved earlier instead of asking again.
    func show(_ saved: CachedAIAnswer) {
        cancel()
        var answer = AIAnswer()
        answer.apply(.text(saved.text))
        answer.apply(.stop(.complete))
        answer.apply(.end)
        self.answer = answer
        error = nil
        model = saved.model
    }

    func cancel() {
        generation += 1
        task?.cancel()
        task = nil
        isRunning = false
    }

    func reset() {
        cancel()
        answer = AIAnswer()
        error = nil
    }

    var hasContent: Bool { isRunning || error != nil || !answer.text.isEmpty || answer.stop == .refused }
}
