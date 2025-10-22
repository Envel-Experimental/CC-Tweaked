// SPDX-FileCopyrightText: 2023 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0
package dan200.computercraft.core.apis.handles;

import dan200.computercraft.api.lua.Coerced;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class EncodedWriteHandle {
    private final AbstractHandle handle;

    public EncodedWriteHandle(AbstractHandle handle) {
        this.handle = handle;
    }

    @LuaFunction
    public final void close() throws LuaException {
        handle.close();
    }

    @LuaFunction
    public final void write(IArguments arguments) throws LuaException {
        handle.write(new EncodedArguments(arguments));
    }

    @LuaFunction
    public final void writeLine(IArguments arguments) throws LuaException {
        handle.writeLine(new Coerced<>(new EncodedArguments(arguments).getBytes(0)));
    }

    @LuaFunction
    public final void flush() throws LuaException {
        handle.flush();
    }

    @LuaFunction
    public final Object @Nullable [] seek(Optional<String> whence, Optional<Long> offset) throws LuaException {
        return handle.seek(whence, offset);
    }

    private static class EncodedArguments implements IArguments {
        private final IArguments original;
        private @Nullable String string;
        private @Nullable ByteBuffer encoded;

        EncodedArguments(IArguments original) {
            this.original = original;
        }

        private String getString() throws LuaException {
            if (string == null) {
                var value = original.get(0);
                if (value instanceof byte[] bytes) {
                    string = new String(bytes, StandardCharsets.UTF_8);
                } else {
                    string = original.getString(0);
                }
            }
            return string;
        }

        @Override
        public int count() {
            return original.count();
        }

        @Override
        public Object[] getAll() throws LuaException {
            return original.getAll();
        }

        @Override
        public @Nullable Object get(int index) throws LuaException {
            return original.get(index);
        }

        @Override
        public String getType(int index) {
            return original.getType(index);
        }

        @Override
        public IArguments drop(int count) {
            return original.drop(count);
        }

        @Override
        public ByteBuffer getBytes(int index) throws LuaException {
            if (encoded == null) encoded = ByteBuffer.wrap(getString().getBytes(StandardCharsets.UTF_8));
            return encoded;
        }
    }
}
