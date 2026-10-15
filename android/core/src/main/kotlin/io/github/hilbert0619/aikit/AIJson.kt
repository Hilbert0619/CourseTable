package io.github.hilbert0619.aikit

import java.text.BreakIterator
import java.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// Swift's `JSONValue` as kotlinx.serialization's `JsonElement`, with the same readings, and Swift's character counts.

/** JSON in the shapes Swift's `Codable` writes (absent optionals left out). */
val AIKitJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

/** Parses JSON text (any value, as Swift's `JSONDecoder` reads fragments); `null` when it isn't JSON. */
fun parseJson(text: String): JsonElement? = runCatching { Json.parseToJsonElement(text) }.getOrNull()

/** Compact JSON with sorted keys, so the same value always gives the same text (slashes not escaped). */
val JsonElement.jsonText: String
    get() = when (this) {
        is JsonObject -> keys.sorted().joinToString(",", "{", "}") { "${JsonPrimitive(it)}:${getValue(it).jsonText}" }
        is JsonArray -> joinToString(",", "[", "]") { it.jsonText }
        else -> toString()
    }

/** A JSON object's field, or `null` for other values. */
operator fun JsonElement.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

val JsonElement.stringValue: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A whole number: an integer, or a number with no fraction (as Swift's `JSONValue.intValue`). */
val JsonElement.intValue: Int?
    get() {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString || primitive is JsonNull || primitive.booleanOrNull != null) return null
        primitive.content.toIntOrNull()?.let { return it }
        val number = primitive.content.toDoubleOrNull() ?: return null
        val whole = number == Math.rint(number) && kotlin.math.abs(number) < 1e15
        return if (whole && number >= Int.MIN_VALUE && number <= Int.MAX_VALUE) number.toInt() else null
    }

val JsonElement.objectValue: JsonObject? get() = this as? JsonObject

val JsonElement.boolValue: Boolean? get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

/** Builds a JSON object from pairs, dropping `null` values. */
internal fun obj(vararg fields: Pair<String, Any?>): JsonObject =
    JsonObject(fields.mapNotNull { (key, value) -> value?.let { key to json(it) } }.toMap())

/** A Kotlin value as JSON: strings, numbers, booleans, lists, maps and JSON values. */
internal fun json(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map(::json))
    is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to json(item) })
    else -> error("Not a JSON value: $value")
}

/** Swift `Data` in `Codable`: base64. */
object Base64Serializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("aikit.Data", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeString(Base64.getEncoder().encodeToString(value))
    override fun deserialize(decoder: Decoder): ByteArray = Base64.getDecoder().decode(decoder.decodeString())
}

// Swift counts characters as grapheme clusters ("👍🏽" and CR LF are one each); limits and cuts use the same counts.

/** Only characters that are each one Swift character (no combining marks, surrogates or CR LF pairs). */
private val String.isSimpleText: Boolean get() = all { it.code < 0x300 && it != CR }

private const val CR = '\u000D'

/** Swift `String.count`. */
internal val String.characterCount: Int
    get() {
        if (isSimpleText) return length
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(this)
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }

/** Swift `String(prefix(n))`: the first [n] characters. */
internal fun String.prefixCharacters(n: Int): String {
    if (n <= 0) return ""
    if (isSimpleText) return take(n)
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(this)
    var end = 0
    repeat(n) {
        val next = iterator.next()
        if (next == BreakIterator.DONE) return this
        end = next
    }
    return substring(0, end)
}

/** `nilIfEmpty` */
internal fun String.nullIfEmpty(): String? = ifEmpty { null }
