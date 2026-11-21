// Unit conversion and display. Everything is stored in °C, km/h, mm; converted only when shown.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import java.util.Locale
import kotlin.math.roundToInt

enum class TempUnit(val label: String) { C("°C"), F("°F") }
enum class WindUnit(val label: String) { KMH("km/h"), MS("m/s"), MPH("mph"), KN("kn") }
enum class PrecipUnit(val label: String) { MM("mm"), IN("in") }
enum class ClockFormat { H24, H12 }

data class UnitPrefs(
    val temp: TempUnit = TempUnit.C,
    val wind: WindUnit = WindUnit.KMH,
    val precip: PrecipUnit = PrecipUnit.MM,
    val clock: ClockFormat = ClockFormat.H24,
)

object Units {
    fun temp(c: Double?, u: TempUnit): Double? = c?.let { if (u == TempUnit.F) it * 9.0 / 5.0 + 32.0 else it }

    fun wind(kmh: Double?, u: WindUnit): Double? = kmh?.let {
        when (u) {
            WindUnit.KMH -> it
            WindUnit.MS -> it / 3.6
            WindUnit.MPH -> it / 1.609344
            WindUnit.KN -> it / 1.852
        }
    }

    fun precip(mm: Double?, u: PrecipUnit): Double? = mm?.let { if (u == PrecipUnit.IN) it / 25.4 else it }

    /** "17°C" (no "-0°"). */
    fun fmtTemp(c: Double?, u: TempUnit, withUnit: Boolean = true): String {
        val v = temp(c, u) ?: return "–"
        val r = v.roundToInt()
        return if (withUnit) "$r${u.label}" else "$r°"
    }

    fun fmtWind(kmh: Double?, u: WindUnit): String {
        val v = wind(kmh, u) ?: return "–"
        return "${v.roundToInt()} ${u.label}"
    }

    fun fmtPrecip(mm: Double?, u: PrecipUnit): String {
        val v = precip(mm, u) ?: return "–"
        return if (u == PrecipUnit.IN) String.format(Locale.US, "%.2f in", v) else String.format(Locale.US, "%.1f mm", v)
    }

    private val compass = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    fun compass(degrees: Double?): String {
        val d = degrees ?: return ""
        val i = (((d % 360 + 360) % 360) / 22.5 + 0.5).toInt() % 16
        return compass[i]
    }

    /** "19:30" / "7:30 pm" from a local ISO time "2026-11-22T19:30". */
    fun clock(iso: String?, f: ClockFormat): String {
        if (iso == null || iso.length < 16) return "–"
        val h = iso.substring(11, 13).toIntOrNull() ?: return "–"
        val m = iso.substring(14, 16).toIntOrNull() ?: return "–"
        return clock(h, m, f)
    }

    fun clock(h: Int, m: Int, f: ClockFormat): String =
        if (f == ClockFormat.H12) "${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(m)} ${if (h < 12) "am" else "pm"}"
        else "%02d:%02d".format(h, m)

    /** Short hour label for the chart: "19:00" / "7 pm". */
    fun hourLabel(iso: String, f: ClockFormat): String {
        if (iso.length < 16) return iso
        val h = iso.substring(11, 13).toIntOrNull() ?: return iso
        return if (f == ClockFormat.H12) "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "am" else "pm"}"
        else "%02d:00".format(h)
    }
}
