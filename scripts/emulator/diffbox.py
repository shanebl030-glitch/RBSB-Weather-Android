#!/usr/bin/env python3
# The box that differs between two screenshots (the home screen before and after placing the
# widget): prints "x1 y1 x2 y2", or exits 1. Created By Shane BlackbuRn
import sys
from PIL import Image, ImageChops
a = Image.open(sys.argv[1]).convert("L"); b = Image.open(sys.argv[2]).convert("L")
d = ImageChops.difference(a, b).point(lambda v: 255 if v > 30 else 0)
w, h = d.size
d = d.crop((0, int(h * 0.05), w, int(h * 0.80)))        # not the status bar, dock or search bar
box = d.getbbox()
if not box: sys.exit(1)
x1, y1, x2, y2 = box; y1 += int(h * 0.05); y2 += int(h * 0.05)
if (x2 - x1) < w * 0.25 or (y2 - y1) < h * 0.08: sys.exit(1)
print(x1, y1, x2, y2)
