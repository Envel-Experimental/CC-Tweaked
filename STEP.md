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

### Найденные точки обрезки кириллицы (все надо починить)
> 1. `FixedWidthFontRenderer.java:125` — `if (index > 255) index = '?'`
> 2. `DirectFixedWidthFontRenderer.java:106` — `if (index > 255) index = '?'`
> 3. `LuaValues.encode():31` — `c < 256 ? (byte) c : 63` (используется в blit/HTTP body)
> 4. `NetworkedTerminal.write():30` — `(byte)(text.charAt(x) & 0xFF)` — сетевая сериализация

### Реализация
- [/] A7.  Создать расширенный `term_font.png` 512×256 (32 колонки × 16 строк; кириллица в правой половине)
- [ ] A8.  Создать `CyrillicFontPatcher.java` — извлечение глифов из MC FontManager в runtime, upload DynamicTexture
- [ ] A9.  Зарегистрировать `CyrillicFontPatcher` в `ComputerCraftClient.init()` (Fabric client init)
- [ ] A10. `FixedWidthFontRenderer.java` — убрать clamp; пересчитать UV: `column = index % 32`, `row = index / 32`, `WIDTH = 512f`
- [ ] A11. `DirectFixedWidthFontRenderer.java` — идентичные правки (строка 52-56 + 106)
- [ ] A12. Monitor VBO / shader путь — verify нет clamp-а (подтверждено что clamp только в drawString)
- [ ] A13. `NetworkedTerminal.write()` — мигрировать текстовые строки на UTF-16 BE (2 байта на символ) с version flag
- [ ] A14. `NetworkedTerminal.read()` — обратная десериализация UTF-16 BE
- [ ] A15. `LuaValues.encode()` — **НЕ МЕНЯТЬ** (это для ByteBuffer/blit — там 1 байт = 1 ячейка, это нормально). Добавить `encodeUtf8()` для HTTP-тела.
- [ ] A16. Создать `rom/apis/utf8_compat.lua` — документация гарантий кодировки для пользователей

### Тесты (обязательно перед merge)
- [ ] A17. `CyrillicTerminalTest` — Lua unit: `term.write("Привет")` → буфер == `"Привет"` (не `"??????"`)
- [ ] A18. `CyrillicTerminalTest` — render unit: mock QuadEmitter записывает `(charIndex, uvX, uvY)`, assert `П` (cp 1055) → column=31, row=32 (расширенный атлас)
- [ ] A19. `TerminalNetworkEncodingTest` — encode → decode round-trip с кириллицей, assert равенство
- [ ] A20. `TerminalNetworkEncodingTest` — version flag: старый клиент получает новый пакет → graceful fallback
- [ ] A21. Game test (Kotlin) — компьютер пишет `"Привет мир"`, capture TerminalState, assert корректные символы
- [ ] A22. Регрессия: ASCII 0-127 и Latin-1 128-255 после рефакторинга рендерятся корректно

---

## Блок B — AI API: Конфиг и Rate Limiter

### Исследование
- [x] B1. Изучить `CoreConfig.java` — plain static fields, синхронизируются через ConfigSpec.syncServer()
- [x] B2. Изучить `ComputerExecutor.java:148-165` — API добавляются через `addApi()` в конструкторе
- [x] B3. Изучить `NetworkUtils.java` — `EXECUTOR` (ScheduledThreadPoolExecutor, 4 threads), `LOOP_GROUP` (NioEventLoopGroup, 4 threads) — переиспользуем
- [ ] B4. Изучить `HttpRequest.java` — паттерн async запроса и event firing

### Конфиг
- [ ] B5.  Создать `AiConfig.java` в `projects/core/src/main/java/dan200/computercraft/core/`
- [ ] B6.  Добавить `[ai]` секцию в `ConfigSpec.java` — builder паттерн, `builder.push("ai")`
- [ ] B7.  `syncServer()` в `ConfigSpec` — синхронизировать все поля в `AiConfig`

### Rate Limiter (security-critical)
- [ ] B8.  Создать `AiRateLimiter.java` в `core/apis/ai/`
- [ ] B9.  `PlayerLimitState` — три sliding window (minute/hour/day) с `long windowStart` + `AtomicInteger count`, CAS-обновление
- [ ] B10. Глобальный `AtomicInteger globalConcurrent` — guard против DDoS
- [ ] B11. `LimitResult` enum: `ALLOWED`, `RATE_LIMITED_MINUTE`, `RATE_LIMITED_HOUR`, `RATE_LIMITED_DAY`, `GLOBAL_CONCURRENT_LIMIT`
- [ ] B12. Background eviction: `NetworkUtils.EXECUTOR.scheduleAtFixedRate()` каждые 5 мин

### Тесты Rate Limiter
- [ ] B13. `AiRateLimiterTest` — 11 rapid calls → 11й = `RATE_LIMITED_MINUTE`
- [ ] B14. `AiRateLimiterTest` — hour window: simulate time skip, assert `RATE_LIMITED_HOUR`
- [ ] B15. `AiRateLimiterTest` — global concurrent: fill to max → next = `GLOBAL_CONCURRENT_LIMIT`
- [ ] B16. `AiRateLimiterTest` — eviction: после периода неактивности запись удаляется из map
- [ ] B17. `AiRateLimiterTest` — thread safety: 100 параллельных потоков × 50 calls, assert no negative counters, no exception
- [ ] B18. `AiConfigTest` — дефолты: `enabled = false`, все лимиты верные значения

---

## Блок C — AI API: Request Pipeline

### Реализация
- [ ] C1.  Создать `AiRequestBuilder.java` — сборка OpenAI JSON тела (system prompt + history + user)
- [ ] C2.  В `AiRequestBuilder`: **strip** все `role:system` из Lua input ПЕРЕД добавлением
- [ ] C3.  В `AiRequestBuilder`: inject `AiConfig.systemPrompt` ПЕРВЫМ, неудалимо
- [ ] C4.  В `AiRequestBuilder`: token truncation — удалять oldest non-system messages до `max_context_tokens`
- [ ] C5.  В `AiRequestBuilder`: enforce `max_message_chars` на каждое сообщение
- [ ] C6.  Создать `AiRequestHandler.java` — Netty HTTP, `Authorization: Bearer`, fire Lua events
- [ ] C7.  `AiRequestHandler` использует `NetworkUtils.EXECUTOR` — no new threads
- [ ] C8.  fire `ai_response` event при успехе (текст ответа)
- [ ] C9.  fire `ai_error` event при ошибке (человекочитаемо)
- [ ] C10. Создать `AiAPI.java` в `core/apis/ai/` — `ILuaAPI`, методы: `chat`, `ask`, `explain_error`
- [ ] C11. Guard: `if (!AiConfig.enabled) throw new LuaException("AI API is disabled on this server")`
- [ ] C12. Зарегистрировать `AiAPI` в `ComputerExecutor` constructor (строка ~164)
- [ ] C13. Создать `rom/apis/ai.lua` — `ai.chat()`, `ai.ask()`, `ai.await()`, `ai.explain_error()`
- [ ] C14. Lua validation: type checks, length, non-empty string

### Тесты Request Pipeline (security-critical)
- [ ] C15. `AiRequestBuilderTest` — `role:system` из Lua input **полностью отсутствует** в итоговом JSON
- [ ] C16. `AiRequestBuilderTest` — `AiConfig.systemPrompt` **всегда первый** в messages array
- [ ] C17. `AiRequestBuilderTest` — token truncation удаляет **СТАРЕЙШИЕ** non-system сообщения
- [ ] C18. `AiRequestBuilderTest` — сообщение > `max_message_chars` → исключение **до** HTTP запроса
- [ ] C19. `AiRequestBuilderTest` — итоговый JSON объект **не содержит** поле `api_key` или любые credentials
- [ ] C20. `AiApiTest` — `enabled = false` → `LuaException` на любом методе
- [ ] C21. `AiApiTest` — пустая строка → ошибка до HTTP
- [ ] C22. Lua integration test (mcfly) — mock backend, `ai.ask("hello")` → event `ai_response`

---

## Блок D — AI API: Network + Error Hint

### Пакеты
- [ ] D1. Создать `AskAiErrorHintMessage.java` (C2S) — поля: `computerId` (int), `errorString` (String, max 2000 chars)
- [ ] D2. Создать `AiHintResponseMessage.java` (S2C) — поля: `computerId` (int), `hintText` (String)
- [ ] D3. Зарегистрировать оба в `NetworkMessages.java`

### Server handler (security-critical)
- [ ] D4. Handler для `AskAiErrorHintMessage`: проверить `error_hint_enabled` — иначе silent drop
- [ ] D5. Ownership check: игрок = активный юзер компьютера через `AbstractComputerMenu`
- [ ] D6. Rate limit check через `AiRateLimiter` ПЕРЕД отправкой в handler
- [ ] D7. Sanitize error string: strip control chars, cap до `max_message_chars`
- [ ] D8. Forward в `AiRequestHandler` с `error_assistant_prompt`
- [ ] D9. Ответ → `AiHintResponseMessage` → клиент

### Client UI
- [ ] D10. `TerminalWidget` — детектор: последние N строк, цвет `e` (red) + pattern `.*:\d+: .*`
- [ ] D11. `AbstractComputerScreen` — показывать `[?]` кнопку если `error_hint_enabled = true` И ошибка найдена
- [ ] D12. `DynamicImageButton` в `ComputerSidebar` — иконка AI
- [ ] D13. Создать `AiHintOverlay.java` — текстовый виджет, dismiss Escape/click-outside
- [ ] D14. On click → отправить `AskAiErrorHintMessage`
- [ ] D15. On `AiHintResponseMessage` → display в `AiHintOverlay`

### Тесты
- [ ] D16. `AiPacketSecurityTest` — пакет от non-owner → reject, assert no response sent
- [ ] D17. `AiPacketSecurityTest` — error string с control chars → sanitized
- [ ] D18. `AiPacketSecurityTest` — error string > limit → truncated/rejected перед handler
- [ ] D19. `AiPacketSecurityTest` — `error_hint_enabled = false` → silent drop, no AI call made
- [ ] D20. Game test (Kotlin) — computer + mock endpoint, `ai.ask()` → event fires < 5s

---

## Блок E — Hardening & Security Audit

- [ ] E1.  **[SECURITY]** Grep по всем packet классам: `server_api_key` не присутствует ни в одном
- [ ] E2.  **[SECURITY]** `role:system` strip: тест C15 должен fail если удалить strip логику (mutation test)
- [ ] E3.  **[SECURITY]** Endpoint URL: HTTPS enforced в `AiConfig` при `allow_http_endpoint = false` (default)
- [ ] E4.  **[SECURITY]** Ownership check D5 покрыт тестом D16 — убедиться что тест реально проверяет reject
- [ ] E5.  **[PERF]** Stress: 100 threads × 1000 calls на rate limiter, assert no deadlock, no NPE
- [ ] E6.  **[PERF]** AI запросы не исчерпывают `NetworkUtils.EXECUTOR` — проверить backpressure через global concurrent limit
- [ ] E7.  **[COMPAT]** Обе фичи независимы: AI выкл + Russian вкл → только шрифт работает
- [ ] E8.  **[REGRESSION]** Full round-trip тест: Lua пишет кириллицу → encode → network → decode → render UV in extended range
- [ ] E9.  **[DEFAULTS]** Финальный review конфиг-дефолтов: достаточно ли жёсткие лимиты для публичного сервера?
- [ ] E10. **[DOCS]** Обновить `TASK.md` — отметить выполненное
