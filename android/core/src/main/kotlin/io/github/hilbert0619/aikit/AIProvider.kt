package io.github.hilbert0619.aikit

import io.github.hilbert0619.coursekit.UuidSerializer
import io.github.hilbert0619.vocabkit.trimmingWhitespaces
import io.github.hilbert0619.vocabkit.trimmingWhitespacesAndNewlines
import java.net.URI
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The two request formats that cover nearly every provider. */
@Serializable
enum class AIWireFormat {
    /** `POST {base}/chat/completions` with a bearer key: OpenAI and the many providers that copy its API. */
    @SerialName("openAI") OPEN_AI,

    /** `POST {base}/v1/messages` with `x-api-key`: Anthropic's Claude. */
    @SerialName("anthropic") ANTHROPIC,
}

/** A provider the user can pick when adding a connection, with what it accepts. */
data class AIProvider(
    val id: String,
    val name: String,
    val region: Region,
    val format: AIWireFormat = AIWireFormat.OPEN_AI,
    val baseURL: String,
    val needsKey: Boolean = true,
    /** Where to get a key. */
    val keyPage: String? = null,
    /**
     * Suggested models, best default first (checked against the providers' docs 2026-10-12). Model names change
     * often; "Load models" asks the provider, and an empty list means "pick from the provider's list".
     */
    val models: List<String> = emptyList(),
    /** Accepts `stream_options: {"include_usage": true}` (token counts at the end of a stream). */
    val reportsStreamUsage: Boolean = false,
    /** Accepts `response_format: {"type": "json_object"}`. */
    val supportsJSONMode: Boolean = false,
    /** The suggested model can call tools (the Assistant's agent abilities). A connection can switch it. */
    val usesTools: Boolean = true,
    /** The suggested model reads pictures. A connection can switch it. */
    val readsImages: Boolean = false,
    /**
     * A model of the same provider that reads pictures, for a timetable photo when the main one can't (checked
     * 2026-10-15; verify the names in the provider's list).
     */
    val photoModel: String? = null,
) {
    enum class Region {
        /** Reachable from mainland China without a VPN. */
        CHINA,
        INTERNATIONAL,

        /** A server on the user's own network, e.g. Ollama or LM Studio. */
        LOCAL,

        /** Any OpenAI-compatible URL the user types. */
        CUSTOM,
    }

    val defaultModel: String get() = models.firstOrNull() ?: ""

    companion object {
        fun preset(id: String): AIProvider? = PRESETS.firstOrNull { it.id == id }

        /** The list shown when adding a connection, in this order. */
        val PRESETS: List<AIProvider> = listOf(
            AIProvider(
                "deepseek", "DeepSeek", Region.CHINA, baseURL = "https://api.deepseek.com",
                keyPage = "https://platform.deepseek.com/api_keys", models = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
                reportsStreamUsage = true, supportsJSONMode = true,
            ),
            AIProvider(
                "qwen", "Qwen 通义千问", Region.CHINA, baseURL = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                keyPage = "https://bailian.console.aliyun.com/?apiKey=1", models = listOf("qwen-plus", "qwen-flash", "qwen3-max"),
                reportsStreamUsage = true, supportsJSONMode = true, photoModel = "qwen3-vl-plus",
            ),
            AIProvider(
                "kimi", "Kimi", Region.CHINA, baseURL = "https://api.moonshot.cn/v1",
                keyPage = "https://platform.moonshot.cn/console/api-keys", models = listOf("kimi-k2.6"),
                supportsJSONMode = true, readsImages = true,
            ),
            AIProvider(
                "glm", "GLM 智谱", Region.CHINA, baseURL = "https://open.bigmodel.cn/api/paas/v4",
                keyPage = "https://open.bigmodel.cn/usercenter/apikeys", models = listOf("glm-4.7-flash"),
                supportsJSONMode = true, photoModel = "glm-4.6v-flash",
            ),
            AIProvider(
                "doubao", "Doubao 豆包", Region.CHINA, baseURL = "https://ark.cn-beijing.volces.com/api/v3",
                keyPage = "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey",
                reportsStreamUsage = true, supportsJSONMode = true,
            ),
            AIProvider(
                "siliconflow", "SiliconFlow 硅基流动", Region.CHINA, baseURL = "https://api.siliconflow.cn/v1",
                keyPage = "https://cloud.siliconflow.cn/account/ak", reportsStreamUsage = true, supportsJSONMode = true,
            ),
            AIProvider(
                "openai", "OpenAI", Region.INTERNATIONAL, baseURL = "https://api.openai.com/v1",
                keyPage = "https://platform.openai.com/api-keys", models = listOf("gpt-5.5", "gpt-5.4-mini"),
                reportsStreamUsage = true, supportsJSONMode = true, readsImages = true,
            ),
            AIProvider(
                "anthropic", "Claude", Region.INTERNATIONAL, format = AIWireFormat.ANTHROPIC,
                baseURL = "https://api.anthropic.com", keyPage = "https://console.anthropic.com/settings/keys",
                models = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"), readsImages = true,
            ),
            AIProvider(
                "gemini", "Gemini", Region.INTERNATIONAL, baseURL = "https://generativelanguage.googleapis.com/v1beta/openai",
                keyPage = "https://aistudio.google.com/apikey", models = listOf("gemini-flash-latest"),
                supportsJSONMode = true, readsImages = true,
            ),
            AIProvider(
                "openrouter", "OpenRouter", Region.INTERNATIONAL, baseURL = "https://openrouter.ai/api/v1",
                keyPage = "https://openrouter.ai/keys", reportsStreamUsage = true, supportsJSONMode = true,
            ),
            AIProvider("local", "Ollama / LM Studio", Region.LOCAL, baseURL = "http://192.168.1.2:11434/v1", needsKey = false),
            AIProvider("custom", "Custom", Region.CUSTOM, baseURL = "https://"),
        )
    }
}

/** A provider plus the model and address the user chose. The key is stored separately (Keystore), by [id]. */
@Serializable
data class AIConnection(
    @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    val providerID: String,
    val name: String,
    val format: AIWireFormat,
    val baseURL: String,
    val model: String,
    /** The user's choice whether the model calls tools; `null` follows the preset. */
    val toolsSwitch: Boolean? = null,
    /** The user's choice whether the model reads pictures; `null` follows the preset. */
    val imagesSwitch: Boolean? = null,
    /** The model for pictures; `null` follows the preset, empty means none. */
    val photoModelChoice: String? = null,
) {
    constructor(provider: AIProvider, model: String? = null) : this(
        providerID = provider.id, name = provider.name, format = provider.format, baseURL = provider.baseURL,
        model = model ?: provider.defaultModel,
    )

    val provider: AIProvider? get() = AIProvider.preset(providerID)

    /** What the preset accepts; a custom connection gets the safe defaults. */
    internal val reportsStreamUsage: Boolean get() = provider?.reportsStreamUsage ?: false
    internal val supportsJSONMode: Boolean get() = provider?.supportsJSONMode ?: false

    /** The Assistant offers the app's tools to the model. */
    val usesTools: Boolean get() = toolsSwitch ?: provider?.usesTools ?: true

    /** The model itself reads pictures. A changed model keeps the preset's answer only for the preset's own models. */
    val readsImages: Boolean
        get() {
            imagesSwitch?.let { return it }
            val provider = provider ?: return false
            if (!provider.readsImages) return false
            return provider.models.isEmpty() || model in provider.models
        }

    /** The model pictures go to when the main model doesn't read them. */
    val photoModel: String?
        get() = (photoModelChoice ?: provider?.photoModel)?.trimmingWhitespaces()?.nullIfEmpty()

    /**
     * Where a picture goes: the connection itself, the same connection with its photo model, or nowhere (the phone
     * reads the text instead).
     */
    val pictureConnection: AIConnection?
        get() {
            if (readsImages) return this
            val photoModel = photoModel ?: return null
            return copy(model = photoModel, imagesSwitch = true)
        }

    /** The base URL without trailing slashes, or `null` when it isn't an http(s) URL with a host. */
    val normalizedBaseURL: String?
        get() {
            val text = baseURL.trimmingWhitespacesAndNewlines().trimEnd('/')
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "https" && scheme != "http") return null
            // A host name Java won't take as one (e.g. with "_") still has an authority.
            if ((uri.host ?: uri.rawAuthority).isNullOrEmpty()) return null
            return text
        }

    /** Sending a key over plain http is only allowed to a server on the user's own network. */
    val isInsecure: Boolean get() = normalizedBaseURL?.let { URI(it).scheme?.lowercase() == "http" } ?: false
}
