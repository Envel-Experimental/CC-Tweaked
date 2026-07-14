// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.apis.ai;

import dan200.computercraft.api.lua.*;
import dan200.computercraft.core.AiConfig;
import dan200.computercraft.core.apis.IAPIEnvironment;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code ai} API — lets ComputerCraft programs call an OpenAI-compatible language model.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * local ai = require("ai")
 *
 * -- List models the server allows
 * for _, m in ipairs(ai.models()) do
 *   print(m.id, "-", m.display_name)
 * end
 *
 * -- Simple one-shot question
 * local id = ai.ask("What is 2+2?")
 * local event, reply_id, text = os.pullEvent("ai_response")
 * print(text)
 *
 * -- Multi-turn chat with a specific model and temperature
 * local id = ai.chat({
 *   {role="user", content="Hello!"},
 *   {role="assistant", content="Hi there!"},
 *   {role="user", content="What can you do?"},
 * }, { model="gpt-4o-mini", temperature=0.7 })
 * local _, _, text = os.pullEvent("ai_response")
 * print(text)
 *
 * -- Await helper (blocks until response or timeout)
 * local ok, text = ai.await(id, 10)
 * if ok then print(text) else print("timed out") end
 * }</pre>
 *
 * <h2>Events fired</h2>
 * <ul>
 *   <li>{@code ai_response} — {@code (string event, int request_id, string text)}</li>
 *   <li>{@code ai_error}    — {@code (string event, int request_id, string reason)}</li>
 * </ul>
 *
 * @cc.module ai
 */
public class AiAPI implements ILuaAPI {

    public static final String EVENT_RESPONSE = "ai_response";
    public static final String EVENT_ERROR    = "ai_error";

    private final IAPIEnvironment env;
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    /**
     * Stable per-computer UUID derived from the computer ID.
     * Used as the rate-limiter key. On servers with the network D-layer,
     * this will be replaced with the actual player UUID from the server.
     */
    private final java.util.UUID rateLimitKey;

    public AiAPI(IAPIEnvironment env) {
        this.env = env;
        this.rateLimitKey = java.util.UUID.nameUUIDFromBytes(
            ("cc-computer-" + env.getComputerID()).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    @Override
    public String[] getNames() {
        return new String[]{"ai"};
    }

    private void requireEnabled() throws LuaException {
        if (!AiConfig.enabled) throw new LuaException("AI API is disabled on this server");
    }

    /**
     * Returns the list of AI models available on this server.
     * Each entry is a table with the following fields:
     * <ul>
     *   <li>{@code id}                — model identifier to pass in options</li>
     *   <li>{@code display_name}      — human-readable name</li>
     *   <li>{@code max_context_tokens}— effective context window size</li>
     * </ul>
     *
     * @return List of model descriptor tables.
     * @throws LuaException If the AI API is disabled.
     * @cc.treturn { {id=string, display_name=string, max_context_tokens=number}... } Model list.
     */
    @LuaFunction
    public final List<Map<String, Object>> models() throws LuaException {
        requireEnabled();
        var result = new ArrayList<Map<String, Object>>(AiConfig.allowedModels.size());
        for (var m : AiConfig.allowedModels) {
            result.add(Map.of(
                "id", m.id(),
                "display_name", m.displayName(),
                "max_context_tokens", m.effectiveContextTokens()
            ));
        }
        return result;
    }

    /**
     * Send a single-turn question to the AI and return a request ID.
     * The response arrives asynchronously as an {@code ai_response} or {@code ai_error} event.
     *
     * <h2>Options</h2>
     * <ul>
     *   <li>{@code model}       — model id string (must be in server's allowed list)</li>
     *   <li>{@code temperature} — float 0.0–2.0, controls randomness (default 1.0)</li>
     *   <li>{@code max_tokens}  — override per-request response token limit</li>
     *   <li>{@code context}     — additional context string appended to the system prompt</li>
     * </ul>
     *
     * @param args Arguments: {@code prompt: string [, options: table]}
     * @return Request ID (number) used to match {@code ai_response}/{@code ai_error} events.
     * @throws LuaException If disabled, prompt is empty, or options are invalid.
     * @cc.tparam string prompt The user's question.
     * @cc.tparam[opt] table options Request options (model, temperature, max_tokens, context).
     * @cc.treturn number Request ID.
     */
    @LuaFunction
    public final int ask(IArguments args) throws LuaException {
        requireEnabled();
        var prompt = args.getString(0);
        if (prompt.isBlank()) throw new LuaException("bad argument #1: prompt must not be empty");

        var options = args.count() > 1 ? parseOptions(args.getTable(1)) : RequestOptions.defaults();

        var messages = List.of(new AiMessage("user", prompt));
        return dispatchRequest(messages, options);
    }

    /**
     * Send a multi-turn conversation to the AI.
     * {@code messages} is an array of tables, each with {@code role} and {@code content} fields.
     * Valid roles: {@code "user"}, {@code "assistant"}.
     * The {@code "system"} role is silently stripped — the server controls the system prompt.
     *
     * @param args Arguments: {@code messages: table [, options: table]}
     * @return Request ID.
     * @throws LuaException If disabled, messages invalid, or options invalid.
     * @cc.tparam { {role=string, content=string}... } messages Conversation history.
     * @cc.tparam[opt] table options Request options.
     * @cc.treturn number Request ID.
     */
    @LuaFunction
    public final int chat(IArguments args) throws LuaException {
        requireEnabled();
        var rawMessages = args.getTable(0);
        var options = args.count() > 1 ? parseOptions(args.getTable(1)) : RequestOptions.defaults();

        var messages = parseMessages(rawMessages);
        if (messages.isEmpty()) throw new LuaException("bad argument #1: messages table is empty");

        return dispatchRequest(messages, options);
    }

    /**
     * Send an AI request to explain a ComputerCraft Lua error.
     * Uses the server-configured {@code error_assistant_prompt} as the system prompt.
     *
     * @param errorString The error string from {@code pcall} or similar.
     * @return Request ID.
     * @throws LuaException If disabled or error string is empty.
     * @cc.tparam string error The Lua error string to explain.
     * @cc.treturn number Request ID.
     */
    @LuaFunction
    public final int explainError(String errorString) throws LuaException {
        requireEnabled();
        if (!AiConfig.errorHintEnabled) throw new LuaException("AI error hints are disabled on this server");
        if (errorString == null || errorString.isBlank()) throw new LuaException("bad argument #1: error string must not be empty");

        var sanitized = sanitize(errorString, AiConfig.maxMessageChars);
        var options = new RequestOptions("", 1.0, AiConfig.maxResponseTokens, "",
            AiConfig.defaultResponseLanguage, "", true);
        var messages = List.of(new AiMessage("user", sanitized));
        return dispatchRequest(messages, options);
    }

    /**
     * Block until an {@code ai_response} or {@code ai_error} event arrives for the given
     * request ID, or until the timeout elapses.
     *
     * <pre>{@code
     * local id = ai.ask("Hello")
     * local ok, text = ai.await(id, 10)
     * if ok then print(text) else print("Error or timeout:", text) end
     * }</pre>
     *
     * @param requestId The request ID returned by {@link #ask} or {@link #chat}.
     * @param timeout   Max seconds to wait (default 30).
     * @return {@code true, response_text} on success; {@code false, error_reason} on failure/timeout.
     * @throws LuaException Never — timeouts return {@code false} instead.
     * @cc.tparam number request_id Request ID to wait for.
     * @cc.tparam[opt] number timeout Seconds to wait (default 30, max 120).
     * @cc.treturn boolean Success flag.
     * @cc.treturn string Response text or error reason.
     */
    @LuaFunction
    public final Object[] await(IArguments args) throws LuaException {
        // This is a yielding function — we instruct the Lua machine to yield and pull events.
        // The actual loop is implemented in the Lua ROM api (rom/apis/ai.lua) for simplicity;
        // this Java method is the lightweight Java-side entry that validates args.
        requireEnabled();
        var requestId = (int) args.getDouble(0);
        var timeout = args.count() > 1 ? Math.min(args.getDouble(1), 120.0) : 30.0;
        if (timeout <= 0) throw new LuaException("bad argument #2: timeout must be > 0");

        // Return a sentinel that tells the Lua wrapper to start yielding.
        // The actual blocking await is done in ai.lua using os.pullEvent in a loop.
        return new Object[]{"__await_sentinel__", requestId, timeout};
    }

    /**
     * Return whether the AI API is enabled and configured on this server.
     *
     * @return {@code true} if the API is usable.
     * @cc.treturn boolean Whether AI is enabled.
     */
    @LuaFunction
    public final boolean isEnabled() {
        return AiConfig.enabled;
    }

    /**
     * Return the default model id for this server.
     *
     * @return Default model id string.
     * @throws LuaException If AI is disabled.
     * @cc.treturn string Default model id.
     */
    @LuaFunction
    public final String defaultModel() throws LuaException {
        requireEnabled();
        return AiConfig.defaultModel;
    }

    // ---- Internal dispatch ----

    private int dispatchRequest(List<AiMessage> messages, RequestOptions options) throws LuaException {
        // Rate limit check — runs before any async work.
        var limitResult = AiRateLimiter.INSTANCE.check(rateLimitKey);
        if (limitResult != AiRateLimiter.LimitResult.ALLOWED) {
            throw new LuaException(limitResult.errorMessage());
        }

        // Validate model selection.
        var model = options.useErrorPrompt()
            ? AiConfig.getDefaultModel()
            : resolveModel(options.modelId());

        // Sanitize each message content.
        var sanitized = new ArrayList<AiMessage>(messages.size());
        for (var msg : messages) {
            sanitized.add(new AiMessage(msg.role(), sanitize(msg.content(), AiConfig.maxMessageChars)));
        }

        var id = requestCounter.incrementAndGet();
        // Dispatch asynchronously; the handler must call AiRateLimiter.INSTANCE.release() on finish.
        AiRequestHandler.dispatch(env, id, sanitized, model, options);

        return id;
    }

    private AiConfig.ModelEntry resolveModel(String modelId) throws LuaException {
        if (modelId == null || modelId.isBlank()) {
            return AiConfig.getDefaultModel();
        }
        return AiConfig.findModel(modelId).orElseThrow(() ->
            new LuaException("Unknown model '" + modelId + "'. Use ai.models() to see available models.")
        );
    }

    private static List<AiMessage> parseMessages(Map<?, ?> raw) throws LuaException {
        var result = new ArrayList<AiMessage>();
        for (var entry : raw.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> msgTable)) {
                throw new LuaException("bad argument #1: each message must be a table");
            }
            var role = getString(msgTable, "role");
            var content = getString(msgTable, "content");

            if (role == null || role.isBlank()) throw new LuaException("bad argument #1: message missing 'role'");
            if (content == null || content.isBlank()) throw new LuaException("bad argument #1: message missing 'content'");

            // SECURITY: strip system role — server controls system prompt
            if ("system".equalsIgnoreCase(role)) continue;

            if (!"user".equalsIgnoreCase(role) && !"assistant".equalsIgnoreCase(role)) {
                throw new LuaException("bad argument #1: unknown role '" + role + "' (expected 'user' or 'assistant')");
            }
            result.add(new AiMessage(role.toLowerCase(Locale.ROOT), content));
        }
        return result;
    }

    private static RequestOptions parseOptions(Map<?, ?> raw) throws LuaException {
        var modelId      = getStringOpt(raw, "model", "");
        var temperature  = getDoubleOpt(raw, "temperature", 1.0);
        var maxTokens    = (int) getDoubleOpt(raw, "max_tokens", AiConfig.maxResponseTokens);
        var context      = getStringOpt(raw, "context", "");        // legacy / short additional context
        var language     = getStringOpt(raw, "language", AiConfig.defaultResponseLanguage);
        var sysContext   = getStringOpt(raw, "system_context", ""); // additional system prompt fragment

        if (temperature < 0.0 || temperature > 2.0) {
            throw new LuaException("bad option 'temperature': must be between 0.0 and 2.0");
        }
        if (maxTokens < 1 || maxTokens > AiConfig.maxResponseTokens) {
            throw new LuaException("bad option 'max_tokens': must be between 1 and " + AiConfig.maxResponseTokens);
        }
        if (language != null && language.length() > 64) {
            throw new LuaException("bad option 'language': max 64 characters");
        }

        // system_context is only honoured if the server permits it.
        if (!sysContext.isBlank() && !AiConfig.allowAdditionalSystemContext) {
            throw new LuaException("bad option 'system_context': additional system context is disabled on this server");
        }
        int sysContextLimit = AiConfig.maxContextStringChars;
        if (sysContext.length() > sysContextLimit) {
            throw new LuaException("bad option 'system_context': max " + sysContextLimit + " characters");
        }
        // Legacy context field — same rules.
        if (!context.isBlank() && !AiConfig.allowAdditionalSystemContext) {
            context = ""; // silently ignore if disabled, no error (backward compat)
        }
        if (context.length() > sysContextLimit) {
            throw new LuaException("bad option 'context': max " + sysContextLimit + " characters");
        }

        return new RequestOptions(modelId, temperature, maxTokens, context, language, sysContext, false);
    }

    private static String sanitize(String s, int maxLen) {
        if (s == null) return "";
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", ""); // strip control chars
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private static String getString(Map<?, ?> m, String key) {
        var v = m.get(key);
        return v instanceof String s ? s : null;
    }

    private static String getStringOpt(Map<?, ?> m, String key, String def) {
        var v = m.get(key);
        return v instanceof String s ? s : def;
    }

    private static double getDoubleOpt(Map<?, ?> m, String key, double def) throws LuaException {
        var v = m.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.doubleValue();
        throw new LuaException("bad option '" + key + "': expected number, got " + LuaValues.getType(v));
    }

    /** Immutable representation of a single chat message. */
    public record AiMessage(String role, String content) {}

    /** Validated request options extracted from the Lua options table. */
    public record RequestOptions(
        String modelId,
        double temperature,
        int maxTokens,
        String additionalContext,
        String language,
        String additionalSystemContext,
        boolean useErrorPrompt
    ) {
        public static RequestOptions defaults() {
            return new RequestOptions("", 1.0, AiConfig.maxResponseTokens, "",
                AiConfig.defaultResponseLanguage, "", false);
        }
    }
}
