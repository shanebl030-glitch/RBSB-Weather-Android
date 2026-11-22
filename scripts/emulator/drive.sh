#!/usr/bin/env bash
# Drives RBSB-Weather on a real Android emulator the way Shane used it, and reports any crash
# with Android's own log. Created By Shane BlackbuRn
set -u
APK="$1"; OUT="${2:-emu-out}"
PKG=io.github.shanebl030glitch.rbsbweather
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"
n=0
shot() { n=$((n+1)); adb exec-out screencap -p > "$OUT/$(printf %02d $n)-$1.png"; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null; }
alive() { adb shell pidof $PKG >/dev/null 2>&1; }
tap() {   # tap <text> [starts]
  for i in 1 2 3 4 5 6 7 8 9 10; do
    dump
    if xy=$(python3 "$HERE/find.py" "$OUT/ui.xml" "$1" "${2:-exact}"); then adb shell input tap $xy; echo "tapped '$1' at $xy"; return 0; fi
    sleep 2
  done
  echo "NOT FOUND on screen: '$1'"; return 1
}
step() { echo "== $1"; }
check() {
  if ! alive; then
    echo "APP CRASHED after: $1"
    adb logcat -d -b crash > "$OUT/crash.txt" 2>/dev/null
    adb logcat -d > "$OUT/logcat.txt"
    echo "----- crash log -----"; cat "$OUT/crash.txt" | head -120
    grep -n "FATAL EXCEPTION\|ANR in\|OutOfMemory\|AndroidRuntime" "$OUT/logcat.txt" | head -40
    shot crashed; exit 2
  fi
  echo "alive after: $1"
}

adb install -r "$APK" || exit 1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell getprop dalvik.vm.heapgrowthlimit; adb shell getprop dalvik.vm.heapsize
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
sleep 6; check "launch"; shot launch
step "add Auckland"
tap "Add a place" && sleep 2 && adb shell input text Auckland && sleep 8
tap "Auckland, New Zealand"; sleep 10; check "adding a place"; shot forecast
step "Moon tab"; tap "Moon"; sleep 8; check "Moon tab"; shot moon
step "Earth tab"; tap "Earth"; sleep 15; check "Earth tab"; shot earth-flat
step "Earth Globe (what crashed)"; tap "Earth Globe"; sleep 3; check "tapping Earth Globe (3 s)"; sleep 12; check "Earth Globe (15 s)"; shot earth-globe
step "zoom, spin, drag"
for i in 1 2 3 4 5 6; do tap "+" >/dev/null; sleep 1; done; sleep 4; check "zooming in"; shot zoomed
tap "Rotate slowly"; sleep 6; check "rotating"; tap "Rotate slowly"
adb shell input swipe 500 1000 900 1100 500; sleep 3; check "dragging"
tap "Earth Flat"; sleep 4; check "back to flat"; tap "Earth Globe"; sleep 6; check "globe again"; shot globe-again
tap "Forecast"; sleep 4; tap "Earth"; sleep 8; check "leaving and coming back"; shot final
adb logcat -d > "$OUT/logcat.txt"
grep -n "FATAL\|ANR in\|OutOfMemory\|Skipped [0-9]* frames\|Davey" "$OUT/logcat.txt" | head -40
echo "APP ALIVE: no crash"
