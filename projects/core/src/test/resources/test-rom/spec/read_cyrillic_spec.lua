local expect = require "cc.expect".expect

describe("read() with cyrillic", function()
    it("handles cyrillic text correctly with backspace", function()
        -- Mock term
        local old_term = term.current()
        local win = window.create(old_term, 1, 1, 50, 19)
        term.redirect(win)
        
        -- Start a coroutine for read()
        local co = coroutine.create(function()
            local res = read()
            coroutine.yield("RESULT", res)
        end)
        
        -- Run until it waits for events
        local ok, filter = coroutine.resume(co)
        
        -- Type "А" (U+0410) and "Б" (U+0411), then backspace, then enter
        os.queueEvent("char", "\208\144")
        os.queueEvent("char", "\208\145")
        os.queueEvent("key", keys.backspace, false)
        os.queueEvent("key_up", keys.backspace)
        os.queueEvent("key", keys.enter, false)
        os.queueEvent("key_up", keys.enter)
        
        -- Drain events and pass to read()
        local result
        while coroutine.status(co) ~= "dead" do
            local event = { os.pullEvent() }
            if filter == nil or event[1] == filter then
                ok, filter, result = coroutine.resume(co, unpack(event))
                if not ok then 
                    term.redirect(old_term)
                    error(filter) 
                end
                
                -- Check if it yielded the final result
                if filter == "RESULT" then
                    term.redirect(old_term)
                    if result ~= "\208\144" then
                        error("Expected '\\208\\144', got " .. tostring(result))
                    end
                    return
                end
            end
        end
        term.redirect(old_term)
        error("read() did not return")
    end)
end)
