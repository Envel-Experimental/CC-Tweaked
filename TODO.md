# CC:Tweaked — Feature Architecture TODO

Target platform: **Fabric MC**
Architecture overview: `projects/ARCHITECTURE.md`

---

## Feature 1: Russian Language Support

### Problem Analysis

The current terminal pipeline has a hard ceiling at codepoint 255:

1. `Terminal.java` stores text in `TextBuffer` (a `char[]`) — Unicode-capable at the Java level.
2. `TermMethods.write()` accepts a Java `String` — UTF-16 internally, so Cyrillic arrives intact.
3. **The wall**: `FixedWidthFontRenderer.drawString()` line 125:
   `int index = text.charAt(i); if (index > 255) index = '?';`
4. The font atlas `term_font.png` is a 256x256 bitmap covering only codepoints 0-255 (Latin-1).
5. `TextBuffer.write(ByteBuffer, int)` interprets every byte as `& 0xFF` — so any multi-byte
   UTF-8 is corrupted **before** rendering even begins if data passes through the byte path.

**Conclusion**: Two independent problems must be solved together:
- Font atlas must cover Cyrillic (U+0400-U+04FF).
- The rendering pipeline must correctly handle codepoints > 255.

### Architecture Decision: Extended Font Atlas (Option A — recommended)

Extend `term_font.png` from 256x256 to **512x256**. Add Cyrillic glyphs (U+0400-U+04FF, ~128
chars) in the right half. Change the atlas layout so each row holds 32 chars instead of 16.
Adjust UV math in both `FixedWidthFontRenderer` and `DirectFixedWidthFontRenderer`.

Alternative (Option B) — second texture + shader switch — discarded: more complex, no benefit.

### Glyph Source

Do **not** require the user to supply images. Use Minecraft's own font engine:

1. At mod startup (`ClientModInitializer`), iterate Minecraft's `FontManager` and extract glyph
   bitmaps for each Cyrillic codepoint using `BakedGlyph` / `GlyphProvider`.
2. Blit extracted glyphs into a 512x256 `NativeImage` and upload it as a `DynamicTexture` under
   `computercraft:textures/gui/term_font.png` (replacing the static asset).
3. This runs once per client session and is fully deterministic — no manual font drawing needed.

Fallback: if Minecraft's font does not provide a glyph, draw an empty cell (not `?`).

### Terminal Internals Change

- `TextBuffer` stays as `char[]` — no change needed, already Unicode at the Java level.
- **Encoding contract**: paths that write a `String` into `TextBuffer` via `write(String, int)`
  are safe. Only the `ByteBuffer` path (from network packets) needs attention.
- `TerminalState` currently serialises raw bytes. Migrate to **length-prefixed UTF-8** with a
  version flag byte so the server can send Cyrillic and the client decodes correctly.
- Cursor advance in `TermMethods.write()`: `cursorX += text.length()`. For Cyrillic, each char
  is 1 Java char = 1 terminal cell. Correct as-is.

### Rendering Changes

| File | Change |
|---|---|
| `FixedWidthFontRenderer.java` | Remove `> 255` clamp; extend UV formula for columns 16-31 |
| `DirectFixedWidthFontRenderer.java` | Same (IMPORTANT: always kept in sync per existing comment) |
| `FONT_WIDTH` | Keep at 6px — glyphs auto-scaled to fit |
| Monitor VBO / shader path | Ensure `MonitorTextureBufferShader` also drops the 255 clamp |

### Encoding Path (full chain)

`Lua bytes -> Java String (UTF-8 decode in LuaValues) -> TextBuffer.write(String) -> char[] -> renderer (index now 0-511)`

Verify this in `LuaValues.decode()` — it must use `StandardCharsets.UTF_8`.

### Automated Verification (no manual game launches)

1. **Lua unit test** (`ComputerTestDelegate`): call `term.write("Привет")`, assert
   `terminal.getLine(0).toString()` equals `"Привет"` (not `"??????"`).
2. **Java render test**: mock `QuadEmitter` records every `(charIndex, x, y)` call from
   `drawString`. Assert `П` (codepoint 1055) produces UV in the extended range, not `'?'`.
3. **Font atlas test**: after dynamic atlas generation, read back pixel at the expected glyph
   coordinates and assert non-zero. Runs headless via LWJGL native stub.
4. **Game test** (Kotlin, Fabric game-test framework): place a computer, run Lua writing
   `"Привет мир"`, capture `TerminalState`, decode it, assert correct characters. CI-green
   required before merge.

### Files to Create / Modify

```
projects/common/src/main/resources/assets/computercraft/textures/gui/
  term_font.png                            [MODIFY — extend to 512x256]

projects/common/src/client/java/dan200/computercraft/client/
  font/CyrillicFontPatcher.java            [NEW — extract MC glyphs, upload atlas]
  render/text/FixedWidthFontRenderer.java  [MODIFY — remove clamp, fix UV]
  render/text/DirectFixedWidthFontRenderer.java  [MODIFY — same as above]

projects/common/src/main/java/dan200/computercraft/shared/computer/terminal/
  TerminalState.java                       [MODIFY — UTF-8 network encoding]

projects/core/src/main/resources/data/computercraft/lua/rom/apis/
  utf8_compat.lua                          [NEW — document encoding guarantees]

projects/core/src/test/java/dan200/computercraft/core/terminal/
  CyrillicTerminalTest.java                [NEW — automated Lua + render tests]
```

---

## Feature 2: AI API (OpenAI-Compatible Proxy)

### Overview

Expose an `ai` Lua global on every computer (when enabled), backed by the server operator's own
OpenAI-compatible proxy. All requests flow:

`Computer -> CC Server AI layer -> Operator proxy -> LLM`

No API key ever reaches the client. The server controls all access.

### Server Config (`computercraft-server.toml` — new `[ai]` section)

```toml
[ai]
  enabled = false
  endpoint = "https://your-proxy.example.com/v1"
  server_api_key = ""          # never sent to clients
  model = "deepseek-chat"

  system_prompt = """
    You are a helpful assistant inside the game Minecraft ComputerCraft.
    Keep responses concise and appropriate for all ages.
    Do not generate harmful, offensive, or adult content.
    Respond in the language the user writes in.
  """

  error_assistant_prompt = """
    You are a Lua debugging assistant for ComputerCraft (CC: Tweaked).
    Explain the given runtime error simply and suggest a fix.
    Be concise. Respond in the user's language.
    Do not generate code that could harm the server.
  """

  error_hint_enabled = false   # shows [?] button in computer sidebar

[ai.limits]
  max_context_tokens   = 4096   # truncate oldest messages to stay under this
  max_response_tokens  = 512
  requests_per_minute  = 10     # sliding window, per player UUID
  requests_per_hour    = 60
  requests_per_day     = 500
  max_global_concurrent = 20   # across all players simultaneously
  max_message_chars    = 2000   # per single Lua message string
  queue_on_limit       = false  # true = silently queue; false = immediate error
  queue_timeout_s      = 30
```

### Rate Limiter — `AiRateLimiter.java`

- `ConcurrentHashMap<UUID, PlayerLimitState>` — one entry per player.
- `PlayerLimitState`: three sliding windows (minute / hour / day) via `AtomicLong` timestamp +
  `AtomicInteger` counter pairs. CAS increment on hot path — no locks.
- Global concurrent counter: `AtomicInteger` shared across all players.
- Background eviction task (every 5 min via Fabric scheduler) removes stale entries to prevent
  memory leak.
- Returns typed `LimitResult` enum: `ALLOWED`, `RATE_LIMITED_MINUTE`, `RATE_LIMITED_HOUR`,
  `RATE_LIMITED_DAY`, `GLOBAL_CONCURRENT_LIMIT`.

### Request Pipeline — `AiRequestHandler.java`

- Runs on the existing CC Netty thread pool (`NetworkUtils`) — **no new threads created**.
- Builds OpenAI-compatible JSON: injected system prompt + conversation history + user message.
- **Security**: strips any `role: system` entries supplied by Lua — user cannot override prompt.
- Enforces `max_context_tokens` by dropping oldest non-system messages (word-count / 0.75
  heuristic — no tiktoken dep).
- Sends `Authorization: Bearer <server_api_key>` — key is server-only.
- v1: non-streaming JSON response only. v2: SSE streaming optional.
- On success: fires `ai_response` Lua event on requesting computer.
- On error: fires `ai_error` with human-readable message (Russian if the error source is known).

### Lua-Side `ai` Global (`rom/apis/ai.lua`)

```lua
-- ai.chat(messages, [options]) -> true | false, err
--   messages: { {role="user"|"assistant", content="..."}, ... }
--   options:  { timeout=30 }
--   Fires "ai_response" or "ai_error" event.

-- ai.ask(prompt, [options]) -> true | false, err
--   Convenience wrapper: single user message.

-- ai.await() -> text | nil, err
--   Blocks via os.pullEvent until ai_response or ai_error.

-- ai.explain_error(err_string) -> true | false
--   Sends to error-assistant endpoint.
--   Fires "ai_error_hint" event with the response.
```

Lua-side validation: `type(prompt) == "string"`, UTF-8 length check, no empty strings.
Server-side validation: repeat length + content checks (defense in depth).

### Java API Registration — `AiAPI.java`

Pattern mirrors `HTTPAPI.java` exactly:

1. Implement `ILuaAPI` in `projects/core/src/main/java/dan200/computercraft/core/apis/AiAPI.java`.
2. Register in `Environment.java` alongside other APIs.
3. Guard every method with `if (!AiConfig.enabled) throw new LuaException("AI is disabled")`.
4. `startup()` / `shutdown()` lifecycle methods manage per-computer ResourceGroup if needed.

### Error Hint Button

Requires `error_hint_enabled = true` AND `enabled = true`.

**Client detection**: scan last N terminal lines for a line whose colour buffer contains `e`
(red) and whose text matches `.*:\d+: .*` (Lua error pattern). Runs in `TerminalWidget` render
pass — no extra tick cost.

**On match**: show a small `[?]` button via existing `DynamicImageButton` in `ComputerSidebar`.

**On click**: send `AskAiErrorHintMessage` (C2S packet) with computer ID + error string.

**Server**: verify player is active user of that computer (`AbstractComputerMenu` ownership
check), check rate limits, call `AiRequestHandler`, stream result back as `AiHintResponseMessage`
(S2C packet).

**Client**: render hint in `AiHintOverlay` — a simple text box positioned near the sidebar,
dismissable with Escape or clicking outside.

### Fabric Integration

- Config: add `[ai]` section in `ConfigSpec.java` using the same `builder.push("ai")` pattern
  as `[http]`. Mirror fields through `AiConfig.java` (like `CoreConfig`).
- Networking: add both packets to `NetworkMessages.java` using the existing `MessageType` system.
- No changes to `forge` or `fabric-api` modules — everything lives in `core` and `common`.

### Security Checklist

- [ ] API key stored server-side only — never in any packet to client
- [ ] `role: system` stripped from Lua input server-side
- [ ] Rate limits enforced server-side — not bypassable from Lua
- [ ] `max_message_chars` enforced server-side (Lua validation is UX only)
- [ ] Player must be active user of computer to send AI packets
- [ ] Endpoint URL validated at config load (HTTPS enforced unless `allow_http_endpoint = true`)
- [ ] Error hint only available if both flags are true
- [ ] Default system prompt is kids-safe; operator must consciously weaken it
- [ ] No conversation history persisted server-side beyond the current request

### Automated Verification

1. **Unit — rate limiter** (`AiRateLimiterTest.java`): simulate 11 rapid calls from one UUID,
   assert 11th returns `RATE_LIMITED_MINUTE`. Test hour/day windows separately.
2. **Unit — request builder** (`AiRequestBuilderTest.java`): assert `role:system` stripping;
   assert token-count truncation removes oldest messages first, keeps system prompt.
3. **Unit — config** (`AiConfigTest.java`): load default config, assert `enabled = false`,
   all limit values match documented defaults.
4. **Integration — Lua API** (mcfly.lua test): mock HTTP backend returns fixed JSON, call
   `ai.ask("hello")`, `os.pullEvent("ai_response")`, assert response text matches.
5. **Game test** (Kotlin): place computer, inject mock endpoint via config, run Lua `ai.ask`,
   assert event fires within 5s timeout.

### Files to Create / Modify

```
projects/core/src/main/java/dan200/computercraft/core/
  AiConfig.java                                     [NEW]
  apis/AiAPI.java                                   [NEW]
  apis/AiRequestHandler.java                        [NEW]
  apis/AiRateLimiter.java                           [NEW]

projects/common/src/main/java/dan200/computercraft/shared/config/
  ConfigSpec.java                                   [MODIFY — add [ai] section]

projects/common/src/main/java/dan200/computercraft/shared/network/server/
  AskAiErrorHintMessage.java                        [NEW]

projects/common/src/main/java/dan200/computercraft/shared/network/client/
  AiHintResponseMessage.java                        [NEW]

projects/common/src/main/java/dan200/computercraft/shared/network/
  NetworkMessages.java                              [MODIFY — register packets]

projects/common/src/client/java/dan200/computercraft/client/gui/
  AbstractComputerScreen.java                       [MODIFY — error hint trigger]
  widgets/AiHintOverlay.java                        [NEW]

projects/core/src/main/resources/data/computercraft/lua/rom/apis/
  ai.lua                                            [NEW]

projects/core/src/test/java/dan200/computercraft/core/apis/
  AiRateLimiterTest.java                            [NEW]
  AiRequestBuilderTest.java                         [NEW]
  AiConfigTest.java                                 [NEW]
```

---

## Implementation Order

| Phase | Scope | Key deliverable |
|---|---|---|
| 1 | Russian font | Atlas + both renderers + TerminalState encoding + all tests green |
| 2 | AI config + rate limiter | `AiConfig`, `AiRateLimiter`, unit tests pass |
| 3 | AI request pipeline + Lua API | `AiRequestHandler`, `AiAPI`, `ai.lua`, integration test |
| 4 | Network packets + error hint button | Packets, sidebar button, `AiHintOverlay`, game test |
| 5 | Hardening | Security audit, stress test rate limiter, review all defaults |
