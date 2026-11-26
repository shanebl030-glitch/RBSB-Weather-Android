#!/usr/bin/env python3
# The box on screen that's animating in a short recording (on the home screen: our widget).
# Prints "x1 y1 x2 y2" in screen pixels, or exits 1 if nothing moves. Created By Shane BlackbuRn
import os, subprocess, sys, tempfile
from PIL import Image, ImageChops
video, sw, sh = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
try:
    import imageio_ffmpeg; FF = imageio_ffmpeg.get_ffmpeg_exe()
except Exception:
    FF = "ffmpeg"
d = tempfile.mkdtemp()
subprocess.run([FF, "-loglevel", "error", "-i", video, "-vf", "fps=10,scale=270:-1", f"{d}/f%03d.png"], check=False)
fs = sorted(os.listdir(d))
if len(fs) < 4: sys.exit(1)
ims = [Image.open(os.path.join(d, f)).convert("L") for f in fs]
acc = Image.new("L", ims[0].size, 0)
for a, b in zip(ims, ims[1:]):
    acc = ImageChops.lighter(acc, ImageChops.difference(a, b))
w, h = acc.size; px = acc.load()
top = int(h * 0.05); bottom = int(h * 0.95)            # leave out the status bar and the gesture bar
pts = [(x, y) for y in range(top, bottom) for x in range(w) if px[x, y] > 12]
if len(pts) < 50: sys.exit(1)
xs = sorted(p[0] for p in pts); ys = sorted(p[1] for p in pts)
q = lambda v, f: v[int(len(v) * f)]
x1, x2, y1, y2 = q(xs, 0.01), q(xs, 0.99), q(ys, 0.01), q(ys, 0.99)
s = sw / w
print(int(x1 * s), int(y1 * s), int(x2 * s), int(y2 * s))
