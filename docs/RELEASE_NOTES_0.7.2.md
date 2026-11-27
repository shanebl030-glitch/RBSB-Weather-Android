# RBSB-Weather-Android 0.7.2

Created By Shane BlackbuRn

The first stable release since 0.6.2. It has been through a full stability audit and was tested by Shane on his phone.

## What's new since 0.6.2

- **📍 My location** (Settings → 📍 Use my location): a place that follows your phone to another suburb, town or country.
  - **Named by the most specific local area,** worldwide: suburb, neighbourhood, quarter, district and so on. A city is never passed off as a suburb. Examples: "East Tāmaki, Auckland", "Kreuzberg, Berlin", "Trastevere, Rome".
  - **The phone's geocoder first, OpenStreetMap as the backup.** Coordinates are rounded to about 100 m before they're sent anywhere.
  - **It updates** whenever you open the app. With Android's "Allow all the time" location access, it also updates in the background, about every 15 minutes when Android allows.
- **The widgets and the status-bar temperature can follow 📍 My location.**

## Fixed since 0.6.2

- **Tapping the widget could open the app and then close it.** A rare crash in Android's text drawing, made frequent by 0.7.0; it no longer happens.
- **The widget flickered:** it was redrawn several times in a row. It now redraws only when something has changed.
- **The Moon flickered in the 24-hour playback,** and Play could stop at once. Both are fixed.
- **The screen could freeze ("isn't responding") on a slow or failing connection** while warnings or earthquakes downloaded. This had been there since 0.4.
- **A widget update could be reported as "isn't responding" on a slow connection.** Also fixed.
- **Found by the stability audit:**
  - "isn't responding" from the Moon and cloud pictures, and from the Earth and Wind globes;
  - running out of memory on phones that give the app little memory;
  - My location on Android 10 and 12;
  - warnings from the previous place showing after My location moved;
  - GPS woken too often in the background.

The full audit, with every test and result, is in `AUDIT_REPORT_0.7.2.md` below.

## Signing: a new key, no reinstall

**From 0.7.2, releases are signed with a private release key that only Shane holds.** Earlier versions were signed with a test key that was public in this repository.

**You don't need to uninstall:**
- 0.7.2 carries a hand-over from the old key to the new one, so it installs over 0.6.2, 0.7.0 or 0.7.1 like any update.
- Your places, settings, API keys, widgets and permissions are kept.
- **Check for updates** in the app works too.

From then on, Android refuses any update signed only with the old public key. See [Signing](https://github.com/shanebl030-glitch/RBSB-Weather-Android/blob/main/docs/SIGNING.md).

**Release key fingerprint (certificate SHA-256):** `RELEASE_CERT_SHA256`

## Google Play Protect warning

Google Play Protect may say **"Harmful app blocked … This app may be harmful."** It does this because the app can install its own updates, and so has Android's "install other apps" permission.

RBSB-Weather only ever installs RBSB-Weather for Android, from this page's releases:
- it checks each download against `SHA256SUMS`;
- Android asks you before installing.

Tap **Install anyway** (on some phones: **More details → Install anyway**). The [README](https://github.com/shanebl030-glitch/RBSB-Weather-Android#google-play-protect-harmful-app-blocked) explains more.

## Files

- **`rbsb-weather-android_0.7.2.apk`:** install on Android 10 or newer. It installs over earlier versions and keeps your places.
- **`SHA256SUMS`:** the APK's checksum. Check it with `sha256sum -c SHA256SUMS`.
- **`AUDIT_REPORT_0.7.2.md`:** the final stability audit.
- **`RELEASE_NOTES_0.7.2.md`:** these notes.
