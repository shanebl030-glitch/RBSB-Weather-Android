#!/usr/bin/env bash
# Experiment (repro branch only): can installs signed with the public test key move to a new
# release key by APK key rotation, without uninstalling (keeping the app's data), and is the
# test key shut out afterwards? Throwaway keys only. Created By Shane BlackbuRn
set -u
OLD="$1"; ROT="$2"; BAD="$3"; NEXT="$4"; OUT="${5:-rot-out}"; ATK1="${6:-}"; ATK2="${7:-}"
PKG=io.github.shanebl030glitch.rbsbweather
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"
dump() { timeout 30 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null; }
tap() { for i in 1 2 3 4 5 6; do dump; if xy=$(python3 "$HERE/find.py" "$OUT/ui.xml" "$1" "${2:-exact}"); then adb shell input tap $xy; return 0; fi; sleep 2; done; return 1; }
has() { dump; grep -q "$1" "$OUT/ui.xml"; }
open() { adb shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1; sleep 8; }
add_auckland() {
  for t in 1 2 3 4; do
    open; tap "Add a place" || true; sleep 2
    adb shell input text Auckland; sleep 10
    tap "Auckland, New Zealand" && { sleep 8; return 0; }
    adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_BACK; sleep 2
  done; return 1; }
for i in $(seq 1 40); do timeout 8 adb shell "ping -c 1 -W 3 geocoding-api.open-meteo.com" >/dev/null 2>&1 && break; sleep 3; done
signer() { adb shell dumpsys package $PKG | grep -iE "versionName|signatures|pastSigningCertificates|signing" | head -6; }

echo "== 1. the published 0.6.2 (test key), with a saved place"
adb install "$OLD" | tail -1
add_auckland && echo "PLACE ADDED" || echo "PLACE NOT ADDED"
open; has "Auckland" && echo "BEFORE: Auckland shown" || echo "BEFORE: Auckland not shown"
signer

echo "== 2. 0.7.2 signed with the NEW key, rotated from the test key, installed over it"
R=$(adb install -r "$ROT" 2>&1 | grep -E "Success|Fail|Error|INSTALL" | tail -1); echo "ROTATED UPDATE: $R"
open; has "Auckland" && echo "AFTER: Auckland still there (data kept)" || echo "AFTER: Auckland gone"
signer
adb exec-out screencap -p > "$OUT/after-rotation.png"

echo "== 3. a later build signed only with the public TEST key (what an attacker could make)"
R=$(adb install -r "$BAD" 2>&1 | grep -E "Success|Fail|Error|INSTALL" | tail -1); echo "TEST-KEY UPDATE: $R"

echo "== 3b. a build signed with someone else's key, with NO hand-over"
R=$(adb install -r "$ATK1" 2>&1 | grep -E "Success|Fail|Error|INSTALL" | tail -1); echo "OTHER-KEY UPDATE: $R"
echo "== 3c. an attacker's own hand-over: the public test key -> the attacker's key (all capabilities)"
R=$(adb install -r "$ATK2" 2>&1 | grep -E "Success|Fail|Error|INSTALL" | tail -1); echo "ATTACKER-HANDOVER UPDATE: $R"
signer

echo "== 4. a later release signed with the new key (the normal next release)"
R=$(adb install -r "$NEXT" 2>&1 | grep -E "Success|Fail|Error|INSTALL" | tail -1); echo "NEXT RELEASE: $R"
open; has "Auckland" && echo "NEXT: Auckland still there" || echo "NEXT: Auckland gone"

echo "== 4b. the test-key build again, now that the new key's release is installed"
R=$(adb install -r "$BAD" 2>&1 | grep -E "Success|Fail|Error|INSTALL" | tail -1); echo "TEST-KEY UPDATE AFTER NEXT: $R"
signer

echo "== 5. a fresh install of the rotated 0.7.2 (a new user)"
adb uninstall $PKG >/dev/null
R=$(adb install "$ROT" 2>&1 | tail -1); echo "FRESH INSTALL: $R"
open; dump; grep -q "Welcome to RBSB-Weather" "$OUT/ui.xml" && echo "FRESH: opens (welcome screen)" || echo "FRESH: welcome not seen"
