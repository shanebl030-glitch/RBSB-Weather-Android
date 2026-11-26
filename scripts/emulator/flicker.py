#!/usr/bin/env python3
# Measures flicker in a screen recording of one rectangle (the widget, or the forecast sky):
# frame-to-frame change, sudden flashes (a big jump that comes straight back), and the brightness
# of the brightest spot (the Moon) per frame. Saves a strip of frames to look at.
# Usage: flicker.py video x1 y1 x2 y2 out_prefix label [moon]. Created By Shane BlackbuRn
import subprocess, sys, os
from PIL import Image, ImageChops, ImageStat
video, x1, y1, x2, y2, prefix, label = sys.argv[1], *map(int, sys.argv[2:6]), sys.argv[6], sys.argv[7]
moon = len(sys.argv) > 8
d = prefix + "-frames"; os.makedirs(d, exist_ok=True)
try:
    import imageio_ffmpeg; FF = imageio_ffmpeg.get_ffmpeg_exe()
except Exception:
    FF = "ffmpeg"
subprocess.run([FF, "-loglevel", "error", "-y", "-i", video, "-vf", f"fps=20,crop={x2-x1}:{y2-y1}:{x1}:{y1},scale=360:-1", f"{d}/f%04d.png"], check=False)
files = sorted(f for f in os.listdir(d) if f.endswith(".png"))
if len(files) < 3:
    print(f"FLICKER ({label}): no frames ({len(files)})"); sys.exit(0)
ims = [Image.open(os.path.join(d, f)).convert("L") for f in files]
diffs = [ImageStat.Stat(ImageChops.difference(ims[i], ims[i - 1])).mean[0] for i in range(1, len(ims))]
means = [ImageStat.Stat(im).mean[0] for im in ims]
med = sorted(diffs)[len(diffs) // 2]
# a flash: this frame differs a lot from both neighbours, but they're alike (came straight back)
flashes = []
for i in range(1, len(ims) - 1):
    a = diffs[i - 1]; b = diffs[i]
    back = ImageStat.Stat(ImageChops.difference(ims[i + 1], ims[i - 1])).mean[0]
    if a > max(6, 4 * med) and b > max(6, 4 * med) and back < 0.5 * min(a, b): flashes.append((i, round(a, 1), round(back, 1)))
jumps = sum(1 for x in diffs if x > max(8, 5 * med))
print(f"FLICKER ({label}): {len(ims)} frames at 20/s; change per frame median {med:.2f}, max {max(diffs):.1f}; big jumps {jumps}; flashes (jump and straight back) {len(flashes)}")
if flashes: print(f"FLICKER ({label}): flash frames {flashes[:12]}")
if moon:
    # the Moon: brightest small spot in the sky; record its brightness and position per frame
    tops = []
    for im in ims:
        px = im.load(); w, h = im.size; best = (0, 0, 0)
        for yy in range(0, h, 2):
            for xx in range(0, w, 2):
                v = px[xx, yy]
                if v > best[0]: best = (v, xx, yy)
        tops.append(best)
    br = [t[0] for t in tops]
    drops = sum(1 for i in range(1, len(br) - 1) if br[i] < br[i - 1] - 40 and br[i + 1] > br[i] + 40)
    print(f"MOON ({label}): brightest spot per frame min {min(br)} max {max(br)}; dips that come straight back (flashing) {drops}")
# a frame a second across the whole film (for playback: every hour of the 24), to look at
every = [i for i in range(0, len(ims), 20)][:30]
cw0, ch0 = Image.open(os.path.join(d, files[0])).size
strip = Image.new("RGB", (cw0 * 6, ch0 * ((len(every) + 5) // 6)))
for k, i in enumerate(every): strip.paste(Image.open(os.path.join(d, files[i])).convert("RGB"), ((k % 6) * cw0, (k // 6) * ch0))
strip.save(prefix + "-each-second.png")
# a strip of 24 frames around the worst moment, to look at
c = max(range(len(diffs)), key=lambda i: diffs[i]) if diffs else 0
pick = list(range(max(0, c - 12), min(len(ims), c + 12)))
cw, ch = Image.open(os.path.join(d, files[0])).size
sheet = Image.new("RGB", (cw * 6, ch * ((len(pick) + 5) // 6)))
for k, i in enumerate(pick): sheet.paste(Image.open(os.path.join(d, files[i])).convert("RGB"), ((k % 6) * cw, (k // 6) * ch))
sheet.save(prefix + "-sheet.png")
