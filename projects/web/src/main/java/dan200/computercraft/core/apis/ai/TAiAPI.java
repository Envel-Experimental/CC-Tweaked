// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.apis.ai;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.core.apis.IAPIEnvironment;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Replaces {@link AiAPI} with a stub implementation for the web emulator,
 * as the full AI API uses threads and sockets not supported by TeaVM.
 */
public class TAiAPI implements ILuaAPI {

    public TAiAPI(IAPIEnvironment env) {
    }

    @Override
    public String[] getNames() {
        return new String[]{"ai"};
    }

    private void requireEnabled() throws LuaException {
        throw new LuaException("AI API is disabled on this server (web emulator)");
    }

    @LuaFunction
    public final boolean isToolCallingEnabled() {
        return false;
    }

    @LuaFunction
    public final int maxConversationHistory() {
        return 0;
    }

    @LuaFunction
    public final List<Map<String, Object>> models() throws LuaException {
        requireEnabled();
        return Collections.emptyList();
    }

    @LuaFunction
    public final int ask(IArguments args) throws LuaException {
        requireEnabled();
        return 0;
    }

    @LuaFunction
    public final int chat(IArguments args) throws LuaException {
        requireEnabled();
        return 0;
    }

    @LuaFunction
    public final int explainError(String errorString) throws LuaException {
        requireEnabled();
        return 0;
    }

    @LuaFunction
    public final Object[] await(IArguments args) throws LuaException {
        requireEnabled();
        return new Object[0];
    }

    @LuaFunction
    public final boolean isEnabled() {
        return false;
    }

    @LuaFunction
    public final String defaultModel() throws LuaException {
        requireEnabled();
        return "";
    }
}
