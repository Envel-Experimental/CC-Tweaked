with open('projects/fabric/src/client/java/dan200/computercraft/client/font/CyrillicFontPatcher.java', 'r', encoding='utf-8') as f:
    content = f.read()

import re

def repl(start_idx, lines):
    global content
    for i in range(8):
        old_pattern = r'CYRILLIC_GLYPHS\[' + str(start_idx + i) + r'\] = \".*\";'
        new_str = f'CYRILLIC_GLYPHS[{start_idx + i}] = "{lines[i]}";'
        content = re.sub(old_pattern, new_str, content)

# Ё 8
repl(8, [
    ' #  # ',
    '      ',
    '##### ',
    '#     ',
    '###   ',
    '#     ',
    '##### ',
    '      '
])

# ё 648
repl(648, [
    ' #  # ',
    '      ',
    '      ',
    ' ###  ',
    '#   # ',
    '##### ',
    '#     ',
    ' ###  '
])

# Й 200
repl(200, [
    '  #   ',
    ' #    ',
    '#   # ',
    '#  ## ',
    '# # # ',
    '##  # ',
    '#   # ',
    '      '
])

# й 456
repl(456, [
    '      ',
    '  #   ',
    ' #    ',
    '#   # ',
    '#  ## ',
    '# # # ',
    '##  # ',
    '#   # '
])

# Ц 304 (was #### #, make it 5 wide #   #)
repl(304, [
    '#   # ',
    '#   # ',
    '#   # ',
    '#   # ',
    '#   # ',
    '#   # ',
    '##### ',
    '    # '
])

# ц 560
repl(560, [
    '      ',
    '      ',
    '#  #  ',
    '#  #  ',
    '#  #  ',
    '####  ',
    '   #  ',
    '      '
])

# Щ 328
repl(328, [
    '# # # ',
    '# # # ',
    '# # # ',
    '# # # ',
    '# # # ',
    '# # # ',
    '##### ',
    '    # '
])

# щ 584
repl(584, [
    '      ',
    '      ',
    '# # # ',
    '# # # ',
    '# # # ',
    '##### ',
    '    # ',
    '      '
])

# Ы 344
repl(344, [
    '#   # ',
    '#   # ',
    '#   # ',
    '### # ',
    '# # # ',
    '### # ',
    '      ',
    '      '
])

# ы 600
repl(600, [
    '      ',
    '      ',
    '#   # ',
    '#   # ',
    '### # ',
    '# # # ',
    '### # ',
    '      '
])

# Ю 368
repl(368, [
    '      ',
    '      ',
    '#  ## ',
    '# #  #',
    '##   #',
    '# #  #',
    '#  ## ',
    '      '
])

# ю 624
repl(624, [
    '      ',
    '      ',
    '#  ## ',
    '# #  #',
    '##   #',
    '# #  #',
    '#  ## ',
    '      '
])

# Д 336
repl(336, [
    '      ',
    '      ',
    ' ###  ',
    ' # #  ',
    ' # #  ',
    ' ###  ',
    '##### ',
    '#   # '
])

# д 592
repl(592, [
    '      ',
    '      ',
    ' ###  ',
    ' # #  ',
    ' ###  ',
    '##### ',
    '#   # ',
    '      '
])


with open('projects/fabric/src/client/java/dan200/computercraft/client/font/CyrillicFontPatcher.java', 'w', encoding='utf-8') as f:
    f.write(content)
print('Done!')
