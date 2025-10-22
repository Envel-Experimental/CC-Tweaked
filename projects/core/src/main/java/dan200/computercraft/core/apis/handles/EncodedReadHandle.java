// SPDX-FileCopyrightText: 2023 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0
package dan200.computercraft.core.apis.handles;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class EncodedReadHandle {
    private final AbstractHandle handle;

    public EncodedReadHandle(AbstractHandle handle) {
        this.handle = handle;
    }

    private static Object[] decode(Object[] value) {
        if (value == null || value.length == 0 || value[0] == null) return value;

        if (value[0] instanceof byte[] bytes) {
            value[0] = new String(bytes, StandardCharsets.UTF_8);
        } else if (value[0] instanceof ByteBuffer buffer) {
            var bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            value[0] = new String(bytes, StandardCharsets.UTF_8);
        }

        return value;
    }

    @LuaFunction
    public final void close() throws LuaException {
        handle.close();
    }

    @LuaFunction
    public final Object @Nullable [] read(Optional<Integer> countArg) throws LuaException {
        var result = handle.read(countArg);
        return result == null ? null : decode(result);
    }

    @LuaFunction
    public final Object @Nullable [] readAll() throws LuaException {
        var result = handle.readAll();
        return result == null ? null : decode(result);
    }

    @LuaFunction
    public final Object @Nullable [] readLine(Optional<Boolean> withTrailingArg) throws LuaException {
        var result = handle.readLine(withTrailingArg);
        return result == null ? null : decode(result);
    }

    @LuaFunction
    public final Object @Nullable [] seek(Optional<String> whence, Optional<Long> offset) throws LuaException {
        return handle.seek(whence, offset);
    }
}
