# Fraction of pixels that changed between two screenshots (in an optional box x0 y0 x1 y1).
# Created By Shane BlackbuRn
import sys
from PIL import Image, ImageChops
a, b = Image.open(sys.argv[1]).convert("RGB"), Image.open(sys.argv[2]).convert("RGB")
if len(sys.argv) > 6:
    box = tuple(int(v) for v in sys.argv[3:7]); a, b = a.crop(box), b.crop(box)
d = ImageChops.difference(a, b).convert("L").point(lambda v: 255 if v > 40 else 0)
print(f"{sum(d.histogram()[255:]) / (d.width * d.height):.4f}")
