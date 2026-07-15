with open('projects/fabric/src/client/java/dan200/computercraft/client/font/CyrillicFontPatcher.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

def repl(start_idx, glyph_lines):
    global lines
    for line_idx, line_content in enumerate(lines):
        for offset in range(8):
            expected_prefix = f'CYRILLIC_GLYPHS[{start_idx + offset}] ='
            if expected_prefix in line_content:
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
    ' ###  ',
    '#   # ',
    '##### ',
    '#     ',
    ' ###  ',
    '      '
])

# ч 568
repl(568, [
    '      ',
    '      ',
    '#   # ',
    '#   # ',
    '##### ',
    '    # ',
    '    # ',
    '      '
])

# ю 624
repl(624, [
    '      ',
    '      ',
    '#  ## ',
    '# #  #',
    '###  #',
    '# #  #',
    '#  ## ',
    '      '
])

# Ю 368
repl(368, [
    '#  ## ',
    '# #  #',
    '###  #',
    '# #  #',
    '# #  #',
    '# #  #',
    '#  ## ',
    '      '
])

# щ 584
repl(584, [
    '      ',
    '      ',
    '# # # ',
    '# # # ',
    '# # # ',
    '# # # ',
    '##### ',
    '    # '
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

# ц 560
repl(560, [
    '      ',
    '      ',
    '#   # ',
    '#   # ',
    '#   # ',
    '#   # ',
    '##### ',
    '    # '
])

# Ц 304
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

# д 592
repl(592, [
    '      ',
    '      ',
    ' ###  ',
    ' # #  ',
    ' # #  ',
    ' # #  ',
    '##### ',
    '#   # '
])

# Д 336
repl(336, [
    ' ###  ',
    ' # #  ',
    ' # #  ',
    ' # #  ',
    ' # #  ',
    ' # #  ',
    '##### ',
    '#   # '
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

# Ы 344
repl(344, [
    '#   # ',
    '#   # ',
    '#   # ',
    '#   # ',
    '### # ',
    '# # # ',
    '### # ',
    '      '
])

# й 456
repl(456, [
    '  #   ',
    ' #    ',
    '#   # ',
    '#   # ',
    '#  ## ',
    '# # # ',
    '##  # ',
    '      '
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

with open('projects/fabric/src/client/java/dan200/computercraft/client/font/CyrillicFontPatcher.java', 'w', encoding='utf-8') as f:
    f.writelines(lines)
print('Done!')
