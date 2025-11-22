package dan200.computercraft.core.apis.handles;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.filesystem.TrackingCloseable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EncodedHandleTest {

    @Test
    public void testWriteAndReadUtf8() throws Exception {
        Path tempFile = Files.createTempFile("cc_test", ".txt");
        tempFile.toFile().deleteOnExit();

        SeekableByteChannel writeChannel = Files.newByteChannel(tempFile, StandardOpenOption.WRITE);
        EncodedWriteHandle writer = new EncodedWriteHandle(writeChannel, new TrackingCloseable.Impl(writeChannel));

        // Lua sends bytes D0 A0 (Russian 'Р') as a string.
        // In Java string this appears as chars 00D0 00A0.
        String luaString = "\u00D0\u00A0";

        IArguments args = new IArguments() {
            @Override
            public int count() { return 1; }

            @Override
            public @Nullable Object get(int index) throws LuaException {
                if (index == 0) return luaString;
                return null;
            }

            @Override
            public String getType(int index) { return "string"; }

            @Override
            public IArguments drop(int count) { return this; }
        };

        writer.write(args);
        writer.close();

        // Check actual file content (should be UTF-8 encoded: C3 90 C2 A0 is NOT correct for 'Р' if we wrote double-encoded)
        // Wait.
        // Input: "\u00D0\u00A0" (Latin-1 view of D0 A0).
        // decodeMixedUTF8 converts D0 A0 -> 'Р' (U+0420).
        // BufferedWriter (UTF-8) writes 'Р' -> D0 A0.
        // So file content should be bytes D0 A0.
        byte[] fileContent = Files.readAllBytes(tempFile);
        assertArrayEquals(new byte[] { (byte)0xD0, (byte)0xA0 }, fileContent);

        // Now read back
        SeekableByteChannel readChannel = Files.newByteChannel(tempFile, StandardOpenOption.READ);
        EncodedReadHandle reader = new EncodedReadHandle(readChannel, new TrackingCloseable.Impl(readChannel));

        // Reader reads D0 A0 -> 'Р' (U+0420).
        // Reader converts 'Р' -> UTF-8 bytes (D0 A0).
        // Reader returns String(D0 A0 as Latin-1) -> "\u00D0\u00A0".
        Object[] result = reader.readAll();
        assertEquals(luaString, result[0]);
        reader.close();
    }
}
