--- AI API for ComputerCraft — write AI-powered programs on your computer!
--
-- This library wraps the built-in `ai` Java API, providing a clean, ergonomic
-- interface for building AI-powered ComputerCraft software.
--
-- ## Quick start
-- ```lua
-- local ai = require("ai")
--
-- -- List models available on this server
-- for _, m in ipairs(ai.models()) do
--   print(m.id, "-", m.display_name, "(" .. m.max_context_tokens .. " tokens)")
-- end
--
-- -- One-shot question (blocking)
-- local reply, err = ai.ask("What is the capital of France?")
-- if reply then print(reply) else print("Error:", err) end
--
-- -- Multi-turn chat (blocking)
-- local history = {}
-- ai.addMessage(history, "user", "Hello! Who are you?")
-- local reply = ai.chat(history)
-- ai.addMessage(history, "assistant", reply)
-- ai.addMessage(history, "user", "Can you help me write a turtle mining program?")
-- print(ai.chat(history))
--
-- -- Async style (non-blocking, using events directly)
-- local id = ai.askAsync("Tell me a joke", { model = "gpt-4o-mini" })
-- local ok, text = ai.awaitId(id, 15)
-- print(ok and text or "Failed: " .. text)
--
-- -- Explain a Lua error
-- local ok, err = pcall(function() error("something went wrong:42") end)
-- if not ok then
--   local explanation = ai.explainError(err)
--   print(explanation)
-- end
-- ```
--
-- ## Events
-- When using the async functions, listen for:
-- - `ai_response` — `(event, request_id, text)`
-- - `ai_error`    — `(event, request_id, reason)`
--
-- @module ai

local _ai = ai  -- built-in Java API table

--- Return the list of AI models available on this server.
-- Each entry is a table: `{ id, display_name, max_context_tokens }`.
-- @treturn table List of model descriptor tables.
function models()
    return _ai.models()
end

--- Return whether the AI API is enabled on this server.
-- @treturn boolean
function isEnabled()
    return _ai.isEnabled()
end

--- Return the default model id for this server.
-- @treturn string
function defaultModel()
    return _ai.defaultModel()
end

--- Add a message to a conversation history table.
-- Convenience function so you don't have to manually build the table structure.
--
-- ```lua
-- local history = {}
-- ai.addMessage(history, "user", "Hello!")
-- ai.addMessage(history, "assistant", "Hi there!")
-- ```
--
-- @tparam table history The message history table (modified in place).
-- @tparam string role   `"user"` or `"assistant"`.
-- @tparam string content The message text.
function addMessage(history, role, content)
    expect(1, history, "table")
    expect(2, role, "string")
    expect(3, content, "string")
    if role ~= "user" and role ~= "assistant" then
        error("bad argument #2: role must be 'user' or 'assistant', got '" .. role .. "'", 2)
    end
    history[#history + 1] = { role = role, content = content }
end

--- Send a simple one-shot question and **block** until the response arrives.
--
-- @tparam  string prompt   The question to ask.
-- @tparam[opt] table opts  Options: `model`, `temperature`, `max_tokens`, `context`, `timeout`.
-- @treturn string|nil      The AI response text, or nil on error.
-- @treturn nil|string      Error reason on failure, nil on success.
function ask(prompt, opts)
    expect(1, prompt, "string")
    opts = opts or {}
    local timeout = opts.timeout or 30

    local id = _ai.ask(prompt, opts)
    return _awaitId(id, timeout)
end

--- Send a multi-turn conversation and **block** until the response arrives.
--
-- @tparam  table  messages  Array of `{ role, content }` tables (history).
-- @tparam[opt] table opts   Options: `model`, `temperature`, `max_tokens`, `context`, `timeout`.
-- @treturn string|nil       The AI response text, or nil on error.
-- @treturn nil|string       Error reason on failure, nil on success.
function chat(messages, opts)
    expect(1, messages, "table")
    opts = opts or {}
    local timeout = opts.timeout or 30

    local id = _ai.chat(messages, opts)
    return _awaitId(id, timeout)
end

--- Ask a question **without blocking** — returns a request ID immediately.
-- Listen for `ai_response` or `ai_error` events with matching `request_id`.
--
-- @tparam  string prompt   The question.
-- @tparam[opt] table opts  Options: `model`, `temperature`, `max_tokens`, `context`.
-- @treturn number           Request ID.
function askAsync(prompt, opts)
    expect(1, prompt, "string")
    return _ai.ask(prompt, opts)
end

--- Start a multi-turn chat **without blocking** — returns a request ID immediately.
--
-- @tparam  table  messages  Conversation history.
-- @tparam[opt] table opts   Options.
-- @treturn number            Request ID.
function chatAsync(messages, opts)
    expect(1, messages, "table")
    return _ai.chat(messages, opts)
end

--- Ask the AI to explain a Lua runtime error.
-- Uses the server's error-assistant system prompt for better debugging advice.
-- **Blocks** until the explanation arrives.
--
-- ```lua
-- local ok, err = pcall(myFunction)
-- if not ok then
--   local hint = ai.explainError(err)
--   print("AI says:", hint)
-- end
-- ```
--
-- @tparam  string error    The error string from `pcall`.
-- @tparam[opt] number timeout Seconds to wait (default 20).
-- @treturn string|nil      Explanation text, or nil on error.
-- @treturn nil|string      Error reason on failure.
function explainError(error, timeout)
    expect(1, error, "string")
    timeout = timeout or 20

    local id = _ai.explainError(error)
    return _awaitId(id, timeout)
end

--- Wait for the response to a specific request ID.
-- Blocks until `ai_response` or `ai_error` fires for this ID, or timeout elapses.
--
-- @tparam  number  id       Request ID from an async function.
-- @tparam[opt] number timeout Seconds to wait (default 30).
-- @treturn string|nil       Response text, or nil on timeout/error.
-- @treturn nil|string       Error reason on failure, nil on success.
function awaitId(id, timeout)
    expect(1, id, "number")
    return _awaitId(id, timeout or 30)
end

--- Build a streaming chat pipeline.
-- Returns a stateful conversation object for building multi-turn dialogues more easily.
--
-- ```lua
-- local conv = ai.conversation({ model = "deepseek-chat", temperature = 0.8 })
-- conv:say("You are a helpful coding assistant.")  -- adds user message
-- local reply = conv:reply()                        -- sends and waits
-- print(reply)
-- conv:say("Now help me fix: " .. myCode)
-- print(conv:reply())
-- ```
--
-- @tparam[opt] table opts  Default options for all messages in this conversation.
-- @treturn table            Conversation object with methods: `say`, `reply`, `history`, `reset`.
function conversation(opts)
    opts = opts or {}
    local history = {}

    local conv = {}

    --- Add a user message to the conversation.
    -- @tparam string content The message text.
    function conv:say(content)
        addMessage(history, "user", content)
    end

    --- Add an assistant message (for few-shot prompting).
    -- @tparam string content The assistant's message text.
    function conv:inject(content)
        addMessage(history, "assistant", content)
    end

    --- Send the current conversation and wait for the AI's reply.
    -- Automatically appends the assistant's response to history.
    -- @tparam[opt] number timeout Seconds to wait.
    -- @treturn string|nil  Reply text, or nil on error.
    -- @treturn nil|string  Error reason on failure.
    function conv:reply(timeout)
        local text, err = chat(history, {
            model = opts.model,
            temperature = opts.temperature,
            max_tokens = opts.max_tokens,
            context = opts.context,
            timeout = timeout or opts.timeout or 30,
        })
        if text then
            addMessage(history, "assistant", text)
        end
        return text, err
    end

    --- Return a copy of the current conversation history.
    -- @treturn table
    function conv:history()
        local copy = {}
        for i, m in ipairs(history) do copy[i] = m end
        return copy
    end

    --- Clear the conversation history (start fresh).
    function conv:reset()
        history = {}
    end

    return conv
end

-- ---- Internal helpers ----

--- Block until ai_response/ai_error fires for `id`, or timeout elapses.
-- @local
function _awaitId(id, timeout)
    local timer = os.startTimer(timeout)
    while true do
        local event, arg1, arg2 = os.pullEvent()
        if event == "ai_response" and arg1 == id then
            os.cancelTimer(timer)
            return arg2, nil
        elseif event == "ai_error" and arg1 == id then
            os.cancelTimer(timer)
            return nil, arg2
        elseif event == "timer" and arg1 == timer then
            return nil, "AI request timed out after " .. timeout .. "s"
        end
    end
end
