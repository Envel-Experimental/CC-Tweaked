// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core;

/**
 * Server-side configuration for the CC:Tweaked AI API feature.
 *
 * <p>Fields are populated by {@code ConfigSpec.syncServer()} and should never
 * be mutated directly. All accesses from Lua API code are read-only.
 *
 * <p>Security invariants:
 * <ul>
 *   <li>{@link #serverApiKey} is intentionally package-private and only readable
 *       from within this module — it must never appear in any network packet.</li>
 *   <li>{@link #enabled} defaults to {@code false}; operators must explicitly opt-in.</li>
 * </ul>
 */
public final class AiConfig {

    private AiConfig() {}

    /** Master switch. When false, the {@code ai} Lua API throws immediately. */
    public static volatile boolean enabled = false;

    /** OpenAI-compatible endpoint URL (e.g. {@code https://my-proxy.example.com/v1}). */
    public static volatile String endpoint = "";

    /**
     * Bearer token for the proxy server.
     * SECURITY: never serialise this into any C2S or S2C packet.
     */
    public static volatile String serverApiKey = "";

    /**
     * Default model used when Lua code does not specify one.
     * Must be present in {@link #allowedModels}.
     */
    public static volatile String defaultModel = "deepseek-chat";

    /**
     * Whitelist of models the server makes available to players.
     * Each entry has an id (sent to the endpoint) and an optional display name shown to players.
     * Lua can query this list via {@code ai.models()} and choose a model via
     * {@code ai.ask(prompt, {model="gpt-4o"})}.
     *
     * <p>If empty, only {@link #defaultModel} is accepted.
     *
     * <p>Example config:
     * <pre>
     * [[ai.allowed_models]]
     *   id = "deepseek-chat"
     *   display_name = "DeepSeek Chat"
     *   max_context_tokens = 4096
     *
     * [[ai.allowed_models]]
     *   id = "gpt-4o-mini"
     *   display_name = "GPT-4o Mini"
     *   max_context_tokens = 2048
     * </pre>
     */
    public static volatile java.util.List<ModelEntry> allowedModels = java.util.List.of(
        new ModelEntry("deepseek-chat", "DeepSeek Chat", 4096)
    );

    /**
     * Describes a single model exposed to players.
     *
     * @param id               The model identifier forwarded to the API endpoint.
     * @param displayName      Human-readable name shown by {@code ai.models()} in Lua.
     * @param maxContextTokens Per-model context cap; overrides {@link AiConfig#maxContextTokens}
     *                         if non-zero. Zero means use the global limit.
     */
    public record ModelEntry(String id, String displayName, int maxContextTokens) {
        public ModelEntry {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Model id must not be blank");
            if (displayName == null || displayName.isBlank()) displayName = id;
            if (maxContextTokens < 0) throw new IllegalArgumentException("maxContextTokens must be >= 0");
        }

        /** Effective context token limit: model-specific if set, otherwise global. */
        public int effectiveContextTokens() {
            return maxContextTokens > 0 ? maxContextTokens : AiConfig.maxContextTokens;
        }
    }

    /**
     * System prompt injected as the FIRST message in every request.
     * Lua code cannot override or read this value.
     *
     * <p>The placeholder {@code {language}} is replaced at request time with the
     * effective response language (see {@link #defaultResponseLanguage}).
     */
    public static volatile String systemPrompt =
        "You are a helpful assistant inside the game Minecraft ComputerCraft. " +
        "Keep responses concise and appropriate for all ages. " +
        "Do not generate harmful, offensive, or adult content. " +
        "Always respond in {language}.";

    /**
     * Separate system prompt used when the player clicks the AI error-hint button.
     * The {@code {language}} placeholder is substituted identically.
     */
    public static volatile String errorAssistantPrompt =
        "You are a Lua debugging assistant for ComputerCraft (CC: Tweaked). " +
        "Explain the given runtime error simply and suggest a concrete fix. " +
        "Be concise. Always respond in {language}. " +
        "Do not generate code that could harm the server.";

    /**
     * Default language for AI responses.
     * Injected in place of {@code {language}} in system prompts.
     * Can be overridden per-request from Lua via {@code opts.language}.
     *
     * <p>Examples: {@code "Russian"}, {@code "English"}, {@code "the language the user writes in"}.
     */
    public static volatile String defaultResponseLanguage = "Russian";

    /**
     * Whether Lua programs are allowed to append additional context to the system prompt.
     * When enabled, the Lua {@code opts.context} string is appended to the server system prompt.
     * The context string is always sanitized and length-capped before use.
     *
     * <p>Disabled by default for security; enable on trusted servers.
     */
    public static volatile boolean allowAdditionalSystemContext = false;

    /**
     * Max characters for the {@code opts.context} string when
     * {@link #allowAdditionalSystemContext} is enabled.
     */
    public static volatile int maxContextStringChars = 500;

    /** Show the [?] AI error-hint button in the computer sidebar when an error is detected. */
    public static volatile boolean errorHintEnabled = false;

    /** Whether to enforce HTTPS for the endpoint URL (strongly recommended in production). */
    public static volatile boolean requireHttps = true;

    // ---- Per-player rate limits (sliding windows) ----

    /** Max tokens forwarded to the model per request (context window cap). */
    public static volatile int maxContextTokens = 4096;

    /** Max tokens the model may generate per response. */
    public static volatile int maxResponseTokens = 512;

    /** Max AI requests per player per minute (sliding window). */
    public static volatile int requestsPerMinute = 10;

    /** Max AI requests per player per hour (sliding window). */
    public static volatile int requestsPerHour = 60;

    /** Max AI requests per player per day (sliding window). */
    public static volatile int requestsPerDay = 500;

    /** Max simultaneous in-flight AI requests across ALL players combined. */
    public static volatile int maxGlobalConcurrent = 20;

    /** Max length (characters) of a single Lua message string. */
    public static volatile int maxMessageChars = 2000;

    /**
     * When {@code true}, players who hit a rate limit are silently queued (up to
     * {@link #queueTimeoutSeconds}). When {@code false}, they receive an immediate error.
     */
    public static volatile boolean queueOnLimit = false;

    /** How long (seconds) a queued request waits before timing out. */
    public static volatile int queueTimeoutSeconds = 30;

    // ---- Response validation (optional safety/quality gate) ----

    /**
     * Configuration for the optional validation model.
     *
     * <p>When {@link ValidationConfig#enabled} is {@code true}, every AI response is
     * passed to a second model which answers {@code true} (safe/correct) or {@code false}
     * (unsafe/incorrect). If {@code false}, the main model retries up to
     * {@link ValidationConfig#maxRetries} times. After all retries, the last response
     * is returned with a {@code ai_response_unvalidated} warning event.
     *
     * <p>This is disabled by default. Enabling it doubles the API cost per request.
     *
     * <p>Example config:
     * <pre>
     * [ai.validation]
     *   enabled = true
     *   model = "gpt-4o-mini"       # can be a smaller/cheaper model
     *   max_retries = 3
     *   timeout_seconds = 20
     *   prompt = "Review the following AI response for a Minecraft game assistant..."
     * </pre>
     */
    public static volatile ValidationConfig validation = new ValidationConfig();

    /**
     * Format the mod uses when calling the moderation/validation endpoint.
     *
     * <ul>
     *   <li>{@link #CHAT} — Sends a chat completion request with a true/false prompt.
     *       Works with any OpenAI-compatible chat endpoint. The prompt is configurable.
     *       Use this with custom moderation logic on your proxy.</li>
     *   <li>{@link #OPENAI_MODERATION} — Sends a request in OpenAI {@code /v1/moderations} format:
     *       {@code {"input": "..."}}, parses {@code results[0].flagged}.
     *       Use this when your proxy exposes a standard moderation endpoint
     *       (OpenAI, Mistral, or compatible).</li>
     * </ul>
     */
    public enum ModerationFormat {
        /** Universal chat-based true/false prompt. Works with any chat model. */
        CHAT,
        /** Standard {@code /v1/moderations} format. Parses {@code results[0].flagged}. */
        OPENAI_MODERATION
    }

    /**
     * Configuration for the optional moderation/validation pass.
     *
     * <p>The mod sends a request to <em>your proxy server</em> ({@link #endpoint}).
     * Your server decides what moderation service to use internally
     * (OpenAI Moderation, Mistral Moderation, a custom classifier, etc.).
     * The mod only needs to know the URL and the API key for your proxy.
     *
     * <p>Disabled by default ({@link #enabled} = false).
     * Enabling roughly doubles the API cost per player request.
     *
     * <p>Example config:
     * <pre>
     * [ai.moderation]
     *   enabled = true
     *   endpoint = "https://my-proxy.example.com"
     *   format = "openai_moderation"   # or "chat"
     *   max_retries = 3
     *   timeout_seconds = 15
     * </pre>
     */
    public static final class ValidationConfig {

        /** Whether moderation is active. Default: {@code false}. */
        public volatile boolean enabled = false;

        /**
         * Base URL of <strong>your</strong> moderation proxy server.
         * The mod appends the appropriate path based on {@link #format}:
         * <ul>
         *   <li>{@link ModerationFormat#CHAT} → {@code /chat/completions}</li>
         *   <li>{@link ModerationFormat#OPENAI_MODERATION} → {@code /moderations}</li>
         * </ul>
         * If blank, falls back to the main {@link AiConfig#endpoint}.
         */
        public volatile String endpoint = "";

        /**
         * API key for <strong>your</strong> moderation proxy.
         * SECURITY: never serialised into any network packet.
         * If blank, falls back to {@link AiConfig#serverApiKey}.
         */
        /** Moderation API Key (optional). */
        public String apiKey = "";

        /**
         * Request format the mod sends to your moderation endpoint.
         * Default: {@link ModerationFormat#CHAT} (works with any chat-compatible proxy).
         */
        public volatile ModerationFormat format = ModerationFormat.CHAT;

        /**
         * Prompt template used in {@link ModerationFormat#CHAT} mode.
         * The placeholder {@code {response}} is replaced with the AI response text.
         * The model must answer ONLY {@code true} (safe) or {@code false} (unsafe).
         * Not used in {@link ModerationFormat#OPENAI_MODERATION} mode.
         */
        public volatile String chatPrompt =
            "You are a safety and quality checker for a Minecraft ComputerCraft assistant. " +
            "Evaluate the following response. Reply ONLY with 'true' if it is safe, accurate, " +
            "and appropriate for all ages. Reply ONLY with 'false' if it is unsafe, offensive, " +
            "incorrect, or harmful. Do not explain.\n\nResponse:\n{response}";

        /**
         * Model id sent in {@link ModerationFormat#CHAT} requests.
         * Your proxy may ignore this and route to its own model.
         * Default: empty string (proxy decides).
         */
        public volatile String chatModel = "";

        /** Max retries if moderation returns false. Default: 3. */
        public volatile int maxRetries = 3;

        /** Timeout in seconds for each moderation call. Default: 15. */
        public volatile int timeoutSeconds = 15;

        /** Effective moderation endpoint URL (falls back to main endpoint). */
        public String effectiveEndpoint() {
            return (endpoint == null || endpoint.isBlank()) ? AiConfig.endpoint : endpoint;
        }

        /** Effective API key for the moderation endpoint. */
        public String effectiveApiKey() {
            return (apiKey == null || apiKey.isBlank()) ? AiConfig.getServerApiKey() : apiKey;
        }
    }

    /**
     * Interpolate the {@code {language}} placeholder in a system prompt string.
     *
     * @param prompt   The raw prompt template (may contain {@code {language}}).
     * @param language The effective language (from request options or {@link #defaultResponseLanguage}).
     * @return The prompt with the placeholder replaced.
     */
    public static String interpolatePrompt(String prompt, String language) {
        if (language == null || language.isBlank()) language = defaultResponseLanguage;
        return prompt.replace("{language}", language);
    }

    /**
     * Returns the model entry for the given id, or empty if it is not in the whitelist.
     */
    public static java.util.Optional<ModelEntry> findModel(String modelId) {
        if (modelId == null || modelId.isBlank()) return java.util.Optional.empty();
        return allowedModels.stream()
            .filter(m -> m.id().equalsIgnoreCase(modelId))
            .findFirst();
    }

    /**
     * Returns the default model entry.
     */
    public static ModelEntry getDefaultModel() {
        return allowedModels.stream()
            .filter(m -> m.id().equalsIgnoreCase(defaultModel))
            .findFirst()
            .orElseGet(() -> new ModelEntry(defaultModel, defaultModel, 0));
    }

    public static String getServerApiKey() {
        return serverApiKey;
    }

    public static void validate() {
        if (!enabled) return;

        if (endpoint.isBlank()) {
            throw new IllegalStateException("[AI] 'endpoint' must be set when AI is enabled.");
        }
        if (requireHttps && !endpoint.startsWith("https://")) {
            throw new IllegalStateException(
                "[AI] 'endpoint' must use HTTPS (got: " + endpoint + "). " +
                "Set 'allow_http_endpoint = true' to override."
            );
        }
        if (allowedModels.isEmpty()) {
            throw new IllegalStateException("[AI] 'allowed_models' must contain at least one entry.");
        }
        boolean defaultFound = allowedModels.stream().anyMatch(m -> m.id().equalsIgnoreCase(defaultModel));
        if (!defaultFound) {
            throw new IllegalStateException(
                "[AI] 'default_model' (" + defaultModel + ") is not present in 'allowed_models'."
            );
        }
        if (maxContextTokens < 64) {
            throw new IllegalStateException("[AI] 'max_context_tokens' must be >= 64.");
        }
        if (maxResponseTokens < 16) {
            throw new IllegalStateException("[AI] 'max_response_tokens' must be >= 16.");
        }
        if (validation.enabled && validation.maxRetries < 1) {
            throw new IllegalStateException("[AI] validation.max_retries must be >= 1.");
        }
        if (validation.enabled && validation.timeoutSeconds < 5) {
            throw new IllegalStateException("[AI] validation.timeout_seconds must be >= 5.");
        }
    }
}
