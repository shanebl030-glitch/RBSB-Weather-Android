#!/usr/bin/env bash
# The rest of the stable-release audit on a real Android emulator, after stress.sh (the app is
# installed, with Auckland and My location): restarts and process death, offline use and recovery,
# location and notification permission taken away, large text, dark mode, memory growth, frame
# timing, and what the app leaves running in the background. Prints AUDIT lines; exits 1 on a
# crash, ANR or failed check. Created By Shane BlackbuRn
set -u
OUT="${1:-audit-out}"
PKG=io.github.shanebl030glitch.rbsbweather
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"; FAIL=0; n=0
API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
shot() { n=$((n+1)); adb exec-out screencap -p > "$OUT/$(printf %02d $n)-$1.png"; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null
  if grep -q "isn.t responding" "$OUT/ui.xml" 2>/dev/null; then echo "AUDIT FAIL: Android showed 'isn't responding'"; FAIL=1; xy=$(python3 "$HERE/find.py" "$OUT/ui.xml" "Wait") && adb shell input tap $xy; sleep 2; fi; }
tap() { for i in 1 2 3 4 5 6; do dump; if xy=$(python3 "$HERE/find.py" "$OUT/ui.xml" "$1" "${2:-exact}"); then adb shell input tap $xy; return 0; fi; sleep 2; done; echo "(not found: '$1')"; return 1; }
has() { dump; grep -q "$1" "$OUT/ui.xml"; }
start() { adb shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1; sleep "${1:-5}"; }
home() { adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 2; tap "Forecast" >/dev/null || adb shell input keyevent KEYCODE_BACK; sleep 2; }
settings() { home; tap "Menu" && tap "Settings"; sleep 3; }
ok() { echo "AUDIT OK: $1"; }
bad() { echo "AUDIT FAIL: $1"; FAIL=1; }
crashcheck() {   # $1 = what just happened
  C="$(adb logcat -d -b crash 2>/dev/null | grep -A25 "Process: $PKG" | head -40)$(adb logcat -d 2>/dev/null | grep "ANR in $PKG" | head -3)"
  if [ -n "$C" ]; then bad "crash or ANR after $1"; echo "$C"; adb logcat -b crash -c 2>/dev/null; adb logcat -c; fi
}
pss() {   # total PSS in kB ("TOTAL PSS:" on Android 11+, the "TOTAL" row on Android 10)
  m=$(adb shell dumpsys meminfo $PKG 2>/dev/null | tr -d '\r')
  v=$(echo "$m" | grep -m1 "TOTAL PSS:" | awk '{print $3}'); [ -n "$v" ] || v=$(echo "$m" | grep -m1 -E "^ *TOTAL +[0-9]" | awk '{print $2}'); echo "$v"; }
adb logcat -c; adb logcat -b crash -c 2>/dev/null
for p in POST_NOTIFICATIONS ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION ACCESS_BACKGROUND_LOCATION; do adb shell pm grant $PKG android.permission.$p 2>/dev/null; done
adb emu geo fix 174.8960 -36.9460 >/dev/null     # East Tamaki
echo "== 0. A clean start: Auckland, and My location on"
adb shell pm clear $PKG >/dev/null
for p in POST_NOTIFICATIONS ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION ACCESS_BACKGROUND_LOCATION; do adb shell pm grant $PKG android.permission.$p 2>/dev/null; done
start 6
tap "Add a place" && sleep 2 && adb shell input text Auckland && sleep 8 && tap "Auckland, New Zealand"; sleep 8
settings; tap "📍 Use my location"; for i in $(seq 1 10); do sleep 3; has "Now: " && break; done
crashcheck "setting up"; home

echo "== 1. Settings survive a restart and Android closing the app"
settings; has "Now: " && ok "My location is on before the restart" || bad "My location wasn't on to start with"
before=$(grep -o 'text="Now: [^"]*"' "$OUT/ui.xml" | head -1)
adb shell am force-stop $PKG; start 6
settings; after=$(grep -o 'text="Now: [^"]*"' "$OUT/ui.xml" | head -1); [ -z "$after" ] && { dump; after=$(grep -o 'text="Now: [^"]*"' "$OUT/ui.xml" | head -1); }
[ -n "$after" ] && ok "after force-stop: $after (before: $before)" || bad "My location lost after a restart"
home; has "Auckland" && ok "saved places kept after a restart" || bad "saved places lost after a restart"
adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell am kill $PKG; sleep 2      # Android closing it in the background
start 6; crashcheck "being closed in the background"; has "°" && ok "reopens after Android closed it in the background" || bad "blank after process death"
shot restart

echo "== 2. No internet, then back"
adb shell svc wifi disable; adb shell svc data disable; adb shell cmd connectivity airplane-mode enable >/dev/null 2>&1; sleep 4
home; tap "Refresh" >/dev/null; sleep 12; crashcheck "refresh offline"; shot offline-forecast
dump; grep -o 'text="[^"]*\(internet\|connect\|Couldn\|offline\|old\|saved\)[^"]*"' "$OUT/ui.xml" | head -3
has "°C" && ok "offline: the saved forecast still shows" || bad "offline: no forecast shown"
for t in "Moon" "Earth" "Wind" "Warnings"; do tap "$t" >/dev/null; sleep 6; crashcheck "$t tab offline"; done
shot offline-wind
settings; tap "Update my location now" >/dev/null; sleep 10; crashcheck "My location offline"; dump; grep -o 'text="Now: [^"]*"' "$OUT/ui.xml" | head -1
home; tap "Menu" && tap "Test connection"; sleep 2; tap "Run the test" >/dev/null; for i in $(seq 1 25); do sleep 3; has "Summary:" && break; done
crashcheck "Test connection offline"; has "Summary:" && ok "Test connection finishes offline with a report" || bad "Test connection didn't finish offline"
adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1; adb shell svc wifi enable; adb shell svc data enable; sleep 10
home; tap "Refresh" >/dev/null; sleep 15; crashcheck "refresh back online"; dump
grep -q 'text="↻\|Updated\|just now' "$OUT/ui.xml"; has "°C" && ok "back online: forecast shows" || bad "back online: nothing shown"
shot back-online

echo "== 3. Location permission taken away, then given back"
adb shell pm revoke $PKG android.permission.ACCESS_FINE_LOCATION 2>/dev/null; adb shell pm revoke $PKG android.permission.ACCESS_COARSE_LOCATION 2>/dev/null
adb shell pm revoke $PKG android.permission.ACCESS_BACKGROUND_LOCATION 2>/dev/null; sleep 2
start 6; crashcheck "opening without location permission"
settings; dump; grep -o 'text="[^"]*\(Location access is off\|Allow all the time\|Off: \)[^"]*"' "$OUT/ui.xml" | head -3
has "Location access is off" && ok "without permission it says so (and doesn't crash)" || echo "AUDIT NOTE: no 'Location access is off' text seen"
shot no-permission
adb shell cmd jobscheduler run -f $PKG 0 >/dev/null 2>&1; sleep 8; crashcheck "background check without permission"
for p in ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION ACCESS_BACKGROUND_LOCATION; do adb shell pm grant $PKG android.permission.$p; done
start 4
if [ "$API" -ge 33 ]; then
  adb shell pm revoke $PKG android.permission.POST_NOTIFICATIONS; start 5; crashcheck "notifications refused"
  settings; for i in 1 2 3 4; do adb shell input swipe 540 1800 540 700 300; sleep 1; done
  has "Notifications are turned off" && ok "notifications refused: Settings says so" || echo "AUDIT NOTE: notification note not seen"
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
fi

echo "== 4. Large text and dark mode"
adb shell settings put system font_scale 1.5; sleep 3; start 5; home; crashcheck "large text"; shot large-text-forecast
settings; shot large-text-settings; tap "Menu" >/dev/null; adb shell input keyevent KEYCODE_BACK
adb shell settings put system font_scale 1.0
adb shell cmd uimode night yes; sleep 3; start 5; home; crashcheck "dark mode"; shot dark-forecast
settings; shot dark-settings
adb shell cmd uimode night no; sleep 2

echo "== 5. Memory over repeated use"
start 5; home; sleep 5; m0=$(pss)
for r in 1 2 3 4 5 6; do for t in "Moon" "Earth" "Wind" "Warnings" "Forecast"; do tap "$t" >/dev/null; sleep 3; done; done
tap "Forecast" >/dev/null; sleep 8; m1=$(pss)
for r in 1 2 3 4 5 6; do for t in "Moon" "Earth" "Wind" "Warnings" "Forecast"; do tap "$t" >/dev/null; sleep 3; done; done
tap "Forecast" >/dev/null; sleep 8; m2=$(pss)
crashcheck "30 rounds of the tabs"
echo "AUDIT MEMORY (PSS kB): start $m0, after 6 rounds $m1, after 12 rounds $m2"
if [ -n "$m1" ] && [ -n "$m2" ] && [ $(( m2 - m1 )) -gt 60000 ]; then bad "memory still growing (+$(( (m2 - m1) / 1024 )) MB between round 6 and 12)"; else ok "memory levels off (round 6 to 12: $(( (${m2:-0} - ${m1:-0}) / 1024 )) MB)"; fi
adb shell dumpsys meminfo $PKG | grep -E "Java Heap:|Native Heap:|Graphics:|TOTAL PSS:|Views:|Activities:" | head -8

echo "== 6. Frame timing (software-rendered emulator: slower than a phone)"
adb shell dumpsys gfxinfo $PKG reset >/dev/null; home
for i in 1 2 3; do adb shell input swipe 540 1700 540 600 250; sleep 1; adb shell input swipe 540 600 540 1700 250; sleep 1; done
tap "Moon" >/dev/null; sleep 3; tap "Forecast" >/dev/null; sleep 3
adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|90th percentile|99th percentile" | head -5

echo "== 7. What's left running in the background"
adb shell input keyevent KEYCODE_HOME; sleep 30
open_requests() {   # location requests the app opened (+registration) and hasn't closed (-registration)
  adb shell dumpsys location 2>/dev/null | tr -d '\r' | grep -o "[+-]registration [0-9]*/$PKG/[A-Za-z0-9]*" | awk '{print substr($1,1,1), $2}' \
    | python3 -c 'import sys
o = {}
for line in sys.stdin:
    s, k = line.split()
    o[k] = o.get(k, 0) + (1 if s == "+" else -1)
print(sum(1 for v in o.values() if v > 0))'
}
L=$(open_requests)
[ "${L:-0}" = 0 ] && ok "no location requests left open after leaving the app (every request Android logged was closed)" || bad "$L location request(s) left open"
W=$(adb shell dumpsys power 2>/dev/null | grep -i "$PKG" | grep -i "wake" | head -3)
[ -z "$W" ] && ok "no wake locks held" || { echo "$W"; bad "wake lock held"; }
adb shell dumpsys jobscheduler 2>/dev/null | grep -A4 "$PKG/androidx.work" | grep -E "Required constraints|Minimum latency|period|PERIODIC|interval" | head -4
adb shell dumpsys alarm 2>/dev/null | grep -c "$PKG" | sed 's/^/AUDIT ALARMS for the app: /'
adb shell cmd jobscheduler run -f $PKG 0 >/dev/null 2>&1; sleep 25; crashcheck "background check"
sleep 25; L=$(open_requests); [ "${L:-0}" = 0 ] && ok "the background check closed its location requests" || bad "$L location request(s) left open after the background check"
adb shell dumpsys location 2>/dev/null | grep "$PKG: min/max" | head -3 | sed 's/^ */AUDIT LOCATION USE (since boot): /'
adb shell dumpsys notification --noredact 2>/dev/null | grep -A2 "pkg=$PKG" | grep -m2 "android.title" | sed 's/^/AUDIT NOTIFICATION /'

crashcheck "the end of the audit"
[ $FAIL = 0 ] && echo "AUDIT: all checks passed" || echo "AUDIT: PROBLEMS FOUND"
exit $FAIL
