// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.apis.ai;

import dan200.computercraft.core.AiConfig;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.apis.http.NetworkUtils;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles the actual HTTP round-trip to the OpenAI-compatible endpoint.
 *
 * <p>Architecture:
 * <ol>
 *   <li>Builds a JSON body with system prompt + (optional) additional context + conversation history.</li>
 *   <li>Submits the request to {@link NetworkUtils#EXECUTOR} — no new threads created.</li>
 *   <li>On success, fires {@code ai_response} event on the computer.</li>
 *   <li>On failure, fires {@code ai_error} event with a human-readable message.</li>
 * </ol>
 *
 * <p>Security: the API key ({@link AiConfig#getServerApiKey()}) is set only in the
 * {@code Authorization} header and never forwarded to the client.
 */
public final class AiRequestHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AiRequestHandler.class);

    /** Global concurrent request counter — guards against DDoS via AI endpoint. */
    static final AtomicInteger GLOBAL_CONCURRENT = new AtomicInteger(0);

    private AiRequestHandler() {}

    /**
     * Dispatch an AI request asynchronously.
     * Returns immediately; the Lua {@code ai_response} / {@code ai_error} event is fired later.
     *
     * @param env      The API environment (used to queue events on the computer).
     * @param id       Request ID (matched in events).
     * @param messages Validated, sanitized conversation messages.
     * @param model    Resolved model entry.
     * @param options  Request options (temperature, max_tokens, etc.).
     */
    public static void dispatch(
        IAPIEnvironment env,
        int id,
        List<AiAPI.AiMessage> messages,
        AiConfig.ModelEntry model,
        AiAPI.RequestOptions options
    ) {
        // Check global concurrent limit before accepting the request.
        var concurrent = GLOBAL_CONCURRENT.incrementAndGet();
        if (concurrent > AiConfig.maxGlobalConcurrent) {
            GLOBAL_CONCURRENT.decrementAndGet();
            env.queueEvent(AiAPI.EVENT_ERROR, id, "Server AI request limit reached. Please try again later.");
            return;
        }

        NetworkUtils.EXECUTOR.execute(() -> {
            try {
                doRequest(env, id, messages, model, options);
            } finally {
                GLOBAL_CONCURRENT.decrementAndGet();
                AiRateLimiter.INSTANCE.release();
            }
        });
    }

    private static void doRequest(
        IAPIEnvironment env,
        int id,
        List<AiAPI.AiMessage> messages,
        AiConfig.ModelEntry model,
        AiAPI.RequestOptions options
    ) {
        var validation = AiConfig.validation;
        var maxRetries = validation.enabled ? validation.maxRetries : 1;

        String lastResponse = null;
        boolean validated = false;

        for (var attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                lastResponse = sendRawRequest(
                    AiConfig.endpoint + (AiConfig.endpoint.endsWith("/") ? "" : "/") + "chat/completions",
                    AiConfig.getServerApiKey(),
                    buildJsonBody(messages, model, options), 60
                );
            } catch (Exception e) {
                LOG.warn("[AI] Request {} attempt {} failed: {}", id, attempt, e.getMessage());
                env.queueEvent(AiAPI.EVENT_ERROR, id, humaniseException(e));
                return;
            }

            if (lastResponse == null) {
                env.queueEvent(AiAPI.EVENT_ERROR, id, "AI returned an empty response.");
                return;
            }

            if (!validation.enabled) {
                validated = true;
                break;
            }

            // Run validation pass.
            boolean passed = runValidation(validation, lastResponse);
            if (passed) {
                validated = true;
                break;
            }
            LOG.info("[AI] Request {} attempt {}/{} failed validation — retrying.", id, attempt, maxRetries);
        }

        if (validated) {
            env.queueEvent(AiAPI.EVENT_RESPONSE, id, lastResponse);
        } else {
            // All retries exhausted — return last response with a warning event.
            LOG.warn("[AI] Request {} exhausted {} validation retries — returning unvalidated response.", id, maxRetries);
            env.queueEvent("ai_response_unvalidated", id, lastResponse);
            env.queueEvent(AiAPI.EVENT_RESPONSE, id, lastResponse);
        }
    }

    /**
     * Build the OpenAI-compatible JSON request body.
     *
     * <p>Message order:
     * <ol>
     *   <li>Server system prompt (language-interpolated, always first).</li>
     *   <li>Additional system context from Lua {@code opts.system_context} (if server allows it).</li>
     *   <li>Legacy {@code opts.context} appended to the system message (back-compat).</li>
     *   <li>Conversation history (user/assistant only).</li>
     * </ol>
     */
    static String buildJsonBody(
        List<AiAPI.AiMessage> messages,
        AiConfig.ModelEntry model,
        AiAPI.RequestOptions options
    ) {
        var sb = new StringBuilder();
        sb.append("{\"model\":\"").append(jsonEscape(model.id())).append("\",");
        sb.append("\"max_tokens\":").append(options.maxTokens()).append(",");
        sb.append("\"temperature\":").append(String.format("%.2f", options.temperature())).append(",");
        sb.append("\"messages\":[");

        // 1. Server system prompt — language interpolated, always injected.
        var rawPrompt = options.useErrorPrompt() ? AiConfig.errorAssistantPrompt : AiConfig.systemPrompt;
        var systemPrompt = AiConfig.interpolatePrompt(rawPrompt, options.language());

        // 2. Append Lua-provided system_context (if allowed by server).
        var sysCtx = options.additionalSystemContext();
        if (sysCtx != null && !sysCtx.isBlank()) {
            systemPrompt = systemPrompt + "\n\nAdditional context provided by the program: " + sysCtx;
        }
        // 3. Legacy opts.context field (backward compat).
        var legacyCtx = options.additionalContext();
        if (legacyCtx != null && !legacyCtx.isBlank()) {
            systemPrompt = systemPrompt + "\n\nContext: " + legacyCtx;
        }

        appendMessage(sb, "system", systemPrompt);

        // 4. Conversation history.
        var contextLimit = model.effectiveContextTokens();
        var truncated = truncateToContextWindow(messages, contextLimit);
        for (var msg : truncated) {
            sb.append(",");
            appendMessage(sb, msg.role(), msg.content());
        }

        sb.append("]}");
        return sb.toString();
    }

    /**
     * Send a synchronous HTTP POST to a URL and return the extracted response text.
     * Used for main chat requests and both moderation format types.
     *
     * @param url            Full URL to POST to.
     * @param apiKey         Bearer token for Authorization header.
     * @param jsonBody       Raw JSON request body.
     * @param timeoutSeconds Request timeout.
     * @return Extracted text content from the response.
     */
    private static String sendRawRequest(String url, String apiKey, String jsonBody, int timeoutSeconds) throws Exception {
        var uri = new java.net.URI(url);

        var request = java.net.http.HttpRequest.newBuilder()
            .uri(uri)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Authorization", "Bearer " + apiKey)
            .header("Accept", "application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
            .build();

        var httpClient = java.net.http.HttpClient.newBuilder()
            .executor(NetworkUtils.EXECUTOR)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

        var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new java.io.IOException(humaniseHttpError(response.statusCode()));
        }
        return extractContent(response.body());
    }

    /**
     * Run a moderation/validation check against the configured proxy endpoint.
     *
     * <p>Supports two formats depending on {@link AiConfig.ValidationConfig#format}:
     * <ul>
     *   <li>{@code CHAT} — sends a chat completion request with a true/false prompt to
     *       {@code <endpoint>/chat/completions}. Your proxy routes this to whatever model it wants.</li>
     *   <li>{@code OPENAI_MODERATION} — sends {@code {"input":"..."}} to {@code <endpoint>/moderations}
     *       and parses {@code results[0].flagged}. Returns {@code true} (pass) if NOT flagged.</li>
     * </ul>
     *
     * <p>On any network/timeout error, defaults to {@code true} (pass) to avoid blocking users.
     *
     * @param validation The moderation configuration.
     * @param response   The candidate AI response text to validate.
     * @return {@code true} if the response passes moderation, {@code false} if rejected.
     */
    private static boolean runValidation(AiConfig.ValidationConfig validation, String response) {
        try {
            return switch (validation.format) {
                case CHAT -> runChatModeration(validation, response);
                case OPENAI_MODERATION -> runOpenAiModeration(validation, response);
            };
        } catch (Exception e) {
            LOG.warn("[AI] Moderation call failed: {} — treating as passed to avoid blocking users.", e.getMessage());
            return true; // fail-open: never block users due to moderation infrastructure issues
        }
    }

    /** {@link AiConfig.ModerationFormat#CHAT}: sends a chat completion with a true/false prompt. */
    private static boolean runChatModeration(AiConfig.ValidationConfig validation, String response) throws Exception {
        var prompt = validation.chatPrompt.replace("{response}", jsonEscape(response));
        var modelPart = validation.chatModel.isBlank() ? "" : "\"model\":\"" + jsonEscape(validation.chatModel) + "\",";
        var body = "{" + modelPart +
            "\"max_tokens\":5," +
            "\"temperature\":0.0," +
            "\"messages\":[{\"role\":\"user\",\"content\":\"" + jsonEscape(prompt) + "\"}]}";

        var endpoint = validation.effectiveEndpoint();
        if (!endpoint.endsWith("/")) endpoint += "/";
        var result = sendRawRequest(endpoint + "chat/completions", validation.effectiveApiKey(), body, validation.timeoutSeconds);

        if (result == null) return false;
        var trimmed = result.strip().toLowerCase(java.util.Locale.ROOT);
        return trimmed.startsWith("true");
    }

    /**
     * {@link AiConfig.ModerationFormat#OPENAI_MODERATION}: sends a standard {@code /moderations} request.
     * Returns {@code true} (pass) when {@code results[0].flagged == false}.
     */
    private static boolean runOpenAiModeration(AiConfig.ValidationConfig validation, String response) throws Exception {
        var body = "{\"input\":\"" + jsonEscape(response) + "\"}";

        var endpoint = validation.effectiveEndpoint();
        if (!endpoint.endsWith("/")) endpoint += "/";
        var rawJson = sendRawRequest(endpoint + "moderations", validation.effectiveApiKey(), body, validation.timeoutSeconds);

        if (rawJson == null || rawJson.isBlank()) return false;
        // Parse "flagged": true/false from results[0]
        var flaggedIdx = rawJson.indexOf("\"flagged\":");
        if (flaggedIdx < 0) {
            LOG.warn("[AI] Moderation response missing 'flagged' field — treating as passed.");
            return true;
        }
        var afterFlagged = rawJson.substring(flaggedIdx + 10).stripLeading();
        // If flagged = true → BLOCKED (return false). If flagged = false → PASS (return true).
        return afterFlagged.startsWith("false");
    }

    private static void appendMessage(StringBuilder sb, String role, String content) {
        sb.append("{\"role\":\"").append(jsonEscape(role)).append("\",");
        sb.append("\"content\":\"").append(jsonEscape(content)).append("\"}");
    }

    /**
     * Remove oldest non-system messages until estimated token count is within the context window.
     * Estimation: tokens ≈ characters / 3.5 (conservative for mixed language content).
     */
    static List<AiAPI.AiMessage> truncateToContextWindow(List<AiAPI.AiMessage> messages, int maxTokens) {
        var list = new java.util.ArrayDeque<>(messages);
        while (!list.isEmpty()) {
            int total = list.stream().mapToInt(m -> (int) (m.content().length() / 3.5)).sum();
            if (total <= maxTokens) break;
            list.pollFirst(); // remove oldest
        }
        return new java.util.ArrayList<>(list);
    }

    /** Extract the text content from a successful OpenAI-compatible response JSON. */
    private static String extractContent(String json) {
        try {
            // Minimal JSON extraction — avoid pulling in a full JSON library dependency.
            var marker = "\"content\":\"";
            var start = json.indexOf(marker);
            if (start < 0) return "(no response)";
            start += marker.length();
            var end = json.indexOf('"', start);
            while (end > 0 && json.charAt(end - 1) == '\\') {
                end = json.indexOf('"', end + 1);
            }
            if (end < 0) return json.substring(start);
            return json.substring(start, end)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        } catch (Exception e) {
            return "(parse error)";
        }
    }

    private static String humaniseHttpError(int status) {
        return switch (status) {
            case 400 -> "Invalid request sent to AI endpoint (bad request).";
            case 401 -> "AI endpoint rejected the server API key (unauthorized).";
            case 403 -> "Access denied by AI endpoint (forbidden).";
            case 429 -> "AI endpoint rate limit reached. Try again later.";
            case 500, 502, 503 -> "AI service is temporarily unavailable. Try again later.";
            default -> "AI endpoint returned HTTP " + status + ".";
        };
    }

    private static String humaniseException(Throwable ex) {
        var cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof java.net.ConnectException) return "Could not connect to AI endpoint.";
        if (cause instanceof java.util.concurrent.TimeoutException) return "AI request timed out.";
        return "AI request failed: " + cause.getClass().getSimpleName();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
