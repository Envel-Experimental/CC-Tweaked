# AI API — Proxy Server Integration Guide

This document describes how the CC: Tweaked AI feature communicates with your proxy server, what formats are expected, and how to implement a compatible backend.

---

## Architecture Overview

```
┌────────────────────────────────────────┐
│           Minecraft Server             │
│                                        │
│   Player Lua Program                   │
│       └── ai.ask("Hello")             │
│             │                          │
│   AiAPI.java (validates, rate-limits)  │
│             │                          │
│   AiRequestHandler.java               │
│       ├── POST /chat/completions  ──────────────► Your Proxy Server
│       └── POST /moderations (opt) ─────────────► Your Proxy Server
└────────────────────────────────────────┘        (handles routing to
                                                   any AI provider)
```

**The mod never calls any AI provider directly.** It only talks to *your* proxy server. Your server is responsible for:
- Authentication with the actual AI provider
- Model routing
- Rate limiting at the provider level
- Cost management

---

## Configuration Reference

All settings live in `computercraft-server.toml` (or equivalent for your platform).

```toml
[ai]
  enabled = true

  # Base URL of YOUR proxy server (no trailing slash needed).
  endpoint = "https://my-proxy.example.com/v1"

  # Bearer token for your proxy. Never exposed to players.
  api_key = "sk-my-secret-key"

  # Default language for all AI responses.
  # Injected into system prompts via {language} placeholder.
  default_response_language = "Russian"

  # Whether Lua code can add extra system context via opts.system_context.
  allow_additional_system_context = false
  max_context_string_chars = 500

  # Default model id. Must be in allowed_models list.
  default_model = "deepseek-chat"

  [[ai.allowed_models]]
    id = "deepseek-chat"
    display_name = "DeepSeek Chat"
    max_context_tokens = 4096

  [[ai.allowed_models]]
    id = "gpt-4o-mini"
    display_name = "GPT-4o Mini"
    max_context_tokens = 2048

  # System prompt. {language} is replaced at request time.
  system_prompt = """
You are a helpful assistant inside the game Minecraft ComputerCraft.
Keep responses concise and appropriate for all ages.
Do not generate harmful, offensive, or adult content.
Always respond in {language}.
"""

  [ai.moderation]
    enabled = false

    # URL of your moderation proxy (can be the same server).
    # Falls back to ai.endpoint if blank.
    endpoint = "https://my-proxy.example.com/v1"

    # Format the mod uses when calling your moderation endpoint.
    # "chat"             — OpenAI-compatible chat completion with true/false prompt
    # "openai_moderation"— Standard /moderations endpoint format
    format = "chat"

    max_retries = 3
    timeout_seconds = 15
```

---

## Main Chat Endpoint

The mod sends a standard **OpenAI-compatible chat completion** request.

### Request

```
POST {endpoint}/chat/completions
Content-Type: application/json
Authorization: Bearer {api_key}
```

```json
{
  "model": "deepseek-chat",
  "max_tokens": 512,
  "temperature": 1.0,
  "messages": [
    {
      "role": "system",
      "content": "You are a helpful assistant inside the game Minecraft ComputerCraft. Always respond in Russian."
    },
    {
      "role": "user",
      "content": "What is 2+2?"
    }
  ]
}
```

> **Note:** The `system` message is always injected first by the mod. Players cannot override or read it.

### Expected Response

Any OpenAI-compatible response. The mod extracts `choices[0].message.content`:

```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "2 + 2 = 4."
      }
    }
  ]
}
```

---

## Moderation Endpoint (optional)

When `ai.moderation.enabled = true`, the mod sends a separate check request **after** generating a response, before delivering it to the player. If your server rejects the response, the mod retries the main request up to `max_retries` times.

Two formats are supported:

---

### Format: `chat`

The mod sends a **chat completion** request asking your server to judge the response.

```
POST {moderation.endpoint}/chat/completions
Content-Type: application/json
Authorization: Bearer {moderation.api_key}
```

```json
{
  "max_tokens": 5,
  "temperature": 0.0,
  "messages": [
    {
      "role": "user",
      "content": "You are a safety checker...\n\nResponse:\nThis is the AI response text to evaluate."
    }
  ]
}
```

> The `model` field is included only if `ai.moderation.chat_model` is non-empty. Your proxy can ignore it and use any model it chooses.

**Your server must respond with:**

```json
{
  "choices": [
    {
      "message": {
        "content": "true"
      }
    }
  ]
}
```

- `"true"` → response is **safe**, deliver to player
- `"false"` → response is **unsafe**, retry main request

The mod only reads the first word of `content` (case-insensitive). Anything starting with `true` passes.

---

### Format: `openai_moderation`

The mod sends a request in standard OpenAI Moderation API format.

```
POST {moderation.endpoint}/moderations
Content-Type: application/json
Authorization: Bearer {moderation.api_key}
```

```json
{
  "input": "This is the AI response text to evaluate."
}
```

**Your server must respond with an OpenAI-compatible moderation result:**

```json
{
  "results": [
    {
      "flagged": false,
      "categories": { ... },
      "category_scores": { ... }
    }
  ]
}
```

- `"flagged": false` → response is **safe**, deliver to player
- `"flagged": true` → response is **unsafe**, retry main request

This format is compatible with:
- [OpenAI Moderation API](https://platform.openai.com/docs/api-reference/moderations)
- [Mistral Moderation API](https://docs.mistral.ai/api/#tag/moderations) (via adapter)
- Any custom classifier that returns the same schema

---

## Fail-Open Behavior

If the moderation endpoint is unreachable, times out, or returns an unexpected format, the mod **passes the response through** rather than blocking the player. This prevents moderation infrastructure issues from making the AI feature unusable.

Log output on moderation failure:
```
[AI] Moderation call failed: <reason> — treating as passed to avoid blocking users.
```

---

## Retry Behavior

```
Attempt 1: generate response
  └── moderation check → false (rejected)
Attempt 2: generate response
  └── moderation check → false (rejected)
Attempt 3: generate response (last attempt)
  └── moderation check → false (rejected)
    → fires ai_response_unvalidated event (for server-side logging)
    → fires ai_response event with the last response anyway
```

The `ai_response_unvalidated` event is intended for server-side logging only. Players only see the normal `ai_response` event.

---

## Events Reference

| Event | Arguments | Description |
|---|---|---|
| `ai_response` | `request_id: number, text: string` | AI response delivered to Lua |
| `ai_error` | `request_id: number, reason: string` | Request failed (network, rate limit, etc.) |
| `ai_response_unvalidated` | `request_id: number, text: string` | Response passed to player despite failing all moderation retries |

---

## Security Notes

- **API keys** are stored server-side only. They are never included in any client-bound network packet.
- The `system` role is stripped from all player-provided messages before forwarding.
- The server system prompt cannot be read or overridden by Lua programs.
- `opts.system_context` (additional context injection) is disabled by default (`allow_additional_system_context = false`).
- All player-provided strings are sanitized (control characters stripped) and length-capped before use.

---

## Lua API Quick Reference

```lua
local ai = require("ai")

-- List models the server allows
for _, m in ipairs(ai.models()) do
  print(m.id, m.display_name, m.max_context_tokens)
end

-- Simple blocking question (default language from server config)
local reply, err = ai.ask("Что такое ComputerCraft?")

-- Override language per-request
local reply = ai.ask("What is ComputerCraft?", { language = "English" })

-- Override model
local reply = ai.ask("Hello", { model = "gpt-4o-mini", temperature = 0.7 })

-- Add extra system context (requires allow_additional_system_context = true on server)
local reply = ai.ask("Help me debug this", {
  system_context = "The user is writing a turtle mining program."
})

-- Multi-turn conversation
local conv = ai.conversation({ model = "deepseek-chat", temperature = 0.8 })
conv:say("You are helping me write a Lua program.")
print(conv:reply())
conv:say("How do I iterate over a table?")
print(conv:reply())

-- Async style
local id = ai.askAsync("Tell me a joke")
-- ... do other work ...
local ok, text = ai.awaitId(id, 10)

-- Explain a Lua error (requires error_hint_enabled = true on server)
local ok, err = pcall(badFunction)
if not ok then print(ai.explainError(err)) end
```
