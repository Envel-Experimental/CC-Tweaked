package dan200.computercraft.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringUtilTest {

    @Test
    public void testDecodeMixedUTF8() {
        // Test plain ASCII
        assertEquals("Hello", StringUtil.decodeMixedUTF8("Hello"));

        // Test valid UTF-8 (Russian 'Р') -> D0 A0
        // In Java chars: \u00D0\u00A0
        String utf8Str = "\u00D0\u00A0\u00D1\u0083\u00D1\u0081\u00D1\u0081\u00D0\u00BA\u00D0\u00B8\u00D0\u00B9";
        // Expected: "Русский"
        assertEquals("Русский", StringUtil.decodeMixedUTF8(utf8Str));

        // Test mixed: "Hello " + UTF-8
        assertEquals("Hello Русский", StringUtil.decodeMixedUTF8("Hello " + utf8Str));

        // Test invalid UTF-8 (should remain as is)
        // C0 without continuation
        assertEquals("\u00C0\u0020", StringUtil.decodeMixedUTF8("\u00C0 "));

        // Invalid continuation
        assertEquals("\u00D0\u0020", StringUtil.decodeMixedUTF8("\u00D0 ")); // D0 space (20 is not 80-BF)

        // CC graphics characters (example: 128-159 are often mapped to control or unused in Latin-1 but CC uses them)
        // If we have bytes that form invalid UTF-8, they should remain.
        // 0x80 0x81 -> \u0080\u0081
        assertEquals("\u0080\u0081", StringUtil.decodeMixedUTF8("\u0080\u0081"));
    }
}
