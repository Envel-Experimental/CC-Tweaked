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
            // The text received here is encoded in Latin-1 (from Lua bytes).
            // We need to decode the mixed UTF-8 to get the proper Unicode characters.
            String text = arguments.getString(0);
            String decoded = dan200.computercraft.core.util.StringUtil.decodeMixedUTF8(text);
            writer.write(decoded);
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
        checkOpen();
        try {
            // We receive the raw bytes from Lua. We need to interpret them as UTF-8 string.
            // But wait, AbstractHandle's writeLine gets Coerced<ByteBuffer>, which is the raw bytes of the string.
            // If Lua sent "\208\160", we get bytes 0xD0 0xA0.
            // If we decode them as UTF-8, we get 'Р'.
            // BUT, decodeMixedUTF8 logic expects a String (chars 0-255).
            // ByteBuffer contains bytes.
            // We can create a String from bytes using ISO-8859-1 to match what decodeMixedUTF8 expects,
            // OR just use StandardCharsets.UTF_8.decode(buf) directly if we assume valid UTF-8.
            // decodeMixedUTF8 is robust against invalid UTF-8. StandardCharsets.UTF_8.decode might replace with REPLACEMENT CHAR.

            // Let's use decodeMixedUTF8 for consistency.
            ByteBuffer buf = text.value();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            String raw = new String(bytes, StandardCharsets.ISO_8859_1);
            String decoded = dan200.computercraft.core.util.StringUtil.decodeMixedUTF8(raw);

            writer.write(decoded);
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
