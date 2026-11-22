# RBSB-Weather for Android — Changelog

Created By Shane BlackbuRn

A separate app from RBSB-Weather for the desktop, with its own versions.

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
