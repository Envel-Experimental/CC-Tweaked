import sys; import io; sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
import zipfile, json
from PIL import Image

z = zipfile.ZipFile(r'C:\Users\Nikita\.gradle\caches\fabric-loom\1.20.1\minecraft-client-only.jar')

with z.open('assets/minecraft/textures/font/nonlatin_european.png') as f:
    img_nonlatin = Image.open(f).convert('RGBA')
with z.open('assets/minecraft/textures/font/accented.png') as f:
    img_accented = Image.open(f).convert('RGBA')

j = json.loads(z.read('assets/minecraft/font/include/default.json').decode('utf-8'))
providers = j['providers']

mapping = {}

for p in providers:
    filename = p.get('file')
    if filename not in ('minecraft:font/nonlatin_european.png', 'minecraft:font/accented.png'): continue
    
    if 'chars' in p:
        for r, row in enumerate(p['chars']):
            for c, char in enumerate(row):
                cp = ord(char)
                if 0x0400 <= cp <= 0x04FF:
                    mapping[cp] = (filename, r, c)

glyphs = '    // Pre-rendered Cyrillic glyphs from Minecraft\n'
glyphs += '    public static final String[] CYRILLIC_GLYPHS = new String[256 * 8];\n'
glyphs += '    static {\n'

for cp in range(0x0400, 0x0500):
    if cp in mapping:
        filename, r, c = mapping[cp]
        img = img_nonlatin if filename == 'minecraft:font/nonlatin_european.png' else img_accented
        
        # Determine cell size
        cw = img.width // 16
        if filename == 'minecraft:font/accented.png':
            ch = 12
            y_offset = 3 # 10 (ascent) - 7 (nonlatin ascent)
        else:
            ch = 8
            y_offset = 0
            
        cell = img.crop((c*cw, r*ch, c*cw+cw, r*ch+ch))
        idx = (cp - 0x0400) * 8
        for y in range(8):
            line = ''
            for x in range(6): # We only need the top-left 6x8 pixels
                src_x = x
                src_y = y + y_offset
                if src_x < cw and src_y < ch and src_y >= 0:
                    line += '#' if cell.getpixel((src_x, src_y))[3] > 128 else ' '
                else:
                    line += ' '
            glyphs += f'        CYRILLIC_GLYPHS[{idx+y}] = "{line}";\n'
glyphs += '    }\n'

with open('projects/fabric/src/client/java/dan200/computercraft/client/font/CyrillicFontPatcher.java', 'r', encoding='utf-8') as f:
    code = f.read()

replacement = glyphs + """
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
"""

start_idx = code.find("    public static final String[] CYRILLIC_GLYPHS")
end_idx = code.find("    /**\n     * Compute the UV")
if end_idx == -1:
    end_idx = code.find("    /**\r\n     * Compute the UV")

if start_idx != -1 and end_idx != -1:
    new_code = code[:start_idx] + replacement + "\n" + code[end_idx:]
    with open('projects/fabric/src/client/java/dan200/computercraft/client/font/CyrillicFontPatcher.java', 'w', encoding='utf-8') as f:
        f.write(new_code)
    print("PATCHED")
else:
    print("NOT FOUND", start_idx, end_idx)
