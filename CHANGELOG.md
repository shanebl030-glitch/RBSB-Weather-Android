# RBSB-Weather for Android — Changelog

Created By Shane BlackbuRn

A separate app from RBSB-Weather for the desktop, with its own versions.

## 0.7.2 – 2026-11-27 (stable release)

Shane tested the 0.7.2 release candidate on his phone ("everything is working great") and approved it for the stable release. The app is exactly the tested release candidate (commit 562c992); only the signing, the release workflow and the documents changed after it.

### Signing (release preparation)
- **Releases are now signed with Shane's own release key,** which is never in the repository. The public test key signs only test builds of branches.
- **No uninstall:** 0.7.2 carries a key hand-over (APK key rotation, signature scheme v3) from the test key. It installs over 0.6.2, 0.7.0 and 0.7.1 and keeps all data. After it, Android refuses updates signed only with the test key.
  - Tested on emulators with a throwaway key: the update installs and keeps the saved place, a later test-key build is refused, and a later release installs.
  - The test key keeps the "installed data" and "permission" capabilities. Without "permission" the update was refused (`INSTALL_FAILED_DUPLICATE_PERMISSION`).
- **New Release workflow** (`.github/workflows/release.yml`):
  - it runs by hand, from main, after Shane approves it in the `release` environment;
  - it runs all tests and lint, signs, checks the signer is the release key and not the test key, and makes `SHA256SUMS`;
  - it creates a draft GitHub release for Shane to publish.
- **New `docs/SIGNING.md`:** how the key is made, backed up and given to the workflow.


Shane tested 0.7.1 on his phone: "it seems quite stable". He asked for the outstanding "not responding" to be fixed, then a final audit of the whole app and a release candidate.

### Fixed
- **The screen could freeze while warnings or earthquakes downloaded on a slow or failing connection** ("App isn't responding"; found by the 0.7.1 stability audit, there since 0.4).
  - **Cause:** the warnings and earthquake checks held a lock for the whole download, and the screen reads the warnings under the same lock on the main thread. With a slow or dropped connection the screen waited for the download. A test with a hanging download measured the screen waiting 2 to 4 minutes, until the download was let go.
  - **Fix:** downloads now run outside that lock, one at a time per feed. The screen never waits (under 200 ms in the same test). The open app and the background check still never download the same feed twice at once, and a warning is still notified only once.
  - **Also:** a warnings or earthquake download now has a one-minute limit in total, so a server sending a byte now and then can't keep a check going for ever.
- **A widget update could be reported as "not responding" on a slow connection** (found by this audit, there since 0.5). Android allows a widget update about 10 seconds (60 in the background). The update waited for the weather download, and each weather service can take up to a minute when the connection is slow. The widget's picture is now drawn and the update reported done first; the download follows.

### Tests added
- **Warnings and earthquakes (`SlowNetworkTest`):**
  - with a download that hangs, the screen's reads stay instant (they waited minutes with the old code);
  - a timed-out download is reported, and the last warnings are kept;
  - two checks at once download once and notify once;
  - real connections that hang, trickle or are refused all end in time.
- **Widget (`WidgetStabilityTest`):** with a weather download that hangs, the widget update is reported done first.

## 0.7.1 – 2026-11-26 (test build, not released)

0.7.0 was **not approved** after Shane tested it on his phone: "0.7.0 is not approved for stable release." He found three problems. Each was reproduced first, its cause found in the code, fixed, and covered by an automated test. The worldwide suburb naming and all the 0.7.0 audit fixes are unchanged.

### Fixed
- **The Moon flickered in 24-hour playback** (Shane: "The moon flickers during the 24-hour forecast playback").
  - **Cause:** a 0.7.0 audit fix (against "App isn't responding") made the Moon picture in the background, but showed *nothing* until each new picture was ready, so the sky drew a plain disc instead. In playback the Moon's phase and tilt change every frame, so it was nearly always the plain disc, flashing to the real Moon now and then.
  - **Reproduced:** a test playing the 24 hours frame by frame found the plain Moon in 718 of 720 frames. On the emulator, a film of the playback showed the plain disc at 23:12, 05:20 and 06:10 and the real Moon at 04:20.
  - **Fix:** the last real Moon picture stays on screen while the next one is made. Clouds work the same way: a cloud keeps its last picture, and a cloud never drawn before borrows the latest cloud's picture for a moment instead of popping in. The pictures are still made in the background, so the 0.7.0 ANR fix stays.
  - **A second cause, found on the emulator:** pressing Play could stop the playback at once, and the sky flashed to the plain daytime sky for a moment (the Moon vanishing and coming back). The sky's frame loop kept the playback range from when it started. If the sky appeared before its forecast had loaded, that range was 0, so every Play ended straight away. This has been possible since 0.5, but 0.7.0 made it common: 📍 My location's page appears before its forecast, and again each time it moves. The loop now always uses the current forecast. A test shows the sky before the forecast arrives, then presses Play: with the old code the playback stays at "Now"; now the hour moves on every half second.
  - **Caught on the way:** a first version of this fix drew the very first Moon and each new cloud on the main thread. The stability audit's random testing then caused an ANR on Android 15 (the main thread making a cloud while memory was tight). Now nothing is made on the main thread except the first cloud of all.
- **Tapping the widget opened the app, which then closed** (Shane: "the app opens and then immediately closes").
  - **Cause:** a native crash inside Android's text drawing (SIGSEGV in Skia, `prepare_for_direct_mask_drawing`), which closes the app without a Java error. Android's software text drawing (text into a picture) isn't safe on two threads at once. The widget thread draws the mini forecast's words into each widget frame. 0.7.0 also drew the status-bar temperature icon (text) on the main thread every time My location changed, which happens right after the app opens. So opening the app from the widget made the two meet.
  - **Reproduced on the emulator** with the 0.7.0 candidate (Android 12): the app crashed this way after a widget tap, main thread drawing text while the widget thread drew its frames. The same crash also happened once with 0.6.2 (Android 14, 1 of 15 cold starts by the widget's tap), with the main thread drawing the app's own text. So it's an old risk that 0.7.0 made much more likely.
  - **Fix:** no text is ever drawn into a picture off the main thread. The widget's words don't move, so they're drawn once per update on the main thread and laid over every frame; the temperature icon is drawn there too. If the main thread is too busy to draw them within 10 seconds, the widget keeps its current picture and tries again at the next update.
- **The widget flickered** (Shane: "The desktop widget flickers during normal operation").
  - **Cause:** 0.7.0 redrew the widget several times within seconds: when the app opened, closed, located you and fetched weather, and whenever My location's approximate position wobbled. Each redraw sends new frames to the launcher, which restarts the widget's animation (a visible jump).
  - **Fix:** requests that arrive within 1.2 seconds are merged into one redraw, and a redraw that would show the same picture again isn't sent. My location only moves when you've moved further than the position's own uncertainty, so an approximate position wobbling by a kilometre or two no longer makes it (and the widget) hop between suburbs.
  - **Not reproduced on the emulator:** its launcher barely animates widgets, so the flicker couldn't be seen there. The fix is based on the code and is covered by tests (a burst of 10 requests gives at most one redraw; an unchanged widget isn't redrawn).

### Found, not changed (older than 0.7)
- **A possible "App isn't responding" while warnings are being checked on a slow or lost connection.** The stability audit caught it once on a tablet emulator: the app's screen waited for the warnings check, which held its lock during the download. It's been this way since 0.4 (0.6.2 has it too), so it isn't one of the three problems. It isn't changed here because Shane asked for no unrelated changes. Fixed in 0.7.2.

## 0.7.0 – 2026-11-25 (test build)

Shane: "I'd like to add GPS tracking so if I travel to a different suburb or city or country it updates the widget. This is only for the android not the desktop app."

### Added
- **📍 My location** (Android only). Settings → **📍 Use my location** adds a place that follows the phone:
  - it moves when you've travelled more than 1 km (another suburb, town or country), and is named by Android's geocoder (suburb or town, region, country). Small wobbles of the location fix don't cause new downloads;
  - it's an ordinary place in the pager, My places, warnings, earthquakes, Moon, Earth and Wind, shown with 📍;
  - it updates whenever you open the app, and **Update my location now** updates it at once.
- **Widgets show my location** (on by default when Use my location is on): the home-screen widgets and the status-bar temperature show 📍 My location instead of the place you last looked at.
- **While the app is closed:** with Android's **Allow all the time** location access, the background check (about every 15 minutes, when Android allows) moves My location and refreshes the widget. Without it, My location updates when you open the app. Settings shows which, with a button to Android's setting.
- **Permissions:** approximate or precise location (asked only when you turn it on; approximate is enough), and optional background location. No Google Play services needed.
- **Naming worldwide** (Shane: "suburb identification must work worldwide, not just in New Zealand"):
  - **The most specific meaningful local area,** whatever the country calls it, in this order: suburb, neighbourhood, quarter, city district, district, borough. If none is named: the village or hamlet, then a municipality, and last the town or city.
  - **Never a city or town passed off as a local area:**
    - a "local area" that is just the city's own name again (Paris files Paris as its own district) is skipped;
    - so are administrative units such as "Cape Town Ward 54" or "Municipio Roma I".
  - **What comes after the name:** for a local area, its town or city and state ("Bondi Beach, Sydney, New South Wales"); for a town, its wider area and state ("Marfa, Presidio County, Texas").
  - **Tested** on real OpenStreetMap replies from 29 places in 21 countries, and in the app on emulators at Tokyo, Berlin, Paris, Rome, New York, Sydney, Marfa and Gordes, as well as Waiuku, East Tāmaki and Clarks Beach.
- **How the name is found:**
  - **First:** the phone's own geocoder.
  - **If the phone can't give the suburb** (Shane's phone showed "My location", then only the city), OpenStreetMap (Nominatim) is asked with the rounded position.
  - **A less exact name** (none, a city, a region) is replaced as soon as the suburb is known, without having to move.
  - **Lookups are remembered** for 6 hours per spot, so OpenStreetMap is asked rarely.
- **Privacy:** coordinates are rounded to 3 decimals (about 100 m) before they're stored or sent. They go to your weather providers, the same as any saved place, and to OpenStreetMap only when the phone can't name the local area.

### Fixed (stability audit for a stable release)
- **Warnings from where you were showed at the new place:** after My location moved from Marfa, Texas to Gordes, France, its page still showed a US Freeze Warning until the next check. Official warnings for My location are now filed by where it is, so they go at once when it moves.
- **The turning globes made a new 4 MB picture every frame** (Earth and Wind, while rotating or dragged), causing constant garbage collection, slow frames and an ANR under random use on Android 12. They now reuse three pictures in turn and allocate nothing per frame.
- **Out of memory on the Earth tab** (Android 10 emulator that allowed the app only 16 MB): even with the app's large-memory setting, so the app now keeps the Earth maps at half size when Android gives it less than 96 MB (real phones allow 192–512 MB and keep full detail).
- **Battery:** in the background check (about every 15 minutes), GPS is now asked at most once an hour; the rest of the time the phone's network or fused position is used.
The audit ran lint, plus the full walk-through and 12,500 random taps, swipes, rotations and back presses (Android's "monkey") on each of Android 10, 12, 14 and 15. It found:
- **"App isn't responding" (ANR) under random use on Android 12 and 15:** the forecast sky and the Earth globe made the real Moon picture on the main thread when it wasn't cached. That can take seconds, longer when the Moon tab is saving a 2048-pixel picture at the same time. The picture is now always made in the background (0.7.1: the last real Moon stays on screen meanwhile, not a plain one).
- **Android 10: My location never found a position** unless another app had asked recently (Android 10 has no "current location" call). It now asks for one update itself.
- **GPS position:** now taken by listening for one update; Android 12 gave no fresh GPS position the other way.
- **Low memory:** the Moon picture can no longer crash the sky, widgets or Earth screens when memory runs out; it's skipped and its memory freed. Its texture files are now closed after reading.
- **Lint errors:**
  - location calls are explicitly marked as permission-checked;
  - a Compose `remember` that returned nothing;
  - a test reading state the wrong way.

### Changed
- **Buy me a coffee** is now NZ$1.47 (About and the PayPal link). This was changed on main without a release on 23 Nov and ships with this version.
- Removing 📍 My location from My places turns Use my location off.
- A saved forecast for My location is only shown if it's for the same spot (within 1 km).

## 0.6.2 – 2026-11-23 (released as v0.6.2)

Shane: "I was about to say before you made the change to keep it as is but we can mention it on the GitHub page update".

### Changed
- **The in-app Download and install is back**, exactly as in 0.6.0. The code is identical to 0.6.0's; only the version number differs.
- **The README explains the Google Play Protect warning:**
  - why it appears: the "install other apps" permission that self-updating needs;
  - exactly what the app does with it;
  - how to check the APK against `SHA256SUMS`;
  - how to install anyway.
- **The user guide** says the same in short.
- **Release notes for the GitHub release page** are in `docs/RELEASE_NOTES_0.6.2.md`.
- **Screenshots** of 0.6.2 on Android 14 are on the GitHub page and in the release notes (`docs/screenshots/`). They come from the emulator check, plus the widget and sky pictures from the screen tests.

## 0.6.1 – 2026-11-23 (test build; superseded by 0.6.2)

Shane, installing 0.6.0: "Google Play Protect. Harmful app blocked. RBSB-Weather. This app may be harmful."

### Changed
- **Updates no longer download or install inside the app.**
  - **Why:** 0.6.0 asked for Android's "install other apps" permission and downloaded APKs itself so it could update itself. Google Play Protect treats a sideloaded app that does that as possibly harmful (it can't tell an app updating itself from one installing something else).
  - **Now:** Check for updates still asks GitHub, shows what's new, and notifies you once a day about a new version. **Download from GitHub** opens the download in your browser, and the screen shows the release's published SHA-256 so you can check it. Android installs it, as when you first installed the app.
  - **Removed:** the permission, the in-app downloader, the installer hand-off and the shared-files provider.
  - **A test now fails** if any of them comes back.

## 0.6.0 – 2026-11-23 (test build; blocked by Play Protect – use 0.6.1)

Shane: "perfect." (0.5.2 released as v0.5.2.) Following the roadmap: 0.6, the rest of the desktop's features.

### Added
- **The Wind tab** (🌬 along the bottom), ported from the desktop's `wind.py`, `grib2.py` and `wind_panel.py`:
  - **What it shows:** the world's weather from NOAA's GFS forecast on the globe. There are three layers: **🌬 Wind**, **🌡 Temperature** and **🌧 Rain & snow**. They're shown over the dimmed Earth with coastlines.
  - **Moving streaks** follow the wind on every layer. They only move while animations are on.
  - **Using it:** drag to turn, pinch or +/− to zoom, and tap anywhere for the wind (speed and direction), the temperature and the rain or snow there. A colour key is shown in your units.
  - **The data:** about 400 KB from NOAA NOMADS, or NOAA's copy on Amazon if that fails. It's downloaded every 3 hours, and the saved map is used offline or if a download fails.
  - **The GRIB2 reader** was ported and checked against the desktop's reader on the same NOAA and test files.
- **Check for updates** (menu): asks GitHub for the newest RBSB-Weather for Android release and shows what's new.
  - **Download and install** fetches the APK and checks it against the release's `SHA256SUMS`, and that it's this app and newer. Android then asks you to install it.
  - You can skip a version.
  - It also checks once a day in the background and notifies you once per new version. It can be turned off.
- **Test connection** (menu), as on the desktop:
  - for each weather service: finding it (DNS), connecting over IPv4 and IPv6, and the secure handshake;
  - then a real place search and a forecast from each provider you use, each timed;
  - a plain-English summary, with **Copy** and **Share**. API keys never appear in it.
- **Temperature in the status bar** (Settings): the phone version of the desktop's tray temperature. It's a silent notification with the temperature as its icon, and the conditions, high and low beside it. Off by default.
- **Provider usage** in Weather Providers and in each provider's Test, as on the desktop:
  - Tomorrow.io's requests left today and this hour;
  - Visual Crossing's cost per update;
  - OpenWeather's plan;
  - the Weather Underground station used.
- Wind credit in About.

### Changed
- **Settings switches** turn on and off with a tap anywhere on their row, not only on the small switch.

### Tests
- 180+ tests, plus the emulator check on real Android 14 (see TEST_REPORT.md).

## 0.5.2 – 2026-11-23 (released as v0.5.2)

Shane: "1. the touch and drag to rotate the globe doesn't work. 2. the globe itself doesn't rotate when you tick rotate slowly."

### Fixed
- **Dragging the globe didn't turn it.** Two touch handlers (drag, and pinch to zoom) both watched the same finger, and the pinch one took the movement. One handler now does both: one finger turns the globe, two fingers pinch to zoom.
- **The globe didn't turn with Rotate slowly (or while dragging).** The globe is drawn in the background, and each new position cancelled the picture being drawn. On a phone a picture takes longer than a frame, so while the globe kept moving no picture ever finished. Now each picture finishes, then the newest position is drawn.
- **Tapping a place or quake** on the globe after turning it now finds the right one.

### Changed
- **Rotate slowly, Live clouds and Earthquakes** can be ticked by tapping their words, not only the small box.

### Tests
- **Stress test (simulated phone):** the globe's picture itself must turn about 8° a second with Rotate slowly, and follow a one-finger drag; a drag must not change the zoom. On 0.5.1 this fails: the view turned 32° but only one picture was ever drawn.
- **Emulator check (real Android):** screenshots of the globe before and after Rotate slowly and a drag must differ. Both failed on 0.5.1.

## 0.5.1 – 2026-11-22 (test build; globe didn't turn – use 0.5.2)

Shane: "when I click on earth globe it crashes".

### Fixed
- **Tapping Earth Globe closed the app.** Reproduced on a real Android emulator with the 0.5.0 build:
  `IllegalArgumentException: ending radius must be > 0` from the glow around the globe. On the first
  frame the globe hasn't been measured, so the glow had a radius of 0, which real Android refuses.
  The test environment (Robolectric) doesn't check this, so every test had passed.
  - The globe now waits until it's measured before drawing.
  - Every radial gradient in the app goes through one safe helper. A test stops any new code from
    drawing one directly.

### Added
- **Crash reports:** if the app ever closes unexpectedly, the reason is saved on the phone. The next
  time it opens, it shows the details with **Copy** and **Share**, so they can be sent to Shane.
  Nothing is sent by itself.
- **An emulator check on GitHub:** every push installs the app on a real Android emulator and goes
  through it like a person. It adds Auckland, then visits the Moon, Earth Flat and Earth Globe
  (zoom, spin, drag), every tab, menu and Settings screen, saves a Moon picture, turns the phone
  sideways and back, and leaves and reopens the app. If the app closes, the check prints Android's
  crash log.

### Changed (from the audit)
- **The globe uses less memory:**
  - it's drawn at no more than 1024 pixels (bigger zooms are scaled on screen);
  - it keeps at most two sets of working data;
  - it draws at lower detail while it turns.
- **A failed drawing no longer closes the app.** If the globe can't be drawn, it says so. If the
  Moon picture can't be saved, you get a message instead.
- **The Moon behind the globe** is one small picture, made off the main thread, so the screen doesn't stall.
- **The 2048-pixel Moon export** is no longer kept in memory afterwards (it used 16 MB, plus 33 MB of textures).
- **The app asks Android for a larger memory allowance**, as image-heavy apps do.

## 0.5.0 – 2026-11-22 (test build; crashed on Earth Globe – use 0.5.1)

Shane: "now the next update". Following the roadmap: the Moon and the Earth.

### Added
- **Tabs along the bottom:** Forecast, Moon, Earth and Warnings (the phone version of the desktop's tabs).
- **The Moon tab**, as on the desktop:
  - the Moon turned exactly as it looks in that place's sky;
  - phase, % lit, age, waxing or waning, rise and set, how long it's up and how far away it is;
  - the next new, first quarter, full and last quarter Moons, and the next 7 days with pictures;
  - **Save high-resolution image** saves a 2048-pixel picture to Pictures/RBSB-Weather.
- **The Earth tab**, ported from the desktop's `earth.py`, `globe.py`, `sky.py` and `clouds.py`:
  - **Earth Flat:** the world's day and night now, with city lights, twilight shading, where the Sun and Moon are overhead, your places (with a card for the selected one: time, temperature, day/twilight/night) and earthquakes. Pinch to zoom and drag; double-tap to reset.
  - **Earth Globe:** drag to turn, pinch or +/− to zoom, double-tap to centre, **Rotate slowly**, and it turns smoothly to the place you pick. Behind it are the real Sun, Moon (in its phase, lit side towards the Sun) and stars, with the sunrise glow on the edge.
  - **Live satellite clouds** (EUMETSAT data via Live Cloud Maps): checked every hour and downloaded only when newer; a failed download keeps the last map and waits 15 minutes. A line says how old the clouds are. Can be turned off.
  - Earthquakes as dots coloured by age, sized by magnitude, pulsing in the last hour; tap for details.
- **Show on Earth map** on each warning, and **Show on the globe** on the Earthquakes screen. Tapping an earthquake notification opens the globe turned to that quake.
- Earth-map and cloud credits in About.
- **New app icon:** Shane's RBSB-Weather artwork (the round version), sized to fit inside every launcher's icon shape (round, squircle or square) on a deep-blue background. It's also at the top of About. On Android 13 and newer, themed (single-colour) icons still use a simple sun-and-cloud outline.

### Changed
- The Earth tab's Flat/Globe switch is a pair of tabs: in the screen tests a real tap didn't reach the first design (a segmented button).

### Tests
- 150 tests, all passing (see TEST_REPORT.md).
- New tests mirror the desktop's `test_earth.py`, `test_globe.py`, `test_sky.py` and `test_clouds.py`, using the same PyEphem reference file (see TEST_REPORT.md).

## 0.4.0 – 2026-11-22 (test build; on branch claude/android-0.2.0; not released)

Shane: "okay, that looking really godd." Next on his list: warnings and earthquakes.

### Added
- **Weather warnings**, ported from the desktop's `alerts.py`. Official warnings come straight from the issuer, matched to each place by the warning's own area:
  - MetService (New Zealand), using its colour;
  - the US National Weather Service;
  - MET Norway;
  - MeteoAlarm (most of Europe).
- **Where warnings show:**
  - a coloured **banner across the sky**, with when it ends and who issued it; tap it for the full warning;
  - **badges** in My places;
  - **tags** on the 7-day forecast;
  - **shading** on the 24-hour chart;
  - a **warning line on the widget**.
- **The ⚠ Warnings screen** (menu → Warnings) shows each warning in full: area, valid times, what's expected, what to do, who issued it, and the forecast rain for the same hours. **Open on…** opens it on the issuer's website, and **Mute** takes it off the banner, badges and notifications.
- **My own limits:** gusts, rain in a day, heat, frost, snow and thunderstorms, in your units. They're marked MY LIMIT and "not official". Open-Meteo's daily gust forecast is now downloaded for this.
- **Earthquakes**, ported from the desktop's `quakes.py`:
  - USGS worldwide and GeoNet for New Zealand (GeoNet's version is kept when both report a quake);
  - an Earthquakes list (menu → Earthquakes) with each quake's colour by age, size by magnitude, place, depth, time, distance to your nearest place and a link to GeoNet or USGS;
  - the tsunami reminder for large quakes under the sea.
- **Notifications:**
  - a warning is notified when it's issued, upgraded or reissued;
  - an earthquake is notified at M4.5+ within 300 km of a place, or M7+ anywhere;
  - each is notified once, even after a restart;
  - an optional alarm sound for Red warnings, and an option to notify only for the place on screen.
  - Android 13 and newer asks for permission once.
- **Background checks:** while the app is closed, Android checks about every 15 minutes at most, when there's a network, and may delay it to save battery.
- **Settings → Weather Alerts** and **Settings → Earthquakes**, with the desktop's choices and each place's coverage and last check.
- **Weather Providers:**
  - **Use the primary now**;
  - Tomorrow.io is asked at most every 10 minutes per place, and OpenWeather and Visual Crossing every 5;
  - AccuWeather and Weatherbit are listed as planned, with the desktop's reasons.

### Build and tests
- 136 tests (35 new):
  - the desktop's warning tests, with the same fixture files: geometry, levels, every source, CAP documents fetched once, a failing source, own limits, showing and notifying, the store, settings;
  - the earthquake tests, with the same fixtures: parsing, merging, colours, notification rules, the store;
  - the shared check: notified once across a restart, settings, spacing between checks, a failure keeping the old warnings;
  - the banner, Warnings screen, mute, Earthquakes and Weather Alerts screens;
  - the quota guard.
- Every build now also saves pictures of the Home screen with a warning, the Warnings, Earthquakes and Weather Alerts screens. I looked at them.
- CI keeps Robolectric's Android jars between runs, after one run failed because Maven Central wouldn't send them.
- GitHub Actions run 44985012092: 136 run, 136 passed.

## 0.3.1 – 2026-11-22 (test build; on branch claude/android-0.2.0; not released)

Reported by Shane: "The widget doesn't work. Say Couldn't add widget".

### Fixed
- **"Couldn't add widget":** the widget layout in 0.3.0 had a plain spacer view, which Android doesn't allow in widgets, so the launcher refused it. The spacer is now an allowed type (FrameLayout).

### Build and tests
- 3 new tests load the widget the way a launcher does:
  - every view in the widget layouts must be one Android allows in widgets;
  - the first layout the launcher shows while adding the widget must load;
  - the full widget, with its 20 animation frames and its text, must load.
- Before the fix, all three failed with the same error the launcher hit (run 44976546841). After it, they pass.
- GitHub Actions run 44976754434: 101 run, 101 passed.

## 0.3.0 – 2026-11-22 (test build; on branch claude/android-0.2.0; not released)

Requested by Shane: "the widget doesn't animate like it does on the desktop version. I want the android to be a replica of RBSB-Weather desktop version. nothing missing."

### Added
- **The desktop's animated scene, ported in full** (`scene.py`). It's now the same sky as the desktop:
  - the desktop's sky colours and dawn/dusk rules;
  - the Sun's glow and slowly turning rays;
  - **the Moon in its real phase, position and tilt**, drawn from NASA's Moon images. It's shown whenever it's really above the horizon, and paler by day;
  - twinkling stars;
  - three layers of soft cloud, plus a lower deck in heavy cloud, crossing at the desktop's speeds for the wind (150 seconds in calm air, down to 19 seconds in a gale), towards where the wind blows;
  - a new wind eases in over a few seconds;
  - rain slanted by the wind with splashes, drizzle, swaying snow, rolling fog, and lightning with bolts;
  - weather changes fade into each other.
- **Moon astronomy** (`moon.py`, ported): phase, illumination, moonrise and moonset, the next new, first quarter, full and last quarter moons, and the Moon's tilt in each place's sky. It's checked against the same PyEphem reference values as the desktop, and gets the Moon tab ready.
- **The mini forecast as on the desktop** (`forecast_strip.py`): bottom-right of the sky, with day names (Today, Tomorrow or Tmrw, then the weekday), **animated icons**, the high and low, and the chance of rain with a drop. It shows as many days as fit (small 1–2, medium 3–5, large up to 7) and keeps clear of the text.
- **Animated icons in the 7-day forecast:** turning sun rays, drifting cloud, falling rain and snow, rolling fog, thunder flashes and windy gusts. The maximum wind for each day is now shown too.
- **Playback as on the desktop:**
  - ▶ plays an hour a second and ⏸ pauses;
  - the card shows that hour's temperature, weather and chance of rain, with "▶ Forecast for …";
  - the Sun, Moon and day or night are as they'll be at that time;
  - clouds keep today's wind (a note says so);
  - providers with no hourly forecast say so.
- **An animated home-screen widget:**
  - Android widgets can't run live animation, so the widget loops 3 seconds drawn from the real scene, mini forecast included, and redraws them with each update.
  - The text matches the desktop widget: the provider line ("(backup)"), and "↻ time" top right, amber with "(2 h ago)" when the weather is old.
  - The size you give the widget decides how many forecast days fit.
  - If a launcher refuses the frames, the widget falls back to a still picture.
- **Refreshing as on the desktop:**
  - At start-up every place updates, the one on screen first, then the others 1½ seconds apart. Places updated in the last 2 minutes are skipped.
  - A failed update retries after 30 seconds, 1 minute, 2 minutes, then every 5 minutes.
- **PARITY.md:** every desktop feature and its Android status, kept up to date.
- The About page credits NASA's Moon imagery.

### Changed
- The weather status line moved under the sky, as the desktop's status bar, leaving the sky's bottom-right corner for the mini forecast.

### Build and tests
- 98 tests (38 new):
  - the Moon against PyEphem (rise and set, illumination, phase times, tilt);
  - the scene's conditions (day, night, twilight, Sun, wind, hemisphere, playback hours);
  - cloud speeds, wind easing, weather fades, particles staying in bounds for 10 simulated minutes, lightning only in storms, rain slant;
  - real drawing of every weather code by day and night, the Moon picture, and the widget frames;
  - widget text, retries and staggered start-up.
- Every build now saves pictures of the sky and widget frames, so they can be checked; they're published with the test build in `screens/`.
- GitHub Actions run 44974363240: 98 run, 98 passed.

## 0.2.0 – 2026-11-22 (test build; on branch claude/android-0.2.0; not released)

Requested by Shane: "It's a good start but there a lot that are missing". From the list he chose Weather Providers and Playback + mini forecast (for 0.2), Warnings + earthquakes (0.3), and Moon, Earth, Globe, Wind (0.4 and 0.5). He then added: "The widget are missing as wel".

### Added
- **Weather Providers** (Settings → Weather Providers):
  - Open-Meteo and MET Norway (no key needed), plus OpenWeather, WeatherAPI.com, Visual Crossing, Tomorrow.io and Weather Underground with your own free API keys. These are ported from the desktop app and read the services the same way.
  - Choose the primary, turn backups on or off and put them in order. A backup without a key is skipped.
  - When the primary fails: try it again at the next update, after 15, 30 or 60 minutes, or stay on the backup.
  - Each service shows its coverage, forecast length, hourly detail, free-use limits and credit, when it last worked or what went wrong, a **Get a key** link and **Test connection**.
  - OpenWeather uses One Call 3.0 when your key has it, otherwise the free current and 5-day endpoints (and remembers that for a day).
  - Weather Underground uses your own station ID, or the nearest reporting station; offline or stale stations are skipped.
  - API keys are checked when you paste them, kept in their own private storage (never in the settings), never shown in messages, and left out of phone backups.
  - Every forecast is checked before it's shown: impossible values are dropped, highs and lows put the right way round, and missing sunrise, sunset, "feels like" and weather symbols are worked out.
  - The bottom of the screen credits whichever service supplied the forecast, and says when it came from a backup.
- **Forecast playback:** ▶ under the sky plays the next 24 hours, or drag the slider in 15-minute steps. The sky (Sun or Moon position, cloud, rain, snow, thunder), temperature and conditions change to that time, labelled "Forecast for …". Tap **Now** to go back.
- **Mini forecast in the sky:** the next few days along the bottom of the sky, as many as fit the screen (3 to 7).
- **Home-screen widgets:** a small one (place, temperature, sky symbol, conditions, high and low) and one with the next five days. They have a day and a night background, show the place last viewed in the app, update when the app does and by themselves about every 30 minutes when the weather is stale, and open the app when tapped.

### Changed
- The sky card is taller, to hold the mini forecast.

### Build and tests
- 60 tests (29 new): every provider read from the desktop app's saved replies; missing, wrong and over-quota keys; the normaliser; the primary/backup chain and each recovery choice; provider settings; keys kept out of the settings; playback; the mini forecast; widget contents; and the Weather Providers screen.
- GitHub Actions run 44970300356: 60 run, 60 passed.

## 0.1.0 – 2026-11-22 (test build; on branch claude/android-0.1.0; not released)

Requested by Shane: "okay start the android version". He chose:

- the app ID `io.github.shanebl030glitch.rbsbweather`;
- Android 10 and newer;
- GitHub to build the app.

### Added
- **Your places:**
  - search for any city or town and add it (up to 50);
  - swipe between places;
  - reorder or remove them under **My places** (removing asks first).
- **The animated sky:**
  - its colours follow the real Sun at the place (day, sunrise and sunset, night with stars);
  - the Sun or Moon moves along its arc between rising and setting;
  - clouds drift with the wind;
  - rain, snow, fog and lightning show when they're happening;
  - can be turned off in Settings to save battery.
- **The weather:**
  - the temperature, conditions, feels like, and today's high and low;
  - humidity, wind and gusts with direction, pressure, cloud cover, rain, UV index, sunrise and sunset;
  - the next 24 hours as a chart (temperature line, chance-of-rain bars; swipe sideways);
  - the 7-day forecast.
- **Refreshing:**
  - pull down or tap ⟳;
  - it also updates by itself when the weather is older than your setting (30 minutes by default), while the app is open and when you return to it.
- **Offline:** the last forecast for every place is saved on the phone and shown, marked "saved copy" with its age, together with what went wrong.
- **Providers:** Open-Meteo, with MET Norway as the automatic backup. Sunrise and sunset for the backup are worked out on the phone.
- **Settings:** °C/°F; km/h, m/s, mph or knots; mm or inches; 12- or 24-hour time; refresh interval; animated sky on or off.
- **User guide and About:** the user guide (menu → User guide). The About page has the credits, the GitHub link, the coffee link, and "Created By Shane BlackbuRn", "Designed and Created By Shane BlackbuRn" and "Coded By Claude AI".

### Build and tests
- Built by GitHub Actions on every push. The tests run first, then the APK is built.
- Test builds are signed with a test key kept in the repo, so each new test build installs over the last. A private release key replaces it before any public release.
- The tests use the same forecast samples as the desktop app's test server.
