package io.github.hilbert0619.vocabkit

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * A sign-in email link that opened the app (docs/vocabulary-spec.md §1): Supabase verifies the email, then redirects to
 * `io.github.hilbert0619.coursetable://auth-callback` with the result in the URL fragment (implicit flow), e.g.
 * `#access_token=…&refresh_token=…&type=recovery`.
 */
sealed interface AuthLink {
    /** A new account's email was confirmed; the link carries a session. */
    data object SignUpConfirmed : AuthLink

    /** A password-reset link: the link carries a session, and the app asks for a new password. */
    data object PasswordRecovery : AuthLink

    /** Another kind of link with a session (magic link, email change). */
    data object SignedIn : AuthLink

    /** The link didn't work, e.g. it expired or was already used. */
    data class Failed(val code: String, val message: String) : AuthLink

    /** Not a sign-in link for this app. */
    data object NotAuthLink : AuthLink

    /** Whether the link would sign the user in (it carries a session or a code for one). */
    val signsIn: Boolean get() = this == SignUpConfirmed || this == PasswordRecovery || this == SignedIn

    companion object {
        const val SCHEME = "io.github.hilbert0619.coursetable"
        const val CALLBACK = "$SCHEME://auth-callback"

        fun of(url: String): AuthLink {
            val uri = try {
                URI(url)
            } catch (error: URISyntaxException) {
                return NotAuthLink
            }
            if (uri.scheme?.lowercase() != SCHEME) return NotAuthLink
            val parameters = parameters(uri)
            val error = parameters["error_code"] ?: parameters["error"]
            return when {
                error != null -> Failed(error, (parameters["error_description"] ?: error).replace('+', ' '))
                parameters["access_token"] != null || parameters["code"] != null -> when (parameters["type"]) {
                    "recovery" -> PasswordRecovery
                    "signup" -> SignUpConfirmed
                    else -> SignedIn
                }
                else -> NotAuthLink
            }
        }

        /** Query and fragment parameters together; the fragment wins on a clash. */
        internal fun parameters(uri: URI): Map<String, String> {
            val result = mutableMapOf<String, String>()
            for (part in listOfNotNull(uri.rawQuery, uri.rawFragment)) {
                for (item in part.split('&')) {
                    if (item.isEmpty()) continue
                    val equals = item.indexOf('=')
                    val name = decode(if (equals < 0) item else item.substring(0, equals))
                    result[name] = if (equals < 0) "" else decode(item.substring(equals + 1))
                }
            }
            return result
        }

        /** Percent-decoding without turning `+` into a space, as `URLComponents.queryItems` does. */
        private fun decode(text: String): String =
            runCatching { URLDecoder.decode(text.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(text)
    }
}
