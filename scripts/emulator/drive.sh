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
dump() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null
  # a slow emulator sometimes shows Android's own "… isn't responding" dialog over everything: wait it out
  if grep -q "isn.t responding" "$OUT/ui.xml" 2>/dev/null; then
    echo "(dismissing an Android 'isn't responding' dialog: $(grep -o 'text="[^"]*isn.t responding[^"]*"' "$OUT/ui.xml" | head -1))"
    xy=$(python3 "$HERE/find.py" "$OUT/ui.xml" "Wait") && adb shell input tap $xy; sleep 2
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null
  fi
}
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
sleep 6; check "launch"; dump; shot launch
step "add Auckland"
tap "Add a place" && sleep 2 && adb shell input text Auckland && sleep 8
tap "Auckland, New Zealand"; sleep 10; check "adding a place"; shot forecast
step "Moon tab"; tap "Moon"; sleep 8; check "Moon tab"; shot moon
step "Earth tab"; tap "Earth"; sleep 15; check "Earth tab"; shot earth-flat
step "Earth Globe (what crashed)"; tap "Earth Globe"; sleep 3; check "tapping Earth Globe (3 s)"; sleep 12; check "Earth Globe (15 s)"; shot earth-globe
step "zoom, spin, drag"
for i in 1 2 3 4 5 6; do tap "+" >/dev/null; sleep 1; done; sleep 4; check "zooming in"; shot zoomed
for i in 1 2 3 4 5 6 7 8; do tap "−" >/dev/null; sleep 1; done; sleep 4          # back to the smallest zoom
# the globe box: between the Earth Flat/Globe tabs and the Rotate slowly row (about 420 dp tall)
GLOBE="60 620 1020 1600"
moved() {  # moved <name> <before> <after> <min fraction>
  f=$(python3 "$HERE/compare.py" "$2" "$3" $GLOBE); echo "$1: $f of the globe changed"
  python3 -c "import sys; sys.exit(0 if float('$f') >= float('$4') else 1)" && echo "GLOBE MOVED: $1" || { echo "GLOBE DID NOT MOVE: $1"; FAILED=1; }
}
FAILED=0
adb exec-out screencap -p > "$OUT/still-a.png"; sleep 4; adb exec-out screencap -p > "$OUT/still-b.png"
echo "still: $(python3 "$HERE/compare.py" "$OUT/still-a.png" "$OUT/still-b.png" $GLOBE) of the globe changed with nothing moving"
tap "Rotate slowly"; sleep 2; adb exec-out screencap -p > "$OUT/spin-a.png"; sleep 5; adb exec-out screencap -p > "$OUT/spin-b.png"
check "rotating"; moved "Rotate slowly" "$OUT/spin-a.png" "$OUT/spin-b.png" 0.05
tap "Rotate slowly"; sleep 3
adb exec-out screencap -p > "$OUT/drag-a.png"
adb shell input swipe 250 1350 800 1350 700; sleep 4
adb exec-out screencap -p > "$OUT/drag-b.png"
check "dragging"; moved "drag" "$OUT/drag-a.png" "$OUT/drag-b.png" 0.05
adb shell input swipe 540 1300 540 900 600; sleep 4; check "dragging up"
[ "$FAILED" = 0 ] || { echo "GLOBE CHECK FAILED"; GLOBE_FAILED=1; }
tap "Earth Flat"; sleep 4; check "back to flat"; tap "Earth Globe"; sleep 6; check "globe again"; shot globe-again
tap "Forecast"; sleep 4; tap "Earth"; sleep 8; check "leaving and coming back"; shot final

step "Wind tab (a real download from NOAA)"
tap "Wind"; for i in $(seq 1 12); do sleep 5; dump; grep -q "GFS forecast run" "$OUT/ui.xml" && break; done
check "Wind tab"; shot wind
grep -q "GFS forecast run" "$OUT/ui.xml" && echo "WIND DATA: downloaded and shown" || { echo "WIND DATA: not shown"; grep -o 'text="[^"]*NOAA[^"]*"' "$OUT/ui.xml" | head -3; GLOBE_FAILED=1; }
sleep 8; adb exec-out screencap -p > "$OUT/wind-a.png"; sleep 2; adb exec-out screencap -p > "$OUT/wind-b.png"
moved "wind streaks" "$OUT/wind-a.png" "$OUT/wind-b.png" 0.003
for L in "🌡 Temperature" "🌧 Rain & snow" "🌬 Wind"; do tap "$L"; sleep 6; check "Wind layer $L"; done
shot wind-layers
adb shell input tap 540 1100; sleep 2; check "tapping the wind globe"

step "every other screen"
back_to_forecast() { adb shell am start -n $PKG/.MainActivity >/dev/null 2>&1; sleep 2; tap "Forecast" >/dev/null || adb shell input keyevent KEYCODE_BACK; sleep 2; }
tap "Warnings"; sleep 5; check "Warnings tab"; shot warnings
back_to_forecast
for item in "My places" "Earthquakes" "Settings" "User guide" "Test connection" "Check for updates" "About"; do
  back_to_forecast
  tap "Menu" && sleep 1 && tap "$item"; sleep 5; check "menu: $item"; shot "menu-${item// /-}"
  if [ "$item" = "Test connection" ]; then
    tap "Run the test"; for i in $(seq 1 20); do sleep 3; dump; grep -q "Summary:" "$OUT/ui.xml" && break; done
    check "Test connection"; shot connection-report
    grep -q "Summary:" "$OUT/ui.xml" && echo "TEST CONNECTION: report finished" || echo "TEST CONNECTION: no summary"
  fi
  if [ "$item" = "Check for updates" ]; then
    tap "Check now"; sleep 8; check "Check for updates"; shot updates-checked
    dump; grep -o 'text="[^"]*\(newest version\|no release\|is available\|Couldn.t\)[^"]*"' "$OUT/ui.xml" | head -2
  fi
  if [ "$item" = "Settings" ]; then
    for i in 1 2 3 4; do adb shell input swipe 540 1800 540 700 300; sleep 1; done      # the switches are at the bottom
    tap "Temperature in the status bar"; sleep 3
    adb shell dumpsys notification --noredact 2>/dev/null | grep -A3 "pkg=$PKG" | grep -m3 "temperature\|android.title" && echo "TEMP NOTIFICATION: shown" || echo "TEMP NOTIFICATION: not found"
    shot settings-temp
    for i in 1 2 3 4 5; do adb shell input swipe 540 700 540 1800 300; sleep 1; done       # back to the top for the links
    for sub in "Weather Alerts" "Earthquakes" "Weather Providers"; do
      tap "$sub" && { sleep 4; check "Settings: $sub"; shot "settings-${sub// /-}"; adb shell input keyevent KEYCODE_BACK; sleep 2; }
    done
  fi
done
back_to_forecast

step "save a Moon picture"
tap "Moon"; sleep 8
for i in 1 2 3 4; do adb shell input swipe 540 1800 540 600 300; sleep 1; done
tap "Save high-resolution image"; sleep 15; check "saving the Moon picture"; shot moon-saved

step "turn the phone sideways and back"
adb shell settings put system accelerometer_rotation 0
tap "Earth"; sleep 4; tap "Earth Globe"; sleep 5
adb shell settings put system user_rotation 1; sleep 8; check "landscape on the globe"; shot landscape-globe
tap "Forecast"; sleep 5; check "landscape forecast"; shot landscape-forecast
adb shell settings put system user_rotation 0; sleep 6; check "back to portrait"

step "leave the app and come back"
adb shell input keyevent KEYCODE_HOME; sleep 5
adb shell am start -n $PKG/.MainActivity; sleep 6; check "reopening"; shot reopened
tap "Earth"; sleep 3; tap "Earth Globe"; sleep 8; check "globe after reopening"; shot globe-reopened

step "pull to refresh"
tap "Forecast"; sleep 3; adb shell input swipe 540 700 540 1500 400; sleep 8; check "refresh"

adb logcat -d > "$OUT/logcat.txt"
grep -n "FATAL\|ANR in\|OutOfMemory\|Skipped [0-9]* frames\|Davey" "$OUT/logcat.txt" | head -40
echo "APP ALIVE: no crash"
[ "${GLOBE_FAILED:-0}" = 0 ] || exit 3
