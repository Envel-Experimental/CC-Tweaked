# STEP.md — Пошаговое выполнение

Легенда: `[ ]` не начато | `[/]` в процессе | `[x]` выполнено | `[!]` заблокировано

> Приоритеты: **Тесты на каждый шаг. Безопасность прежде функциональности.**

---

## Блок A — Русский язык: Font Pipeline

### Исследование (read-only)
- [x] A1. Изучить `term_font.png` — 256×256, 16 колонок × 16 строк, каждый глиф 6×9px + паддинг
- [x] A2. Изучить `DirectFixedWidthFontRenderer.java` — clamp `> 255` на строке 106, UV: `column = index % 16`, `row = index / 16`, `WIDTH = 256f`
- [x] A3. Изучить `TerminalState.java` — `contents = byte[]`, `writeByteArray` → **raw bytes, нет UTF-8**
- [x] A4. Изучить `LuaValues.java` — строка 31: `c < 256 ? (byte) c : 63` — **3-й clamp кириллицы!**
- [x] A5. Изучить `NetworkedTerminal.java` — строка 30: `(byte)(text.charAt(x) & 0xFF)` — **4-й clamp**. NBT путь использует `putString` (Unicode-safe).
- [x] A6. Изучить Fabric ClientModInitializer — `ComputerCraftClient.init()` в `fabric/src/client/.../ComputerCraftClient.java`

### Найденные точки обрезки кириллицы (все починены)
> 1. `FixedWidthFontRenderer.java:125` — ~~`if (index > 255) index = '?'`~~ → поднято до 511
> 2. `DirectFixedWidthFontRenderer.java:106` — ~~`if (index > 255) index = '?'`~~ → поднято до 511
> 3. `StringUtil.unicodeToTerminal()` — ~~U+0400..U+04FF → -1~~ → теперь pass-through в атлас
> 4. `StringUtil.isTypableChar()` — ~~`chr <= 255`~~ → теперь допускает 0x0400..0x04FF
> 5. `TerminalWidget.charTyped()` — ~~`(byte) terminalChar`~~ → `int` без каста
> 6. `InputHandler.charTyped(byte)` — ~~`byte`~~ → `int` во всём pipeline
> 7. `KeyEventServerMessage` — ~~`(byte) key`~~ → `key` напрямую
> 8. `NetworkedTerminal.write()` — ~~`& 0xFF`~~ → UTF-16 BE (VERSION 0x02 + 2 байта/символ)
> 9. `NetworkedTerminal.read()` — обратная десериализация с version-based fallback

### Реализация
- [/] A7.  Создать расширенный `term_font.png` 512×256 (32 колонки × 16 строк; кириллица в правой половине)
- [x] A8.  Создать `CyrillicFontPatcher.java` — runtime патчер атласа
- [ ] A9.  Зарегистрировать `CyrillicFontPatcher` в `ComputerCraftClient.init()` (Fabric client init)
- [x] A10. `FixedWidthFontRenderer.java` — убрать clamp; пересчитать UV: `column = index % COLS`, `row = index / COLS`, `ATLAS_HEIGHT = 256f`
- [x] A11. `DirectFixedWidthFontRenderer.java` — идентичные правки ✓
- [ ] A12. Monitor VBO / shader путь — verify нет clamp-а (подтверждено что clamp только в drawString)
- [x] A13. `NetworkedTerminal.write()` — мигрировать текстовые строки на UTF-16 BE (2 байта на символ) с version flag ✓
- [x] A14. `NetworkedTerminal.read()` — обратная десериализация UTF-16 BE с legacy fallback ✓
- [x] A15. `StringUtil` — ввод/ввод/буфер обмена кириллица: `unicodeToTerminal`, `isTypableChar`, `getClipboardString` ✓
- [x] A16. `InputHandler` → весь pipeline `charTyped` обновлён до `int` (TerminalWidget, ClientInputHandler, KeyEventServerMessage, ServerInputState) ✓
- [ ] A17. `ServerInputState.charTyped(byte)` → `int` — последнее звено на сервере, найдено в B8 ниже

### Тесты (обязательно перед merge)
- [ ] A18. `CyrillicTerminalTest` — Lua unit: `term.write("Привет")` → буфер == `"Привет"` (не `"??????"`)
- [ ] A19. `CyrillicTerminalTest` — render unit: mock QuadEmitter записывает `(charIndex, uvX, uvY)`, assert `П` (cp 1055) → column=31, row=32 (расширенный атлас)
- [ ] A20. `TerminalNetworkEncodingTest` — encode → decode round-trip с кириллицей, assert равенство
- [ ] A21. `TerminalNetworkEncodingTest` — version flag: legacy пакет (без VERSION_UTF16) → graceful fallback
- [ ] A22. Game test (Kotlin) — компьютер пишет `"Привет мир"`, capture TerminalState, assert корректные символы
- [ ] A23. Регрессия: ASCII 0-127 и Latin-1 128-255 после рефакторинга рендерятся корректно

---

## Блок B — AI API: Конфиг и Rate Limiter

### Исследование
- [x] B1. Изучить `CoreConfig.java` — plain static fields, синхронизируются через ConfigSpec.syncServer()
- [x] B2. Изучить `ComputerExecutor.java:148-165` — API добавляются через `addApi()` в конструкторе
- [x] B3. Изучить `NetworkUtils.java` — `EXECUTOR` (ScheduledThreadPoolExecutor, 4 threads), `LOOP_GROUP` — переиспользуем
- [ ] B4. Изучить `HttpRequest.java` — паттерн async запроса и event firing

### Конфиг
- [x] B5.  `AiConfig.java` — создан в `core/`, включает:
  - master switch `enabled = false`
  - `endpoint` + `serverApiKey` (package-private, не в пакетах)
  - `defaultModel` + `allowedModels` (whitelist с `ModelEntry` record)
  - `defaultResponseLanguage = "Russian"` + `{language}` placeholder в промтах
  - `allowAdditionalSystemContext` + `maxContextStringChars`
  - `systemPrompt` и `errorAssistantPrompt` с `{language}`
  - rate limits (per-minute/hour/day, global concurrent)
  - `ValidationConfig` — moderation proxy (endpoint, apiKey, format: CHAT/OPENAI_MODERATION)
  - `validate()` — проверяет все инварианты при загрузке конфига
  - `interpolatePrompt(prompt, language)` helper
- [ ] B6.  Добавить `[ai]` секцию в `ConfigSpec.java` — builder паттерн, `builder.push("ai")`
- [ ] B7.  `syncServer()` в `ConfigSpec` — синхронизировать все поля в `AiConfig`

### Rate Limiter (security-critical)
- [ ] B8.  Создать `AiRateLimiter.java` в `core/apis/ai/`
- [ ] B9.  `PlayerLimitState` — три sliding window (minute/hour/day), CAS-обновление
- [ ] B10. Глобальный `AtomicInteger globalConcurrent` — guard против DDoS (уже в `AiRequestHandler`)
- [ ] B11. `LimitResult` enum: `ALLOWED`, `RATE_LIMITED_MINUTE/HOUR/DAY`, `GLOBAL_CONCURRENT_LIMIT`
- [ ] B12. Background eviction каждые 5 мин

### Тесты Rate Limiter
- [ ] B13. `AiRateLimiterTest` — 11 rapid calls → 11й = `RATE_LIMITED_MINUTE`
- [ ] B14. `AiRateLimiterTest` — hour window: simulate time skip, assert `RATE_LIMITED_HOUR`
- [ ] B15. `AiRateLimiterTest` — global concurrent: fill to max → next = `GLOBAL_CONCURRENT_LIMIT`
- [ ] B16. `AiRateLimiterTest` — eviction: после неактивности запись удаляется из map
- [ ] B17. `AiRateLimiterTest` — thread safety: 100 параллельных потоков × 50 calls
- [ ] B18. `AiConfigTest` — дефолты, validate() срабатывает корректно

---

## Блок C — AI API: Request Pipeline

### Реализация
- [x] C1.  `AiRequestHandler.java` — сборка JSON тела (system prompt + history + user)
- [x] C2.  strip `role:system` из Lua input ПЕРЕД форвардингом
- [x] C3.  inject `AiConfig.systemPrompt` (language-interpolated) ПЕРВЫМ, неудалимо
- [x] C4.  token truncation — удалять oldest non-system messages до `effectiveContextTokens()`
- [x] C5.  enforce `maxMessageChars` на каждое сообщение
- [x] C6.  `AiRequestHandler` — HTTP через `java.net.http.HttpClient` на `NetworkUtils.EXECUTOR`
- [x] C7.  `sendRawRequest()` — generic POST helper для chat и moderation форматов
- [x] C8.  fire `ai_response` event при успехе
- [x] C9.  fire `ai_error` event при ошибке (человекочитаемые сообщения)
- [x] C10. Moderation/validation loop: до `max_retries` попыток; fail-open при ошибке сервиса
- [x] C11. `runChatModeration()` — format CHAT: POST `/chat/completions` → парсит `true`/`false`
- [x] C12. `runOpenAiModeration()` — format OPENAI_MODERATION: POST `/moderations` → парсит `flagged`
- [x] C13. `ai_response_unvalidated` event если все retries провалились (для серверного логгинга)
- [x] C14. `AiAPI.java` — полный `ILuaAPI`: `models()`, `ask()`, `chat()`, `explainError()`, `await()`, `isEnabled()`, `defaultModel()`
- [x] C15. `AiAPI` — `language` + `system_context` options с server-side проверками
- [x] C16. Guard: `if (!AiConfig.enabled) throw new LuaException(...)`
- [ ] C17. Зарегистрировать `AiAPI` в `ComputerExecutor` constructor (строка ~164)
- [x] C18. `rom/apis/ai.lua` — high-level Lua API: `ask`, `chat`, `askAsync`, `chatAsync`, `explainError`, `awaitId`, `conversation()` builder, `addMessage`, `models`, `isEnabled`, `defaultModel`

### Тесты Request Pipeline (security-critical)
- [ ] C19. `AiRequestBuilderTest` — `role:system` из Lua input **полностью отсутствует** в JSON
- [ ] C20. `AiRequestBuilderTest` — system prompt **всегда первый** в messages
- [ ] C21. `AiRequestBuilderTest` — token truncation удаляет **СТАРЕЙШИЕ** non-system сообщения
- [ ] C22. `AiRequestBuilderTest` — итоговый JSON **не содержит** `api_key` или credentials
- [ ] C23. `AiApiTest` — `enabled = false` → `LuaException` на любом методе
- [ ] C24. `AiApiTest` — пустая строка → ошибка до HTTP
- [ ] C25. `AiApiTest` — неизвестная модель → `LuaException` с hint "Use ai.models()"
- [ ] C26. `AiApiTest` — `system_context` при `allow=false` → `LuaException`
- [ ] C27. Lua integration test — mock backend, `ai.ask("hello")` → event `ai_response`

---

## Блок D — AI API: Network + Error Hint

### Пакеты
- [ ] D1. Создать `AskAiErrorHintMessage.java` (C2S)
- [ ] D2. Создать `AiHintResponseMessage.java` (S2C)
- [ ] D3. Зарегистрировать оба в `NetworkMessages.java`

### Server handler (security-critical)
- [ ] D4. Handler: проверить `error_hint_enabled` — иначе silent drop
- [ ] D5. Ownership check: игрок = активный юзер компьютера
- [ ] D6. Rate limit check ПЕРЕД отправкой в handler
- [ ] D7. Sanitize error string: strip control chars, cap до `max_message_chars`
- [ ] D8. Forward в `AiRequestHandler` с `errorAssistantPrompt`
- [ ] D9. Ответ → `AiHintResponseMessage` → клиент

### Client UI
- [ ] D10. `TerminalWidget` — детектор ошибок (red + `.*:\d+: .*` pattern)
- [ ] D11. `AbstractComputerScreen` — показывать `[?]` кнопку
- [ ] D12. `DynamicImageButton` в sidebar — иконка AI
- [ ] D13. `AiHintOverlay.java` — текстовый виджет, dismiss Escape/click-outside
- [ ] D14. On click → `AskAiErrorHintMessage`
- [ ] D15. On `AiHintResponseMessage` → display в overlay

### Тесты
- [ ] D16. `AiPacketSecurityTest` — non-owner → reject, no response
- [ ] D17. `AiPacketSecurityTest` — control chars → sanitized
- [ ] D18. `AiPacketSecurityTest` — `error_hint_enabled = false` → silent drop, no AI call

---

## Блок E — Hardening & Security Audit

- [ ] E1.  **[SECURITY]** Grep: `server_api_key` / `api_key` НЕТ ни в одном S2C пакете
- [ ] E2.  **[SECURITY]** `role:system` strip: mutation test — удалить strip → тест должен упасть
- [ ] E3.  **[SECURITY]** HTTPS enforced при `require_https = true` (default)
- [ ] E4.  **[SECURITY]** Ownership check покрыт тестом D16
- [ ] E5.  **[PERF]** Stress: 100 threads × 1000 calls на rate limiter, no deadlock, no NPE
- [ ] E6.  **[PERF]** AI запросы не исчерпывают `NetworkUtils.EXECUTOR` (global concurrent limit)
- [ ] E7.  **[COMPAT]** AI выкл + Russian вкл → только шрифт работает, no errors
- [ ] E8.  **[REGRESSION]** Full round-trip: Lua пишет кириллицу → encode → network → decode → render UV in extended range
- [ ] E9.  **[DEFAULTS]** Финальный review конфиг-дефолтов для публичного сервера
- [ ] E10. **[DOCS]** `docs/ai/proxy-integration.md` ✓ создан

---

## Документация

- [x] `docs/ai/proxy-integration.md` — полный guide: форматы, конфиг, security, Lua quick reference

---

## Следующие шаги (приоритет)

1. `ServerInputState.charTyped` → `int` (A17, последнее звено input pipeline)
2. `ConfigSpec.java` — зарегистрировать `[ai]` секцию (B6, B7)
3. `ComputerExecutor` — зарегистрировать `AiAPI` (C17)
4. `AiRateLimiter.java` — sliding window rate limiter (B8-B12)
5. Тесты A18-A23, B13-B18, C19-C27
