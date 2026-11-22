# RBSB-Weather for Android

The Android version of RBSB-Weather. It's a **separate app** from
[RBSB-Weather for the desktop](https://github.com/shanebl030-glitch/RBSB-Weather),
with its own versions, releases and tests.

**Created By Shane BlackbuRn**

**Designed and Created By Shane BlackbuRn** · **Coded By Claude AI**

## What it does (0.1)

- Search for any city or town and save up to 50 places. Swipe between them.
- An animated sky that follows the real Sun at the place, with the Sun or Moon,
  stars, drifting clouds, rain, snow, fog and lightning.
- The temperature, feels like, high and low, humidity, wind and gusts, pressure,
  cloud cover, rain, UV index, sunrise and sunset.
- A 24-hour chart and the 7-day forecast.
- Pull down to refresh. It also updates by itself when the weather gets old.
- Works offline with the last saved forecast.
- Weather from Open-Meteo, with MET Norway as an automatic backup. No account or
  key needed.
- °C/°F, km/h · m/s · mph · knots, mm/inches, 12/24-hour time.

Needs **Android 10 or newer**.

## Install a test build

1. On your phone, download `rbsb-weather-android_0.1.0.apk` from the
   [test-builds branch](https://github.com/shanebl030-glitch/RBSB-Weather-Android/tree/test-builds)
   (or use the file sent to you).
2. Open it. Android asks to allow installing apps from this source (for example
   Chrome or Files). Allow it once.
3. Tap **Install**.

New test builds install over the old one, and your places are kept.

To check the download, compare its SHA-256 with `SHA256SUMS`.

**Uninstall:** long-press the app icon → App info → Uninstall.

## Signing

Test builds (0.x) are signed with a **test key** kept in this repository
(`keystore/rbsb-test.keystore`), so every test build installs over the last.

Before any public release, a private release key replaces it. That key is kept
by Shane and never put in the repository. Moving from the test key to the
release key means uninstalling the test build once.

## Building

Every push is built by GitHub Actions (`.github/workflows/android.yml`):

1. the tests run first;
2. then the APK is built;
3. the newest test build goes to the `test-builds` branch.

To build on your own computer, you need JDK 17 and the Android SDK:

```bash
./gradlew testDebugUnitTest assembleRelease
```

## Project layout

| Path | Purpose |
|---|---|
| `app/src/main/java/.../data/` | Weather data: Open-Meteo and MET Norway, parsing, the backup, the offline cache, settings, units, the Sun |
| `app/src/main/java/.../ui/` | The app's state (`AppModel`), the animated sky, the 24-hour chart, the screens |
| `app/src/main/assets/USER_GUIDE.md` | The in-app user guide |
| `app/src/test/` | Unit tests and Robolectric screen tests, with forecast samples shared with the desktop app's tests |

## Data and privacy

Only the coordinates of your saved places (and what you type when searching) are
sent, to Open-Meteo, or to MET Norway when it's used as the backup. There's no
tracking and no account.

Weather data by Open-Meteo.com (CC BY 4.0) and MET Norway (CC BY 4.0).

Created By Shane BlackbuRn
