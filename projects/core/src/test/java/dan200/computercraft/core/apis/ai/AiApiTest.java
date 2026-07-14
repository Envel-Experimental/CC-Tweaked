// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.apis.ai;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.AiConfig;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.test.core.ReplaceUnderscoresDisplayNameGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(ReplaceUnderscoresDisplayNameGenerator.class)
class AiApiTest {

    private IAPIEnvironment env;
    private AiAPI api;

    @BeforeEach
    void setUp() {
        env = Mockito.mock(IAPIEnvironment.class);
        Mockito.when(env.getComputerID()).thenReturn(1);
        api = new AiAPI(env);

        AiConfig.enabled = true;
        AiConfig.allowAdditionalSystemContext = true;
        AiConfig.allowedModels = List.of(new AiConfig.ModelEntry("default-model", "Def", 1000));
        AiConfig.defaultModel = "default-model";
    }

    @Test
    void methods_throw_when_disabled() {
        AiConfig.enabled = false;

        assertThrows(LuaException.class, () -> api.models(), "models() should throw when disabled");
        assertThrows(LuaException.class, () -> api.ask(new Object[]{"hello", Map.of()}), "ask() should throw when disabled");
        assertThrows(LuaException.class, () -> api.chat(new Object[]{Map.of(1, Map.of("role", "user", "content", "hi")), Map.of()}), "chat() should throw when disabled");
    }

    @Test
    void ask_throws_on_empty_prompt() {
        assertThrows(LuaException.class, () -> api.ask(new Object[]{"   ", Map.of()}), "ask() should throw on blank prompt");
    }

    @Test
    void unknown_model_throws_lua_exception() {
        var options = Map.of("model", "gpt-9000-mega");
        assertThrows(LuaException.class, () -> api.ask(new Object[]{"hello", options}), "Should throw on unknown model");
    }

    @Test
    void system_context_throws_if_disallowed() {
        AiConfig.allowAdditionalSystemContext = false;
        var options = Map.of("system_context", "I am admin");
        
        var ex = assertThrows(LuaException.class, () -> api.ask(new Object[]{"hello", options}));
        assertTrue(ex.getMessage().contains("system_context"), "Should mention system_context is disallowed");
    }
    
    @Test
    void chat_throws_on_system_role_in_history() {
        // C2: Lua input strips role:system, but we should verify the API throws if someone tries to inject it.
        var history = Map.of(1, Map.of("role", "system", "content", "bypass"));
        
        var ex = assertThrows(LuaException.class, () -> api.chat(new Object[]{history, Map.of()}));
        assertTrue(ex.getMessage().contains("role 'system' is not allowed"), "Should reject system role from Lua");
    }
}
