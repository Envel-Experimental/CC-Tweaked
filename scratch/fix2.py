import re

with open('projects/fabric/src/client/java/dan200/computercraft/client/font/CyrillicFontPatcher.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

def repl(start_idx, glyph_lines):
    global lines
    for line_idx, line_content in enumerate(lines):
        for offset in range(8):
            expected_prefix = f'CYRILLIC_GLYPHS[{start_idx + offset}] ='
            if expected_prefix in line_content:
                # Keep the indentation, just replace the assignment part
                indent = line_content[:line_content.find(expected_prefix)]
                lines[line_idx] = f'{indent}CYRILLIC_GLYPHS[{start_idx + offset}] = "{glyph_lines[offset]}";\n'

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
    f.writelines(lines)
print('Done!')
