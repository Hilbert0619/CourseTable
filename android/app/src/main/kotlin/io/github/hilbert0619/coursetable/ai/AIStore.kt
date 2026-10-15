package io.github.hilbert0619.coursetable.ai

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import io.github.hilbert0619.aikit.AIAnswer
import io.github.hilbert0619.aikit.AIAnswerLanguage
import io.github.hilbert0619.aikit.AIConnection
import io.github.hilbert0619.aikit.AIKitJson
import io.github.hilbert0619.coursekit.uuidString
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Requests and tokens sent through one connection in one month. */
@Serializable
data class AIUsageTotal(val requests: Int = 0, val inputTokens: Int = 0, val outputTokens: Int = 0)

/** A finished answer kept on the device, so showing it again costs nothing. */
@Serializable
data class CachedAIAnswer(val text: String, val model: String, val savedAt: Long)

/** What the AI screens show from the store. */
data class AIStoreState(
    val connections: List<AIConnection> = emptyList(),
    val defaultId: UUID? = null,
    /** By [AIStore.usageKey]. */
    val usage: Map<String, AIUsageTotal> = emptyMap(),
    val savedAnswerCount: Int = 0,
) {
    /** The connection AI help uses: the chosen default, or else the first. */
    val defaultConnection: AIConnection? get() = connections.firstOrNull { it.id == defaultId } ?: connections.firstOrNull()

    val isSetUp: Boolean get() = defaultConnection != null
}

/**
 * The user's AI connections (keys in [AIKeys]), the default one, monthly usage and saved answers (docs/ai-spec.md).
 * Nothing here talks to the app's own server. Main thread only. With [prefs] `null` everything stays in memory
 * (tests and screenshots).
 */
class AIStore(
    private val prefs: SharedPreferences?,
    private val cacheFile: File?,
    val transport: AITransport,
    private val keys: AIKeys,
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private var cache: MutableMap<String, CachedAIAnswer> = loadCache()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AIStoreState> = _state.asStateFlow()

    val defaultConnection: AIConnection? get() = _state.value.defaultConnection
    val isSetUp: Boolean get() = _state.value.isSetUp

    // Connections

    fun key(connection: AIConnection): String? = keys.key(connection.id)

    fun hasKey(connection: AIConnection): Boolean = !key(connection).isNullOrEmpty()

    /** Adds or updates a connection. A `null` key keeps the stored one; an empty key removes it. */
    fun save(connection: AIConnection, key: String?) {
        if (key != null) keys.setKey(connection.id, key.trim())
        _state.update { state ->
            val connections = if (state.connections.any { it.id == connection.id }) {
                state.connections.map { if (it.id == connection.id) connection else it }
            } else {
                state.connections + connection
            }
            state.copy(connections = connections, defaultId = state.defaultId ?: connection.id)
        }
        persistConnections()
    }

    fun delete(connection: AIConnection) {
        keys.setKey(connection.id, null)
        _state.update { state ->
            val connections = state.connections.filter { it.id != connection.id }
            state.copy(connections = connections, defaultId = if (state.defaultId == connection.id) connections.firstOrNull()?.id else state.defaultId)
        }
        persistConnections()
    }

    fun makeDefault(connection: AIConnection) {
        _state.update { it.copy(defaultId = connection.id) }
        persistConnections()
    }

    private fun persistConnections() {
        val state = _state.value
        prefs?.edit()
            ?.putString(CONNECTIONS, AIKitJson.encodeToString(ListSerializer(AIConnection.serializer()), state.connections))
            ?.putString(DEFAULT_ID, state.defaultId?.uuidString)
            ?.apply()
    }

    // Usage

    fun record(answer: AIAnswer, connection: AIConnection) {
        val key = usageKey(connection.id, clock())
        _state.update { state ->
            val total = state.usage[key] ?: AIUsageTotal()
            val updated = total.copy(
                requests = total.requests + 1,
                inputTokens = total.inputTokens + (answer.inputTokens ?: 0),
                outputTokens = total.outputTokens + (answer.outputTokens ?: 0),
            )
            state.copy(usage = state.usage + (key to updated))
        }
        prefs?.edit()?.putString(USAGE, AIKitJson.encodeToString(USAGE_SERIALIZER, _state.value.usage))?.apply()
    }

    fun usage(connection: AIConnection, at: Instant = clock()): AIUsageTotal = _state.value.usage[usageKey(connection.id, at)] ?: AIUsageTotal()

    /** `<connection>|2026-10` */
    fun usageKey(id: UUID, at: Instant): String {
        val date = at.atZone(zone)
        return "${id.uuidString}|${date.year}-${date.monthValue}"
    }

    // Saved answers

    fun cachedAnswer(key: String): CachedAIAnswer? = cache[key]

    fun saveAnswer(text: String, model: String, key: String) {
        cache[key] = CachedAIAnswer(text, model, clock().toEpochMilli())
        if (cache.size > MAX_CACHED_ANSWERS) {
            cache.entries.sortedBy { it.value.savedAt }.take(cache.size - MAX_CACHED_ANSWERS).forEach { cache.remove(it.key) }
        }
        persistCache()
        _state.update { it.copy(savedAnswerCount = cache.size) }
    }

    /** Deletes the saved answers (and, with the Assistant, its chats). */
    fun clearSavedAnswers() {
        cache.clear()
        cacheFile?.delete()
        _state.update { it.copy(savedAnswerCount = 0) }
    }

    private fun persistCache() {
        val file = cacheFile ?: return
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(AIKitJson.encodeToString(CACHE_SERIALIZER, cache))
        temporary.renameTo(file)
    }

    private fun loadCache(): MutableMap<String, CachedAIAnswer> {
        val text = cacheFile?.takeIf { it.exists() }?.readText() ?: return mutableMapOf()
        return runCatching { AIKitJson.decodeFromString(CACHE_SERIALIZER, text).toMutableMap() }.getOrElse { mutableMapOf() }
    }

    private fun load(): AIStoreState {
        val connections = prefs?.getString(CONNECTIONS, null)?.let { text ->
            runCatching { AIKitJson.decodeFromString(ListSerializer(AIConnection.serializer()), text) }.getOrNull()
        }.orEmpty()
        val defaultId = prefs?.getString(DEFAULT_ID, null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val usage = prefs?.getString(USAGE, null)?.let { runCatching { AIKitJson.decodeFromString(USAGE_SERIALIZER, it) }.getOrNull() }.orEmpty()
        return AIStoreState(connections, defaultId, usage, cache.size)
    }

    companion object {
        const val MAX_CACHED_ANSWERS = 400
        private const val CONNECTIONS = "connections"
        private const val DEFAULT_ID = "defaultConnection"
        private const val USAGE = "usage"
        private val USAGE_SERIALIZER = MapSerializer(String.serializer(), AIUsageTotal.serializer())
        private val CACHE_SERIALIZER = MapSerializer(String.serializer(), CachedAIAnswer.serializer())

        /** The app's store: settings in the `ai` preferences, answers in `files/ai/answers.json`. */
        fun create(context: Context, transport: AITransport = KtorAITransport()): AIStore = AIStore(
            context.getSharedPreferences("ai", Context.MODE_PRIVATE),
            File(context.filesDir, "ai/answers.json"),
            transport,
            KeystoreAIKeys(context),
        )

        /** A store in memory with one DeepSeek connection answered by canned text: screenshots. */
        fun demo(transport: AITransport = DemoAITransport()): AIStore {
            val store = AIStore(null, null, transport, MemoryAIKeys())
            io.github.hilbert0619.aikit.AIProvider.preset("deepseek")?.let { store.save(AIConnection(it), "demo-key") }
            return store
        }
    }
}

/** Answers are written in the app's language. */
fun aiAnswerLanguage(configuration: Configuration): AIAnswerLanguage =
    if (configuration.locales[0]?.language == "zh") AIAnswerLanguage.SIMPLIFIED_CHINESE else AIAnswerLanguage.ENGLISH
