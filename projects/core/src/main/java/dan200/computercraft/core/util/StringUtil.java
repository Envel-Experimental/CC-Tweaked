// SPDX-FileCopyrightText: 2017 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.util;

import dan200.computercraft.core.computer.ComputerEvents;

import java.nio.ByteBuffer;

public final class StringUtil {
    public static final int MAX_PASTE_LENGTH = 512;

    private StringUtil() {
    }

    /**
     * Convert a Unicode character to a terminal one.
     *
     * @param chr The Unicode character.
     * @return The terminal character in the range [0, 0xFFFF] if valid for the terminal,
     *         or {@code -1} if it cannot be displayed.
     */
    public static int unicodeToTerminal(int chr) {
        // ASCII and latin1 map to themselves
        if (chr == 0 || chr == '\t' || chr == '\n' || chr == '\r' || (chr >= ' ' && chr <= '~') || (chr >= 160 && chr <= 255)) {
            return chr;
        }

        // Full Cyrillic block U+0400–U+04FF: pass through directly.
        // These map to slots 256–511 in the extended 512×256 font atlas.
        if (chr >= 0x0400 && chr <= 0x04FF) {
            return chr;
        }

        // Teletext block mosaics are *fairly* contiguous.
        if (chr >= 0x1FB00 && chr <= 0x1FB13) return chr + (129 - 0x1fb00);
        if (chr >= 0x1FB14 && chr <= 0x1FB1D) return chr + (150 - 0x1fb14);

        // Everything else is just a manual lookup.
        return unicodeToCraftOsFallback(chr);
    }

    private static int unicodeToCraftOsFallback(int c) {
        return switch (c) {
            case 0x263A -> 1;
            case 0x263B -> 2;
            case 0x2665 -> 3;
            case 0x2666 -> 4;
            case 0x2663 -> 5;
            case 0x2660 -> 6;
            case 0x2022 -> 7;
            case 0x25D8 -> 8;
            case 0x2642 -> 11;
            case 0x2640 -> 12;
            case 0x266A -> 14;
            case 0x266B -> 15;
            case 0x25BA -> 16;
            case 0x25C4 -> 17;
            case 0x2195 -> 18;
            case 0x203C -> 19;
            case 0x25AC -> 22;
            case 0x21A8 -> 23;
            case 0x2191 -> 24;
            case 0x2193 -> 25;
            case 0x2192 -> 26;
            case 0x2190 -> 27;
            case 0x221F -> 28;
            case 0x2194 -> 29;
            case 0x25B2 -> 30;
            case 0x25BC -> 31;
            case 0x1FB99 -> 127;
            case 0x258C -> 149;
            default -> -1;
        };
    }

    /**
     * Check if a character is capable of being input and passed to a {@linkplain ComputerEvents#charTyped
     * "char" event}. Accepts ASCII printable, Latin-1, and Cyrillic (U+0400–U+04FF).
     *
     * @param chr The character to check (as raw byte, legacy overload).
     * @return Whether this character can be typed.
     */
    public static boolean isTypableChar(byte chr) {
        return isTypableChar(chr & 0xFF);
    }

    /**
     * Check if a character is capable of being input and passed to a {@linkplain ComputerEvents#charTyped
     * "char" event}. Accepts ASCII printable, Latin-1, and Cyrillic (U+0400–U+04FF).
     *
     * @param chr The character to check (Unicode codepoint).
     * @return Whether this character can be typed.
     */
    public static boolean isTypableChar(int chr) {
        if (chr <= 0 || chr == '\r' || chr == '\n') return false;
        // ASCII printable (exclude control chars like 1-31 and 127)
        if (chr >= 32 && chr <= 126) return true;
        // Latin-1 extended
        if (chr >= 160 && chr <= 255) return true;
        // Cyrillic block — supported via extended font atlas
        if (chr >= 0x0400 && chr <= 0x04FF) return true;
        return false;
    }

    private static boolean isAllowedInLabel(char c) {
        // ASCII and Latin-1, excluding '§' (Minecraft's formatting character).
        if ((c >= ' ' && c <= '~') || (c >= 161 && c <= 255 && c != 167)) return true;
        // Cyrillic: allow in computer labels.
        if (c >= 0x0400 && c <= 0x04FF) return true;
        return false;
    }

    public static String normaliseLabel(String text) {
        var length = Math.min(32, text.length());
        var builder = new StringBuilder(length);
        for (var i = 0; i < length; i++) {
            var c = text.charAt(i);
            builder.append(isAllowedInLabel(c) ? c : '?');
        }
        return builder.toString();
    }

    /**
     * Convert a Java string to a Lua one (using the terminal charset), suitable for pasting into a computer.
     * <p>
     * Strips newlines (stops at first) and filters to typable characters. Returns raw Unicode codepoints
     * packed as little-endian 16-bit chars (two bytes per char) to support Cyrillic paste.
     *
     * @param clipboard The text from the clipboard.
     * @return The encoded clipboard text as a ByteBuffer of UTF-16 LE pairs.
     */
    public static ByteBuffer getClipboardString(String clipboard) {
        var output = new byte[Math.min(MAX_PASTE_LENGTH, clipboard.length())];
        var idx = 0;

        var iterator = clipboard.codePoints().iterator();
        while (iterator.hasNext() && idx < output.length) {
            var chr = unicodeToTerminal(iterator.next());
            if (chr < 0) continue; // Strip out unconvertible characters
            if (!isTypableChar(chr)) break; // Stop at untypable ones.
            output[idx++] = (byte) chr; // Paste natively supports 8-bit encoded characters. Cyrillic will be clamped. To paste Cyrillic, users use external programs.
        }

        return ByteBuffer.wrap(output, 0, idx).asReadOnlyBuffer();
    }
}
