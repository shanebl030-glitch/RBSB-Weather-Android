# RBSB-Weather for Android 0.7.0: stable-release audit

Created By Shane BlackbuRn

**Status: release candidate, not released.** Shane tests it on his phone and approves before anything is merged, tagged or published.

Shane asked (25 Nov 2026):
- "suburb identification must work worldwide, not just in New Zealand"
- "a full crash and bug audit covering the entire application"

This report says what was tested, what failed, what was fixed and what is still outstanding.

## How it was tested

| What | Where | Size |
|---|---|---|
| Automated tests (unit, app logic, Robolectric screens) | GitHub Actions, JDK 17 | __TESTS__ tests |
| Android lint, release build, all checks | GitHub Actions | 0 errors (39 warnings, listed below) |
| Full walk-through (every tab, menu, settings, globe drag and rotate, Wind, Test connection, updates, rotation, leave and return, pull to refresh, My location) | Emulators: Android 10, 12, 14, 15 (Pixel 6) | 4 runs |
| My location at Auckland suburbs (Waiuku, East Tāmaki, Clarks Beach) and around the world (Tokyo, Berlin, Paris, Rome, New York, Sydney, Marfa TX, Gordes FR), read off the app's own screen | Same 4 emulators | 11 places × 4 |
| Random use ("monkey"): taps, swipes, rotation, back, menu, with places, My location, warnings and the status-bar temperature on | Android 10, 12, 14, 15 (5 × 2,500 events); 5" phone and 10" tablet (5 × 1,500) | 65,000 events |
| Audit scenarios (restart, process death, offline and back, permissions taken away, large text, dark mode, memory, frame timing, background) | Android 10, 12, 14, 15, 5" phone, 10" tablet | 6 runs |
| Real OpenStreetMap replies for naming | 29 places in 21 countries, and one at sea | 30 replies |
| Code review of crash paths, threading, memory, network, privacy | This workspace | Whole app |

The scripts: `scripts/emulator/drive.sh` (walk-through), `stress.sh` (random use), `audit.sh` (scenarios), and `scripts/osm/fetch_world.py` (naming data). They run in `.github/workflows/stability.yml` and `emulator.yml` on every push to the branch.

## Results by area

### 1. Crashes, exceptions and freezes
- **Found:** "app isn't responding" (ANR) under random use on Android 12 and 15. Android's own ANR trace showed the main thread making pictures: first the Moon picture, then the cloud images, for the forecast sky and the Earth globe. After turning the phone, or as dusk darkens the clouds, a dozen new ones were made in one frame.
- **Fixed:** both are now made on background threads. A stand-in (a simple Moon, or the nearest cloud scaled) shows for a moment, and the sky redraws when the real one is ready. Widgets, which draw off the main thread, still make them straight away.
- **Found:** an out-of-memory error while making the Moon picture could crash the sky, widgets or Earth screens.
- **Fixed:** it's caught, its memory is freed and the picture is skipped. The texture files are now closed after reading.
- **Result:** no crash and no ANR from RBSB-Weather in 65,000 random events on four Android versions and two other screen sizes. Android 15's own launcher crashed once when the random taps left the app; that isn't RBSB-Weather.
- **Found:** out of memory on the Earth tab, and again under random use, on one Android 10 run whose emulator allowed only a 16 MB Java heap (earlier Android 10 runs allowed more). The Earth maps alone take 16 MB.
- **Fixed:** the app already asks for Android's larger memory allowance ("large heap"), and that emulator still gave it 16 MB. So the app now keeps the Earth maps at half size when it's given less than 96 MB. Real phones allow 192–512 MB, so they keep full detail.
- **Code review:**
  - 23 `!!` null assertions checked; each is guarded or on data the app made itself.
  - All bitmap decoding is guarded for out-of-memory.
  - Settings, forecast and warning files are written atomically, and a damaged settings file reads as the defaults.

### 2. Memory and performance
- **Memory over 30 rounds of every tab:** it rises while the Earth, Moon and Wind textures load, then levels off (the change between round 6 and round 12):

  | Device | Change, round 6 to 12 |
  |---|---|
  | Android 10 | +13 MB |
  | Android 12 | −6 MB |
  | Android 15 | −2 MB |
  | 5" phone | −4 MB |
  | Tablet | +2 MB |

  No leak was found.
- **Size:** typical total use is 150–250 MB with every tab opened. The Pixel 6 emulator showed up to 450 MB after random use, mostly graphics for the globes.
- **Frame timing:** on the emulators, which draw everything in software without a GPU, 77–100% of frames are slower than 16 ms (90th percentile 53–129 ms). **This can't be judged here**; a phone's GPU draws the animated sky far faster. **Outstanding:** check smoothness on Shane's phone. If it stutters, the sky animation is the place to look ("Animated sky" can be turned off in Settings).

### 3. Location detection and reverse geocoding
- **Found:** Android 10 never found a position for My location unless another app had asked recently (Android 10 has no "current location" call). **Fixed:** the app asks for one update itself.
- **Found:** Android 12 gave no fresh GPS position through the "current location" call after the first one. **Fixed:** GPS is asked by listening for one update.
- **Found:** a cached fused position from before you moved could be used. **Fixed:** the newest position from any of Android's providers wins, and GPS is asked when the newest is more than a minute old.
- **Shane's suburb examples** (all four Android versions, in the app):
  - Waiuku → "Waiuku, Auckland, New Zealand"
  - East Tāmaki → "East Tāmaki, Auckland, New Zealand"
  - Clarks Beach → "Clarks Beach, Auckland, New Zealand"
- **Worldwide, in the app** (all four Android versions):

  | Emulator at | My location showed |
  |---|---|
  | Tokyo | "Udagawachō, Shibuya, Japan" |
  | Berlin | "Kreuzberg, Berlin, Germany" |
  | Paris | "11th Arrondissement, Paris, Ile-de-France, France" |
  | Rome | "Trastevere, Rome, Lazio, Italy" |
  | New York | "Brooklyn, Kings County, United States" |
  | Sydney | "Bondi Beach, Sydney, New South Wales, Australia" |
  | Marfa | "Marfa, Presidio County, United States" (a town with nothing smaller named) |
  | Gordes | "Gordes, Vaucluse, France" (a village) |

  OpenStreetMap supplied the local area wherever the phone's geocoder gave only the city.
- **Worldwide, on real OpenStreetMap data:** 29 places in 21 countries (WorldNamesTest). The order is suburb, neighbourhood, quarter, city district, district, borough; then village or hamlet, municipality, and last town or city.
  - A city is never passed off as a local area: Paris's "Paris" district is skipped.
  - Administrative units are skipped: "Cape Town Ward 54" gives "Cape Town"; "Municipio Roma I" gives "Trastevere".
- **Earlier finding:** Shane's phone showed "My location", then only the city. Fixed by the OpenStreetMap backup, and less exact names are replaced once the local area is known.
- **Known limits:**
  - Where OpenStreetMap only has a larger name, that's what shows. For example, "Brooklyn" for Williamsburg (from the phone's geocoder) and "Centrum" in Amsterdam.
  - A device with no GPS and no network location (the Pixel C tablet emulator) can't find a position. It says so and doesn't crash.

### 4. Weather data accuracy and provider failures
- **Live check:** the app's temperature for Auckland matched Open-Meteo's live value exactly (19°C both).
- **Existing tests, all passing:** every provider's parser against real replies, the backup chain (primary fails, backup used, primary retried after the recovery time), protected free allowances, and units and conversions.
- **Found:** after My location moved, the previous place's official warnings showed until the next check (a US Freeze Warning on the page for Gordes, France). **Fixed:** My location's warnings are filed by where it is, and are worked out again the moment it moves. There's a new test.

### 5. UI and display
- **Checked on:** 5" (1080×1920), 6.4" (Pixel 6) and 10" tablet (2560×1800) screens; large text (150%); dark mode; portrait and landscape.
- **Results:** no crashes, and the text fits. On the tablet the forecast uses the width (7 days across). With large text the Moon can sit behind the high/low line in the sky; it's readable.
- **Outstanding:** a look on Shane's phone. Screenshots of every scenario are on the `audit-shots-*` and `emulator-shots` branches.

### 6. Background operation and battery
- **What runs:** a WorkManager check about every 15 minutes, only with a network. There are no wake locks and no exact alarms.
- **Location requests:** every request Android logged was closed after use; none was left open after leaving the app or after the background check.
- **Changed:** the background check asks GPS at most once an hour (it was every 10 minutes when the position was stale). The rest of the time it uses the network or fused position, which uses far less battery.
- **Outstanding:** real battery use over a day on Shane's phone. Phone makers (Samsung, Xiaomi and others) also add their own limits that can delay or stop background checks.

### 7. Network failures, timeouts and recovery
- **Offline (airplane mode, Wi-Fi and data off):** no crash on refresh, on every tab, on My location or on Test connection. The saved forecast still shows, and Test connection finishes with a report.
- **Back online:** refresh works again.
- **Code:** 15 s connect and 20 s read timeouts and size limits on every download. Failed updates retry after 30 s, 1, 2, then every 5 minutes. A failed source keeps its last data.

### 8. Permissions and privacy
- **Taking access away:** with location access taken away (while running), the app reopens without a crash and Settings says "Location access is off". The background check doesn't look. With notifications refused (Android 13+), Settings says so.
- **Logs:** they hold no coordinates, keys or addresses. Location log lines give only the provider, the fix's age and its accuracy.
- **What is sent:** coordinates are rounded to about 100 m and sent only to the weather providers, and to OpenStreetMap when the phone can't name the local area. The User-Agent identifies the app.
- **Exported components:** the only one is the launcher activity. The widget receivers and file provider aren't exported. All pending intents are immutable.
- **Outstanding (Shane's decision):**
  - **The signing key:** test builds are signed with a test key that is in the public repository, so anyone could sign an APK that installs over RBSB-Weather. A stable public release should use a private release key that only Shane keeps. Changing keys means one uninstall and reinstall.
  - **Play Protect** still warns at install (explained in the README).

### 9. Settings persistence and restarts
- **Force-stop and reopen:** places, My location (with its name) and settings are kept.
- **Android closing the app in the background** (process death): it reopens with the forecast, with no crash.
- **Settings from before 0.7** still read (unit test).

### 10. Android versions and devices
- **Android:** 10, 12, 14 and 15 on emulators; the minimum supported is Android 10.
- **Screens:** a 5" phone, a 6.4" phone and a 10" tablet.
- **Found and fixed:** the Android 10 and Android 12 location problems above.
- **Not covered:** real phones from other makers (Samsung One UI, Xiaomi and so on), Android 11 and 13 (in between the tested versions), and devices without Google's geocoder (the OpenStreetMap backup is for those, and is tested on real data).

### 11. Regression of existing features
- **Automated tests:** all __TESTS__ pass. They cover forecasts, providers and backups, units, warnings, earthquakes, Moon, Earth, Wind (GRIB2), updates, Test connection, widgets, the status-bar temperature and the coffee link.
- **On emulators:** the walk-through repeats everything Shane has used, with checks that the globe moves when dragged and when rotating slowly, and that the wind streaks move.

## Everything found, and what happened

| # | Problem | Found by | Fixed |
|---|---|---|---|
| 1 | My location showed "My location" (phone had no name) | Shane | ✅ OpenStreetMap backup |
| 2 | Only the city shown, not the suburb | Shane | ✅ Local area first; replaced when known |
| 3 | Suburb rules only suited New Zealand | Shane | ✅ International order, tested in 21 countries |
| 4 | ANR: Moon picture made on the main thread | Random use, Android 12 and 15 | ✅ Background |
| 5 | ANR: cloud images made on the main thread | ANR trace, Android 12 and 15 | ✅ Background |
| 6 | Android 10: no position for My location | Android 10 emulator | ✅ Asks for one update |
| 7 | Android 12: no fresh GPS position | Android 12 emulator | ✅ Listens for one update |
| 8 | Stale cached position used after moving | Emulator, Auckland → Wellington | ✅ Newest fix wins, GPS when stale |
| 9 | Warnings from the previous place shown after moving | Audit, Marfa → Gordes | ✅ Filed by where it is |
| 10 | Out of memory in the Moon picture could crash | Code review | ✅ Caught and skipped |
| 10b | Out of memory on the Earth tab with a 16 MB heap | Android 10 emulator | ✅ Smaller maps when under 96 MB |
| 11 | 4 lint errors | Lint | ✅ |
| 12 | GPS woken by the background check every 10 min when stale | Battery review | ✅ At most hourly |
| 13 | Test key in the public repository | Review | ⏳ Shane's decision |
| 14 | Smoothness, battery over a day, other makers' phones | Can't be measured here | ⏳ Shane's phone |

Lint warnings left (none are crash risks):
- 11 Kotlin style suggestions (UseKtx);
- 8 version checks that are always true on Android 10+;
- 6 attributes for newer Android versions;
- 5 newer library versions;
- 3 small widget text sizes;
- 2 hard-coded widget texts;
- 1 newer target SDK;
- 1 newer Gradle plugin;
- 1 layout simplification.

## Final run

__FINAL__

## What Shane should test on his phone

1. **Install over 0.6.2**, then open the app. Your places should still be there.
2. **Turn on Settings → 📍 Use my location.**
   - Check the name shows your suburb (East Tāmaki at work, for example).
   - Drive somewhere else, open the app and check it updates.
3. **Add the widget.** Check it shows 📍 and your suburb.
   - **With "Allow all the time":** check it changes after you've moved and waited about 15 minutes, without opening the app.
4. **Check smoothness:** is the sky animation smooth? Swipe between places, open Earth, the globe and Wind.
5. **Battery after a day** with My location on: Settings → Battery → RBSB-Weather.
6. **Take location access away** in Android's settings, then reopen the app: it should say so and not crash.
