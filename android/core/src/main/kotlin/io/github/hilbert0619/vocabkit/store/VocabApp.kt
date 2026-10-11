package io.github.hilbert0619.vocabkit.store

import io.github.hilbert0619.vocabkit.AuthLink
import io.github.hilbert0619.vocabkit.backend.Account
import io.github.hilbert0619.vocabkit.backend.SignUpOutcome
import io.github.hilbert0619.vocabkit.backend.VocabBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Words tab's coordinator (iOS `VocabApp`): who is signed in, the vocabulary model and syncing, and the email links
 * that open the app. One instance lives for the app's lifetime.
 */
class VocabApp(
    val backend: VocabBackend,
    val model: VocabModel,
    /** The store couldn't be opened, so this session keeps words in memory only. */
    val storeProblem: String? = null,
) {
    sealed interface AuthState {
        data object Starting : AuthState
        data object SignedOut : AuthState
        data class SignedIn(val account: Account) : AuthState
    }

    /** What to tell the learner after an email link opened the app. */
    sealed interface LinkMessage {
        data object Confirmed : LinkMessage
        data class Failed(val error: Throwable) : LinkMessage
    }

    val sync = SyncEngine(backend, model)

    private val _auth = MutableStateFlow<AuthState>(AuthState.Starting)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    /** A password-reset link opened the app: ask for a new password. */
    private val _needsNewPassword = MutableStateFlow(false)
    val needsNewPassword: StateFlow<Boolean> = _needsNewPassword.asStateFlow()

    private val _linkMessage = MutableStateFlow<LinkMessage?>(null)
    val linkMessage: StateFlow<LinkMessage?> = _linkMessage.asStateFlow()

    val account: Account? get() = (_auth.value as? AuthState.SignedIn)?.account

    /** Restores the session stored on this device, then syncs. */
    suspend fun start() {
        if (_auth.value != AuthState.Starting) return
        model.load()
        sync.loadLastSync()
        val account = backend.restoreAccount()
        if (account != null) {
            _auth.value = AuthState.SignedIn(account)
            sync.sync(account)
        } else {
            _auth.value = AuthState.SignedOut
        }
    }

    suspend fun syncNow() {
        account?.let { sync.sync(it) }
    }

    suspend fun signIn(email: String, password: String) {
        val account = backend.signIn(clean(email), password)
        _auth.value = AuthState.SignedIn(account)
        sync.sync(account)
    }

    /** `true` if the account must be confirmed with the emailed link first. */
    suspend fun signUp(email: String, password: String): Boolean = when (val outcome = backend.signUp(clean(email), password)) {
        is SignUpOutcome.SignedIn -> {
            _auth.value = AuthState.SignedIn(outcome.account)
            sync.sync(outcome.account)
            false
        }
        SignUpOutcome.ConfirmationSent -> true
    }

    suspend fun sendPasswordReset(email: String) = backend.sendPasswordReset(clean(email))

    /** Handles a URL that opened the app; sign-in email links sign the learner in. */
    suspend fun open(url: String) {
        val link = AuthLink.of(url)
        if (link == AuthLink.NotAuthLink) return
        try {
            val account = backend.openLink(url)
            _auth.value = AuthState.SignedIn(account)
            when (link) {
                AuthLink.PasswordRecovery -> _needsNewPassword.value = true
                AuthLink.SignUpConfirmed -> _linkMessage.value = LinkMessage.Confirmed
                else -> Unit
            }
            sync.sync(account)
        } catch (error: Exception) {
            _linkMessage.value = LinkMessage.Failed(error)
        }
    }

    suspend fun setNewPassword(password: String) {
        backend.updatePassword(password)
        _needsNewPassword.value = false
    }

    fun dismissNewPassword() {
        _needsNewPassword.value = false
    }

    fun dismissLinkMessage() {
        _linkMessage.value = null
    }

    /** Uploads what it can, signs out and clears this device's vocabulary data. */
    suspend fun signOut() {
        syncNow()
        backend.signOut()
        model.wipe()
        _auth.value = AuthState.SignedOut
    }

    /** Deletes the account and all of its data on the server, then everything here. */
    suspend fun deleteAccount() {
        backend.deleteAccount()
        model.wipe()
        _auth.value = AuthState.SignedOut
    }

    private fun clean(email: String) = email.trim().lowercase()
}
