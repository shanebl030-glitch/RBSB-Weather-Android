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
crashes() {   # Java crashes, and native ones with their backtrace (from the crash log and Android's tombstone)
  local c; c=$(timeout 20 adb logcat -d -b crash 2>/dev/null)
  echo "$c" | grep -A30 "Process: $PKG" | head -45
  if echo "$c" | grep -q ">>> $PKG <<<"; then
    echo "$c" | grep -A60 ">>> $PKG <<<" | grep -E ">>>|signal|Abort|Cause|backtrace|#[0-9]{2} pc" | head -45
    local t; t=$(timeout 10 adb shell 'ls -t /data/tombstones/ 2>/dev/null | grep -v pb | head -1' | tr -d '\r')
    [ -n "$t" ] && { echo "--- tombstone $t: every thread that was drawing ---"; timeout 20 adb shell cat "/data/tombstones/$t" 2>/dev/null | python3 -c '
import sys, re
blocks = re.split(r"\n(?=-{3} -{3} -{3}|pid: \d+, tid: )", sys.stdin.read())
for b in blocks:
    head = next((l for l in b.splitlines() if l.startswith("pid:")), "")
    frames = [l.strip() for l in b.splitlines() if re.search(r"#\d+ pc", l)]
    interesting = [f for f in frames if re.search(r"hwui|Sk|rbsbweather|Canvas|Text|Layout|Widget|Scene|Strip|Moon|Cloud|ThreadPool|Looper|Choreographer|RenderThread", f)]
    if head and interesting:
        print("THREAD", head[:120]); [print("   ", f[:200]) for f in interesting[:40]]
'; }
  fi
}
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
WBOX=""
widget_rect() { [ -n "$WBOX" ] && echo "$WBOX" || return 1; }
# where to tap: the middle of the widget
widget_tap_xy() { local r; r=$(widget_rect) || return 1; set -- $r; echo $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }

place_widget() {   # through the launcher: long-press the home screen → Widgets → search → drag
  for attempt in 1 2 3; do
    adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 2
    timeout 30 adb exec-out screencap -p > "$OUT/home-before.png"
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
    dump; if grep -q "package=\"$PKG\"" "$OUT/ui.xml"; then
      sleep 3; timeout 30 adb exec-out screencap -p > "$OUT/home-after.png"
      WBOX=$(python3 "$HERE/diffbox.py" "$OUT/home-before.png" "$OUT/home-after.png") || WBOX=""
      echo "WIDGET PLACED at ${WBOX:-unknown}"; [ -n "$WBOX" ] && return 0
    fi
    echo "(not on the home screen yet, attempt $attempt)"; shot "home-$attempt"
  done; return 1; }

film_widget() {   # $1 = label, $2 = seconds: records the home screen and measures the widget's frame-to-frame jumps
  local label="$1" secs="$2" r
  adb shell input keyevent KEYCODE_HOME; sleep 3
  r=$(widget_rect) || { echo "WIDGET ($label): position unknown"; shot "widget-$label-not-found"; return 1; }
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
  # where Play and the playback slider are
  local play slider
  play=$(python3 "$HERE/find.py" "$OUT/ui.xml" "Play the coming hours in the sky (an hour a second)") || { echo "PLAYBACK ($label): no play button"; return; }
  slider=$(python3 - "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if "SeekBar" in (n.get("class") or ""):
        print(*map(int, re.findall(r"\d+", n.get("bounds")))); break
PY
)
  echo "PLAYBACK ($label): play at $play, slider at ${slider:-?}"
  # A. Play pressed once while filming (no screen reading meanwhile, which can disturb the app)
  timeout $((secs + 20)) adb shell screenrecord --time-limit "$secs" --bit-rate 8000000 /sdcard/p.mp4 2>/dev/null &
  local rec=$!
  sleep 2; adb shell input tap $play
  sleep 4; timeout 20 adb exec-out screencap -p > "$OUT/playback-$label-4s.png" 2>/dev/null
  sleep 6; timeout 20 adb exec-out screencap -p > "$OUT/playback-$label-10s.png" 2>/dev/null
  wait $rec
  timeout 60 adb pull /sdcard/p.mp4 "$OUT/playback-$label.mp4" >/dev/null 2>&1
  python3 "$HERE/flicker.py" "$OUT/playback-$label.mp4" $sky "$OUT/playback-$label" "playback-$label" moon
  # B. the same 24 hours by sliding the playback slider slowly from Now to the end (every frame a new hour)
  [ -n "$slider" ] || return
  set -- $slider
  local y=$(( ($2 + $4) / 2 )) x1=$(( $1 + 8 )) x2=$(( $3 - 8 ))
  adb shell input tap $x1 $y; sleep 2
  timeout 45 adb shell screenrecord --time-limit 26 --bit-rate 8000000 /sdcard/s.mp4 2>/dev/null &
  rec=$!
  sleep 1; adb shell input swipe $x1 $y $x2 $y 22000 &
  sleep 11; timeout 20 adb exec-out screencap -p > "$OUT/scrub-$label-mid.png" 2>/dev/null
  wait $rec
  timeout 60 adb pull /sdcard/s.mp4 "$OUT/scrub-$label.mp4" >/dev/null 2>&1
  python3 "$HERE/flicker.py" "$OUT/scrub-$label.mp4" $sky "$OUT/scrub-$label" "scrub-$label" moon
}

net() { for i in $(seq 1 40); do timeout 8 adb shell "ping -c 1 -W 3 geocoding-api.open-meteo.com" >/dev/null 2>&1 && return 0; sleep 3; done; echo "(no network after 2 minutes)"; }
# the widget's tap, sent directly: what Android runs when the widget is tapped (open the app, clearing above it)
intent_tap() {   # $1 = label
  clearlogs
  timeout 30 adb shell am start -W -n $PKG/.MainActivity -f 0x14000000 2>&1 | grep -E "Status|Activity|TotalTime|Warning" | tr '\n' ' '; echo
  local ok=yes; for t in 3 8; do sleep 5; local top; top=$(resumed); echo "INTENT TAP ($1) after ~${t}s: $top"; echo "$top" | grep -q "$PKG" || ok=no; done
  timeout 10 adb shell pidof $PKG >/dev/null || { echo "INTENT TAP ($1): the app's process is gone"; ok=no; }
  echo "--- Android's activity log ---"; events 16
  C="$(crashes)"; [ -n "$C" ] && { echo "--- crash ---"; echo "$C"; ok=no; }
  echo "INTENT TAP ($1): app stayed open = $ok"; [ $ok = yes ] || FAIL=1
}

adb root >/dev/null 2>&1; sleep 4; adb wait-for-device
coldstarts() {   # $1 = label: 15 cold starts by the widget's own tap, counting crashes
  local crashed=0
  for k in $(seq 1 15); do
    adb shell am force-stop $PKG; sleep 2; clearlogs
    timeout 30 adb shell am start -n $PKG/.MainActivity -f 0x14000000 >/dev/null 2>&1; sleep 9
    local ev; ev=$(events 30)
    if echo "$ev" | grep -q "am_crash\|am_proc_died"; then crashed=$((crashed + 1)); echo "COLD START $k ($1): CRASHED"; echo "$ev" | grep "am_crash\|am_proc_died"; crashes; fi
    adb shell input keyevent KEYCODE_HOME; sleep 2
  done
  echo "COLD STARTS ($1): $crashed of 15 crashed"; [ $crashed = 0 ] || FAIL=1
}
echo "== 1. The last good release (0.6.2), as on Shane's phone before"
net
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
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 5; adb shell input keyevent KEYCODE_HOME; sleep 3; intent_tap "0.6.2-background"
adb shell am force-stop $PKG; sleep 2; intent_tap "0.6.2-closed"; adb shell input keyevent KEYCODE_HOME; sleep 2
temp_on() { adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 3; tap "Menu" >/dev/null && tap "Settings" >/dev/null; sleep 2
  for i in 1 2 3 4; do adb shell input swipe 540 1800 540 700 300; sleep 1; done
  tap "Temperature in the status bar" >/dev/null; sleep 3
  timeout 20 adb shell dumpsys notification --noredact 2>/dev/null | grep -c "pkg=$PKG" | sed 's/^/notifications from the app: /'; adb shell input keyevent KEYCODE_HOME; sleep 2; }
temp_on
coldstarts "0.6.2 with the widget and the temperature"
FAIL_BASE=$FAIL; FAIL=0
playback "0.6.2" 26

echo "== 2. The new build installed over it (as Shane did)"
adb install -r "$NEW" || exit 1
adb shell input keyevent KEYCODE_HOME; sleep 3; shot "home-right-after-update"; sleep 30; shot "home-30s-after-update"
dump; grep -q "package=\"$PKG\"" "$OUT/ui.xml" && echo "WIDGET after the update: on the home screen" || echo "WIDGET after the update: our views not on the home screen"
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
for how in background closed open backed-out; do
  case $how in
    closed) adb shell am force-stop $PKG; sleep 2 ;;
    background) adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 5; adb shell input keyevent KEYCODE_HOME; sleep 3 ;;
    open) adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 5 ;;
    backed-out) adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 5; adb shell input keyevent KEYCODE_BACK; sleep 3 ;;
  esac
  intent_tap "new-$how"; adb shell input keyevent KEYCODE_HOME; sleep 2
done
coldstarts "new with the widget, My location and the temperature"
playback "new" 26
echo "WIDGET CHECK: 0.6.2 taps failed=$FAIL_BASE, new taps failed=$FAIL"
exit $FAIL
