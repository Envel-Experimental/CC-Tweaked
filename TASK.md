# TASK.md — CC:Tweaked Feature Implementation

> Architecture details: see `TODO.md`
> Platform: **Fabric MC**

---

## Phase 1 — Russian Language Support

### 1.1 Font Atlas
- [ ] Extend `term_font.png` from 256×256 to 512×256
- [ ] Populate right half with Cyrillic glyphs U+0400–U+04FF (32 chars per row layout)

### 1.2 Dynamic Glyph Extraction (Client)
- [ ] Create `CyrillicFontPatcher.java` in `client/font/`
- [ ] Hook `ClientModInitializer` (Fabric) to run patcher on startup
- [ ] Extract Cyrillic bitmaps from Minecraft `FontManager` / `BakedGlyph`
- [ ] Blit into 512×256 `NativeImage`, upload as `DynamicTexture` over static `term_font.png`
- [ ] Fallback: empty cell if MC font has no glyph (not `?`)

### 1.3 Renderer Fixes
- [ ] `FixedWidthFontRenderer.java` — remove `if (index > 255) index = '?'` clamp
- [ ] `FixedWidthFontRenderer.java` — fix UV formula for columns 16–31 (512-wide atlas)
- [ ] `DirectFixedWidthFontRenderer.java` — same two changes (keep in sync)
- [ ] Monitor VBO / `MonitorTextureBufferShader` path — verify no 255 clamp exists there

### 1.4 Network Encoding
- [ ] `TerminalState.java` — migrate from raw bytes to length-prefixed UTF-8 with version flag byte
- [ ] Verify `LuaValues.decode()` uses `StandardCharsets.UTF_8` (not Latin-1)

### 1.5 Lua Helper
- [ ] Create `rom/apis/utf8_compat.lua` — document encoding guarantees for mod users

### 1.6 Automated Tests
- [ ] `CyrillicTerminalTest.java` — Lua unit: `term.write("Привет")` → buffer equals `"Привет"`
- [ ] `CyrillicTerminalTest.java` — render unit: mock `QuadEmitter`, assert `П` → UV in extended range
- [ ] Font atlas test — read back pixel at known Cyrillic glyph coords, assert non-zero
- [ ] Game test (Kotlin) — computer writes `"Привет мир"`, decode `TerminalState`, assert correct chars

---

## Phase 2 — AI Config & Rate Limiter

### 2.1 Config
- [ ] Add `[ai]` section to `ConfigSpec.java` (builder pattern matching `[http]`)
- [ ] Create `AiConfig.java` in `core/` (mirrors `CoreConfig` pattern)
- [ ] Fields: `enabled`, `endpoint`, `server_api_key`, `model`, `system_prompt`, `error_assistant_prompt`, `error_hint_enabled`
- [ ] Fields in `[ai.limits]`: `max_context_tokens`, `max_response_tokens`, `requests_per_minute`, `requests_per_hour`, `requests_per_day`, `max_global_concurrent`, `max_message_chars`, `queue_on_limit`, `queue_timeout_s`
- [ ] All defaults: `enabled = false`, safe limits as per `TODO.md`

### 2.2 Rate Limiter
- [ ] Create `AiRateLimiter.java` in `core/apis/`
- [ ] `ConcurrentHashMap<UUID, PlayerLimitState>` with sliding windows (minute / hour / day)
- [ ] Atomic CAS increment — no locks on hot path
- [ ] Global concurrent counter (`AtomicInteger`)
- [ ] Background eviction task every 5 min (Fabric scheduler via `ServerLifecycleEvents`)
- [ ] `LimitResult` enum: `ALLOWED`, `RATE_LIMITED_MINUTE`, `RATE_LIMITED_HOUR`, `RATE_LIMITED_DAY`, `GLOBAL_CONCURRENT_LIMIT`

### 2.3 Tests
- [ ] `AiRateLimiterTest.java` — simulate 11 rapid calls, assert 11th = `RATE_LIMITED_MINUTE`
- [ ] Test hour and day windows independently
- [ ] `AiConfigTest.java` — load defaults, assert `enabled = false` and all limit values

---

## Phase 3 — AI Request Pipeline & Lua API

### 3.1 Request Handler
- [ ] Create `AiRequestHandler.java` in `core/apis/`
- [ ] Reuse Netty from `NetworkUtils` — no new threads
- [ ] Build OpenAI-compatible JSON body (system prompt + history + user message)
- [ ] Strip `role: system` entries from Lua input (server-side, always)
- [ ] Truncate oldest messages to stay within `max_context_tokens` (word-count / 0.75 heuristic)
- [ ] Send `Authorization: Bearer <server_api_key>` — key never in client packets
- [ ] Fire `ai_response` Lua event on success
- [ ] Fire `ai_error` Lua event on failure (human-readable message)

### 3.2 Java API
- [ ] Create `AiAPI.java` implementing `ILuaAPI`
- [ ] Register in `Environment.java` alongside other APIs
- [ ] Guard all methods: `if (!AiConfig.enabled) throw new LuaException("AI is disabled")`
- [ ] `startup()` / `shutdown()` lifecycle methods
- [ ] Expose: `chat(messages, options)`, `ask(prompt, options)`, `explain_error(err_string)`

### 3.3 Lua ROM API
- [ ] Create `rom/apis/ai.lua`
- [ ] Implement: `ai.chat()`, `ai.ask()`, `ai.await()`, `ai.explain_error()`
- [ ] Lua-side validation: type checks, length check, non-empty string

### 3.4 Tests
- [ ] `AiRequestBuilderTest.java` — assert `role:system` stripping
- [ ] `AiRequestBuilderTest.java` — assert token truncation removes oldest messages first
- [ ] Lua integration test (mcfly.lua) — mock backend, `ai.ask("hello")`, assert `ai_response` event

---

## Phase 4 — Network Packets & Error Hint Button

### 4.1 Packets
- [ ] `AskAiErrorHintMessage.java` (C2S) in `shared/network/server/`
- [ ] `AiHintResponseMessage.java` (S2C) in `shared/network/client/`
- [ ] Register both in `NetworkMessages.java` using existing `MessageType` pattern

### 4.2 Server-Side Packet Handler
- [ ] Verify player is active user of computer (`AbstractComputerMenu` ownership check)
- [ ] Check rate limits before forwarding to `AiRequestHandler`
- [ ] Stream result back as `AiHintResponseMessage`

### 4.3 Client-Side Error Detection
- [ ] In `TerminalWidget` render pass — scan last N lines for red-coloured (`e`) Lua error pattern `.*:\d+: .*`
- [ ] Expose detected error string to `AbstractComputerScreen`

### 4.4 Error Hint UI
- [ ] `AbstractComputerScreen.java` — add `[?]` button via `DynamicImageButton` in sidebar when error detected AND `error_hint_enabled = true`
- [ ] Create `AiHintOverlay.java` widget — text box near sidebar, dismissable with Escape or click-outside
- [ ] On button click: send `AskAiErrorHintMessage`
- [ ] On packet received: display response in `AiHintOverlay`

### 4.5 Tests
- [ ] Game test (Kotlin) — computer with mock endpoint, `ai.ask()`, assert `ai_response` fires within 5s

---

## Phase 5 — Hardening & Security Audit

- [ ] Security: confirm API key not present in any C2S or S2C packet (wireshark-style log check)
- [ ] Security: confirm `role:system` stripping is in place and tested
- [ ] Security: confirm endpoint validation at config load (HTTPS enforced by default)
- [ ] Security: confirm player-ownership check for error hint packets
- [ ] Performance: stress test rate limiter with 100 concurrent threads, assert no deadlock
- [ ] Performance: verify Netty thread pool not exhausted by AI requests under load
- [ ] Review all config defaults with a fresh eye — are limits sane for a public server?
- [ ] Verify both features work when the other is disabled
- [ ] Check that Russian text survives a full round-trip: Lua → server → packet → client → render
