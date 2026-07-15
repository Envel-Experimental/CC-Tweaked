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

with open('glyphs_all.txt', 'w', encoding='utf-8') as out:
    out.write('    // Pre-rendered Cyrillic glyphs from Minecraft\n')
    out.write('    public static final String[] CYRILLIC_GLYPHS = new String[256 * 8];\n')
    out.write('    static {\n')
    for cp in range(0x0400, 0x0500):
        if cp in mapping:
            filename, r, c = mapping[cp]
            img = img_nonlatin if filename == 'minecraft:font/nonlatin_european.png' else img_accented
            cell = img.crop((c*8, r*8, c*8+8, r*8+8))
            idx = (cp - 0x0400) * 8
            for y in range(8):
                line = ''
                for x in range(6):
                    line += '#' if cell.getpixel((x,y))[3] > 128 else ' '
                out.write(f'        CYRILLIC_GLYPHS[{idx+y}] = "{line}";\n')
    out.write('    }\n')
