// What a home-screen widget shows, as on the desktop widget: the place with the provider under
// it ("Open-Meteo", "MET Norway (backup)"), "↻ 19:30" top right (amber, with how long ago, when
// the weather is getting old), the temperature, conditions and today's high and low. The sky and
// the mini forecast are drawn into the animation frames. Plain Kotlin (tested).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.widget

import io.github.shanebl030glitch.rbsbweather.data.Alert
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.data.providerInfo
import java.time.Instant
import java.time.ZoneId

data class WidgetData(
    val place: String,
    val source: String,            // "Open-Meteo", "MET Norway (backup)"
    val temp: String,
    val condition: String,
    val hiLo: String,
    val updated: String,           // "↻ 19:30" or "↻ 19:30 (3 h ago)"
    val updatedWarn: Boolean,      // amber
    val alert: String = "",        // "⚠ Orange Heavy Rain Warning" under the place (desktop 2.4)
    val alertLevel: String = "",
)

const val OLD_DATA_WARN_S = 3 * 60 * 60

fun ageText(seconds: Long): String {
    val s = maxOf(0L, seconds)
    return when {
        s < 90 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} days ago"
    }
}

fun widgetData(settings: Settings, fc: Forecast?, nowMs: Long = System.currentTimeMillis(), alerts: List<Alert> = emptyList()): WidgetData {
    val place = settings.widgetPlace()
        ?: return WidgetData("RBSB-Weather", "", "–", "Open the app to add a place", "", "", false)
    if (fc == null) return WidgetData(place.label, "", "–", "Waiting for the weather", "", "Updating…", false)
    val u = settings.units
    val c = fc.current
    val today = fc.daily.firstOrNull()
    val hiLo = if (today != null) "H ${Units.fmtTemp(today.tempMax, u.temp)}  ·  L ${Units.fmtTemp(today.tempMin, u.temp)}" else ""
    val primary = settings.providers.firstOrNull()?.let { providerInfo(it)?.name }
    val source = when {
        primary != null && fc.source != primary -> "${fc.source} (backup)"
        else -> fc.source
    }
    var updated = ""; var warn = false
    if (fc.fetchedAt > 0) {
        val t = Instant.ofEpochMilli(fc.fetchedAt).atZone(ZoneId.systemDefault())
        val stamp = Units.clock(t.hour, t.minute, u.clock)
        val age = (nowMs - fc.fetchedAt) / 1000
        val mins = settings.refreshMinutes
        warn = if (mins > 0) age > maxOf(2L * mins * 60, 90L * 60) else age > OLD_DATA_WARN_S
        updated = "↻ $stamp" + if (warn) " (${ageText(age)})" else ""
    }
    val top = if (settings.alerts.banner) alerts.firstOrNull() else null
    return WidgetData(place.label, source, Units.fmtTemp(c.temperature, u.temp), WeatherCodes.describe(c.weatherCode), hiLo, updated, warn,
        top?.let { "⚠ " + it.title() } ?: "", top?.level ?: "")
}
