#!/usr/bin/env bash
# Stability audit: random taps, swipes, rotation and back presses all over RBSB-Weather (Android's
# "monkey"), with places, My location, warnings and the status-bar temperature on, then checks
# Android's crash and ANR logs. Run on several Android versions. Created By Shane BlackbuRn
set -u
APK="$1"; OUT="${2:-stress-out}"; EVENTS="${3:-2500}"
PKG=io.github.shanebl030glitch.rbsbweather
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"
FAIL=0
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null
  if grep -q "isn.t responding" "$OUT/ui.xml" 2>/dev/null; then xy=$(python3 "$HERE/find.py" "$OUT/ui.xml" "Wait") && adb shell input tap $xy; sleep 2; fi; }
tap() { for i in 1 2 3 4 5 6 7 8; do dump; if xy=$(python3 "$HERE/find.py" "$OUT/ui.xml" "$1" "${2:-exact}"); then adb shell input tap $xy; echo "tapped '$1'"; return 0; fi; sleep 2; done; echo "not found: '$1'"; return 1; }
crashes() {   # print any crash or ANR of the app since the last clear
  timeout 30 adb logcat -d -b crash 2>/dev/null | grep -A40 "Process: $PKG" | head -60
  timeout 30 adb logcat -d 2>/dev/null | grep -E "ANR in $PKG" | grep -v "^--" | head -10      # other apps' crashes (the launcher, say) aren't ours
}

adb root >/dev/null 2>&1; sleep 4; adb wait-for-device      # to read Android's ANR traces (allowed on test images)
anr_trace() {   # the app's main thread from the newest ANR trace
  f=$(adb shell 'ls -t /data/anr/ 2>/dev/null' | tr -d '\r' | head -1)
  [ -n "$f" ] || { echo "(no ANR trace file readable)"; return; }
  echo "----- main thread in /data/anr/$f -----"
  adb shell cat "/data/anr/$f" 2>/dev/null | tr -d '\r' | awk '/^----- pid .* -----/{p=0} /Cmd line: io.github.shanebl030glitch.rbsbweather/{p=1} p&&/^"main"/{m=1} m{print; n++} m&&/^$/{exit} n>60{exit}'
}
echo "Android $(adb shell getprop ro.build.version.release) (API $(adb shell getprop ro.build.version.sdk))"
adb install -r "$APK" || exit 1
for p in POST_NOTIFICATIONS ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION ACCESS_BACKGROUND_LOCATION; do adb shell pm grant $PKG android.permission.$p 2>/dev/null; done
adb emu geo fix 174.7460 -36.8580
adb logcat -c; adb logcat -b crash -c 2>/dev/null
adb shell am start -W -n $PKG/.MainActivity; sleep 6

echo "== set up: two places, My location, the temperature notification"
tap "Add a place" && sleep 2 && adb shell input text Auckland && sleep 8 && tap "Auckland, New Zealand"; sleep 8
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 2
tap "Menu" && tap "Settings" && sleep 3
tap "📍 Use my location"; sleep 12
for i in 1 2 3 4; do adb shell input swipe 540 1800 540 700 300; sleep 1; done
tap "Temperature in the status bar"; sleep 3
dump; grep -o 'text="Now: [^"]*"' "$OUT/ui.xml" | head -1
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 3
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 3
echo "== the background check, run now (WorkManager)"
adb shell cmd jobscheduler run -f $PKG 0 >/dev/null 2>&1 || true
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -p $PKG >/dev/null 2>&1

C="$(crashes)"; [ -n "$C" ] && { echo "CRASH DURING SET-UP:"; echo "$C"; FAIL=1; }

responsive() { [ "$(timeout 25 adb shell echo ok 2>/dev/null | tr -d '\r')" = ok ]; }
for seed in 11 23 37 41 59; do
  echo "== monkey, seed $seed, $EVENTS events"
  if ! responsive; then echo "EMULATOR STOPPED RESPONDING before seed $seed (adb gets no answer): the rest of the random use can't run"; HUNG=1; break; fi
  echo "memory: device free $(timeout 15 adb shell cat /proc/meminfo 2>/dev/null | grep -E 'MemAvailable' | awk '{print int($2/1024) " MB"}'), app $(timeout 20 adb shell dumpsys meminfo $PKG 2>/dev/null | grep -m1 'TOTAL PSS:' | awk '{print int($3/1024) " MB"}')"
  timeout 20 adb logcat -c; timeout 20 adb logcat -b crash -c 2>/dev/null
  timeout 20 adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 3
  # no system keys (power, volume) or app switching; touches, swipes, rotation, back and menu
  ( timeout -k 20 1200 adb logcat -v time > "$OUT/logcat-$seed.txt" 2>&1 & )      # Android's log during the round, for a hang
  timeout -k 30 900 adb shell monkey -p $PKG -s $seed --throttle 120 --pct-syskeys 0 --pct-appswitch 0 --pct-anyevent 0 \
      --pct-rotation 4 --pct-nav 6 --pct-majornav 6 --pct-touch 50 --pct-motion 25 --pct-trackball 0 --pct-flip 0 \
      --monitor-native-crashes -v "$EVENTS" > "$OUT/monkey-$seed.txt" 2>&1
  rc=$?
  if [ $rc = 124 ] || [ $rc = 137 ]; then
    echo "MONKEY HUNG (seed $seed): stopped after 15 minutes. Last events:"; tail -5 "$OUT/monkey-$seed.txt"
    timeout 20 adb shell pkill -f com.android.commands.monkey 2>/dev/null
    echo "app running: $(timeout 10 adb shell pidof $PKG || echo no)"; echo "top activity: $(timeout 10 adb shell dumpsys activity activities 2>/dev/null | grep -m1 -E 'mResumedActivity|topResumedActivity')"
    echo "--- Android's log, last 40 lines ---"; tail -40 "$OUT/logcat-$seed.txt" | cut -c1-220
    grep -E "ANR in|FATAL|Watchdog|lowmemorykiller|Killing.*$PKG" "$OUT/logcat-$seed.txt" | tail -8
  fi
  pkill -f "adb logcat -v time" 2>/dev/null
  tail -3 "$OUT/monkey-$seed.txt"
  grep "// CRASH: \|// NOT RESPONDING: " "$OUT/monkey-$seed.txt" | grep -v "$PKG" | sed 's/^/(another app, not counted) /' | head -3
  if grep -q "// CRASH: $PKG\|// NOT RESPONDING: $PKG" "$OUT/monkey-$seed.txt"; then
    echo "MONKEY FOUND A PROBLEM (seed $seed):"; grep -A30 "// CRASH\|// NOT RESPONDING" "$OUT/monkey-$seed.txt" | head -50; FAIL=1
    grep -q "NOT RESPONDING: $PKG" "$OUT/monkey-$seed.txt" && anr_trace
  fi
  C="$(crashes)"; [ -n "$C" ] && { echo "CRASH LOG (seed $seed):"; echo "$C"; FAIL=1; echo "$C" | grep -q "ANR in" && ! grep -q "NOT RESPONDING: $PKG" "$OUT/monkey-$seed.txt" && anr_trace; }
  timeout 30 adb exec-out screencap -p > "$OUT/after-$seed.png"
  timeout 20 adb shell settings put system user_rotation 0 2>/dev/null
  # the monkey can pull down quick settings and switch things off: put them back for the next round
  timeout 20 adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1; timeout 20 adb shell svc wifi enable 2>/dev/null; timeout 20 adb shell svc data enable 2>/dev/null
done

if [ "${HUNG:-0}" = 1 ]; then
  echo "--- last Android log before it stopped ---"; ls -t "$OUT"/logcat-*.txt 2>/dev/null | head -1 | xargs -r tail -30 | cut -c1-200
  ls -t "$OUT"/logcat-*.txt 2>/dev/null | head -1 | xargs -r grep -E "lowmemorykiller|Killing|ANR in|FATAL|Watchdog|kswapd" | tail -10
  echo "STRESS: INCONCLUSIVE (the emulator stopped responding; no crash or ANR of the app was recorded before that)"
  [ $FAIL = 0 ] && exit 3 || exit 1
fi
echo "== memory after the run"
adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:|Java Heap|Native Heap|Graphics" | head -6
adb shell pidof $PKG >/dev/null && echo "app still running" || echo "app not running (may have been closed by the monkey's back presses)"
[ $FAIL = 0 ] && echo "STRESS: no crash, no ANR" || echo "STRESS: PROBLEMS FOUND"
exit $FAIL
