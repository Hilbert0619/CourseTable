package io.github.hilbert0619.aikit

import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.vocabkit.trimmingWhitespacesAndNewlines
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** One argument of a tool. */
data class AIToolParameter(val name: String, val kind: Kind, val description: String, val isRequired: Boolean = false) {
    sealed interface Kind {
        /** Text up to [maxLength] characters (trimmed; an empty text counts as left out). */
        data class Text(val maxLength: Int) : Kind

        /** `YYYY-MM-DD`. */
        data object Date : Kind

        /** 24-hour `HH:mm`. */
        data object Time : Kind
        data class Integer(val range: IntRange) : Kind
        data object Boolean : Kind

        /** One of these words. */
        data class Choice(val words: List<String>) : Kind
    }
}

/**
 * A function of the app the model may call ("tool"). Reading tools run on their own; changing tools become a proposal
 * the user confirms (docs/ai-agent-spec.md §2).
 */
data class AITool(
    val name: String,
    val description: String,
    val parameters: List<AIToolParameter> = emptyList(),
    val effect: Effect,
) {
    enum class Effect { READS, CHANGES }

    /**
     * The parameters as JSON Schema. Only the keywords every provider accepts (type, description, enum, minimum,
     * maximum, properties, required); length limits and formats are in the descriptions, and [validate] checks them.
     */
    val schema: JsonElement
        get() {
            val properties = linkedMapOf<String, Any?>()
            for (parameter in parameters) {
                val property = linkedMapOf<String, Any?>("description" to parameter.description)
                when (val kind = parameter.kind) {
                    is AIToolParameter.Kind.Text -> {
                        property["type"] = "string"
                        property["description"] = "${parameter.description} (at most ${kind.maxLength} characters)"
                    }
                    AIToolParameter.Kind.Date -> {
                        property["type"] = "string"
                        property["description"] = "${parameter.description} (YYYY-MM-DD)"
                    }
                    AIToolParameter.Kind.Time -> {
                        property["type"] = "string"
                        property["description"] = "${parameter.description} (24-hour HH:mm)"
                    }
                    is AIToolParameter.Kind.Integer -> {
                        property["type"] = "integer"
                        property["minimum"] = kind.range.first
                        property["maximum"] = kind.range.last
                    }
                    AIToolParameter.Kind.Boolean -> property["type"] = "boolean"
                    is AIToolParameter.Kind.Choice -> {
                        property["type"] = "string"
                        property["enum"] = kind.words
                    }
                }
                properties[parameter.name] = property
            }
            val schema = linkedMapOf<String, Any?>("type" to "object", "properties" to properties)
            val required = parameters.filter { it.isRequired }.map { it.name }
            if (required.isNotEmpty()) schema["required"] = required
            return json(schema)
        }

    /**
     * Checks the arguments the model wrote (JSON text) against the parameters: required ones are there, each value has
     * the right type, length, format or range. Values are normalised (dates and times in their canonical form, numbers
     * given as text read as numbers); unknown names are ignored.
     */
    fun validate(text: String): Result<AIToolArguments> {
        val trimmed = text.trimmingWhitespacesAndNewlines()
        val fields: Map<String, JsonElement> = when {
            trimmed.isEmpty() || trimmed == "null" -> emptyMap()
            else -> parseJson(trimmed) as? JsonObject
                ?: return Result.failure(AIToolError("The arguments aren't a JSON object. Call $name again with valid JSON."))
        }
        val values = linkedMapOf<String, JsonElement>()
        for (parameter in parameters) {
            val key = parameter.name
            when (val normalized = normalize(fields[key], parameter.kind)) {
                Normalized.Missing -> if (parameter.isRequired) return Result.failure(AIToolError("\"$key\" is missing."))
                is Normalized.Value -> values[key] = normalized.value
                is Normalized.Wrong -> return Result.failure(AIToolError("\"$key\" ${normalized.expectation}."))
            }
        }
        return Result.success(AIToolArguments(values))
    }

    private sealed interface Normalized {
        data object Missing : Normalized
        data class Value(val value: JsonElement) : Normalized
        data class Wrong(val expectation: String) : Normalized
    }

    private fun normalize(value: JsonElement?, kind: AIToolParameter.Kind): Normalized {
        if (value == null || value is JsonNull) return Normalized.Missing
        val primitive = value as? JsonPrimitive
        val text: String? = when {
            primitive == null -> null
            primitive.isString -> primitive.content.trimmingWhitespacesAndNewlines()
            primitive.booleanOrNull != null -> primitive.content
            else -> value.intValue?.let { if (primitive.content.toIntOrNull() != null) it.toString() else null }
                ?: primitive.content.toDoubleOrNull()?.toString()
        }
        return when (kind) {
            is AIToolParameter.Kind.Text -> when {
                text == null -> Normalized.Wrong("must be text")
                text.isEmpty() -> Normalized.Missing
                text.characterCount <= kind.maxLength -> Normalized.Value(JsonPrimitive(text))
                else -> Normalized.Wrong("is too long (at most ${kind.maxLength} characters)")
            }
            AIToolParameter.Kind.Date -> {
                val wrong = Normalized.Wrong("must be a date like 2026-10-15")
                when {
                    text == null -> wrong
                    text.isEmpty() -> Normalized.Missing
                    else -> {
                        val date = LocalDate.parse(text.take(10))
                        if (date == null || !(text.length == 10 || text.getOrNull(10) == 'T')) wrong else Normalized.Value(JsonPrimitive(date.toString()))
                    }
                }
            }
            AIToolParameter.Kind.Time -> {
                val wrong = Normalized.Wrong("must be a 24-hour time like 08:30")
                when {
                    text == null -> wrong
                    text.isEmpty() -> Normalized.Missing
                    else -> TimeOfDay.parse(text)?.let { Normalized.Value(JsonPrimitive(it.toString())) } ?: wrong
                }
            }
            is AIToolParameter.Kind.Integer -> {
                val number = value.intValue ?: text?.toIntOrNull()
                when {
                    number == null -> if (text?.isEmpty() == true) Normalized.Missing else Normalized.Wrong("must be a whole number")
                    number in kind.range -> Normalized.Value(JsonPrimitive(number))
                    else -> Normalized.Wrong("must be between ${kind.range.first} and ${kind.range.last}")
                }
            }
            AIToolParameter.Kind.Boolean -> {
                val flag = primitive?.let { if (it.isString) null else it.booleanOrNull }
                    ?: primitive?.takeIf { it.isString }?.content?.lowercase()?.let { word ->
                        when (word) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        }
                    }
                if (flag != null) Normalized.Value(JsonPrimitive(flag)) else Normalized.Wrong("must be true or false")
            }
            is AIToolParameter.Kind.Choice -> {
                val word = text?.let { given -> kind.words.firstOrNull { it.lowercase() == given.lowercase() } }
                if (word != null) Normalized.Value(JsonPrimitive(word)) else Normalized.Wrong("must be one of: ${kind.words.joinToString(", ")}")
            }
        }
    }
}

/** Arguments that passed [AITool.validate]. */
data class AIToolArguments(val values: Map<String, JsonElement> = emptyMap()) {
    fun text(name: String): String? = values[name]?.stringValue
    fun int(name: String): Int? = values[name]?.intValue
    fun date(name: String): LocalDate? = text(name)?.let(LocalDate::parse)
    fun time(name: String): TimeOfDay? = text(name)?.let(TimeOfDay::parse)
    fun bool(name: String): Boolean? = values[name]?.boolValue
    fun has(name: String): Boolean = values[name] != null
}

/** Why a tool call can't run, in words for the model (sent back as an error result so it can fix the call). */
class AIToolError(override val message: String) : Exception(message) {
    override fun equals(other: Any?): Boolean = other is AIToolError && other.message == message
    override fun hashCode(): Int = message.hashCode()
}
