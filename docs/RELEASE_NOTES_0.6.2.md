# RBSB-Weather-Android 0.6.2

Created By Shane BlackbuRn

## What's new since 0.5.2

- **Wind tab:** the world's wind, temperature, and rain and snow from NOAA's GFS forecast on a globe, with moving wind streaks.
  - Drag, pinch, or tap anywhere for the weather there.
  - Updated every 3 hours and kept for offline use.
- **Check for updates (menu):** checks this page for a newer version, shows what's new, and downloads and installs it when you tap **Download and install**.
  - Each download is checked against `SHA256SUMS` before Android asks you to install it.
  - The app also checks once a day and notifies you (you can turn this off in Settings).
- **Test connection (menu):** checks how your phone reaches each weather service and where any delay comes from. You can copy or share the report.
- **Temperature in the status bar (Settings):** an optional quiet notification with the temperature as its icon.
- **Provider usage:** for the services that report it, such as Tomorrow.io's requests left today.

## Screenshots

<table>
<tr><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/forecast.png" width="250" alt="Forecast: the animated sky, mini forecast and details"><br><sub>Forecast: the animated sky, mini forecast and details</sub></td><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/moon.png" width="250" alt="Moon: tonight's Moon as it looks in your sky"><br><sub>Moon: tonight's Moon as it looks in your sky</sub></td><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/earth-flat.png" width="250" alt="Earth Flat: day and night with city lights"><br><sub>Earth Flat: day and night with city lights</sub></td></tr>
<tr><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/earth-globe.png" width="250" alt="Earth Globe: drag, pinch, rotate slowly"><br><sub>Earth Globe: drag, pinch, rotate slowly</sub></td><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/globe-clouds.png" width="250" alt="Live satellite clouds, zoomed in"><br><sub>Live satellite clouds, zoomed in</sub></td><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/wind.png" width="250" alt="Wind: NOAA's forecast with moving streaks"><br><sub>Wind: NOAA's forecast with moving streaks</sub></td></tr>
<tr><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/wind-temperature.png" width="250" alt="Wind tab: air temperature"><br><sub>Wind tab: air temperature</sub></td><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/wind-rain.png" width="250" alt="Wind tab: rain and snow"><br><sub>Wind tab: rain and snow</sub></td><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/earthquakes.png" width="250" alt="Earthquakes near your places"><br><sub>Earthquakes near your places</sub></td></tr>
<tr><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/warning-banner.png" width="250" alt="A warning across the sky (example)"><br><sub>A warning across the sky (example)</sub></td><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/warnings.png" width="250" alt="The warning in full (example)"><br><sub>The warning in full (example)</sub></td><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/settings.png" width="250" alt="Settings"><br><sub>Settings</sub></td></tr>
<tr><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/providers.png" width="250" alt="Weather Providers"><br><sub>Weather Providers</sub></td><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/test-connection.png" width="250" alt="Test connection"><br><sub>Test connection</sub></td><td align="center" width="33%"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/updates.png" width="250" alt="Check for updates"><br><sub>Check for updates</sub></td></tr>
</table>

<p align="center"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/skies.png" width="760" alt="The animated sky: sun and cloud, thunderstorm, snow, and night with the Moon"><br><sub>The animated sky: sun and cloud, a thunderstorm, snow, and night with the Moon in its real phase</sub></p>

<p align="center"><img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/widget.png" width="410" alt="Home-screen widget"><br><sub>Home-screen widget (animated)</sub>&nbsp;&nbsp;&nbsp;<img src="https://raw.githubusercontent.com/shanebl030-glitch/RBSB-Weather-Android/main/docs/screenshots/landscape.png" width="430" alt="Sideways"><br><sub>Turned sideways</sub></p>

## Google Play Protect warning

When you install this version, Google Play Protect may say **"Harmful app blocked … This app may be harmful."**

This is because the app can now install its own updates. That needs Android's "install other apps" permission, and Play Protect warns about any app from outside the Play Store that has it. RBSB-Weather only ever installs RBSB-Weather for Android, from this page's releases:
- It checks the file against `SHA256SUMS`, and that it's this app and newer.
- Android itself asks you before installing, and refuses an update not signed like the app you have.

To install, tap **Install anyway** (on some phones: **More details → Install anyway**). To check the file first, its SHA-256 must match the line for it in `SHA256SUMS` below. See the [README](https://github.com/shanebl030-glitch/RBSB-Weather-Android#google-play-protect-harmful-app-blocked) for the full explanation.

## Files

- `rbsb-weather-android_0.6.2.apk`: install on Android 10 or newer. It installs over earlier versions and keeps your places.
- `SHA256SUMS`: the checksum of the APK.
