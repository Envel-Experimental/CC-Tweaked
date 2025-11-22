// SPDX-FileCopyrightText: 2017 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.apis.handles;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.core.filesystem.TrackingCloseable;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A file handle opened for reading in text mode.
 */
public class EncodedReadHandle extends AbstractHandle {
    private final BufferedReader reader;

    public EncodedReadHandle(SeekableByteChannel channel, TrackingCloseable closeable) {
        super(channel, closeable, false);
        this.reader = new BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8));
    }

    @Override
    @LuaFunction
    public final Object @Nullable [] read(Optional<Integer> countArg) throws LuaException {
        checkOpen();
        try {
            if (countArg.isPresent()) {
                int count = countArg.get();
                if (count < 0) throw new LuaException("Cannot read a negative number of bytes");
                if (count == 0) {
                    // Check for EOF
                    return reader.read(new char[0]) == -1 ? null : new Object[]{ "" };
                }

                char[] buffer = new char[count];
                int read = reader.read(buffer);
                if (read == -1) return null;

                // Convert the read text (Unicode) to UTF-8 bytes, then to Latin-1 string
                // so Lua receives the bytes directly.
                byte[] utf8Bytes = new String(buffer, 0, read).getBytes(StandardCharsets.UTF_8);
                return new Object[]{ new String(utf8Bytes, StandardCharsets.ISO_8859_1) };
            } else {
                int read = reader.read();
                if (read == -1) return null;

                byte[] utf8Bytes = String.valueOf((char) read).getBytes(StandardCharsets.UTF_8);
                return new Object[]{ new String(utf8Bytes, StandardCharsets.ISO_8859_1) };
            }
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    @LuaFunction
    public final Object @Nullable [] readAll() throws LuaException {
        checkOpen();
        try {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }

            byte[] utf8Bytes = builder.toString().getBytes(StandardCharsets.UTF_8);
            return new Object[]{ new String(utf8Bytes, StandardCharsets.ISO_8859_1) };
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    @LuaFunction
    public final Object @Nullable [] readLine(Optional<Boolean> withTrailingArg) throws LuaException {
        checkOpen();
        boolean withTrailing = withTrailingArg.orElse(false);
        try {
            String line = reader.readLine();
            if (line == null) return null;
            if (withTrailing) line += "\n";

            byte[] utf8Bytes = line.getBytes(StandardCharsets.UTF_8);
            return new Object[]{ new String(utf8Bytes, StandardCharsets.ISO_8859_1) };
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    @LuaFunction
    public final Object @Nullable [] seek(Optional<String> whence, Optional<Long> offset) throws LuaException {
         throw new LuaException("Redirecting seek on text files is not supported");
    }

    @Override
    @LuaFunction
    public final void close() throws LuaException {
        checkOpen();
        try {
            reader.close();
        } catch (IOException e) {
            // Ignore
        }
        super.close();
    }
}
