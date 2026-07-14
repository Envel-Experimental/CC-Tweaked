// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.util;

import dan200.computercraft.test.core.ReplaceUnderscoresDisplayNameGenerator;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Cyrillic support additions in {@link StringUtil}.
 */
@DisplayNameGeneration(ReplaceUnderscoresDisplayNameGenerator.class)
class CyrillicStringUtilTest {

    // --- isTypableChar ---

    @ParameterizedTest
    @ValueSource(ints = { 0x0400, 0x041F, 0x0430, 0x044F, 0x04FF }) // А, П, а, я, ӿ
    void isTypableChar_accepts_cyrillic(int cp) {
        assertTrue(StringUtil.isTypableChar(cp),
            "U+" + Integer.toHexString(cp) + " must be typable");
    }

    @ParameterizedTest
    @ValueSource(ints = { 0x20, 0x41, 0x7E, 0xA0, 0xFE }) // space, A, ~, nbsp, þ
    void isTypableChar_accepts_standard_printable(int cp) {
        assertTrue(StringUtil.isTypableChar(cp));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0x00, 0x01, 0x1F, 0x7F, 0x0500, 0xFFFF }) // control, DEL, beyond Cyrillic
    void isTypableChar_rejects_non_typable(int cp) {
        assertFalse(StringUtil.isTypableChar(cp),
            "U+" + Integer.toHexString(cp) + " must NOT be typable");
    }

    // --- unicodeToTerminal ---

    @ParameterizedTest
    @ValueSource(ints = { 0x0400, 0x041F, 0x0430, 0x044F, 0x04FF })
    void unicodeToTerminal_passes_through_cyrillic(int cp) {
        assertEquals(cp, StringUtil.unicodeToTerminal(cp),
            "Cyrillic U+" + Integer.toHexString(cp) + " must pass through as-is");
    }

    @ParameterizedTest
    @ValueSource(ints = { 0x41, 0x61, 0x20, 0xAE, 0xFE }) // A, a, space, ®, þ
    void unicodeToTerminal_passes_through_latin(int cp) {
        assertEquals(cp, StringUtil.unicodeToTerminal(cp));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0x0500, 0x2603, 0x1F600, 0xFFFF }) // beyond Cyrillic block
    void unicodeToTerminal_returns_minus1_for_unsupported(int cp) {
        assertEquals(-1, StringUtil.unicodeToTerminal(cp),
            "Unsupported codepoint U+" + Integer.toHexString(cp) + " must map to -1");
    }

    // --- Boundary: edge codepoints of Cyrillic block ---

    @Test
    void cyrillic_block_boundaries() {
        // U+03FF is just before Cyrillic — not typable
        assertFalse(StringUtil.isTypableChar(0x03FF));
        assertEquals(-1, StringUtil.unicodeToTerminal(0x03FF));

        // U+0400 is the first Cyrillic — must pass
        assertTrue(StringUtil.isTypableChar(0x0400));
        assertEquals(0x0400, StringUtil.unicodeToTerminal(0x0400));

        // U+04FF is the last Cyrillic — must pass
        assertTrue(StringUtil.isTypableChar(0x04FF));
        assertEquals(0x04FF, StringUtil.unicodeToTerminal(0x04FF));

        // U+0500 is just after Cyrillic — not typable
        assertFalse(StringUtil.isTypableChar(0x0500));
        assertEquals(-1, StringUtil.unicodeToTerminal(0x0500));
    }

    // --- getClipboardString with Cyrillic ---

    @Test
    void getClipboardString_includes_cyrillic() {
        var result = StringUtil.getClipboardString("Привет мир");
        assertNotNull(result);
        assertTrue(result.remaining() > 0,
            "Cyrillic clipboard string must not be empty");
    }

    @Test
    void getClipboardString_strips_newline_in_cyrillic() {
        // Multi-line with Cyrillic: only first line returned.
        var result = StringUtil.getClipboardString("Привет\nМир");
        assertNotNull(result);
        // Decode UTF-16 LE back to String to verify content.
        var bytes = new byte[result.remaining()];
        result.duplicate().get(bytes);
        // Must contain "Привет" (first line only) encoded as UTF-16 LE.
        assertTrue(result.remaining() >= "Привет".length() * 2,
            "Clipboard must contain at least the Cyrillic first line");
    }
}
