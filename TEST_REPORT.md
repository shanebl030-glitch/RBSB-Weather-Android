# RBSB-Weather for Android — Test report

Created By Shane BlackbuRn

| Item | Result |
|---|---|
| Version | 0.7.0 (test build) |
| Date | 2026-11-25 |
| Where | GitHub Actions: Ubuntu 26.04 runner, JDK 17 (Temurin), Gradle 8.14.3, Android Gradle Plugin 8.10.1, Kotlin 2.1.21, Compose BOM 2025.05.01, compile/target SDK 35, min SDK 29 |
| Command | `./gradlew testDebugUnitTest`, then `./gradlew assembleRelease` |
| Tests | 180+ run, all passed on GitHub Actions (see the CI run for this commit), plus the emulator check (run 45069457909): every step passed, no crash |

## What's tested

**New in 0.7 (📍 My location):**
- **When it moves** (HereTest): a first fix makes My location, rounded to 3 decimals and named; under 1 km changes nothing and doesn't ask for a name; 1.5 km, another city and another country move it; NaN, out-of-range, older than 30 minutes or vaguer than 5 km fixes are ignored; without a name it keeps the old name nearby, else "My location".
- **Settings:** saved and read back; first in the list when new, keeps its spot after that; the widget's place with each switch; settings from before 0.7 still read.
- **Saved forecast:** Auckland's saved weather isn't shown after My location moves to Wellington; its file is kept while it's in use.
- **The app** (SevenTest): off by default and never looks; on, it adds My location and fetches its weather; a few hundred metres changes nothing; driving to Wellington renames it, fetches Wellington's weather, says "📍 Now at …" and updates the widget; no fix explains why and keeps the last place; turning it off or the bin removes it and its saved forecast; the widget shows 📍 My location or the place on screen.
- **Choosing a position:** the newest fix from any of Android's providers wins (so a cached position from before you moved isn't used), then the most accurate; if the newest is over 2 minutes old, GPS is asked too.
- **The background check:** without "Allow all the time" it never looks; with it, it moves My location and gets the weather; unchanged, it does nothing; with the app open it hands the place to the app instead; turned off, it never looks.
- **The Settings section** (Robolectric, with Android's location permission granted): the switch, the status ("Now: …"), the approximate-location note, Update my location now, Widgets show my location, the "while the app is closed" text with and without background access, and turning it off. My places shows 📍 and the bin asks "Stop using your location?".
- **Emulator check:** location permissions granted, the emulator's position set to Auckland and then Wellington (`adb emu geo fix`); the step passes only if My location changes.
- **Not testable here:** a real phone travelling, Android's battery limits on the background check, and the geocoder names on your phone (the test emulator may not have one).

**0.6.2:**
- The same code and tests as 0.6.0. 0.6.1 had removed the in-app installer, and Shane chose to keep it and explain the Play Protect warning instead.
- **Not testable here:** Play Protect itself. The test emulator doesn't have it.

**New in 0.6:**
- **GRIB2 reader** (Grib2Test), checked on the desktop's 11 files (real NOAA GFS and synthetic files in every supported packing) against the desktop's reader, itself checked against ECMWF's ecCodes:
  - every sample point, missing-value count, sum and weighted sum;
  - real GFS winds are plausible;
  - a south-first file reads reversed;
  - damaged files fail cleanly;
  - sign-magnitude integers.
- **Wind data** (WindTest), as the desktop's test_wind:
  - which run and forecast hour to use; the NOMADS and AWS addresses;
  - index byte ranges, including shared messages;
  - the direction convention; interpolation, wrapping and the poles;
  - the temperature and rain layers; the colours; rain see-through where dry.
  - **Store:** download, then NOMADS down so AWS is used, then everything down so the saved map is kept and it waits 20 minutes; nothing saved gives a clear error.
- **Wind tab** (WindScreenTest, a 2× screen, NOAA's real sample):
  - the globe draws, and the streaks move (20+ frames in 2 s);
  - the temperature and rain layers switch;
  - a tap gives the wind, temperature and rain there in your units.
  - The texts and the coloured map (more than 5,000 colours) are checked, and the Pacific counts as sea for the coastlines.
- **Updates** (UpdatesTest, SixTest):
  - versions and tags; reading GitHub's reply; only the exact APK name is offered;
  - drafts, pre-releases, unknown tags, odd links and huge notes are handled;
  - SHA256SUMS; GitHub-only hosts; the daily and 3-hour schedule; the stored state.
  - **Download:** checked against its checksum (a wrong one is deleted, one not listed isn't downloaded) and by the this-app-and-newer check.
  - **On screen:** found, then downloaded, checked and handed to the installer; up to date; a bad checksum is refused; skip a version.
- **Test connection** (DiagnosticsTest, SixTest):
  - each server once; IPv6 failures; no DNS;
  - the summary wording; keys removed;
  - Android's errors in plain words.
- **Provider usage:**
  - Tomorrow.io's headers;
  - Visual Crossing's cost (from the reply);
  - OpenWeather's plan;
  - recorded by the repository.
- **Temperature notification:** the icon text in °C and °F; the title and text; the icon really drawn.
- **Settings:** the new switches are saved, older settings get the defaults, and the rows take a tap.
- **Emulator check (real Android 14):**
  - the Wind tab downloaded the real map from NOAA, showed it, and the streaks moved (2.8% of the globe changed in 2 s);
  - all three layers and a tap worked;
  - Test connection finished its report;
  - Check for updates asked GitHub (the newest release was v0.5.2: up to date);
  - the temperature notification was posted;
  - every other step passed, with no crash.
- **Not tested here:**
  - installing a real update: there's no newer release on GitHub yet, and 0.5.2 has no update checker. The first real download-and-install will be the release after 0.6.2, installed from 0.6.2;
  - the speed of the wind streaks on a phone (the emulator draws in software).


**New in 0.5.2 (the globe must really turn):**
- **Stress test** (GlobeStressTest, a simulated phone):
  - with Rotate slowly, the globe's picture must be redrawn at least 4 times in 4 seconds and turn 12–45° east;
  - a one-finger drag to the right must turn it west by more than 10°, the picture must follow within 0.5°, and the zoom must not change;
  - an upward drag turns it north and south.
- **ScreensTest:** tapping the words "Live clouds" ticks the box.
- **Emulator check (real Android 14)**, comparing screenshots of the globe:

| | 0.5.1 (temporary branch `repro/globe-turn-0.5.1`) | 0.5.2 |
|---|---|---|
| Nothing moving | 0.10% changed | 0.03% changed |
| Rotate slowly, 5 s | 0.06%: **didn't move** | 12.2%: moved |
| One-finger drag | 0.10%: **didn't move** | 19.3%: moved |
| Stress test | **failed**: the view turned 32°, but only 1 picture was drawn | passed |

(The first 0.5.2 emulator run showed no movement for Rotate slowly. Its screenshot showed the script had tapped the words, which didn't tick the box then. The words now tick it, and the numbers above are from the run after that.)


**New in 0.5.1:**
- **Emulator check: real Android 14 on a Pixel 6 emulator** (`.github/workflows/emulator.yml`, `scripts/emulator/drive.sh`).
  - Installs the release APK and adds Auckland by searching for it.
  - Moon tab; Earth Flat; **Earth Globe**; zoom to 300%; Rotate slowly; drag; Flat and back.
  - Leaving and returning to Earth.
  - The Warnings tab; My places, Earthquakes, Settings (Weather Alerts, Earthquakes, Weather Providers), User guide and About.
  - Saving a Moon picture.
  - Landscape and back.
  - Home and reopen; pull to refresh.
  - It checks the app is still running after every step, and prints Android's crash log if not.
  - **The 0.5.0 build crashed on tapping Earth Globe** (`ending radius must be > 0`). **0.5.1 passed every step.**
- **SafeDrawingTest:** no radius below 0.5 reaches Android, and no code calls `Brush.radialGradient` directly.
- **GlobeStressTest:** a phone-like setup under Robolectric.
  - A 3× screen, animation on, earthquakes, four places, live clouds, the Moon tab first.
  - Then a real tap on Earth Globe; zoom to both limits; spin; drag; pinch; double-tap; Flat and back; leave and return; open from a quake notification.
- **Crash report:** saved with the app version and stack trace, shown once, and removed when closed.
- **Note:** the emulator uses software graphics, so its slow frames don't say how fast the app is on a phone.


**New in 0.5 (14 tests):**
- **The Earth** (mirroring the desktop's `test_earth.py`, same PyEphem reference file):
  - where the Sun is overhead, within 0.05°; the Sun's height anywhere, within 0.1°;
  - equinox and solstices; day, twilight and night (Auckland and London at set times);
  - the map projection both ways; the day/night masks (day where the Sun is overhead, night opposite).
- **The globe** (`test_globe.py`): the centre, the far side hidden, east and north, and 3,000 random points there and back.
- **The sky behind the globe** (`test_sky.py`): the distances behind the Earth, east on the right and north up, the Sun touching the Earth's edge exactly at sunset there, the Moon's lit side facing the Sun on screen (within 1°), and the stars turning with time.
- **Live clouds** (`test_clouds.py`): first download, "not modified" (the app sends If-Modified-Since and the ETag), a newer map, the hourly check, and three kinds of bad download (garbage, HTTP 404, wrong shape) that keep the last map and wait 15 minutes; a corrupt record is ignored.
- **Drawing:** the globe is filled (within 3% of a perfect disc), faces the right way and is darker at the edge; the Earth map from the real textures is bright at noon and dark at midnight over the same desert.
- **Screens:** the Moon, Earth Flat and Earth Globe tabs open; a real tap switches to the globe; the Live clouds box takes a tap below it; and back to the forecast.
- **Pictures** of the Moon, Earth Flat and Earth Globe (the globe picture waits until it's drawn).
- **APK:** checksum, version 0.5.0 (code 6), v2 signature, the new icon at every density (108–432 px foregrounds) and the About logo, and the Earth maps.

**Not tested here:** a real phone (pinch, drag and double-tap gestures, the globe's speed, the live-cloud download over a real network, saving the Moon picture to the gallery, and how the new icon looks on your launcher).


**New in 0.4 (35 tests), mirroring the desktop's test_alerts.py and test_quakes.py with the same fixture files:**
- **Warning geometry:**
  - point in polygon, including across the date line (Chatham Islands);
  - bad polygons are ignored;
  - circles, and GeoJSON with a hole.
- **Warning levels:**
  - MetService's colour beats its lower CAP severity;
  - CAP severity, MeteoAlarm awareness levels and titles.
- **Parsing:**
  - the MetService CAP document (level, title, hazard, area, in force);
  - tests and cancellations are dropped;
  - NWS and MET Norway;
  - DOCTYPE and entities are refused;
  - garbage never crashes;
  - only web links survive.
- **Sources:**
  - every country goes to the right service, including a place saved without a country;
  - every source matches the right places;
  - CAP documents are fetched once;
  - one failing source keeps the others;
  - a country without a feed, and no warnings at all.
- **Own limits:** the defaults, your units, later days, validation, today's gust when there's no daily gust, and rain between times.
- **Showing and notifying:**
  - each level, and muting;
  - notified once, then again when upgraded or reissued, but not for an extension of the same issue.
- **The warnings store:** round trip, clean-up, a damaged file. Alert and quake settings round trip, and bad values fall back.
- **Earthquakes:**
  - USGS and GeoNet parsing, and the merge preferring GeoNet;
  - garbage and round trip;
  - colours, sizes and text;
  - the period and magnitude filters;
  - near-a-place and major-anywhere notification rules, once only and only for recent quakes;
  - the feeds and GeoNet's Accept header;
  - a failed source keeping its quakes.
- **The shared check:**
  - notified once across a restart;
  - "notify" and "only for the place on screen";
  - checks spaced out, and a failed check keeping the old warnings;
  - quakes notified once;
  - the period setting.
- **Screens:** banner → Warnings → Mute (off the banner, still listed) → Earthquakes → Weather Alerts → Red alarm switch.
- **Quota guard:** Tomorrow.io isn't asked again within 10 minutes; Open-Meteo has no limit.


**New in 0.3.1 (3 tests): the widget loads as a launcher loads it.**
- Only views Android allows in widgets are used.
- The launcher's first and preview layouts load.
- The full widget loads, with its 20 frames, place and temperature, and also with no place yet.

These three failed on 0.3.0 with the launcher's error, which is the "Couldn't add widget" you saw, and pass after the fix.


**New in 0.3 (38 tests):**
- **Moon:** compared with the desktop's PyEphem reference values:
  - rise and set within 90 s (Tromsø within 300 s);
  - illumination within 0.3 percentage points;
  - new, quarter and full moon times within 2 minutes;
  - the tilt (parallactic angle) within 2.5°.
- **Moon, other checks:**
  - phase names, and a principal phase named only on its own day;
  - waxing and waning, and the bright limb pointing towards the setting Sun;
  - the polar always-up/always-down case;
  - time zones, the next phases, the north/south views, "now or at its highest", and the current pass.
- **Scene conditions:**
  - every weather code classified;
  - day, night, dusk and dawn;
  - the Sun's progress, and the clock moving on since download;
  - falling back to day/night from the forecast when there are no sun times;
  - wind direction to drift, and hemisphere;
  - the desktop's cloud-speed table;
  - cloud amounts in order;
  - the Moon up or down at real times in Auckland;
  - playback using that hour's weather.
- **Moving parts:**
  - rain, snow, cloud and fog stay in bounds for 10 simulated minutes;
  - lightning only in storms;
  - rain slants with the wind;
  - the wind eases in;
  - fades finish, and are instant when animation is off.
- **Real drawing (Robolectric native graphics):**
  - every weather code draws by day and by night;
  - day and night skies differ;
  - the Sun and Moon follow their arcs, and the Moon isn't drawn below the horizon;
  - the NASA Moon picture is lit for full and new moon;
  - the widget makes 20 moving frames, or 1 with animation off.
- **Pictures:** seven skies and three widget frames are saved as PNGs and checked not to be blank. I looked at them.
- **Widget text:**
  - the provider and "(backup)";
  - "↻ time", turning amber with its age at the desktop's thresholds;
  - age texts;
  - the frames fit the picture budget.
- **Playback:** an hour a second, 96 steps, the time stamp (12- and 24-hour), that hour's forecast, and why playback is unavailable.
- **Refreshing:**
  - retries after 30 s, 1, 2 and 5 minutes, back to normal once it works;
  - at start-up the visible place first, then the others 1.5 s apart.


**New in 0.2 (29 tests):**
- **Providers.** OpenWeather (One Call; and the free-key fallback, which isn't asked again), WeatherAPI.com, Visual Crossing, Tomorrow.io and Weather Underground (nearest station; your own station offline; a 5-hour-old report). These use the saved replies from the desktop app's provider fixtures, with the same expected values as the desktop tests.
- **Every provider passes the desktop app's common checks:**
  - the current time and today's date;
  - lows no higher than highs;
  - sunrise and sunset on each day;
  - percentages from 0 to 100;
  - hours in order;
  - the UTC offset.
- **Keys:**
  - a missing key, a rejected key (the key isn't in the message) and running out of quota each give a clear message;
  - a key is never in the forecast or the settings;
  - pasted keys are checked.
- **Weather codes:** every code each service can send maps to one the app knows.
- **The normaliser:**
  - impossible values are dropped;
  - a swapped high and low is put right;
  - missing sunrise and weather symbols are worked out.
- **The chain:**
  - the backup takes over;
  - the primary rests for 30 minutes, "stay on the backup" works, and so does "next update";
  - every error is reported;
  - backups without keys are skipped;
  - settings round-trip and bad values are rejected.
- **App behaviour:**
  - make primary, backups on and off, order, and the primary can't be removed or passed;
  - recovery;
  - keys saved apart from the settings and then used;
  - Test connection;
  - the station-ID check;
  - Back from Weather Providers goes to Settings.
- **Playback:**
  - labels;
  - temperature eased between hours;
  - times outside the forecast;
  - the sky changes for a clear night and a rainy afternoon.
- **The mini forecast** fits the width (3 to 7 days).
- **Widgets:**
  - no place, and no weather yet;
  - the chosen units and five days;
  - an old forecast is marked "Saved copy".
- **Screens (Robolectric):**
  - Settings → Weather Providers → Make primary → Details shows the key box → Back;
  - the mini forecast and playback controls show.


**Data (plain JVM):**
- Open-Meteo and MET Norway parsing, using the same sample responses as the desktop app's test server.
- Damaged replies: empty, not JSON, missing parts, NaN, error messages.
- Place search, with duplicates and bad entries dropped.
- MET symbol codes.
- The backup: MET Norway is used when Open-Meteo fails, and when both fail you get a clear message.
- The offline cache: round trip, a damaged file, cleaning up removed places.
- Settings: round trip, bad values falling back one by one, the 50-place limit.
- Units: °C/°F, wind, rain, compass, 12/24-hour time, no "-0°".
- The Sun: Auckland sunrise and sunset within 3 minutes of Open-Meteo's, polar night, day and night.

**App behaviour:**
- Adding a place saves it and fetches its weather.
- Refreshing happens only when due (10 minutes: no; 31: yes; forced: yes; "only when I pull down": no).
- A failure keeps the saved forecast and says why; it clears when the weather is back.
- Saved forecasts show at start-up while offline.
- Remove, reorder and change units.
- A download that finishes after its place was removed is ignored.
- Search waits for a pause in typing, and shows a clear error.

**Screens (Robolectric, headless):**
- First start → Add a place → search → pick Auckland → the weather shows (17°C, the details, the credit).
- Switching to °F shows 62°F.
- Removing a place asks first.
- Offline shows the saved copy and what went wrong.
- The user guide and About, with the credit lines.

**The sky:**
- The Sun's arc follows the day.
- Night colours are darker than day colours.
- Wind direction turns the clouds.
- Every kind of weather (clear, cloud, fog, rain, snow, thunder, unknown) draws for 2 seconds of animation without a problem.

## The APK

`rbsb-weather-android_0.4.0.apk` (about 22 MB with the Moon images) was downloaded from the
`test-builds` branch and checked here:

- its SHA-256 matches `SHA256SUMS`;
- the zip is intact;
- the manifest has the app ID `io.github.shanebl030glitch.rbsbweather`, version 0.4.0 (code 5), min SDK 29, target SDK 35, MainActivity and the INTERNET permission;
- both widget receivers are there, and so are the backup rules that keep API keys out of backups;
- it's signed (APK Signature Scheme v2) with the RBSB-Weather test key.

## Not tested: needs your phone

There's no Android device or emulator here. These need checking on a real phone:

- installing, and the app opening;
- the live Open-Meteo and MET Norway services;
- the look of the animated sky and its smoothness;
- swiping between places and pull to refresh;
- rotation, dark mode, and battery use with the sky animated;
- each keyed provider with your real keys (the tests use saved replies);
- playback and the slider by touch;
- the live warning feeds (MetService etc.) and earthquake feeds, notifications with the app closed, the Android 13+ permission request, and the Red alarm sound;
- the widget's looping animation on your launcher (some launchers may refuse many frames; it then shows a still picture), its battery use, and smoothness of the sky in the app;
- the widgets on your launcher: adding them, their size and look, the 30-minute self-update (Android decides exactly when), and tapping to open the app.

## Latest run

GitHub Actions run 44985012092: `TEST TOTALS: 136 run, 136 passed, 0 failed, 0 errors, 0 skipped`. The APK was built and published to `test-builds`. This report was updated afterwards (docs only), so the delivered APK comes from the next run of the same code.
