import Foundation
import Supabase
import VocabKit

/// The real backend: Supabase Auth (email + password, email links back into the app) and the REST API
/// over the tables in supabase/migrations. All uses of the Supabase SDK are in this file.
final class SupabaseBackend: VocabBackend {
    private let client: SupabaseClient

    init(url: URL = BackendConfig.url, key: String = BackendConfig.publishableKey) {
        client = SupabaseClient(
            supabaseURL: url,
            supabaseKey: key,
            options: SupabaseClientOptions(
                // Implicit flow: the email link returns the session in its fragment together with its
                // type (signup / recovery), which tells the app whether to ask for a new password.
                auth: .init(redirectToURL: AuthLink.callback, flowType: .implicit, emitLocalSessionAsInitialSession: true)
            )
        )
    }

    // MARK: - Auth

    func restoreAccount() async -> Account? {
        client.auth.currentUser.map(Self.account)
    }

    func signUp(email: String, password: String) async throws -> SignUpOutcome {
        try await mapAuthErrors {
            let response = try await client.auth.signUp(email: email, password: password, redirectTo: AuthLink.callback)
            if let session = response.session {
                return .signedIn(Self.account(session.user))
            }
            return .confirmationSent
        }
    }

    func signIn(email: String, password: String) async throws -> Account {
        try await mapAuthErrors {
            Self.account(try await client.auth.signIn(email: email, password: password).user)
        }
    }

    func signOut() async {
        try? await client.auth.signOut()
    }

    func sendPasswordReset(email: String) async throws {
        try await mapAuthErrors {
            try await client.auth.resetPasswordForEmail(email, redirectTo: AuthLink.callback)
        }
    }

    func openLink(_ url: URL) async throws -> Account {
        if case .failed(let code, let message) = AuthLink(url) {
            throw code == "otp_expired" || code == "access_denied" ? VocabAuthError.linkExpired : VocabAuthError.other(message)
        }
        return try await mapAuthErrors {
            Self.account(try await client.auth.session(from: url).user)
        }
    }

    func updatePassword(_ password: String) async throws {
        try await mapAuthErrors {
            _ = try await client.auth.update(user: UserAttributes(password: password))
        }
    }

    func deleteAccount() async throws {
        try await client.rpc("delete_my_account").execute()
        try? await client.auth.signOut(scope: .local)
    }

    private static func account(_ user: User) -> Account {
        Account(id: user.id, email: user.email ?? "")
    }

    private func mapAuthErrors<T>(_ body: () async throws -> T) async throws -> T {
        do {
            return try await body()
        } catch let error as AuthError {
            throw Self.vocabError(code: error.errorCode.rawValue, message: error.message)
        } catch let error as URLError {
            throw error.code == .notConnectedToInternet || error.code == .networkConnectionLost || error.code == .timedOut
                ? VocabAuthError.offline : error
        }
    }

    /// Supabase Auth error codes (https://supabase.com/docs/guides/auth/debugging/error-codes).
    static func vocabError(code: String, message: String) -> VocabAuthError {
        switch code {
        case "invalid_credentials": .invalidCredentials
        case "email_not_confirmed": .emailNotConfirmed
        case "weak_password": .weakPassword
        case "email_address_invalid", "validation_failed": .invalidEmail
        case "over_email_send_rate_limit", "over_request_rate_limit": .rateLimited
        case "email_address_not_authorized": .emailNotAllowed
        case "otp_expired", "flow_state_expired": .linkExpired
        default: .other(message)
        }
    }

    // MARK: - Data

    func books() async throws -> [RemoteBook] {
        try await client.from("books").select().execute().value
    }

    func entries(book: UUID, from: Int, to: Int) async throws -> [RemoteEntry] {
        try await client.from("book_entries")
            .select("*, words(*)")
            .eq("book_id", value: book.uuidString.lowercased())
            .order("position")
            .range(from: from, to: to)
            .execute()
            .value
    }

    func saveBooks(_ books: [RemoteBook]) async throws {
        guard !books.isEmpty else { return }
        try await client.from("books").upsert(books, returning: .minimal).execute()
    }

    func saveEntries(_ entries: [RemoteEntry]) async throws {
        guard !entries.isEmpty else { return }
        try await client.from("book_entries").upsert(entries, returning: .minimal).execute()
    }

    func uploadReviews(_ reviews: [RemoteReview]) async throws {
        guard !reviews.isEmpty else { return }
        try await client.from("review_logs").upsert(reviews, returning: .minimal, ignoreDuplicates: true).execute()
    }

    func reviews(receivedAfter: Date?, from: Int, to: Int) async throws -> [RemoteReview] {
        var query = client.from("review_logs").select()
        if let receivedAfter {
            query = query.gt("received_at", value: Self.timestamp(receivedAfter))
        }
        return try await query.order("received_at").range(from: from, to: to).execute().value
    }

    func favorites() async throws -> [RemoteFavorite] {
        try await client.from("favorites").select("headword, is_favorite, updated_at").execute().value
    }

    func saveFavorites(_ favorites: [RemoteFavorite]) async throws {
        guard !favorites.isEmpty else { return }
        try await client.from("favorites").upsert(favorites, returning: .minimal).execute()
    }

    func settings() async throws -> RemoteSettings? {
        let rows: [RemoteSettings] = try await client.from("user_settings").select().execute().value
        return rows.first
    }

    func saveSettings(_ settings: RemoteSettings) async throws {
        try await client.from("user_settings").upsert(settings, returning: .minimal).execute()
    }

    /// `2026-09-30T10:00:00.123Z`, as the REST API filters expect.
    private static func timestamp(_ date: Date) -> String {
        Date.ISO8601FormatStyle(includingFractionalSeconds: true).format(date)
    }
}
