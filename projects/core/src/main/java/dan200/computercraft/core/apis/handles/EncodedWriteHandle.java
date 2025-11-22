// SPDX-FileCopyrightText: 2017 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.apis.handles;

import dan200.computercraft.api.lua.Coerced;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.core.filesystem.TrackingCloseable;
import org.jspecify.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A file handle opened for writing in text mode.
 */
public class EncodedWriteHandle extends AbstractHandle {
    private final BufferedWriter writer;

    public EncodedWriteHandle(SeekableByteChannel channel, TrackingCloseable closeable) {
        super(channel, closeable, false);
        this.writer = new BufferedWriter(Channels.newWriter(channel, StandardCharsets.UTF_8));
    }

    @Override
    @LuaFunction
    public final void write(IArguments arguments) throws LuaException {
        checkOpen();
        try {
            String text = arguments.getString(0);
            writer.write(text);
        } catch (IOException e) {
            throw new LuaException(e.getMessage());
        }
    }

    @Override
    @LuaFunction
    public final void close() throws LuaException {
        checkOpen();
        try {
            writer.close();
        } catch (IOException e) {
            // Ignore
        }
        super.close();
    }

    @Override
    @LuaFunction
    public final void writeLine(Coerced<ByteBuffer> text) throws LuaException {
        // This method signature in AbstractHandle takes ByteBuffer (coerced).
        // But AbstractHandle's writeLine is designed for bytes.
        // We should overload or handle it.
        // Wait, AbstractHandle.writeLine takes Coerced<ByteBuffer>.
        // But in text mode, users usually pass strings to .writeLine() in Lua.
        // The LuaFunction annotation will handle conversion.
        // If the user passes a string to writeLine, it comes here?
        // Actually, AbstractHandle.writeLine uses Coerced<ByteBuffer>, which implies it expects bytes or string-as-bytes.

        // Let's override with a String signature if possible, or convert.
        // Since we extend AbstractHandle, we must match the signature or overload.
        // But LuaFunction calls are based on the method name.

        checkOpen();
        try {
            // Convert the bytebuffer back to string assuming UTF-8 if we must?
            // Or better, change the signature to accept String if that's allowed by the framework?
            // Looking at AbstractHandle, it uses Coerced<ByteBuffer>.
            // If I want to support "writeLine" with strings properly in text mode,
            // I should probably accept String.
            // But I can't change the signature if I'm overriding.
            // I'll just hide the parent method and define a new one?
            // No, Java doesn't work like that for Lua mapping unless I shadow it.

            // Let's just interpret the bytes as UTF-8 string.
             ByteBuffer buf = text.value();
             String s = StandardCharsets.UTF_8.decode(buf).toString();
             writer.write(s);
             writer.newLine();
        } catch (IOException e) {
            throw new LuaException(e.getMessage());
        }
    }

    // Redefine writeLine to accept String directly for better performance/correctness if the framework supports it?
    // The framework likely picks the best match. But AbstractHandle has it.
    // Let's try to stick to the AbstractHandle signature but decode.

    @Override
    @LuaFunction
    public final void flush() throws LuaException {
        checkOpen();
        try {
            writer.flush();
            super.flush(); // Flush the channel too
        } catch (IOException e) {
            throw new LuaException(e.getMessage());
        }
    }

    @Override
    @LuaFunction
    public final Object @Nullable [] seek(Optional<String> whence, Optional<Long> offset) throws LuaException {
        throw new LuaException("Redirecting seek on text files is not supported");
    }
}
