# RBSB-Weather for Android: matching RBSB-Weather Desktop

Created By Shane BlackbuRn

Shane asked for the Android app to be "a replica of RBSB-Weather desktop version.
nothing missing." This page lists every feature in the desktop user guide (2.5.0)
and where it stands on Android. It's updated with every version.

**Status:**
- ✅ **Done:** works as on the desktop.
- 🟡 **Partly:** part of the feature is built; what's left is listed.
- ⏳ **Planned:** not started yet; the version it's planned for is given.
- 📱 **Phone version:** the feature doesn't exist on a phone in the desktop's form, so the nearest phone equivalent is used (explained in the row).

## The weather on screen

| Desktop feature | Android | Notes |
|---|---|---|
| Top card: temperature, conditions, feels like, humidity, wind and direction, gusts, pressure, cloud, precipitation, UV, sunrise and sunset | ✅ 0.1 | The sky card plus the Details card. |
| Next 24 hours chart (temperature line, rain-chance bars and %) | ✅ 0.1 | |
| 7-day forecast: high/low, chance and amount of rain, maximum wind | ✅ 0.3 | Maximum wind added in 0.3. |
| Animated icons in the 7-day forecast | ✅ 0.3 | The desktop's icons, ported. |
| Status line: last download, *saved copy*, warning when older than 3 hours | ✅ 0.1 | Shown under the sky. |
| Mini forecast in the sky: day name (Today, Tomorrow or Tmrw, weekday), animated icon, high/low, chance of rain, as many days as fit (small 1–2, medium 3–5, large up to 7), clear of the text | ✅ 0.3 | The desktop's forecast_strip, ported. |

## Animated weather

| Desktop feature | Android | Notes |
|---|---|---|
| Sky colour follows local time, with dawn and dusk | ✅ 0.3 | The same colours and twilight rules. |
| The Sun and Moon on their real arcs (rise bottom-left, highest mid-sky, set bottom-right) | ✅ 0.3 | |
| The Moon in its real phase and tilt, shown whenever it's really up (paler by day), using NASA images | ✅ 0.3 | The desktop's moon astronomy and renderer, ported; checked against the same PyEphem reference values. |
| Stars at night | ✅ 0.3 | |
| Three cloud layers and a lower deck in heavy cloud, at the desktop's wind speeds (150 s to cross in calm air, 70 s at 10 km/h, 32 s at 40 km/h, 19 s at 100 km/h and above) | ✅ 0.3 | |
| Clouds move the way the wind blows towards (east to the right), at least 30% sideways for north/south winds | ✅ 0.3 | |
| A new wind eases in over a few seconds (slows, turns, speeds up) | ✅ 0.3 | |
| Rain slanted by the wind, with splashes; drizzle; swaying snow; rolling fog; lightning with bolts | ✅ 0.3 | |
| Weather changes cross-fade (clouds, rain/snow/fog, sky tint) | ✅ 0.3 | |
| Animated weather on/off; no animation work while hidden | ✅ 0.1 | Settings → Animated sky. Compose stops drawing when the app isn't visible. |

## Forecast playback

| Desktop feature | Android | Notes |
|---|---|---|
| ▶ Play at an hour a second; ⏸ pause | ✅ 0.3 | |
| Slider in 15-minute steps; Now | ✅ 0.2 | |
| That hour's weather, the Sun and Moon where they'll be (tomorrow's sunrise and sunset for tomorrow) | ✅ 0.3 | |
| Card text for that hour: temperature, weather, chance of rain, "▶ Forecast for …" | ✅ 0.3 | |
| Clouds keep the wind of now (with a note) | ✅ 0.3 | The note is shown under the bar. |
| Providers without an hourly forecast: the bar says so | ✅ 0.3 | |

## Widget

| Desktop feature | Android | Notes |
|---|---|---|
| Animated sky with temperature, conditions, high/low | 🟡 0.3 | **Android limit:** a home-screen widget can't run live animation. The widget loops a 3-second animation drawn from the real scene (clouds, rain, snow, stars, lightning, and the mini forecast's icons), redrawn with each update. If a launcher refuses that many pictures, it falls back to a still picture. Needs checking on your phone. |
| Mini forecast in the widget | ✅ 0.3 | Drawn into the frames. |
| "↻ 19:30" last update, amber with "(2 h ago)" when old | ✅ 0.3 | |
| Provider line under the place, "(backup)" | ✅ 0.3 | |
| Warning line under the place | ✅ 0.4 | Coloured by level. Tap opens the app. |
| Drag, lock position, keep on top | 📱 | The launcher places widgets. |
| Size small/medium/large | 📱 | Resize the widget on the home screen. The mini forecast adapts in the same way. |
| Double-click opens the app; right-click Refresh | 📱 | Tap opens the app; pull down in the app to refresh. |

## Places, refreshing, units, offline

| Desktop feature | Android | Notes |
|---|---|---|
| Up to 50 saved places, reorder, remove | ✅ 0.1 | |
| Data newer than 10 minutes isn't re-downloaded on switching | ✅ 0.1 | It follows the refresh setting. |
| Auto refresh every 10–120 minutes or never | ✅ 0.1 | Choices: 15, 30, 60, 120 or never. |
| At start-up every place updates, the visible one first, then 1½ s apart; places updated in the last 2 minutes are skipped | ✅ 0.3 | |
| A failed update retries after 30 s, 1, 2, then every 5 minutes | ✅ 0.3 | |
| Units: temperature, wind, precipitation, 12/24-hour time | ✅ 0.1 | |
| Offline: saved copy for every place | ✅ 0.1 | |

## Weather providers

| Desktop feature | Android | Notes |
|---|---|---|
| Open-Meteo, MET Norway, OpenWeather, WeatherAPI.com, Visual Crossing, Tomorrow.io, Weather Underground (station ID) | ✅ 0.2 | |
| Status for each (working, not working, needs key, not used yet) and details | ✅ 0.2 | |
| Usage reported by the service (e.g. Tomorrow.io's requests left today) | ✅ 0.6 | Tomorrow.io's requests left (today and this hour), Visual Crossing's cost per update, OpenWeather's plan, Weather Underground's station; in Weather Providers and in Test. |
| Primary, ordered backups, recovery choices, "Use the primary now" | ✅ 0.4 | |
| Ask small allowances less often (Tomorrow.io 10 min; OpenWeather and Visual Crossing 5 min) | ✅ 0.4 | |
| Keys kept private and never shown | ✅ 0.2 | App-private storage, excluded from backups. |
| Same display whatever the source; bad data caught | ✅ 0.2 | |
| AccuWeather and Weatherbit listed as planned | ✅ 0.4 | With the desktop's reasons. |

## Moon, Earth, earthquakes, wind, warnings

| Desktop feature | Android | Planned |
|---|---|---|
| Weather warnings: MetService, US National Weather Service, MET Norway, MeteoAlarm, matched by each warning's own area | ✅ | 0.4: the desktop's alerts.py, ported and tested with the same fixtures. |
| Banner across the sky (coloured, until when, who issued it; tap for the full warning) | ✅ | 0.4 |
| Badges in My places, tags on the 7-day forecast, shading on the 24-hour chart | ✅ | 0.4 |
| Warnings screen: area, valid times, what's expected, what to do, issuer, forecast rain for the same hours, Open on…, Mute | ✅ | 0.4; **Show on Earth map** added in 0.5. |
| Notifications when a warning is issued, upgraded or reissued (once each, across restarts), Red alarm sound, selected place only | ✅ | 0.4: phone notifications, also while the app is closed (Android checks about every 15 minutes at most). |
| Tray dot | 📱 | The widget's warning line instead. |
| My own limits (gusts, rain, heat, frost, snow, thunder), in your units, marked MY LIMIT | ✅ | 0.4 |
| Settings → Weather Alerts, with each place's coverage and last check | ✅ | 0.4 |
| Earthquakes: USGS worldwide, GeoNet for New Zealand (GeoNet preferred), colour by age, size by magnitude, details, distance to your nearest place, link to GeoNet/USGS, tsunami reminder | ✅ | 0.4 list; on the Earth map and globe in 0.5 (pulsing in the last hour, tap for details). |
| Earthquake notifications (M4.5+ within 300 km, M7+ anywhere, once each, only the last 2 hours), Settings → Earthquakes | ✅ | 0.4. Checks every 5, 10 or 30 minutes while the app is open; about every 15 minutes at most while it's closed (an Android limit). |
| Moon tab: picture turned as in that sky, phase, illumination, age, rise/set, next phases, next 7 days, save image | ✅ | 0.5. Saves a 2048-pixel picture to Pictures/RBSB-Weather. |
| Earth Flat: day/night map, city lights, Sun and Moon points, places with cards | ✅ | 0.5. Pinch to zoom (up to 4×) and drag; double-tap to reset. Needs checking on your phone. |
| Earth Globe: drag, zoom, rotate slowly, real Sun, Moon and stars behind | ✅ | 0.5. Drag, pinch or +/−, double-tap to centre; the desktop's sky geometry, tested with its reference values. Speed on a real phone needs checking. |
| Live satellite clouds | ✅ | 0.5: checked hourly, only downloaded when newer; the last map is kept if a download fails. |
| Wind globe (NOAA GFS): wind, temperature, rain and snow, moving streaks, key | ✅ | 0.6: the Wind tab; the desktop's GRIB2 reader ported and checked on the same files; tap for the weather at a point (the desktop shows it on hover). |

## Desktop-only features and their phone versions

| Desktop feature | Android |
|---|---|
| Tabs across the top (Forecast, Moon, Earth Flat, Earth Globe, Wind) | 📱 0.5–0.6: tabs along the bottom (Forecast, Moon, Earth, Wind, Warnings), the usual place on a phone; Flat and Globe are a switch at the top of Earth. |
| System tray temperature, Close to tray | 📱 0.6: Settings → Temperature in the status bar, an optional silent notification with the temperature as its icon. Close to tray isn't needed on a phone. |
| Start at login | 📱 Not needed: the widgets and background checks run without opening the app. |
| Updates: daily check of GitHub, download, checksum, install | 📱 0.6.1: menu → Check for updates, and once a day in the background with a notification. Shows what's new and the published SHA-256; the browser downloads it and Android installs it. (Installing from inside the app needs Android's "install other apps" permission, and Google Play Protect blocked 0.6.0 for it.) |
| Help → Test connection (DNS, IPv4/IPv6, timings, small-packet workaround) | ✅ 0.6: menu → Test connection, with Copy and Share. The small-packet workaround isn't possible on Android (apps can't set the packet size); the report says when a handshake stalls. |
| Keyboard shortcuts | 📱 Not applicable (touch); Refresh is pull-down or ⟳. |
| Files and settings locations | 📱 App-private storage; described in the user guide. |
| Coming from WeatherDesk | 📱 Not applicable (new app). |
| Buy me a coffee (PayPal) | ✅ 0.1 About. |
| App icon (Shane's artwork) | ✅ 0.5: the round version on Android (desktop 2.5.1 uses the rounded square). |
| Credits (weather, Earth maps, Moon imagery, wind, warnings, quakes) | ✅ 0.6 |
