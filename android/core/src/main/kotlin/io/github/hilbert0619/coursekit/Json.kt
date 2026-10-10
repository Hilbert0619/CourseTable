package io.github.hilbert0619.coursekit

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * JSON in the shapes Swift's `Codable` writes for the same types, so the iOS and Android code read the same files
 * (the shared test cases in `spec/fixtures/`): dates as `"2026-09-07"`, times as `"08:05"`, UUIDs upper-case,
 * weekdays as numbers, absent optionals left out.
 */
val CourseKitJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

/** `UUID.uuidString` on Apple platforms: upper-case. */
val UUID.uuidString: String get() = toString().uppercase()

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("coursekit.LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate {
        val text = decoder.decodeString()
        return LocalDate.parse(text) ?: throw IllegalArgumentException("Invalid date \"$text\"; expected YYYY-MM-DD")
    }
}

object TimeOfDaySerializer : KSerializer<TimeOfDay> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("coursekit.TimeOfDay", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TimeOfDay) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): TimeOfDay {
        val text = decoder.decodeString()
        return TimeOfDay.parse(text) ?: throw IllegalArgumentException("Invalid time \"$text\"; expected HH:MM")
    }
}

object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("coursekit.UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.uuidString)
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

object WeekdaySerializer : KSerializer<Weekday> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("coursekit.Weekday", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Weekday) = encoder.encodeInt(value.rawValue)
    override fun deserialize(decoder: Decoder): Weekday {
        val raw = decoder.decodeInt()
        return Weekday.of(raw) ?: throw IllegalArgumentException("Invalid weekday $raw")
    }
}

/** Swift's default `Date` encoding: seconds since 2001-01-01 00:00 UTC, as a number. */
object ReferenceDateSerializer : KSerializer<Instant> {
    private val referenceDate: Instant = Instant.ofEpochSecond(978_307_200)

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("coursekit.Date", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeDouble(secondsBetween(referenceDate, value))
    override fun deserialize(decoder: Decoder): Instant = referenceDate.plusSeconds(decoder.decodeDouble())
}

/** Seconds from [from] to [to], with fractions. */
fun secondsBetween(from: Instant, to: Instant): Double =
    java.time.Duration.between(from, to).let { it.seconds + it.nano / 1e9 }

/** The instant [seconds] (with fractions, may be negative) after this one. */
fun Instant.plusSeconds(seconds: Double): Instant {
    val whole = kotlin.math.floor(seconds).toLong()
    val nanos = kotlin.math.round((seconds - whole) * 1e9).toLong()
    return plusSeconds(whole).plusNanos(nanos)
}
