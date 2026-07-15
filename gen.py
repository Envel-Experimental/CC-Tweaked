from PIL import Image
img = Image.open('nonlatin.png').convert('RGBA')

with open('mapping.txt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

mapping = {}
for line in lines:
    parts = line.strip().split(': ')
    cp = int(parts[0], 16)
    r, c = eval(parts[1])
    mapping[cp] = (r, c)

with open('glyphs.txt', 'w', encoding='utf-8') as out:
    out.write('    public static final String[] CYRILLIC_GLYPHS = new String[256 * 8];\n')
    out.write('    static {\n')
    for cp in range(0x0400, 0x0500):
        if cp in mapping:
            r, c = mapping[cp]
            cell = img.crop((c*8, r*8, c*8+8, r*8+8))
            idx = (cp - 0x0400) * 8
            for y in range(8):
                line = ''
                for x in range(6):
                    line += '#' if cell.getpixel((x,y))[3] > 128 else ' '
                out.write(f'        CYRILLIC_GLYPHS[{idx+y}] = "{line}";\n')
    out.write('    }\n')
