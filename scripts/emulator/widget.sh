#!/usr/bin/env bash
# Widget and playback regression check (Shane's 0.7.0 phone test, 26 Nov 2026): installs the last
# good release (0.6.2), adds Auckland, puts the real widget on the home screen through the
# launcher, films it, then installs the new build over it the way Shane did and repeats:
# filming the widget (flicker), tapping it with the app closed / in the background / backed out
# of (the app must open and stay open), and filming 24-hour playback in the app (the Moon).
# Prints WIDGET/TAP/PLAYBACK lines; screenshots and frames go to $OUT. Created By Shane BlackbuRn
set -u
BASE="$1"; NEW="$2"; OUT="${3:-widget-out}"
PKG=io.github.shanebl030glitch.rbsbweather
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"; n=0; FAIL=0
shot() { n=$((n+1)); timeout 30 adb exec-out screencap -p > "$OUT/$(printf %02d $n)-$1.png"; }
dump() { timeout 30 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null
  if grep -q "isn.t responding" "$OUT/ui.xml" 2>/dev/null; then xy=$(python3 "$HERE/find.py" "$OUT/ui.xml" "Wait") && adb shell input tap $xy; sleep 2; dump; fi; }
tap() { for i in 1 2 3 4 5 6; do dump; if xy=$(python3 "$HERE/find.py" "$OUT/ui.xml" "$1" "${2:-exact}"); then adb shell input tap $xy; echo "tapped '$1' at $xy"; return 0; fi; sleep 2; done; echo "(not found: '$1')"; return 1; }
resumed() { timeout 20 adb shell dumpsys activity activities 2>/dev/null | grep -m1 -E "topResumedActivity|mResumedActivity" | sed 's/^ *//'; }
events() { timeout 20 adb logcat -d -b events 2>/dev/null | grep -E "am_(crash|anr|proc_died|kill|finish_activity|create_activity|destroy_activity|on_resume_called|on_stop_called|activity_launch_time|focused_root_task|wtf)" | grep -i "rbsbweather\|$PKG" | tail -${1:-14} | cut -c1-230; }
crashes() { timeout 20 adb logcat -d -b crash 2>/dev/null | grep -A30 "Process: $PKG" | head -45; }
clearlogs() { timeout 10 adb logcat -c; timeout 10 adb logcat -b events -c 2>/dev/null; timeout 10 adb logcat -b crash -c 2>/dev/null; }

add_auckland() {
  for t in 1 2 3 4; do
    adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 3
    tap "Add a place" >/dev/null || tap "Add a place" >/dev/null; sleep 2
    adb shell input text Auckland; sleep 10
    tap "Auckland, New Zealand" && { sleep 8; return 0; }
    adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK; sleep 2
  done; return 1; }

# the widget's rectangle on the home screen (the launcher's nodes for it carry our package name)
widget_rect() { dump; python3 - "$OUT/ui.xml" "$PKG" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):     # the widget's own layout (RemoteViews keep their ids)
    if n.get("resource-id") == sys.argv[2] + ":id/widget_root":
        print(*map(int, re.findall(r"\d+", n.get("bounds")))); sys.exit(0)
sys.exit(1)
PY
}
# where to tap: the widget's temperature (a big target, and plainly part of the widget)
widget_tap_xy() { dump; python3 - "$OUT/ui.xml" "$PKG" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if n.get("resource-id") == sys.argv[2] + ":id/w_temp":
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds"))); print((x1 + x2) // 2, (y1 + y2) // 2); sys.exit(0)
sys.exit(1)
PY
}

place_widget() {   # through the launcher: long-press the home screen → Widgets → search → drag
  for attempt in 1 2 3; do
    adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 2
    adb shell input swipe 540 1250 540 1250 1500; sleep 2
    tap "Widgets" || { shot "no-widgets-menu-$attempt"; continue; }
    sleep 3; shot "picker-$attempt"
    tap "Search" starts >/dev/null || tap "Search widgets" starts >/dev/null; sleep 1
    adb shell input text RBSB; sleep 3; adb shell input keyevent KEYCODE_ESCAPE 2>/dev/null; sleep 1; shot "picker-search-$attempt"
    dump
    if ! xy=$(python3 "$HERE/find.py" "$OUT/ui.xml" "RBSB-Weather with 5 days"); then
      tap "RBSB-Weather" >/dev/null; sleep 2; dump
      xy=$(python3 "$HERE/find.py" "$OUT/ui.xml" "RBSB-Weather with 5 days") || { echo "(widget not in the picker)"; grep -o 'text="[^"]\+"' "$OUT/ui.xml" | head -12; continue; }
    fi
    set -- $xy; px=$1; py=$(( $2 - 120 ))          # the preview sits above its label
    echo "dragging the widget from $px,$py"
    timeout 20 adb shell input draganddrop $px $py 540 700 2500 2>/dev/null || timeout 20 adb shell input swipe $px $py 540 700 4000
    sleep 4; shot "after-drop-$attempt"
    dump; for b in "Add" "ADD" "Add automatically" "Create"; do python3 "$HERE/find.py" "$OUT/ui.xml" "$b" >/dev/null && tap "$b"; done
    adb shell input keyevent KEYCODE_HOME; sleep 4
    if r=$(widget_rect); then echo "WIDGET PLACED at $r"; return 0; fi
    echo "(not on the home screen yet, attempt $attempt)"; shot "home-$attempt"
  done; return 1; }

film_widget() {   # $1 = label, $2 = seconds: records the home screen and measures the widget's frame-to-frame jumps
  local label="$1" secs="$2" r
  adb shell input keyevent KEYCODE_HOME; sleep 3
  r=$(widget_rect) || { echo "WIDGET ($label): not found on the home screen"; return 1; }
  timeout $((secs + 20)) adb shell screenrecord --time-limit "$secs" --bit-rate 8000000 /sdcard/w.mp4 2>/dev/null
  timeout 60 adb pull /sdcard/w.mp4 "$OUT/widget-$label.mp4" >/dev/null 2>&1
  python3 "$HERE/flicker.py" "$OUT/widget-$label.mp4" $r "$OUT/widget-$label" "$label"
  shot "widget-$label"
}

tap_widget() {   # $1 = how the app was before the tap
  local how="$1" xy; xy=$(widget_tap_xy) || { echo "TAP ($how): widget not found"; FAIL=1; return; }
  set -- $xy; cx=$1; cy=$2; echo "tapping the widget's temperature at $cx,$cy"
  clearlogs
  adb shell input tap $cx $cy
  local ok=yes
  for t in 2 5 10; do
    sleep $([ $t = 2 ] && echo 2 || echo $([ $t = 5 ] && echo 3 || echo 5))
    local top; top=$(resumed)
    echo "TAP ($how) after ${t}s: $top"
    echo "$top" | grep -q "$PKG" || ok=no
  done
  timeout 10 adb shell pidof $PKG >/dev/null || { echo "TAP ($how): the app's process is gone"; ok=no; }
  echo "--- Android's activity log for the app ---"; events 14
  C="$(crashes)"; [ -n "$C" ] && { echo "--- crash ---"; echo "$C"; ok=no; }
  timeout 20 adb logcat -d 2>/dev/null | grep -E "$PKG|AndroidRuntime|ActivityTaskManager" | grep -iE "fatal|exception|finish|START u0|Force|died|kill|Displayed" | tail -8 | cut -c1-230
  shot "tap-$how"
  echo "TAP ($how): app stayed open = $ok"; [ $ok = yes ] || FAIL=1
}

playback() {   # films the forecast's 24-hour playback (the Moon) for $2 seconds
  local label="$1" secs="$2"
  adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 4; tap "Forecast" >/dev/null; sleep 2
  dump; local sky; sky=$(python3 - "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if n.get("resource-id", "").endswith("sky") or n.get("resource-id") == "sky":
        print(*map(int, re.findall(r"\d+", n.get("bounds")))); break
PY
)
  [ -n "$sky" ] || sky="27 320 1053 1170"
  tap "Play the coming hours in the sky (an hour a second)" >/dev/null || { echo "PLAYBACK ($label): no play button"; return; }
  timeout $((secs + 20)) adb shell screenrecord --time-limit "$secs" --bit-rate 8000000 /sdcard/p.mp4 2>/dev/null
  timeout 60 adb pull /sdcard/p.mp4 "$OUT/playback-$label.mp4" >/dev/null 2>&1
  python3 "$HERE/flicker.py" "$OUT/playback-$label.mp4" $sky "$OUT/playback-$label" "playback-$label" moon
}

echo "== 1. The last good release (0.6.2), as on Shane's phone before"
adb install -r "$BASE" || exit 1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb emu geo fix 174.8960 -36.9460 >/dev/null
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 6
add_auckland && echo "Auckland added" || echo "(couldn't add Auckland)"
place_widget || { echo "WIDGET: couldn't be placed through the launcher"; FAIL=1; }
sleep 20
film_widget "0.6.2" 20
for how in closed background; do
  [ $how = closed ] && adb shell am force-stop $PKG
  [ $how = background ] && { adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 4; adb shell input keyevent KEYCODE_HOME; sleep 3; }
  tap_widget "0.6.2-$how"; adb shell input keyevent KEYCODE_HOME; sleep 2
done
FAIL_BASE=$FAIL; FAIL=0
playback "0.6.2" 26

echo "== 2. The new build installed over it (as Shane did)"
adb install -r "$NEW" || exit 1
for p in POST_NOTIFICATIONS ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION ACCESS_BACKGROUND_LOCATION; do adb shell pm grant $PKG android.permission.$p 2>/dev/null; done
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 6
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 2; tap "Menu" && tap "Settings"; sleep 3
tap "📍 Use my location"; sleep 15; dump; grep -o 'text="Now: [^"]*"' "$OUT/ui.xml" | head -1
adb shell input keyevent KEYCODE_HOME; sleep 30
film_widget "new" 60
for how in closed background backed-out closed background; do
  case $how in
    closed) adb shell am force-stop $PKG ;;
    background) adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 5; adb shell input keyevent KEYCODE_HOME; sleep 3 ;;
    backed-out) adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 5; adb shell input keyevent KEYCODE_BACK; sleep 3; adb shell input keyevent KEYCODE_HOME; sleep 2 ;;
  esac
  tap_widget "new-$how"; adb shell input keyevent KEYCODE_HOME; sleep 3
done
playback "new" 26
echo "WIDGET CHECK: 0.6.2 taps failed=$FAIL_BASE, new taps failed=$FAIL"
exit $FAIL
