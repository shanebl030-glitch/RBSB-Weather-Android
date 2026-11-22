# RBSB-Weather for Android — Test report

Created By Shane BlackbuRn

| Item | Result |
|---|---|
| Version | 0.1.0 (test build) |
| Date | 2026-11-22 |
| Where | GitHub Actions: Ubuntu 26.04 runner, JDK 17 (Temurin), Gradle 8.14.3, Android Gradle Plugin 8.10.1, Kotlin 2.1.21, Compose BOM 2025.05.01, compile/target SDK 35, min SDK 29 |
| Command | `./gradlew testDebugUnitTest`, then `./gradlew assembleRelease` |
| Tests | see the latest run below |

## What's tested

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

`rbsb-weather-android_0.1.0.apk` (about 19.8 MB) was downloaded from the
`test-builds` branch and checked here:

- its SHA-256 matches `SHA256SUMS`;
- the zip is intact;
- the manifest has the app ID `io.github.shanebl030glitch.rbsbweather`, version 0.1.0, MainActivity and the INTERNET permission;
- it's signed (APK Signature Scheme v2) with the RBSB-Weather test key.

## Not tested: needs your phone

There's no Android device or emulator here. These need checking on a real phone:

- installing, and the app opening;
- the live Open-Meteo and MET Norway services;
- the look of the animated sky and its smoothness;
- swiping between places and pull to refresh;
- rotation, dark mode, and battery use with the sky animated.

## Latest run

Filled in below by each session.
