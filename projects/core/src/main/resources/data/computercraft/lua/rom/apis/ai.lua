--- AI API for ComputerCraft — write AI-powered programs on your computer!
--
-- This library wraps the built-in `ai` Java API, providing a clean, ergonomic
-- interface for building AI-powered ComputerCraft software.
--
-- ## Quick start
-- ```lua
-- -- The ai API is available globally.
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
local expect = require("cc.expect").expect

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

--- Return whether the server allows the AI to execute tools (interact with the world).
-- @treturn boolean
function isToolCallingEnabled()
    return _ai.isToolCallingEnabled()
end

--- Return the maximum allowed conversation history depth.
-- @treturn number
function maxConversationHistory()
    return _ai.maxConversationHistory()
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
    if role ~= "user" and role ~= "assistant" and role ~= "tool" then
        error("bad argument #2: role must be 'user', 'assistant' or 'tool', got '" .. role .. "'", 2)
    end
    history[#history + 1] = { role = role, content = content }
    
    local max_hist = maxConversationHistory()
    while #history > max_hist do
        table.remove(history, 1)
    end
end

--- Read a local file and either attach it to the history or return its content as a string.
-- @tparam[opt] table history The message history table (optional).
-- @tparam string path        The path to the file on the computer.
-- @treturn string            The formatted file content.
function attachFile(history_or_path, path)
    local history, filepath
    if type(history_or_path) == "table" and type(path) == "string" then
        history = history_or_path
        filepath = path
    elseif type(history_or_path) == "string" then
        history = nil
        filepath = history_or_path
    else
        error("Invalid arguments. Expected (history, path) or (path)", 2)
    end

    if not fs.exists(filepath) or fs.isDir(filepath) then
        error("File not found or is a directory: " .. filepath, 2)
    end
    
    local file = fs.open(filepath, "r")
    if file then
        local content = file.readAll()
        file.close()
        local formatted = "File content of " .. filepath .. ":\n```\n" .. content .. "\n```"
        if history then
            addMessage(history, "user", formatted)
        end
        return formatted
    end
    return ""
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

--- Execute a prompt with optional tools (redstone, gps, fs).
-- Extracts `<tool>{...}</tool>` from the response and executes them safely.
-- @tparam table history The conversation history
-- @tparam table opts    Request options (model, temperature, etc). Can include `tools = {"redstone", "gps", "fs"}`
-- @treturn string|nil   The cleaned text response, or nil on error
-- @treturn nil|string   Error reason
function executeWithTools(history, opts)
    expect(1, history, "table")
    opts = opts or {}
    
    if opts.tools and #opts.tools > 0 then
        if not isToolCallingEnabled() then
            error("Tool calling is disabled in server config (AiConfig.allowToolCalling).", 2)
        end
        
        local toolInstructions = "You have access to the following tools: " .. table.concat(opts.tools, ", ") .. ".\n"
        toolInstructions = toolInstructions .. "To use a tool, include a JSON object inside <tool> and </tool> tags. Do not use markdown for the tags.\n"
        toolInstructions = toolInstructions .. "Example: <tool>{\"tool\":\"redstone\", \"side\":\"right\", \"on\":true}</tool>\n"
        toolInstructions = toolInstructions .. "Example: <tool>{\"tool\":\"gps\"}</tool>\n"
        
        opts.context = (opts.context and (opts.context .. "\n\n") or "") .. toolInstructions
    end
    
    local text, err = chat(history, opts)
    if not text then return nil, err end
    
    local cleaned_text = ""
    local last_idx = 1
    
    -- Extract and execute tools
    for before, tool_json, after in text:gmatch("(.-)<tool>(.-)</tool>()") do
        cleaned_text = cleaned_text .. before
        last_idx = after
        
        local tool_data = textutils.unserializeJSON(tool_json)
        if type(tool_data) == "table" and tool_data.tool then
            print("[System: AI is executing tool '" .. tool_data.tool .. "']")
            
            local ok, tool_err = pcall(function()
                if tool_data.tool == "redstone" then
                    if type(tool_data.side) == "string" and type(tool_data.on) == "boolean" then
                        redstone.setOutput(tool_data.side, tool_data.on)
                        print(" -> Redstone on " .. tool_data.side .. " set to " .. tostring(tool_data.on))
                    end
                elseif tool_data.tool == "gps" then
                    local x, y, z = gps.locate(2)
                    if x then
                        print(" -> GPS Location: " .. x .. ", " .. y .. ", " .. z)
                        addMessage(history, "tool", "System: GPS Location is " .. x .. ", " .. y .. ", " .. z)
                    else
                        print(" -> GPS Location not available")
                        addMessage(history, "tool", "System: GPS Location not available")
                    end
                elseif tool_data.tool == "fs" then
                    -- Very basic fs read for demonstration
                    if type(tool_data.path) == "string" and tool_data.action == "read" then
                        attachFile(history, tool_data.path)
                        print(" -> Attached file " .. tool_data.path)
                    end
                else
                    print(" -> Unknown tool: " .. tool_data.tool)
                end
            end)
            if not ok then
                print(" -> Tool execution failed: " .. tostring(tool_err))
            end
        end
    end
    
    cleaned_text = cleaned_text .. text:sub(last_idx)
    
    -- Strip any remaining empty space
    cleaned_text = cleaned_text:gsub("^%s*(.-)%s*$", "%1")
    
    -- Ensure the history has the RAW text (with tools) so the AI remembers it called them
    addMessage(history, "assistant", text)
    
    return cleaned_text
end
