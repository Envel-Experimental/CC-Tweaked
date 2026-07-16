# CC:Tweaked AI API

The `ai` API allows your ComputerCraft computers and turtles to communicate with an AI language model through a proxy server. This feature must be enabled and configured by the server administrator.

To use the AI API, you can simply use the `ai` global variable in your Lua program:
```lua
-- No need to require("ai"), it's available globally!
local reply, err = ai.ask("What is the capital of France?")
```

---

## Quick Start

### Basic Question
Send a simple question and wait for the response:
```lua
local reply, err = ai.ask("What is the capital of France?")
if reply then
    print(reply)
else
    print("Error:", err)
end
```

### Multi-turn Chat
Use the conversation builder for an easy way to manage chat history:
```lua
local conv = ai.conversation({ model = "gpt-4o-mini", temperature = 0.8 })
conv:say("I'm building a turtle program.")
print(conv:reply())

conv:say("How do I make the turtle move forward?")
print(conv:reply())
```

### Async Requests
Send a request without blocking your program:
```lua
local id = ai.askAsync("Tell me a joke")

-- Do some other work...
print("Waiting for response...")

local ok, text = ai.awaitId(id, 15) -- Wait up to 15 seconds
if ok then
    print(text)
else
    print("Failed:", text)
end
```

### Explaining Errors
Ask the AI to explain a Lua runtime error:
```lua
local ok, err = pcall(function() error("something went wrong:42") end)
if not ok then
    local explanation = ai.explainError(err)
    print("AI says:", explanation)
end
```

---

## Events

When using async functions like `ai.askAsync()` or `ai.chatAsync()`, the system will yield events when the response arrives or fails.

- `ai_response`
  - **Arg 1 (`number`)**: The request ID.
  - **Arg 2 (`string`)**: The AI's text response.
- `ai_error`
  - **Arg 1 (`number`)**: The request ID.
  - **Arg 2 (`string`)**: The human-readable error reason (e.g., rate limit, network timeout).

---

## API Reference

### Environment Queries

#### `ai.isEnabled()`
Returns whether the AI API is enabled on the server.
- **Returns**: `boolean`

#### `ai.models()`
Returns a list of AI models available on the server.
- **Returns**: A table array where each entry is `{ id = "...", display_name = "...", max_context_tokens = 1234 }`.

#### `ai.defaultModel()`
Returns the default model ID used when no model is specified.
- **Returns**: `string`

#### `ai.isToolCallingEnabled()`
Returns whether the server allows the AI to execute tools (interact with the world).
- **Returns**: `boolean`

#### `ai.maxConversationHistory()`
Returns the maximum allowed depth (number of messages) for conversation history.
- **Returns**: `number`

---

### Blocking Functions

#### `ai.ask(prompt, [opts])`
Sends a one-shot question and blocks until the response arrives.
- **Arguments**:
  - `prompt` (`string`): The question to ask.
  - `opts` (`table`, optional): Options table containing `model`, `temperature`, `max_tokens`, `context`, `tools`, `timeout`.
- **Returns**:
  - `string|nil`: The response text.
  - `string|nil`: Error reason on failure.

#### `ai.chat(messages, [opts])`
Sends a multi-turn conversation and blocks until the response arrives.
- **Arguments**:
  - `messages` (`table`): Array of `{ role = "...", content = "..." }` tables.
  - `opts` (`table`, optional): Options table.
- **Returns**:
  - `string|nil`: The response text.
  - `string|nil`: Error reason on failure.

#### `ai.explainError(error, [timeout])`
Asks the AI to explain a Lua runtime error, blocking until the explanation arrives.
- **Arguments**:
  - `error` (`string`): The error string from `pcall` or `_G.error`.
  - `timeout` (`number`, optional): Seconds to wait (default 20).
- **Returns**:
  - `string|nil`: The explanation text.
  - `string|nil`: Error reason on failure.

---

### Async Functions

#### `ai.askAsync(prompt, [opts])`
Sends a one-shot question immediately and returns a request ID without blocking.
- **Arguments**: Same as `ai.ask()`.
- **Returns**: `number` (Request ID).

#### `ai.chatAsync(messages, [opts])`
Sends a multi-turn conversation immediately and returns a request ID without blocking.
- **Arguments**: Same as `ai.chat()`.
- **Returns**: `number` (Request ID).

#### `ai.awaitId(id, [timeout])`
Waits for the response to a specific request ID (blocks until the `ai_response` or `ai_error` event fires).
- **Arguments**:
  - `id` (`number`): The request ID from an async call.
  - `timeout` (`number`, optional): Seconds to wait (default 30).
- **Returns**:
  - `string|nil`: The response text.
  - `string|nil`: Error reason on failure.

---

### Utilities

#### `ai.conversation([opts])`
Builds a streaming chat pipeline for easier multi-turn dialogues.
- **Arguments**:
  - `opts` (`table`, optional): Default options applied to all messages in the conversation.
- **Returns**: A conversation object with the following methods:
  - `conv:say(content)`: Adds a user message.
  - `conv:inject(content)`: Adds an assistant message (e.g., for few-shot prompting).
  - `conv:reply([timeout])`: Sends the conversation, waits for a reply, and appends it to history. Returns the text or an error.
  - `conv:history()`: Returns a copy of the current history.
  - `conv:reset()`: Clears the history.

#### `ai.addMessage(history, role, content)`
Convenience function to append a message to a history table and truncate if it exceeds `maxConversationHistory()`.
- **Arguments**:
  - `history` (`table`): The message history table (modified in place).
  - `role` (`string`): `"user"`, `"assistant"`, or `"tool"`.
  - `content` (`string`): The message text.

#### `ai.attachFile(history_or_path, path)`
Reads a local file and either attaches it to the provided history or returns its content formatted in markdown.
- **Arguments**:
  - `history_or_path` (`table` or `string`): The history table, or the file path if no history is provided.
  - `path` (`string`, optional): The file path if a history table was provided.
- **Returns**: `string` (Formatted file content).

#### `ai.executeWithTools(history, opts)`
Executes a prompt, enabling the AI to execute tools (`redstone`, `gps`, `fs`) if it returns specific `<tool>...</tool>` tags in its response. 
- **Arguments**:
  - `history` (`table`): The conversation history.
  - `opts` (`table`): Request options. Must include a `tools` array (e.g., `{"redstone", "gps", "fs"}`).
- **Returns**: 
  - `string|nil`: The cleaned text response (tool tags removed).
  - `string|nil`: Error reason.
