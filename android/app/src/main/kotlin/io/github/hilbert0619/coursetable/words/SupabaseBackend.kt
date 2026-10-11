package io.github.hilbert0619.coursetable.words

import io.github.hilbert0619.vocabkit.AuthLink
import io.github.hilbert0619.vocabkit.backend.Account
import io.github.hilbert0619.vocabkit.backend.RemoteBook
import io.github.hilbert0619.vocabkit.backend.RemoteEntry
import io.github.hilbert0619.vocabkit.backend.RemoteFavorite
import io.github.hilbert0619.vocabkit.backend.RemoteReview
import io.github.hilbert0619.vocabkit.backend.RemoteSettings
import io.github.hilbert0619.vocabkit.backend.SignUpOutcome
import io.github.hilbert0619.vocabkit.backend.VocabAuthError
import io.github.hilbert0619.vocabkit.backend.VocabBackend
import io.github.hilbert0619.vocabkit.backend.VocabJson
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.serializer.KotlinXSerializer
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** The Supabase project the app talks to (as on iOS). The publishable key is public by design: row-level security decides. */
object BackendConfig {
    const val URL = "https://prkvpkxrugvbmrwxehhs.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_Wo7kzWr72kGjftesgD4EUg_OD0qBuno"
}

/**
 * The real backend (iOS `SupabaseBackend`): Supabase Auth (email + password, email links back into the app) and the REST
 * API over the tables in supabase/migrations. All uses of supabase-kt are in this file.
 */
class SupabaseBackend : VocabBackend {
    private val client = createSupabaseClient(BackendConfig.URL, BackendConfig.PUBLISHABLE_KEY) {
        install(Auth) {
            // Implicit flow: the email link returns the session in its fragment together with its type (signup /
            // recovery), which tells the app whether to ask for a new password.
            flowType = FlowType.IMPLICIT
            scheme = AuthLink.SCHEME
            host = "auth-callback"
            defaultRedirectUrl = AuthLink.CALLBACK
        }
        install(Postgrest)
        defaultSerializer = KotlinXSerializer(VocabJson)
    }

    // Auth

    override suspend fun restoreAccount(): Account? {
        client.auth.awaitInitialization()
        client.auth.currentUserOrNull()?.let { return account(it) }
        // Offline with an expired token: the stored session still names the account; it is refreshed when online.
        return runCatching { client.auth.sessionManager.loadSessionOrNull()?.user }.getOrNull()?.let(::account)
    }

    override suspend fun signUp(email: String, password: String): SignUpOutcome = auth {
        val user = client.auth.signUpWith(Email, redirectUrl = AuthLink.CALLBACK) {
            this.email = email
            this.password = password
        }
        val signedIn = client.auth.currentSessionOrNull()?.user
        if (user == null && signedIn != null) SignUpOutcome.SignedIn(account(signedIn)) else SignUpOutcome.ConfirmationSent
    }

    override suspend fun signIn(email: String, password: String): Account = auth {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        account(client.auth.currentUserOrNull() ?: client.auth.retrieveUserForCurrentSession(updateSession = true))
    }

    override suspend fun signOut() {
        try {
            client.auth.signOut(SignOutScope.LOCAL)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Offline: forget the session on this device anyway.
            runCatching { client.auth.clearSession() }
        }
    }

    override suspend fun sendPasswordReset(email: String) = auth {
        client.auth.resetPasswordForEmail(email, redirectUrl = AuthLink.CALLBACK)
    }

    override suspend fun openLink(url: String): Account {
        when (val link = AuthLink.of(url)) {
            is AuthLink.Failed -> throw if (link.code == "otp_expired" || link.code == "access_denied") VocabAuthError.LinkExpired else VocabAuthError.Other(link.message)
            AuthLink.NotAuthLink -> throw VocabAuthError.Other("Not a sign-in link")
            else -> Unit
        }
        val fragment = fragmentParameters(url)
        val accessToken = fragment["access_token"] ?: throw VocabAuthError.LinkExpired
        return auth {
            val user = client.auth.retrieveUser(accessToken)
            client.auth.importSession(
                UserSession(
                    accessToken = accessToken,
                    refreshToken = fragment["refresh_token"].orEmpty(),
                    expiresIn = fragment["expires_in"]?.toLongOrNull() ?: 3600,
                    tokenType = fragment["token_type"] ?: "bearer",
                    user = user,
                    type = fragment["type"].orEmpty(),
                ),
            )
            account(user)
        }
    }

    override suspend fun updatePassword(password: String) = auth {
        client.auth.updateUser { this.password = password }
        Unit
    }

    override suspend fun deleteAccount() {
        data { client.postgrest.rpc("delete_my_account") }
        runCatching { client.auth.clearSession() }
    }

    private fun account(user: UserInfo) = Account(UUID.fromString(user.id), user.email.orEmpty())

    /** Auth errors in the learner's terms. */
    private suspend fun <T> auth(block: suspend () -> T): T = try {
        block()
    } catch (error: AuthRestException) {
        throw VocabAuthError.fromCode(error.error, error.errorDescription)
    } catch (error: IOException) {
        throw VocabAuthError.Offline
    }

    // Data

    override suspend fun books(): List<RemoteBook> = data { client.from("books").select().decodeList<RemoteBook>() }

    override suspend fun entries(book: UUID, from: Int, to: Int): List<RemoteEntry> = data {
        client.from("book_entries").select(Columns.raw("*, words(*)")) {
            filter { eq("book_id", book.toString()) }
            order("position", Order.ASCENDING)
            range(from.toLong(), to.toLong())
        }.decodeList<RemoteEntry>()
    }

    override suspend fun saveBooks(books: List<RemoteBook>) {
        if (books.isNotEmpty()) data { client.from("books").upsert(books) }
    }

    override suspend fun saveEntries(entries: List<RemoteEntry>) {
        if (entries.isNotEmpty()) data { client.from("book_entries").upsert(entries) }
    }

    override suspend fun uploadReviews(reviews: List<RemoteReview>) {
        if (reviews.isNotEmpty()) data { client.from("review_logs").upsert(reviews) { ignoreDuplicates = true } }
    }

    override suspend fun reviews(receivedAfter: Instant?, from: Int, to: Int): List<RemoteReview> = data {
        client.from("review_logs").select {
            if (receivedAfter != null) filter { gt("received_at", DateTimeFormatter.ISO_INSTANT.format(receivedAfter)) }
            order("received_at", Order.ASCENDING)
            range(from.toLong(), to.toLong())
        }.decodeList<RemoteReview>()
    }

    override suspend fun favorites(): List<RemoteFavorite> = data {
        client.from("favorites").select(Columns.list("headword", "is_favorite", "updated_at")).decodeList<RemoteFavorite>()
    }

    override suspend fun saveFavorites(favorites: List<RemoteFavorite>) {
        if (favorites.isNotEmpty()) data { client.from("favorites").upsert(favorites) }
    }

    override suspend fun settings(): RemoteSettings? = data { client.from("user_settings").select().decodeList<RemoteSettings>().firstOrNull() }

    override suspend fun saveSettings(settings: RemoteSettings) {
        data { client.from("user_settings").upsert(settings) }
    }

    /** Network failures become [VocabAuthError.Offline]; the server's refusals keep its message. */
    private suspend fun <T> data(block: suspend () -> T): T = try {
        block()
    } catch (error: IOException) {
        throw VocabAuthError.Offline
    } catch (error: RestException) {
        throw VocabAuthError.Other(error.description ?: error.error)
    }

    companion object {
        /** The fragment's parameters (`#access_token=…&type=recovery`), percent-decoded. */
        fun fragmentParameters(url: String): Map<String, String> {
            val fragment = runCatching { URI(url).rawFragment }.getOrNull() ?: return emptyMap()
            return fragment.split('&').filter { it.isNotEmpty() }.associate { item ->
                val equals = item.indexOf('=')
                val name = if (equals < 0) item else item.substring(0, equals)
                val value = if (equals < 0) "" else item.substring(equals + 1)
                decode(name) to decode(value)
            }
        }

        private fun decode(text: String): String = runCatching { URLDecoder.decode(text.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(text)
    }
}
