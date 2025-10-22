// SPDX-FileCopyrightText: 2023 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0
package dan200.computercraft.core.apis.handles;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class EncodedReadWriteHandle {
    private final EncodedReadHandle reader;
    private final EncodedWriteHandle writer;

    public EncodedReadWriteHandle(AbstractHandle handle) {
        this.reader = new EncodedReadHandle(handle);
        this.writer = new EncodedWriteHandle(handle);
    }

    @LuaFunction
    public final void close() throws LuaException {
        reader.close();
    }

    @LuaFunction
    public final Object @Nullable [] read(Optional<Integer> countArg) throws LuaException {
        return reader.read(countArg);
    }

    @LuaFunction
    public final Object @Nullable [] readAll() throws LuaException {
        return reader.readAll();
    }

    @LuaFunction
    public final Object @Nullable [] readLine(Optional<Boolean> withTrailingArg) throws LuaException {
        return reader.readLine(withTrailingArg);
    }

    @LuaFunction
    public final void write(IArguments arguments) throws LuaException {
        writer.write(arguments);
    }

    @LuaFunction
    public final void writeLine(IArguments arguments) throws LuaException {
        writer.writeLine(arguments);
    }

    @LuaFunction
    public final void flush() throws LuaException {
        writer.flush();
    }

    @LuaFunction
    public final Object @Nullable [] seek(Optional<String> whence, Optional<Long> offset) throws LuaException {
        return reader.seek(whence, offset);
    }
}
