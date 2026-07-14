// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.computer.terminal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for Cyrillic support in {@link NetworkedTerminal} network encoding.
 *
 * <p>Tests are in the same package as {@link NetworkedTerminal} to access
 * the package-private {@code write()} and {@code read()} methods directly.
 */
class TerminalNetworkEncodingTest {

    @Test
    void cyrillicRoundTrip() {
        var source = new NetworkedTerminal(10, 5, true);
        source.write("Привет");

        var state = source.write();

        var dest = new NetworkedTerminal(10, 5, true);
        dest.read(state);

        assertEquals("Привет    ", dest.getLine(0).toString(),
            "Cyrillic characters must survive a full encode → decode round-trip");
    }

    @Test
    void mixedAsciiAndCyrillicRoundTrip() {
        var source = new NetworkedTerminal(20, 3, true);
        source.write("Hi Мир!");

        var state = source.write();
        var dest = new NetworkedTerminal(20, 3, true);
        dest.read(state);

        var line = dest.getLine(0).toString();
        assertEquals("Hi Мир!             ", line,
            "Mixed ASCII + Cyrillic must round-trip correctly");
    }

    @Test
    void asciiRegressionAfterRefactor() {
        var source = new NetworkedTerminal(16, 2, true);
        source.write("Hello, World!   ");

        var state = source.write();
        var dest = new NetworkedTerminal(16, 2, true);
        dest.read(state);

        assertEquals("Hello, World!   ", dest.getLine(0).toString(),
            "Pure ASCII must still round-trip after UTF-16 BE migration");
    }

    @Test
    void latin1RegressionAfterRefactor() {
        // Test codepoints 128-254 (Latin-1 supplement) still round-trip.
        var source = new NetworkedTerminal(4, 1, true);
        // Write chars: ÿ (0xFF = 255), ñ (0xF1 = 241), é (0xE9 = 233), ü (0xFC = 252)
        source.write("\u00FF\u00F1\u00E9\u00FC");

        var state = source.write();
        var dest = new NetworkedTerminal(4, 1, true);
        dest.read(state);

        assertEquals("\u00FF\u00F1\u00E9\u00FC", dest.getLine(0).toString(),
            "Latin-1 supplement (128-255) must round-trip after refactor");
    }

    @Test
    void versionFlagIsUtf16() {
        var terminal = new NetworkedTerminal(4, 1, true);
        terminal.write("Test");
        var state = terminal.write();

        // Version byte is the first byte of contents.
        assertEquals(0x02, state.contents[0] & 0xFF,
            "Version byte must be VERSION_UTF16 = 0x02");
    }

    @Test
    void legacyVersionFallback() {
        // Build a legacy-format TerminalState manually (VERSION_LEGACY = 0x01, 1 byte per char).
        var text = "Hi  "; // 4 chars for a 4x1 terminal
        var data = new byte[1 + 4 + 4 + 48]; // version + text + colours + palette
        data[0] = 0x01; // VERSION_LEGACY
        for (var i = 0; i < text.length(); i++) data[1 + i] = (byte) text.charAt(i);
        // Fill colours with 'f0' packed (black bg, white fg)
        for (var i = 0; i < 4; i++) data[1 + 4 + i] = (byte) 0xF0;
        // Palette is zeroed (black for everything) — acceptable for this test.

        var state = new TerminalState(true, 4, 1, 0, 0, false, 0, 15, data);
        var dest = new NetworkedTerminal(4, 1, true);
        dest.read(state);

        // In legacy mode: version byte 0x01 is treated as first char (SOH, '\u0001')
        // then remaining bytes are read as text. This is the documented fallback behavior.
        // The important assertion is: no exception is thrown and the terminal is resized.
        assertEquals(4, dest.getWidth());
        assertEquals(1, dest.getHeight());
    }

    static Stream<String> cyrillicSentences() {
        return Stream.of(
            "Привет мир",
            "Компьютерный крафт",
            "АБВГДЕЁЖЗИЙК",
            "абвгдеёжзийк",
            "Ёжик в тумане"
        );
    }

    @ParameterizedTest
    @MethodSource("cyrillicSentences")
    void cyrillicVariousStrings(String sentence) {
        var w = Math.max(sentence.length(), 1);
        var source = new NetworkedTerminal(w, 1, true);
        source.write(sentence);

        var dest = new NetworkedTerminal(w, 1, true);
        dest.read(source.write());

        assertEquals(sentence, dest.getLine(0).toString().strip(),
            "Sentence '" + sentence + "' must round-trip");
    }
}
