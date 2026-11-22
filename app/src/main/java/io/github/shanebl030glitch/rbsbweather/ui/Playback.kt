// Forecast playback (the sky and the big numbers show a chosen time in the next 24 hours)
// and the sizing of the mini forecast inside the sky. Plain Kotlin, so it is unit tested.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.Place
import io.github.shanebl030glitch.rbsbweather.data.zoneOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.round

const val PLAYBACK_STEPS = 96                     // 24 hours in 15-minute steps

data class Moment(
    val at: Instant,
    val label: String,                            // "Mon 14:15" (local time at the place)
    val temperature: Double?,
    val weatherCode: Int?,
    val precipitationProbability: Double?,
)

/** "1 h 15 min" for a playback step. */
fun playbackLabel(step: Int): String {
    val m = step * 15
    return when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }
}

/**
 * The forecast at a moment: temperature eased between the hours either side, weather from
 * the hour it falls in. Null when the forecast doesn't reach that far.
 */
fun momentAt(fc: Forecast, place: Place, at: Instant): Moment? {
    val zone = zoneOf(place, fc.timezone, fc.utcOffsetSeconds.toDouble())
    val hours = fc.hourly.mapNotNull { h ->
        try { LocalDateTime.parse(h.time).atZone(zone).toInstant() to h } catch (e: Exception) { null }
    }.sortedBy { it.first }
    if (hours.isEmpty()) return null
    val i = hours.indexOfLast { !it.first.isAfter(at) }
    if (i < 0) return null
    val (t0, h0) = hours[i]
    if (at.epochSecond - t0.epochSecond > 3 * 3600) return null           // past the end of the hourly data
    val next = hours.getOrNull(i + 1)
    val temp = if (next != null && h0.temperature != null && next.second.temperature != null &&
        next.first.epochSecond > t0.epochSecond) {
        val f = (at.epochSecond - t0.epochSecond).toDouble() / (next.first.epochSecond - t0.epochSecond)
        round((h0.temperature + (next.second.temperature!! - h0.temperature) * f.coerceIn(0.0, 1.0)) * 10) / 10
    } else h0.temperature
    val local = at.atZone(zone)
    val label = local.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " +
        String.format(Locale.US, "%02d:%02d", local.hour, local.minute)
    return Moment(at, label, temp, h0.weatherCode, h0.precipitationProbability)
}

/** How many days fit along the bottom of the sky (about 62 dp each), between 3 and 7. */
fun miniDayCount(widthDp: Float, available: Int): Int =
    minOf(available, (widthDp / 62f).toInt().coerceIn(3, 7))

fun miniDayName(date: String): String = try {
    LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
} catch (e: Exception) { date.takeLast(5) }
