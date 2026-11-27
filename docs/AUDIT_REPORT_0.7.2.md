# RBSB-Weather for Android 0.7.2: final stability audit

Created By Shane BlackbuRn

**Status: release candidate, not released.** Nothing is merged, tagged or published. Shane reviews this report and tests the APK before anything is released.

Shane asked (27 Nov 2026), after testing 0.7.1 on his phone ("it seems quite stable"):
1. Fix the outstanding "not responding" (the warnings check holding its lock during slow downloads), with tests for slow connections, timeouts and failed downloads.
2. A final stability audit of the whole application.
3. Regression testing against 0.6.2, 0.7.0 and 0.7.1.
4. A release candidate APK and this report.

The earlier reports are [AUDIT_REPORT_0.7.0.md](AUDIT_REPORT_0.7.0.md) (the 0.7.0 audit, and Shane's 0.7.1 phone-test fixes). This report covers what changed since, and the final results for the whole app.

## Summary

- **Fixed:** the warnings-check freeze. A test with a hanging download measured the screen waiting 2 to 4 minutes with the old code; it now waits under 200 ms.
- **Found and fixed by this audit:** a widget update could be reported as "not responding" on a slow connection.
- **Final results, commit `562c992`:**
  - 224 of 224 automated tests pass;
  - lint has 0 errors;
  - the widget check finds no crash in 30 cold starts by the widget's tap, with every tap keeping the app open on Android 14 and 12;
  - the 24-hour playback runs, and the Moon rises as the real Moon picture with no flashing;
  - the full stability audit passed on Android 10, 12 and 14, with the same 3 suburbs and 8 world places named as before;
  - the other emulator jobs: see "Final run".
- **Outstanding:** the test signing key (Shane's decision), and what can only be judged on a real phone (smoothness, battery over a day, other makers' phones).

## 1. The warnings-check freeze

**What happened:** the warnings and earthquake checks held one lock for the whole download. The screen reads warnings under the same lock on the main thread, each time the forecast updates. On a slow or dropped connection the screen waited for the download, and Android reported "App isn't responding". The 0.7.1 stability audit caught it on a tablet emulator. It had been this way since 0.4, so 0.6.2 had it too.

**The fix:**
- Downloads now run outside that lock. The lock is only held for quick work in memory and the small warning files.
- Each feed (warnings, earthquakes) downloads one at a time. So the open app and the background check never download the same feed at once, and a warning is still notified only once.
- A warnings or earthquake download now has a one-minute limit in total, so a server that sends a byte now and then can't keep a check going for ever. The big wind downloads (up to 4 MB) are left as they were, so a slow connection can still finish them.

**Tests (`SlowNetworkTest`):**

| Test | Old code | 0.7.2 |
|---|---|---|
| The screen's reads while the warnings download hangs | waited 240 s (until the test let the download go) | under 200 ms |
| The same while the earthquake download hangs | waited 120 s | under 200 ms |
| A download that times out | reported; last warnings kept | same |
| Two checks at once (app and background) | | one download, each warning notified once |
| Real connections: one that never answers, one that trickles a byte every 0.5 s, one refused | | each ends in time with "took too long" or a connection error |

## 2. Found and fixed by this audit: the widget update on a slow connection

**What happened:** when Android asks the widget to update, it allows about 10 seconds (60 in the background) before reporting the app as not responding. The update waited for the weather download, and on a slow connection each weather service can take up to a minute. This has been the case since 0.5.

**The fix:** the widget's picture is drawn and the update reported done first, and the download follows.

**Test:** with a weather download that hangs, the update is reported done before it.

## 3. Whole-app review

Every lock, every thread and every place the main thread could wait was checked, with these results:

| Area | Result |
|---|---|
| Locks the main thread takes | The warnings lock was fixed (section 1). The Moon and Earth picture locks are only taken off the main thread (the sky and globes use the picture made in the background). The Moon's 24-hour cache and the cloud cache are quick in-memory locks. |
| Main thread waiting for another thread | None. Widgets wait for the main thread to draw their words (0.7.1), with a 10 s limit. |
| Network on the main thread | None. Forecasts, search, naming, Test connection, updates and warnings all download in the background. |
| Android's broadcast time limits | The widget update was fixed (section 2). There are no other receivers. |
| Background work | One WorkManager check about every 15 minutes, only with a network. There are no wake locks or exact alarms, and GPS is asked at most hourly in the background. |
| Memory | Caches have limits: 96 clouds, 40 Moon pictures, 12 latest Moons, Earth maps halved on small heaps. Widget frames use a 6 MB budget. |
| Text into pictures | Only on the main thread (the 0.7.1 widget-tap crash). |
| Settings and warning files | Written atomically. A damaged settings file reads as the defaults. |
| Exported components, pending intents | Only the launcher activity is exported. All pending intents are immutable. |

**Left as it is:** settings are saved on the main thread when you change one (a small file, as in 0.6.2).

## 4. Regression against 0.6.2, 0.7.0 and 0.7.1

| Feature | 0.6.2 | 0.7.0 | 0.7.1 | 0.7.2 |
|---|---|---|---|---|
| Forecast, providers and backups, units | ✅ | ✅ | ✅ | ✅ (tests) |
| Animated sky: Sun, Moon, clouds | ✅ | ⚠️ Moon flicker in playback | ✅ | ✅ |
| 24-hour playback | ⚠️ stopped at once if the sky appeared before its forecast | ⚠️ often (📍 My location) | ✅ | ✅ (emulator: plays through 24 h) |
| Widget: picture, animation, tap | ⚠️ rare native crash on tap | ❌ flicker, closes after tap | ✅ | ✅ (30 cold starts, every tap state) |
| Status-bar temperature | ✅ | ✅ | ✅ | ✅ |
| 📍 My location, worldwide naming | n/a | ✅ | ✅ | ✅ (3 suburbs, 8 world places, 29 places in 21 countries) |
| Warnings and earthquakes | ⚠️ could freeze on slow networks | ⚠️ same | ⚠️ same | ✅ |
| Moon, Earth, Wind tabs | ✅ | ✅ | ✅ | ✅ (walk-through) |
| Offline and back online | ✅ | ✅ | ✅ | ✅ (audit) |
| Updates from GitHub | ✅ | ✅ | ✅ | ✅ (tests; the APK download is unchanged) |

## 5. Final run

Commit `562c992`, 27 Nov 2026. This is the APK delivered as the release candidate.

**Results by job:**

| Job | Walk-through, suburbs and world names | Random use | Audit scenarios |
|---|---|---|---|
| Automated tests | 224 of 224 passed | | |
| Lint | 0 errors | | |
| Widget check, Android 14 | every tap kept the app open; 0 of 15 cold starts crashed (0.6.2: 0 of 15 in this run) | | |
| Widget check, Android 12 | the same | | |
| Android 10 (Pixel 6) | ✅ | ✅ 12,500 events, no crash or ANR | ✅ all passed |
| Android 12 (Pixel 6) | ✅ | ✅ 12,500 events, no crash or ANR | ✅ all passed |
| Android 14 (Pixel 6) | ✅ | ✅ 12,500 events, no crash or ANR | ✅ all passed |
| Android 15 (Pixel 6) | ✅ | ✅ 12,500 events, no crash or ANR | not finished: see below |
| 5" phone, Android 14 | n/a | ✅ 7,500 events, no crash or ANR | ✅ all passed |
| 10" tablet, Android 14 | n/a | ✅ 7,500 events, no crash or ANR | ✅ all passed |
| Emulator check, Android 14 | ✅ every step; Auckland 17°C, the same as Open-Meteo | | |

**Memory** (total PSS after 6, 12 and 18 rounds of every tab):

| Device | Round 6 | Round 12 | Round 18 |
|---|---|---|---|
| Android 10 | 151 MB | 149 MB | 146 MB |
| Android 12 | 142 MB | 144 MB | 145 MB |
| Android 14 | 184 MB | 156 MB | 159 MB |
| 5" phone | 158 MB | 151 MB | 143 MB |
| 10" tablet | 449 MB | 343 MB | 364 MB |

**Jobs that had to be run again** (none of these was the app; each was checked):
- **Android 15's audit scenarios never finished.**
  - In two runs the emulator stopped responding during the random testing.
  - In the third, the random taps had left Android's quick-settings panel pulled down over the app. The audit then couldn't type into the app's search, so every scenario after it failed ("couldn't add Auckland"); its screen dump shows the panel's Internet, Bluetooth and Flashlight tiles.
  - Android 14's audit had failed the same way in an earlier run and passed when run again.
  - The audit script now closes the panel first, and presses Back when Android 15's tile editor stays open (`scripts/emulator/audit.sh`).
  - Android 15's walk-through, names and random testing passed in each run where the emulator kept going.
- **The 5" phone and the tablet:** the emulator stopped responding in the first run; both passed when run again.
  - The tablet's first audit also flagged memory: 323 MB, then 437 MB. Two seconds later it read 322 MB again, and the rerun levelled off (343 → 364 MB), so this was a picture briefly held, not a leak.
- **The emulator check:** Android's own Pixel Launcher stopped responding in the first run. It passed when run again.

**The 24-hour playback on the emulator** (Android 14, one picture a second): it runs from Friday morning to Saturday 05:00. The Moon rises after 01:00 and shows as the real Moon picture in every frame, with no flash to the plain one.

## 6. Limitations and what's outstanding

- **The signing key (Shane's decision):** test builds are signed with a test key that is in the public repository. A stable public release should use a private release key that only Shane keeps. Changing keys means one uninstall and reinstall.
- **Only on a real phone:**
  - smoothness (the emulators draw in software, so their frame times say nothing about a phone);
  - battery over a day;
  - the widget flicker itself (the emulator's launcher barely animates widgets);
  - other makers' phones (Samsung, Xiaomi and others add their own background limits).
- **GitHub's emulators** were unreliable during this audit:
  - the emulator stopped responding mid-test;
  - Android's own Pixel Launcher reported "isn't responding";
  - one audit started with the network still off and couldn't add a place.
  - Each was checked, and none was the app. Jobs that failed this way were run again; the final run table says which.
- **Play Protect** still warns at install (explained in the README).

## 7. What Shane should test on the phone

1. **Install over 0.7.1.** Your places should stay.
2. **Widget:** tap it many times (app closed, in the background, already open), and watch it for a few minutes.
3. **Playback at night with the Moon up:** the Moon should change smoothly.
4. **Warnings on a slow connection:** with a weak signal, or switching Wi-Fi off and on, open the app and pull to refresh. Then open Warnings → Check now. The screen should never freeze.
5. **📍 My location:** check the suburb at home and at work.
6. **Battery after a day:** Settings → Battery → RBSB-Weather.
