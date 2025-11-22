package dan200.computercraft.core.apis.handles;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.filesystem.TrackingCloseable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EncodedHandleTest {

    @Test
    public void testWriteAndReadUtf8() throws Exception {
        Path tempFile = Files.createTempFile("cc_test", ".txt");
        tempFile.toFile().deleteOnExit();

        SeekableByteChannel writeChannel = Files.newByteChannel(tempFile, StandardOpenOption.WRITE);
        EncodedWriteHandle writer = new EncodedWriteHandle(writeChannel, new TrackingCloseable.Impl(writeChannel));

        String russianText = "Привет, мир!";
        IArguments args = new IArguments() {
            @Override
            public int count() { return 1; }

            @Override
            public @Nullable Object get(int index) throws LuaException {
                if (index == 0) return russianText;
                return null;
            }

            @Override
            public String getType(int index) { return "string"; }

            @Override
            public IArguments drop(int count) { return this; } // Mock impl
        };

        writer.write(args);
        writer.close();

        SeekableByteChannel readChannel = Files.newByteChannel(tempFile, StandardOpenOption.READ);
        EncodedReadHandle reader = new EncodedReadHandle(readChannel, new TrackingCloseable.Impl(readChannel));

        Object[] result = reader.readAll();
        assertEquals(russianText, result[0]);
        reader.close();
    }
}
