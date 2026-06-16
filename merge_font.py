#!/usr/bin/env python3
"""Merge DejaVu Sans (Latin) + DroidSansFallback (CJK) into one font"""
from fontTools.ttLib import TTFont
import copy, os

out = '/sessions/sleepy-lucid-cray/mnt/outputs/Merged2.ttf'

dejavu = TTFont('/usr/local/lib/python3.10/dist-packages/matplotlib/mpl-data/fonts/ttf/DejaVuSans.ttf')
droid = TTFont('/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf')

# 1. Copy Droid glyphs into DejaVu
all_names = set(dejavu.getGlyphOrder())
new_order = list(dejavu.getGlyphOrder())
for gn in droid.getGlyphOrder():
    if gn not in all_names:
        try:
            dejavu['glyf'][gn] = copy.deepcopy(droid['glyf'][gn])
            if gn in droid['hmtx'].metrics:
                dejavu['hmtx'][gn] = droid['hmtx'][gn]
            new_order.append(gn)
            all_names.add(gn)
        except Exception:
            pass

# 2. Copy CJK cmap entries
dd_cmap = droid['cmap'].getcmap(3, 1)
dv_cmap = dejavu['cmap'].getcmap(3, 1)
cjk_added = 0
for code, gn in dd_cmap.cmap.items():
    if code not in dv_cmap.cmap:
        is_cjk = (
            (0x2E80 <= code <= 0x2FDF) or
            (0x3000 <= code <= 0x303F) or
            (0x3400 <= code <= 0x4DBF) or
            (0x4E00 <= code <= 0x9FFF) or
            (0xFE30 <= code <= 0xFFEF) or
            (0xF900 <= code <= 0xFAFF) or
            (0x20000 <= code <= 0x2FFFF)
        )
        if is_cjk and gn in all_names:
            dv_cmap.cmap[code] = gn
            cjk_added += 1

# 3. Rename font family to avoid conflict with original DejaVu
for rec in dejavu['name'].names:
    if rec.nameID == 1:
        rec.string = 'DejaVu Sans CJK'
    elif rec.nameID == 4:
        rec.string = 'DejaVu Sans CJK'
    elif rec.nameID == 6:
        rec.string = 'DejaVuSansCJK'

dejavu.setGlyphOrder(new_order)
dejavu['maxp'].numGlyphs = len(new_order)
dejavu.save(out)

# Verify
print("Saved: %s (%d bytes)" % (out, os.path.getsize(out)))
vf = TTFont(out)
vcmap = vf['cmap'].getcmap(3, 1)
for ch in ['A', 'a', '0', 'l', 'p', '-', '.', '(', ' ', 'ni', 'hao', 'tu', 'xie', 'yi']:
    print("  %s" % ch)
