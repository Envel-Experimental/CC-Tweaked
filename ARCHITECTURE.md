# CC:Tweaked — Architecture Notes

Накопленные знания об архитектуре проекта. Обновляй этот файл при открытии новых инвариантов.

---

## Тест-инфраструктура

- JUnit 5 (`@Test`, `@ParameterizedTest`, `@MethodSource`)
- Hamcrest матчеры: `assertThat`, `equalTo`, `allOf`
- Тесты кора: `projects/core/src/test/java/dan200/computercraft/core/`
- Тесты common: `projects/common/src/test/`
- Game-тесты (Kotlin): `projects/common/src/testMod/kotlin/`
- Пакет `dan200.computercraft.test.core` — общие хелперы (`CallCounter`, `TerminalMatchers`)

---

## Cyrillic Input Pipeline (полная цепочка)

```
GLFW/OS char event
  │
  ▼
TerminalWidget.charTyped(char)          [client, common]
  │ int cp = c;                         (no cast to byte)
  ▼
ClientInputHandler.charTyped(int)       [client, common]
  │
  ▼ (over network)
KeyEventServerMessage                   [common/network]
  │ int chr field                       (no cast to byte)
  ▼
ServerInputState.charTyped(int)         [server, common]
  │ StringUtil.isTypableChar(int)
  │ ComputerEvents.charTyped(computer, (char) chr)
  ▼
ComputerEvents.charTyped(Receiver, char)  [core]
  │ String.valueOf(chr).getBytes(UTF_8)   → fires "char" event with UTF-8 bytes
  ▼
Lua os.pullEvent("char") → receives UTF-8 string
```

**Standalone path:**
```
InputState.onCharEvent(int codepoint)
  │ StringUtil.unicodeToTerminal(codepoint) → int
  │ ComputerEvents.charTyped(computer, (char) terminalChar)
```

**Clipboard path:**
```
StringUtil.getClipboardString(String) → ByteBuffer UTF-16 LE pairs
ServerInputState.paste(ByteBuffer)
  │ isValidClipboard(buf) → reads UTF-16 LE pairs, validates isTypableChar(int)
  │ ComputerEvents.paste(computer, contents)
```

---

## NetworkedTerminal — Wire Format

```
Byte layout (VERSION_UTF16 = 0x02):
  [1 byte: version=0x02]
  [width * height * 2 bytes: text as UTF-16 BE]   ← big-endian, high byte first
  [width * height bytes: packed colours]
  [Palette.PALETTE_SIZE * 3 bytes: palette RGB]

Legacy format (VERSION_LEGACY = 0x01):
  [1 byte: version=0x01]
  [width * height bytes: raw Latin-1 codepoints]
  [width * height bytes: packed colours]
  [palette bytes]
```

`read()` dispatches on version byte → fully backward-compatible.

---

## CyrillicFontPatcher

- Path: `projects/fabric/src/client/java/dan200/computercraft/client/font/CyrillicFontPatcher.java`
- Builds a **512×256** NativeImage atlas at runtime (vs original 256×256)
- **32 columns × 16 rows** layout: slots 0–255 = Latin-1 (left half), slots 256–511 = Cyrillic U+0400–U+04FF (right half)
- Cell size: 8×11px (FONT_WIDTH+2, FONT_HEIGHT+2)
- Registered via `ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)` in `ComputerCraftClient.init()`
- `onReload()` resets `patched = false` → re-runs on every resource pack reload
- **Current limitation (A7):** renders sentinel white pixel per cell. Full pixel extraction needs
  either a `GlyphRenderer` Mixin or GL texture readback. Tracked as A7 `[/]`.
- `uvForCodepoint(int)` → `float[4]` UV coords in [0,1] texture space

**Font renderers (both updated identically):**
- `FixedWidthFontRenderer` — `COLS = CyrillicFontPatcher.COLS` (32), clamp at 511 (not 255)
- `DirectFixedWidthFontRenderer` — same constants

---

## StringUtil — Unicode Rules

```java
unicodeToTerminal(int cp):
  0x0020..0x00FE → cp as-is
  0x0400..0x04FF → cp as-is (Cyrillic pass-through to atlas slots 256–511)
  else            → -1 (not renderable)

isTypableChar(int cp):
  0x20..0xFE      → true  (printable Latin-1)
  0x0400..0x04FF  → true  (Cyrillic)
  else            → false
```

---

## AiConfig — Key Invariants

- `serverApiKey` is **package-private** — never in any network packet
- `{language}` placeholder in prompts → replaced via `AiConfig.interpolatePrompt(prompt, language)`
- `defaultResponseLanguage = "Russian"` (configurable)
- `allowAdditionalSystemContext = false` by default — `opts.system_context` requires server opt-in
- `ValidationConfig` is a **proxy-only** config: mod only knows endpoint + api_key; the proxy
  decides which moderation service to use internally

### ModerationFormat
- `CHAT` → POST `/chat/completions`, response parsed as `true`/`false` text
- `OPENAI_MODERATION` → POST `/moderations`, parses `results[0].flagged` boolean
- **Fail-open**: any moderation error → pass-through (never block users due to infra issues)
- `ai_response_unvalidated` event fires if all retries exhausted (server logging only)

### Lua opts fields
```lua
opts = {
  model = "deepseek-chat",           -- must be in allowed_models
  temperature = 0.7,                  -- 0.0..2.0
  max_tokens = 256,                   -- 1..maxResponseTokens
  language = "Russian",               -- overrides defaultResponseLanguage
  system_context = "...",            -- requires allowAdditionalSystemContext=true
  context = "...",                    -- legacy alias for system_context (silent ignore if disabled)
  timeout = 30,                       -- seconds (Lua-side only)
}
```

---

## AI Request Flow

```
AiAPI.ask(prompt, opts)
  │ rate limit check (B8, not yet implemented)
  │ model resolve (whitelist)
  │ sanitize (strip control chars, cap maxMessageChars)
  ▼
AiRequestHandler.dispatch(env, id, messages, model, options)
  │ runs on NetworkUtils.EXECUTOR (off server thread)
  │ loop up to maxRetries:
  │   sendRawRequest(endpoint/chat/completions, ...)
  │   if validation.enabled: runValidation(...)
  │     CHAT: POST /chat/completions → true/false
  │     OPENAI_MODERATION: POST /moderations → !flagged
  │   if passed: break
  ▼
env.queueEvent("ai_response", id, text)   -- or "ai_error" / "ai_response_unvalidated"
  ▼
Lua ai.await(id, timeout)
  │ implemented in ai.lua (coroutine-safe os.pullEvent loop)
  │ Java side returns sentinel "__await_sentinel__" to trigger Lua loop
```

---

## ConfigSpec Pattern (how to add [ai] section)

See `CoreConfig.java` and `ConfigSpec.java`.
Pattern:
```java
builder.push("ai");
AiConfig.enabled = builder.define("enabled", false).get();
AiConfig.endpoint = builder.define("endpoint", "").get();
// ...
builder.pop();
```
`syncServer()` must be called to propagate values from config file into static fields.

---

## Тестовые паттерны

### Terminal unit test (core/test/java):
```java
var terminal = new Terminal(20, 10, true);
terminal.write("Привет");
assertEquals("Привет", terminal.getLine(0).toString().substring(0, 6));
```

### NetworkedTerminal round-trip test (common/test/java):
```java
// needs to be in common test source set (has NetworkedTerminal on classpath)
var terminal = new NetworkedTerminal(10, 5, true);
terminal.write("Привет");
var state = terminal.write();  // package-private — use reflection or move test to same package
// re-read on another terminal:
var copy = new NetworkedTerminal(1, 1, true);
copy.read(state);
assertEquals("Привет", copy.getLine(0).toString().substring(0, 6));
```

**Note:** `NetworkedTerminal.write()` and `read()` are package-private (синхронизированные).
Tests must be in package `dan200.computercraft.shared.computer.terminal` or use `@VisibleForTesting`.

---

## Открытые вопросы / Known Gaps

1. **A7 (CyrillicFontPatcher)** — sentinel pixel только. Нужен Mixin в `GlyphRenderer` или
   GL texture readback (1 раз при init). Без этого Cyrillic отображается через MC font fallback.
2. **B6/B7** — `[ai]` секция в `ConfigSpec.java` не зарегистрирована — AiConfig не загружается из файла.
3. **C17** — `AiAPI` не зарегистрирован в `ComputerExecutor` — недоступен из Lua.
4. **B8-B12** — `AiRateLimiter` не реализован — нет защиты от DoS.
5. **Тесты A18-A23** — не написаны.
6. **Тесты C19-C27** — не написаны.
