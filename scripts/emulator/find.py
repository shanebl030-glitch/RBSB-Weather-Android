# Find a node on screen by its text or description in a uiautomator dump; print its centre "x y".
# Created By Shane BlackbuRn
import re, sys, xml.etree.ElementTree as ET
path, want = sys.argv[1], sys.argv[2]
mode = sys.argv[3] if len(sys.argv) > 3 else "exact"
root = ET.parse(path).getroot()
for n in root.iter("node"):
    for attr in ("text", "content-desc"):
        v = n.get(attr) or ""
        if (mode == "exact" and v == want) or (mode == "starts" and v.startswith(want)):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
