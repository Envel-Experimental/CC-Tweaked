import re

with open('glyphs.txt', 'r', encoding='utf-8') as f:
    glyphs = f.read()

with open('projects/fabric/src/client/java/dan200/computercraft/client/font/CyrillicFontPatcher.java', 'r', encoding='utf-8') as f:
    code = f.read()

replacement = """
    // Pre-rendered Cyrillic glyphs from Minecraft's nonlatin_european.png
""" + glyphs + """
    private static void renderGlyph(NativeImage atlas, Font mcFont, int cp, int slotIndex) {
        var col = 16 + (slotIndex % 16);
        var row = (slotIndex - 256) / 16;

        var destX = 1 + col * CELL_W;
        var destY = 1 + row * CELL_H;

        if (destX + FixedWidthFontRenderer.FONT_WIDTH > EXTENDED_WIDTH ||
            destY + FixedWidthFontRenderer.FONT_HEIGHT > ATLAS_HEIGHT) {
            return;
        }

        int indexStart = (cp - 0x0400) * 8;
        if (indexStart < 0 || indexStart + 7 >= CYRILLIC_GLYPHS.length || CYRILLIC_GLYPHS[indexStart] == null) {
            return; // No glyph available
        }

        // Draw the 6x8 glyph into the 6x9 cell (centered vertically -> draw from y=1)
        for (var dy = 0; dy < 8; dy++) {
            String line = CYRILLIC_GLYPHS[indexStart + dy];
            for (var dx = 0; dx < 6; dx++) {
                if (line.charAt(dx) == '#') {
                    // White pixel
                    atlas.setPixelRGBA(destX + dx, destY + dy + 1, 0xFFFFFFFF);
                } else {
                    // Transparent
                    atlas.setPixelRGBA(destX + dx, destY + dy + 1, 0x00000000);
                }
            }
        }
    }

    /**
     * Compute the UV coordinates for a codepoint in the extended 512A-256 atlas.
     * Used by both font renderers to replace the old 256-wide UV formulas.
"""

code = re.sub(
    r'    /\*\* Lazily initialised AWT font used for rasterising Cyrillic glyphs\..*?    /\*\*\n     \* Compute the UV coordinates for a codepoint in the extended 512A-256 atlas\.\n     \* Used by both font renderers to replace the old 256-wide UV formulas\.',
    replacement,
    code,
    flags=re.DOTALL
)

code = re.sub(r'import java\.awt\..*?;\n', '', code)

with open('projects/fabric/src/client/java/dan200/computercraft/client/font/CyrillicFontPatcher.java', 'w', encoding='utf-8') as f:
    f.write(code)
