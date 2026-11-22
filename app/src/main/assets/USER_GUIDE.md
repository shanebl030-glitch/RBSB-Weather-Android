# RBSB-Weather for Android

Created By Shane BlackbuRn

## Getting started

Tap **+** at the top to add a city or town. Type at least two letters, then tap the place you want. You can save up to 50 places.

## Moving around

The tabs along the bottom: **Forecast**, **Moon**, **Earth** and **Warnings**. Everything else is in the menu (⋮).

## Your places

* Swipe left and right to move between your places. The dots at the top show which one you're on.
* Menu (⋮) → **My places** lists them. Tap one to show it, use the arrows to change the order, or the bin to remove it.

## Reading the screen

* **The sky** is the same animated sky as RBSB-Weather on the desktop. Its colour follows the local time there, including dawn and dusk.
* **The Sun and Moon** follow the same arc at their real times there: each rises just below the bottom left, is highest halfway between rising and setting, and sets at the bottom right. The Moon is shown in its real phase and tilt whenever it's really above the horizon, so you'll often see both. By day it's pale, like the real daytime Moon. Stars come out at night.
* **Clouds** drift the way the wind is blowing towards (east is to the right, as on a weather map). The stronger the wind, the faster they go: about 2½ minutes to cross the sky in calm air, 70 seconds at 10 km/h, 30 seconds at 40 km/h, and no faster than 19 seconds in a gale. When the wind changes, they ease into it.
* **Rain** is slanted by the wind and splashes, **snow** drifts down, **fog** rolls past, and **thunderstorms** flash with lightning. When the weather changes, the sky blends into the new weather.
* In the sky: the temperature, the conditions, what it **feels like**, and today's **high and low**.
* **Mini forecast**: the bottom-right corner of the sky shows the next days: the name (Today, Tomorrow, then the weekday), an **animated icon**, the high and low, and the chance of rain. As many days fit as there's room for.
* Under the sky: when the weather was downloaded, or what went wrong.
* **Forecast playback**: under the sky, tap ▶ to play the next 24 hours at an hour a second (⏸ pauses), or drag the slider to any time in 15-minute steps. The sky shows that hour's weather, with the Sun and Moon where they'll be. The card shows that hour's temperature, weather and chance of rain, marked "▶ Forecast for …". The clouds keep today's wind, because the hourly forecast has none. Tap **Now** to go back. Some providers have no hourly forecast, and the bar says so.
* **Details**: humidity, wind and gusts with the direction they come from, pressure, cloud cover, rain in the last hour, the UV index and sunrise and sunset.
* **Next 24 hours**: the orange line is the temperature; blue bars are the chance of rain. Swipe sideways to see later hours.
* **7-day forecast**: each day's animated icon, weather, high and low, chance of rain and how much is expected, and the strongest wind.

## Refreshing

* Pull the screen down, or tap ⟳, to update straight away.
* When the app starts, every saved place updates: the one on screen first, then the others 1½ seconds apart. A place updated in the last 2 minutes is skipped.
* If an update fails, it's tried again after 30 seconds, then 1 and 2 minutes, then every 5 minutes until it works (while automatic refresh is on).
* The weather updates by itself when it's older than your refresh setting (30 minutes unless you change it), while the app is open and when you come back to it.
* The last forecast for every place is kept on your phone, so it shows even without internet. It says **saved copy** and how old it is.

## Where the weather comes from

* By default **Open-Meteo** supplies the weather, with **MET Norway** (yr.no) as the backup. Both are free and need no account.
* The bottom of the screen names the service the forecast came from, and says when a backup is in use.

## Weather Providers

Menu (⋮) → **Settings** → **Weather Providers**.

* **Primary and backups:** the primary is asked first. If it fails, the backups are tried in order. Tap **Make primary** on any service, turn **Backup** on or off, and use the arrows to change the order of the backups.
* **Services with your own key:** OpenWeather, WeatherAPI.com, Visual Crossing, Tomorrow.io and Weather Underground. Each has a free plan. Tap **Details, key and test**, then **Get a key** to sign up on their website. Paste the key and tap **Save key**. A backup without a key is skipped.
* **Test connection** asks that service for the place you're viewing and says whether it worked.
* **Details** show each service's coverage, forecast length, hourly detail, free-use limits and credit.
* **Weather Underground** works for people who send data from their own weather station. Enter your station ID to use it; otherwise the nearest reporting station is used. It has no hourly forecast, so the 24-hour chart stays empty.
* **When the primary fails:** try it again at the next update, after 15, 30 or 60 minutes, or stay on the backup until you change something. **Use the primary now** asks the primary first straight away.
* To protect small free allowances, Tomorrow.io is asked at most every 10 minutes per place, and OpenWeather and Visual Crossing at most every 5 minutes; a refresh in between shows the forecast it already has.
* **Planned:** AccuWeather and Weatherbit, with the reasons they aren't in yet.
* **Your keys stay private:** they are kept in their own private storage on your phone, are never shown in messages, and are left out of phone backups.
* Your phone sends only the coordinates of your saved places (and what you type when searching). Nothing else, and there's no tracking.

## Weather warnings

RBSB-Weather shows official severe weather warnings for your saved places, and can also alert you when the forecast passes limits you choose.

* **Where official warnings come from:** New Zealand: MetService. United States: the US National Weather Service. Norway: MET Norway. Most of Europe (UK, Ireland, France, Germany, Spain, Italy and others): MeteoAlarm. Each warning is matched to your places by its own area. Elsewhere there's no official source yet; your own limits still work.
* **Where they show:** a banner across the sky, coloured by level, with when it ends and who issued it (tap it for the full warning); a badge next to the place in My places; tags on the 7-day forecast; shading on the 24-hour chart for the hours a warning is in force; and a line on the widget.
* **The ⚠ Warnings screen** (menu → Warnings) has each warning in full: its area, when it's valid, what's expected, what to do and who issued it. For rain it also shows how much rain RBSB-Weather's forecast has for the same hours. **Open on…** opens it on the issuer's website. **Mute this warning** takes it off the banner, badges and notifications; it stays listed, and comes back if it's upgraded or reissued.
* **Levels:** Red means take action now; Orange, take action; Yellow, be aware; a Watch means severe weather is possible. Warnings are shown as issued; their wording is never changed.
* **Notifications** when a warning is issued, upgraded (for example Orange to Red) or reissued. Each change is notified once, even after a restart. On Android 13 and newer the app asks once for permission to notify.
* **My own limits** (Settings → Weather Alerts): wind gusts, rain in a day, a high or low temperature past your limits, or any snow or thunderstorms. They come from RBSB-Weather's forecast, so they have a grey **MY LIMIT** badge and say "not official". Gust limits need gust forecasts: Open-Meteo gives them for every day, other providers only for today.
* **Settings → Weather Alerts** also lets you turn official warnings off, choose which levels to show, check every 15, 30 or 60 minutes, turn the banner and badges or notifications off, use an alarm sound for Red warnings, and notify only for the place on screen. The top shows which service covers each of your places and whether the last check worked.
* **While the app is closed,** Android checks about every 15 minutes at most (when there's a network), and sometimes later to save battery.
* **Always follow official advice.** RBSB-Weather passes warnings on; it doesn't replace your weather service, Civil Defence or emergency services.

## Earthquakes

Menu (⋮) → **Earthquakes** lists recent quakes (they're also dots on the **Earth** map and globe; **Show on the globe** turns the globe to them):

* **Colour** shows how recent: red in the last hour, orange in the last 6 hours, yellow in the last day, grey if older. **Size** shows the magnitude.
* Each shows its magnitude, place, depth, when, and how far it was from your nearest saved place. Tap it to open its page on GeoNet or USGS.
* **Notifications** for a quake of magnitude 4.5 or more within 300 km of one of your places, and for a major quake (7 or more) anywhere. Each is notified once, and only if it happened in the last 2 hours.
* **Settings → Earthquakes:** the past day or week; magnitude 2.5 and up or 4.5 and up; the notification magnitude and distance; check every 5, 10 or 30 minutes while the app is open.
* **Where quakes come from:** USGS for the whole world, and GeoNet for New Zealand quakes likely to have been felt (GeoNet's version is shown when both report the same quake). Nothing about you is sent: distances are worked out on your phone.
* For a large quake under the sea, the list and notification remind you to check your official tsunami advice. **RBSB-Weather is not an earthquake or tsunami warning service.** In New Zealand, follow Civil Defence and emergency mobile alerts. A long or strong quake near the coast is itself the warning: move to high ground straight away.
* On the **Earth** map and globe, tap a quake's dot for its details. Dots from the last hour pulse. Untick **Earthquakes** under the map to hide them. Tapping an earthquake notification opens the globe turned to that quake.

## Moon

The **Moon** tab shows the Moon as it looks tonight from the place on screen, turned the way you'd see it in that sky:

* the phase, how much is lit, its age in days, and whether it's growing (waxing) or shrinking (waning);
* when it rises and sets, how long it's in the sky, and how far away it is;
* the dates of the next new Moon, first quarter, full Moon and last quarter;
* the next 7 days, each with a small picture.

Tap **Save high-resolution image** to save a large (2048-pixel) picture of the Moon to your Pictures, in an RBSB-Weather folder. Times are shown in that place's time zone. The pictures are made from NASA's Moon images.

## Earth

The **Earth** tab shows day and night across the world as it is right now, with city lights on the night side. The switch at the top changes between **Earth Flat** (a map of the whole world) and **Earth Globe**.

* **Your places** are marked; the place on screen is highlighted with a card showing its time, temperature and whether it's day, twilight or night there. Tap another place to switch to it.
* ☀ marks where the Sun is overhead, and ● where the Moon is.
* **Flat:** pinch to zoom in (up to 4×) and drag to move around; double-tap to go back to the whole world.
* **Globe:** drag with one finger to turn it, pinch or use **+**/**−** to zoom, and double-tap to centre on the place on screen. **Rotate slowly** turns it by itself. Behind the globe are the real Sun, Moon and stars in their real positions, so you can see the Sun set behind the edge of the Earth as the day ends there.
* **Live clouds:** the Earth shows the latest satellite cloud picture. It's checked every hour and only downloaded when there's a newer one, and if it can't be downloaded the last one is kept. The line above the map says how old the clouds are. Untick **Live clouds** to turn them off (and stop the download).
* The picture is redrawn every 5 minutes, so the line between day and night moves on.
* A warning's **Show on Earth map** opens the map at that place.

## Home-screen widgets

* Touch and hold an empty part of your home screen → **Widgets** → **RBSB-Weather**.
* The widget shows the same animated sky as the app, with the place and the provider under it, **↻** and the time of the last update (amber, with how long ago, when the weather is getting old), the temperature, conditions, and high and low. When there's room it also shows the mini forecast.
* **Resize it** (touch and hold it, then drag the edges): bigger widgets show more forecast days.
* **About the animation:** Android doesn't let widgets run live animation the way a desktop can. RBSB-Weather draws a few seconds of the sky and the widget loops them. It's redrawn every time the weather updates, so the Sun and Moon move on. If your phone's launcher won't take that many pictures, the widget shows a still picture instead. Turning off **Animated sky** in Settings also makes the widget still.
* The widgets show the place you last looked at in the app. They update when the app does, and by themselves about every 30 minutes when the weather is older than your refresh setting. Tap a widget to open the app.

## Settings

Menu (⋮) → **Settings**: temperature (°C or °F), wind speed (km/h, m/s, mph or knots), rain (mm or inches), 12- or 24-hour time, how often to refresh, and whether the sky is animated (turn it off to save battery).

## If the weather won't load

* Check your phone has internet (try a web page).
* The screen shows what went wrong, for example "No internet connection".
* The saved copy stays on screen, and the app tries again when you pull down or when the next refresh is due.

## If the app closes unexpectedly

RBSB-Weather saves the reason on your phone. The next time you open it, it shows the details with **Copy** and **Share**, so you can send them to Shane. Nothing is sent unless you choose to send it.

## About

RBSB-Weather for Android is a separate app from RBSB-Weather for the desktop. Menu (⋮) → **About** shows the version and credits. The RBSB-Weather icon was designed by Shane BlackbuRn.

Created By Shane BlackbuRn
