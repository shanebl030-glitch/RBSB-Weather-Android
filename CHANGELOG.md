# RBSB-Weather for Android — Changelog

Created By Shane BlackbuRn

A separate app from RBSB-Weather for the desktop, with its own versions.

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
