package io.github.hilbert0619.vocabkit.backend

import io.github.hilbert0619.coursekit.UuidSerializer
import io.github.hilbert0619.vocabkit.Rating
import io.github.hilbert0619.vocabkit.ReviewEvent
import io.github.hilbert0619.vocabkit.ReviewMode
import io.github.hilbert0619.vocabkit.WordEntry
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

// The Words tab's server (iOS CourseTable/Vocabulary/Backend/VocabBackend.swift): the rows exchanged with Supabase's
// REST API (docs/vocabulary-spec.md §5) and what the app needs from it.

data class Account(val id: UUID, val email: String)

sealed interface SignUpOutcome {
    data class SignedIn(val account: Account) : SignUpOutcome

    /** The account exists but its email must be confirmed with the link Supabase sent. */
    data object ConfirmationSent : SignUpOutcome
}

/** Why signing in, a request or a sync failed, in terms the learner can act on (the app words them). */
sealed class VocabAuthError(message: String) : Exception(message) {
    data object InvalidCredentials : VocabAuthError("invalid_credentials")
    data object EmailNotConfirmed : VocabAuthError("email_not_confirmed")
    data object WeakPassword : VocabAuthError("weak_password")
    data object InvalidEmail : VocabAuthError("invalid_email")
    data object RateLimited : VocabAuthError("rate_limited")
    data object EmailNotAllowed : VocabAuthError("email_not_allowed")
    data object LinkExpired : VocabAuthError("link_expired")

    /** No connection: answers stay on the device and upload later. */
    data object Offline : VocabAuthError("offline")

    /** Anything else, with the server's message. */
    data class Other(val text: String) : VocabAuthError(text)

    companion object {
        /** Supabase Auth error codes (https://supabase.com/docs/guides/auth/debugging/error-codes). */
        fun fromCode(code: String, message: String): VocabAuthError = when (code) {
            "invalid_credentials" -> InvalidCredentials
            "email_not_confirmed" -> EmailNotConfirmed
            "weak_password" -> WeakPassword
            "email_address_invalid", "validation_failed" -> InvalidEmail
            "over_email_send_rate_limit", "over_request_rate_limit" -> RateLimited
            "email_address_not_authorized" -> EmailNotAllowed
            "otp_expired", "flow_state_expired" -> LinkExpired
            else -> Other(message)
        }
    }
}

/**
 * Everything the Words tab needs from the server. The app's `SupabaseBackend` is the real one; [DemoBackend] keeps
 * everything in memory for tests and screenshots.
 */
interface VocabBackend {
    /**
     * The account of the session stored on this device, even if its token has expired (it is refreshed on the next
     * request), so the app also opens signed in when offline.
     */
    suspend fun restoreAccount(): Account?
    suspend fun signUp(email: String, password: String): SignUpOutcome
    suspend fun signIn(email: String, password: String): Account

    /** Always signs out on this device, even offline. */
    suspend fun signOut()
    suspend fun sendPasswordReset(email: String)

    /** Signs in with the session carried by an email link. */
    suspend fun openLink(url: String): Account
    suspend fun updatePassword(password: String)
    suspend fun deleteAccount()

    suspend fun books(): List<RemoteBook>

    /** Entries of a book in study order, with their dictionary words; rows `from..to` (0-based). */
    suspend fun entries(book: UUID, from: Int, to: Int): List<RemoteEntry>
    suspend fun saveBooks(books: List<RemoteBook>)
    suspend fun saveEntries(entries: List<RemoteEntry>)

    /** Uploading an answer twice is harmless: the id decides. */
    suspend fun uploadReviews(reviews: List<RemoteReview>)

    /** The learner's answers received after [receivedAfter], oldest first; rows `from..to`. */
    suspend fun reviews(receivedAfter: Instant?, from: Int, to: Int): List<RemoteReview>
    suspend fun favorites(): List<RemoteFavorite>
    suspend fun saveFavorites(favorites: List<RemoteFavorite>)
    suspend fun settings(): RemoteSettings?
    suspend fun saveSettings(settings: RemoteSettings)
}

// Rows exchanged with the server (table columns). Nulls are sent explicitly, so `deleted_at: null` clears a deletion.

/** The JSON for the rows: every column sent (nulls and defaults too), columns the app doesn't know ignored. */
val VocabJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

/** A row of `books`. */
@Serializable
data class RemoteBook(
    @Serializable(with = UuidSerializer::class) val id: UUID,
    @SerialName("owner_id") @Serializable(with = UuidSerializer::class) val ownerId: UUID?,
    val name: String,
    val description: String = "",
    val source: String = "",
    val licence: String = "",
    @SerialName("word_count") val wordCount: Int = 0,
    @SerialName("updated_at") @Serializable(with = TimestampSerializer::class) val updatedAt: Instant,
    @SerialName("deleted_at") @Serializable(with = TimestampSerializer::class) val deletedAt: Instant? = null,
)

/** A row of `words`. */
@Serializable
data class RemoteWord(
    val id: Long,
    val headword: String,
    val display: String,
    val phonetic: String = "",
    @SerialName("translation_zh") val translationZh: String,
    @SerialName("definition_en") val definitionEn: String = "",
    val pos: String = "",
    val collins: Int = 0,
    val oxford: Boolean = false,
    val tags: List<String> = emptyList(),
    val bnc: Int? = null,
    val frq: Int? = null,
    val exchange: String = "",
) {
    val entry: WordEntry
        get() = WordEntry(
            headword = headword, display = display, phonetic = phonetic, translation = translationZh, definition = definitionEn,
            posMix = pos, collins = collins, oxford = oxford, tags = tags, bnc = bnc, frq = frq, exchange = exchange,
        )

    companion object {
        fun of(id: Long, entry: WordEntry) = RemoteWord(
            id = id, headword = entry.headword, display = entry.display, phonetic = entry.phonetic, translationZh = entry.translation,
            definitionEn = entry.definition, pos = entry.posMix, collins = entry.collins, oxford = entry.oxford, tags = entry.tags,
            bnc = entry.bnc, frq = entry.frq, exchange = entry.exchange,
        )
    }
}

/** A row of `book_entries`; [word] is the embedded dictionary row when downloading (never uploaded). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RemoteEntry(
    @Serializable(with = UuidSerializer::class) val id: UUID,
    @SerialName("book_id") @Serializable(with = UuidSerializer::class) val bookId: UUID,
    val position: Int,
    val headword: String,
    @SerialName("word_id") val wordId: Long? = null,
    val display: String? = null,
    val meaning: String? = null,
    val phonetic: String? = null,
    val pos: String? = null,
    val example: String? = null,
    @SerialName("example_translation") val exampleTranslation: String? = null,
    @SerialName("updated_at") @Serializable(with = TimestampSerializer::class) val updatedAt: Instant,
    @SerialName("deleted_at") @Serializable(with = TimestampSerializer::class) val deletedAt: Instant? = null,
    @SerialName("words") @EncodeDefault(EncodeDefault.Mode.NEVER) val word: RemoteWord? = null,
)

/** A row of `review_logs`. [receivedAt] is set by the server and never uploaded. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RemoteReview(
    @Serializable(with = UuidSerializer::class) val id: UUID,
    val headword: String,
    val rating: Int,
    val mode: String,
    @SerialName("reviewed_at") @Serializable(with = TimestampSerializer::class) val reviewedAt: Instant,
    @SerialName("study_day") val studyDay: Int,
    @SerialName("duration_ms") val durationMs: Int,
    @SerialName("received_at") @Serializable(with = TimestampSerializer::class) @EncodeDefault(EncodeDefault.Mode.NEVER)
    val receivedAt: Instant? = null,
) {
    val event: ReviewEvent?
        get() {
            val rating = Rating.of(rating) ?: return null
            val mode = ReviewMode.of(mode) ?: return null
            return ReviewEvent(id, headword, rating, mode, reviewedAt, studyDay, durationMs)
        }

    companion object {
        fun of(event: ReviewEvent) = RemoteReview(
            event.id, event.headword, event.rating.rawValue, event.mode.rawValue, event.reviewedAt, event.studyDay, event.durationMs,
        )
    }
}

/** A row of `favorites`. */
@Serializable
data class RemoteFavorite(
    val headword: String,
    @SerialName("is_favorite") val isFavorite: Boolean,
    @SerialName("updated_at") @Serializable(with = TimestampSerializer::class) val updatedAt: Instant,
)

/** The row of `user_settings`. */
@Serializable
data class RemoteSettings(
    @SerialName("book_id") @Serializable(with = UuidSerializer::class) val bookId: UUID?,
    @SerialName("daily_new_words") val dailyNewWords: Int,
    @SerialName("review_mode") val reviewMode: String,
    /** `yyyy-MM-dd` */
    @SerialName("exam_date") val examDate: String?,
    @SerialName("day_start_minutes") val dayStartMinutes: Int,
    @SerialName("desired_retention") val desiredRetention: Double,
    @SerialName("updated_at") @Serializable(with = TimestampSerializer::class) val updatedAt: Instant,
)

/** Postgres `timestamptz` as the REST API writes it (`2026-09-30T10:00:00.123456+00:00`); sent as `…Z`. */
object TimestampSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("vocabkit.Timestamp", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    override fun deserialize(decoder: Decoder): Instant = OffsetDateTime.parse(decoder.decodeString()).toInstant()
}

/** A system book: a word list shipped by the backend (docs/vocabulary-spec.md §4, iOS `SystemBook`). */
data class SystemBook(val id: UUID, val slug: String, val name: String, val description: String, val source: String, val licence: String) {
    companion object {
        /** 考研词汇: the ECDICT entries tagged `ky`, most frequent first. */
        val KAOYAN = SystemBook(
            id = UUID.fromString("00000000-0000-4000-8000-000000000001"),
            slug = "kaoyan-ecdict",
            name = "考研词汇 (ECDICT)",
            description = "考研英语词汇（ECDICT 中标注为 ky 的词条），按常用程度排序，常用词在前。数据来自 ECDICT（MIT 许可）。",
            source = "ECDICT https://github.com/skywind3000/ECDICT",
            licence = "MIT",
        )
    }
}
