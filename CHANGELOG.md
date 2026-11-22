# RBSB-Weather for Android — Changelog

Created By Shane BlackbuRn

A separate app from RBSB-Weather for the desktop, with its own versions.

## 0.5.0 – 2026-11-22 (test build; on branch claude/android-0.2.0; not released)

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
