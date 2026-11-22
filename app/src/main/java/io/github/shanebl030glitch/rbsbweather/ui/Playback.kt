// Forecast playback, as on the desktop (playback.py and the main window's _apply_playback):
// ▶ plays the coming hours in the sky at an hour a second, the slider picks any moment up to 24
// hours ahead in 15-minute steps, and Now returns to the live weather. The card then shows that
// hour's temperature, weather and chance of rain, marked "▶ Forecast for …". Plain Kotlin.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import io.github.shanebl030glitch.rbsbweather.data.ClockFormat
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.Hour
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.ui.scene.hourlyAt
import io.github.shanebl030glitch.rbsbweather.ui.scene.localTime
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

const val PLAY_RATE = 3600.0          // forecast seconds per real second: an hour a second
const val STEP_MIN = 15
const val WIND_NOTE = "The hourly forecast has no wind, so the clouds keep the wind of now; everything else in the sky is the forecast for this time."

/** Why playback can't be used (when playbackSeconds is 0). */
fun playbackUnavailable(fc: Forecast): String =
    if (fc.hourly.isEmpty()) "${fc.source} doesn't provide an hourly forecast" else "No hourly forecast ahead"

/** The local time shown at an offset, e.g. "Mon 14:15" (or "Mon 2:15 pm"). */
fun playbackStamp(fc: Forecast, offsetS: Double, clock: ClockFormat, nowMs: Long = System.currentTimeMillis()): String {
    val t: LocalDateTime = localTime(fc, nowMs)?.plusSeconds(offsetS.toLong()) ?: return "–"
    return t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + Units.clock(t.hour, t.minute, clock)
}

/** The forecast hour shown at an offset. */
fun playbackHour(fc: Forecast, offsetS: Double, nowMs: Long = System.currentTimeMillis()): Hour? =
    hourlyAt(fc, localTime(fc, nowMs)?.plusSeconds(offsetS.toLong()))

/** The slider's number of 15-minute steps for a playback range. */
fun playbackSteps(maxS: Double): Int = (maxS / (STEP_MIN * 60)).toInt()

/** One tick of playing: the next offset and whether it has reached the end. */
fun playTick(offsetS: Double, dtS: Double, maxS: Double): Pair<Double, Boolean> {
    val nxt = offsetS + PLAY_RATE * dtS
    return if (nxt >= maxS) maxS to true else nxt to false
}
