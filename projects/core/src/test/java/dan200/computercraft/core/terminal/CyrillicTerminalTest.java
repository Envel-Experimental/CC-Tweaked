// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.terminal;

import dan200.computercraft.test.core.ReplaceUnderscoresDisplayNameGenerator;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that Cyrillic characters survive the terminal write → getLine path
 * and that ASCII / Latin-1 regression is clean.
 *
 * <p>These are pure {@link Terminal} unit tests — no network, no rendering.
 */
@DisplayNameGeneration(ReplaceUnderscoresDisplayNameGenerator.class)
class CyrillicTerminalTest {

    @Test
    void terminal_write_stores_cyrillic_characters() {
        var terminal = new Terminal(10, 3, true);
        terminal.write("Привет");

        var line = terminal.getLine(0).toString();
        assertEquals("Привет    ", line,
            "term.write(\"Привет\") must store Cyrillic, not '??????'");
    }

    @Test
    void terminal_write_stores_mixed_ascii_and_cyrillic() {
        var terminal = new Terminal(12, 1, true);
        terminal.write("Hi Мир!");

        assertEquals("Hi Мир!     ", terminal.getLine(0).toString());
    }

    @Test
    void terminal_write_full_cyrillic_sentence() {
        var text = "Компьютер";
        var terminal = new Terminal(text.length(), 1, true);
        terminal.write(text);

        assertEquals(text, terminal.getLine(0).toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "Привет", "Мир", "АБВГД", "абвгд", "Ёжик" })
    void terminal_write_parameterized_cyrillic(String word) {
        var terminal = new Terminal(word.length(), 1, true);
        terminal.write(word);
        assertEquals(word, terminal.getLine(0).toString(),
            "'" + word + "' must be stored and retrieved exactly");
    }

    // --- ASCII / Latin-1 regression ---

    @Test
    void ascii_regression_after_refactor() {
        var terminal = new Terminal(13, 1, true);
        terminal.write("Hello, World!");
        assertEquals("Hello, World!", terminal.getLine(0).toString());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0x20, 0x41, 0x5A, 0x61, 0x7A, 0x7E }) // printable ASCII range
    void ascii_codepoints_stored_correctly(int cp) {
        var terminal = new Terminal(1, 1, true);
        terminal.write(String.valueOf((char) cp));
        assertEquals(String.valueOf((char) cp), terminal.getLine(0).toString().substring(0, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0xA0, 0xC9, 0xE9, 0xF1, 0xFE }) // Latin-1 supplement (non-Cyrillic)
    void latin1_supplement_regression(int cp) {
        var terminal = new Terminal(1, 1, true);
        terminal.write(String.valueOf((char) cp));
        assertEquals(String.valueOf((char) cp), terminal.getLine(0).toString().substring(0, 1),
            "Latin-1 supplement U+" + Integer.toHexString(cp) + " must still render correctly");
    }

    // --- Cursor movement ---

    @Test
    void cursor_advances_after_cyrillic_write() {
        var terminal = new Terminal(10, 1, true);
        terminal.write("АБВ");
        assertEquals(3, terminal.getCursorX(),
            "Cursor must advance by 3 after writing 3 Cyrillic chars");
    }

    // --- Multi-line ---

    @Test
    void cyrillic_on_multiple_lines() {
        var terminal = new Terminal(8, 3, true);
        terminal.write("Привет  ");
        terminal.setCursorPos(0, 1);
        terminal.write("Мир     ");
        terminal.setCursorPos(0, 2);
        terminal.write("Тест    ");

        assertEquals("Привет  ", terminal.getLine(0).toString());
        assertEquals("Мир     ", terminal.getLine(1).toString());
        assertEquals("Тест    ", terminal.getLine(2).toString());
    }
}
