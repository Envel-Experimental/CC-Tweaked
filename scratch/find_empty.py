with open('projects/fabric/src/client/java/dan200/computercraft/client/font/CyrillicFontPatcher.java', 'r', encoding='utf-8') as f:
    content = f.read()
import re
for cp in range(0x400, 0x500):
    idx = (cp - 0x400) * 8
    lines = []
    for i in range(8):
        match = re.search(r'CYRILLIC_GLYPHS\[' + str(idx + i) + r'\] = "(.*)";', content)
        if match: lines.append(match.group(1))
    if len(lines) > 0 and len(lines) < 8:
        print(f'Char {hex(cp)} has {len(lines)} lines!')
    if len(lines) == 0:
        pass # print(f'Char {hex(cp)} is completely empty')
