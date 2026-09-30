import Foundation
import Testing
@testable import VocabKit

@Suite struct AuthLinkTests {
    func link(_ text: String) -> AuthLink { AuthLink(URL(string: text)!) }

    @Test func sessionLinks() {
        let base = "io.github.hilbert0619.coursetable://auth-callback#access_token=abc&expires_at=1&refresh_token=def&token_type=bearer"
        #expect(link(base + "&type=signup") == .signUpConfirmed)
        #expect(link(base + "&type=recovery") == .passwordRecovery)
        #expect(link(base + "&type=magiclink") == .signedIn)
        #expect(link(base) == .signedIn)
        #expect(link("io.github.hilbert0619.coursetable://auth-callback?code=xyz") == .signedIn)
        #expect(link(base + "&type=recovery").signsIn)
    }

    @Test func failedLinks() {
        let expired = link("io.github.hilbert0619.coursetable://auth-callback#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired")
        #expect(expired == .failed(code: "otp_expired", message: "Email link is invalid or has expired"))
        #expect(!expired.signsIn)
        #expect(link("io.github.hilbert0619.coursetable://auth-callback?error=server_error") == .failed(code: "server_error", message: "server_error"))
    }

    @Test func otherLinks() {
        #expect(link("https://example.com/#access_token=abc") == .notAuthLink)
        #expect(link("io.github.hilbert0619.coursetable://auth-callback") == .notAuthLink)
        #expect(link("IO.GITHUB.HILBERT0619.COURSETABLE://auth-callback#access_token=a&type=signup") == .signUpConfirmed)
        #expect(AuthLink.callback.absoluteString == "io.github.hilbert0619.coursetable://auth-callback")
    }
}
