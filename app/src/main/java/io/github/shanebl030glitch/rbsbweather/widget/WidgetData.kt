// What a home-screen widget shows, worked out from the saved forecast. Plain Kotlin (tested).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.widget

import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.Sun
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.ui.miniDayName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WidgetDay(val name: String, val symbol: String, val temps: String)

data class WidgetData(
    val place: String,
    val temp: String,
    val symbol: String,
    val condition: String,
    val hiLo: String,
    val updated: String,           // "Updated 14:05" / "Saved copy from Mon 09:10"
    val isDay: Boolean,
    val days: List<WidgetDay>,
)

const val WIDGET_DAYS = 5

fun widgetData(settings: Settings, fc: Forecast?, nowMs: Long = System.currentTimeMillis()): WidgetData {
    val place = settings.selectedPlace()
        ?: return WidgetData("RBSB-Weather", "–", "🌤️", "Open the app to add a place", "", "", true, emptyList())
    if (fc == null) return WidgetData(place.name, "–", "⏳", "Waiting for the weather", "", "", true, emptyList())
    val u = settings.units
    val c = fc.current
    val now = Instant.ofEpochMilli(nowMs)
    val isDay = Sun.isDay(now, place.latitude, place.longitude)
    val today = fc.daily.firstOrNull()
    val hiLo = if (today != null) "H ${Units.fmtTemp(today.tempMax, u.temp, false)}  L ${Units.fmtTemp(today.tempMin, u.temp, false)}" else ""
    val zone = ZoneId.systemDefault()
    val at = if (fc.fetchedAt > 0) Instant.ofEpochMilli(fc.fetchedAt).atZone(zone) else null
    val clock = if (u.clock.name == "H12") "h:mm a" else "HH:mm"
    val updated = when {
        at == null -> ""
        nowMs - fc.fetchedAt > 3 * 3_600_000L -> "Saved copy from " + at.format(DateTimeFormatter.ofPattern("EEE $clock", Locale.getDefault()))
        else -> "Updated " + at.format(DateTimeFormatter.ofPattern(clock, Locale.getDefault()))
    }
    val days = fc.daily.take(WIDGET_DAYS).mapIndexed { i, d ->
        WidgetDay(if (i == 0) "Today" else miniDayName(d.date), WeatherCodes.symbol(d.weatherCode),
            "${Units.fmtTemp(d.tempMax, u.temp, false)}/${Units.fmtTemp(d.tempMin, u.temp, false)}")
    }
    return WidgetData(place.name, Units.fmtTemp(c.temperature, u.temp), WeatherCodes.symbol(c.weatherCode, isDay),
        WeatherCodes.describe(c.weatherCode), hiLo, updated, isDay, days)
}
