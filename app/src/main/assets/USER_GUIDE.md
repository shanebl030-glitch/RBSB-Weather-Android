# RBSB-Weather for Android

Created By Shane BlackbuRn

## Getting started

Tap **+** at the top to add a city or town. Type at least two letters, then tap the place you want. You can save up to 50 places.

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
* **When the primary fails:** try it again at the next update, after 15, 30 or 60 minutes, or stay on the backup until you change something.
* **Your keys stay private:** they are kept in their own private storage on your phone, are never shown in messages, and are left out of phone backups.
* Your phone sends only the coordinates of your saved places (and what you type when searching). Nothing else, and there's no tracking.

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

## About

RBSB-Weather for Android is a separate app from RBSB-Weather for the desktop. Menu (⋮) → **About** shows the version and credits.

Created By Shane BlackbuRn
