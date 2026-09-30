import Foundation

/// A sign-in email link that opened the app (docs/vocabulary-spec.md §1): Supabase verifies the email,
/// then redirects to `io.github.hilbert0619.coursetable://auth-callback` with the result in the URL
/// fragment (implicit flow), e.g. `#access_token=…&refresh_token=…&type=recovery`.
public enum AuthLink: Equatable, Sendable {
    /// A new account's email was confirmed; the link carries a session.
    case signUpConfirmed
    /// A password-reset link: the link carries a session, and the app asks for a new password.
    case passwordRecovery
    /// Another kind of link with a session (magic link, email change).
    case signedIn
    /// The link didn't work, e.g. it expired or was already used.
    case failed(code: String, message: String)
    /// Not a sign-in link for this app.
    case notAuthLink

    public static let scheme = "io.github.hilbert0619.coursetable"
    public static let callback = URL(string: "\(scheme)://auth-callback")!

    public init(_ url: URL) {
        guard url.scheme?.lowercased() == Self.scheme else {
            self = .notAuthLink
            return
        }
        let parameters = Self.parameters(of: url)
        if let error = parameters["error_code"] ?? parameters["error"] {
            let message = parameters["error_description"] ?? error
            self = .failed(code: error, message: message.replacingOccurrences(of: "+", with: " "))
        } else if parameters["access_token"] != nil || parameters["code"] != nil {
            switch parameters["type"] {
            case "recovery": self = .passwordRecovery
            case "signup": self = .signUpConfirmed
            default: self = .signedIn
            }
        } else {
            self = .notAuthLink
        }
    }

    /// Whether the link would sign the user in (it carries a session or a code for one).
    public var signsIn: Bool {
        switch self {
        case .signUpConfirmed, .passwordRecovery, .signedIn: true
        case .failed, .notAuthLink: false
        }
    }

    /// Query and fragment parameters together; the fragment wins on a clash.
    static func parameters(of url: URL) -> [String: String] {
        var result: [String: String] = [:]
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        for item in components?.queryItems ?? [] {
            result[item.name] = item.value ?? ""
        }
        if let fragment = components?.fragment, !fragment.isEmpty {
            var fragmentComponents = URLComponents()
            fragmentComponents.query = fragment
            for item in fragmentComponents.queryItems ?? [] {
                result[item.name] = item.value ?? ""
            }
        }
        return result
    }
}
