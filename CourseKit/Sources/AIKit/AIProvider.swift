import Foundation

/// The two request formats that cover nearly every provider.
public enum AIWireFormat: String, Hashable, Codable, Sendable {
    /// `POST {base}/chat/completions` with a bearer key: OpenAI and the many providers that copy its API.
    case openAI
    /// `POST {base}/v1/messages` with `x-api-key`: Anthropic's Claude.
    case anthropic
}

/// A provider the user can pick when adding a connection, with what it accepts.
public struct AIProvider: Identifiable, Hashable, Sendable {
    public enum Region: String, Hashable, Sendable {
        /// Reachable from mainland China without a VPN.
        case china
        case international
        /// A server on the user's own network, e.g. Ollama or LM Studio.
        case local
        /// Any OpenAI-compatible URL the user types.
        case custom
    }

    public var id: String
    public var name: String
    public var region: Region
    public var format: AIWireFormat
    public var baseURL: String
    public var needsKey: Bool
    /// Where to get a key.
    public var keyPage: String?
    /// Suggested models, best default first (checked against the providers' docs 2026-10-12). Model names change
    /// often; "Load models" asks the provider, and an empty list means "pick from the provider's list".
    public var models: [String]
    /// Accepts `stream_options: {"include_usage": true}` (token counts at the end of a stream).
    public var reportsStreamUsage: Bool
    /// Accepts `response_format: {"type": "json_object"}`.
    public var supportsJSONMode: Bool
    /// The suggested model can call tools (the Assistant's agent abilities). A connection can switch it.
    public var usesTools: Bool
    /// The suggested model reads pictures. A connection can switch it.
    public var readsImages: Bool
    /// A model of the same provider that reads pictures, for a timetable photo when the main one can't
    /// (checked 2026-10-15; verify the names in the provider's list).
    public var photoModel: String?

    public init(id: String, name: String, region: Region, format: AIWireFormat = .openAI, baseURL: String,
                needsKey: Bool = true, keyPage: String? = nil, models: [String] = [],
                reportsStreamUsage: Bool = false, supportsJSONMode: Bool = false, usesTools: Bool = true,
                readsImages: Bool = false, photoModel: String? = nil) {
        self.id = id
        self.name = name
        self.region = region
        self.format = format
        self.baseURL = baseURL
        self.needsKey = needsKey
        self.keyPage = keyPage
        self.models = models
        self.reportsStreamUsage = reportsStreamUsage
        self.supportsJSONMode = supportsJSONMode
        self.usesTools = usesTools
        self.readsImages = readsImages
        self.photoModel = photoModel
    }

    public var defaultModel: String { models.first ?? "" }

    public static func preset(id: String) -> AIProvider? { presets.first { $0.id == id } }

    /// The list shown when adding a connection, in this order.
    public static let presets: [AIProvider] = [
        AIProvider(id: "deepseek", name: "DeepSeek", region: .china, baseURL: "https://api.deepseek.com",
                   keyPage: "https://platform.deepseek.com/api_keys", models: ["deepseek-v4-flash", "deepseek-v4-pro"],
                   reportsStreamUsage: true, supportsJSONMode: true),
        AIProvider(id: "qwen", name: "Qwen 通义千问", region: .china,
                   baseURL: "https://dashscope.aliyuncs.com/compatible-mode/v1",
                   keyPage: "https://bailian.console.aliyun.com/?apiKey=1", models: ["qwen-plus", "qwen-flash", "qwen3-max"],
                   reportsStreamUsage: true, supportsJSONMode: true, photoModel: "qwen3-vl-plus"),
        AIProvider(id: "kimi", name: "Kimi", region: .china, baseURL: "https://api.moonshot.cn/v1",
                   keyPage: "https://platform.moonshot.cn/console/api-keys", models: ["kimi-k2.6"],
                   supportsJSONMode: true, readsImages: true),
        AIProvider(id: "glm", name: "GLM 智谱", region: .china, baseURL: "https://open.bigmodel.cn/api/paas/v4",
                   keyPage: "https://open.bigmodel.cn/usercenter/apikeys", models: ["glm-4.7-flash"],
                   supportsJSONMode: true, photoModel: "glm-4.6v-flash"),
        AIProvider(id: "doubao", name: "Doubao 豆包", region: .china, baseURL: "https://ark.cn-beijing.volces.com/api/v3",
                   keyPage: "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey", models: [],
                   reportsStreamUsage: true, supportsJSONMode: true),
        AIProvider(id: "siliconflow", name: "SiliconFlow 硅基流动", region: .china, baseURL: "https://api.siliconflow.cn/v1",
                   keyPage: "https://cloud.siliconflow.cn/account/ak", models: [],
                   reportsStreamUsage: true, supportsJSONMode: true),
        AIProvider(id: "openai", name: "OpenAI", region: .international, baseURL: "https://api.openai.com/v1",
                   keyPage: "https://platform.openai.com/api-keys", models: ["gpt-5.5", "gpt-5.4-mini"],
                   reportsStreamUsage: true, supportsJSONMode: true, readsImages: true),
        AIProvider(id: "anthropic", name: "Claude", region: .international, format: .anthropic,
                   baseURL: "https://api.anthropic.com", keyPage: "https://console.anthropic.com/settings/keys",
                   models: ["claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"], readsImages: true),
        AIProvider(id: "gemini", name: "Gemini", region: .international,
                   baseURL: "https://generativelanguage.googleapis.com/v1beta/openai",
                   keyPage: "https://aistudio.google.com/apikey", models: ["gemini-flash-latest"],
                   supportsJSONMode: true, readsImages: true),
        AIProvider(id: "openrouter", name: "OpenRouter", region: .international, baseURL: "https://openrouter.ai/api/v1",
                   keyPage: "https://openrouter.ai/keys", models: [], reportsStreamUsage: true, supportsJSONMode: true),
        AIProvider(id: "local", name: "Ollama / LM Studio", region: .local, baseURL: "http://192.168.1.2:11434/v1",
                   needsKey: false, models: []),
        AIProvider(id: "custom", name: "Custom", region: .custom, baseURL: "https://", models: []),
    ]
}

/// A provider plus the model and address the user chose. The key is stored separately (Keychain / Keystore), by `id`.
public struct AIConnection: Identifiable, Hashable, Codable, Sendable {
    public var id: UUID
    public var providerID: String
    public var name: String
    public var format: AIWireFormat
    public var baseURL: String
    public var model: String
    /// The user's choice whether the model calls tools; `nil` follows the preset.
    public var toolsSwitch: Bool?
    /// The user's choice whether the model reads pictures; `nil` follows the preset.
    public var imagesSwitch: Bool?
    /// The model for pictures; `nil` follows the preset, empty means none.
    public var photoModelChoice: String?

    public init(id: UUID = UUID(), providerID: String, name: String, format: AIWireFormat, baseURL: String, model: String,
                toolsSwitch: Bool? = nil, imagesSwitch: Bool? = nil, photoModelChoice: String? = nil) {
        self.id = id
        self.providerID = providerID
        self.name = name
        self.format = format
        self.baseURL = baseURL
        self.model = model
        self.toolsSwitch = toolsSwitch
        self.imagesSwitch = imagesSwitch
        self.photoModelChoice = photoModelChoice
    }

    public init(provider: AIProvider, model: String? = nil) {
        self.init(providerID: provider.id, name: provider.name, format: provider.format, baseURL: provider.baseURL,
                  model: model ?? provider.defaultModel)
    }

    public var provider: AIProvider? { AIProvider.preset(id: providerID) }

    /// What the preset accepts; a custom connection gets the safe defaults.
    var reportsStreamUsage: Bool { provider?.reportsStreamUsage ?? false }
    var supportsJSONMode: Bool { provider?.supportsJSONMode ?? false }

    /// The Assistant offers the app's tools to the model.
    public var usesTools: Bool { toolsSwitch ?? provider?.usesTools ?? true }

    /// The model itself reads pictures. A changed model keeps the preset's answer only for the preset's own models.
    public var readsImages: Bool {
        if let imagesSwitch { return imagesSwitch }
        guard let provider, provider.readsImages else { return false }
        return provider.models.isEmpty || provider.models.contains(model)
    }

    /// The model pictures go to when the main model doesn't read them.
    public var photoModel: String? {
        let choice = (photoModelChoice ?? provider?.photoModel)?.trimmingCharacters(in: .whitespaces) ?? ""
        return choice.isEmpty ? nil : choice
    }

    /// Where a picture goes: the connection itself, the same connection with its photo model, or nowhere (the
    /// phone reads the text instead).
    public var pictureConnection: AIConnection? {
        if readsImages { return self }
        guard let photoModel else { return nil }
        var copy = self
        copy.model = photoModel
        copy.imagesSwitch = true
        return copy
    }

    /// The base URL without trailing slashes, or `nil` when it isn't an http(s) URL with a host.
    public var normalizedBaseURL: URL? {
        var text = baseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        while text.hasSuffix("/") { text.removeLast() }
        guard let url = URL(string: text), let scheme = url.scheme?.lowercased(), scheme == "https" || scheme == "http",
              let host = url.host, !host.isEmpty
        else { return nil }
        return url
    }

    /// Sending a key over plain http is only allowed to a server on the user's own network.
    public var isInsecure: Bool { normalizedBaseURL?.scheme?.lowercased() == "http" }
}
