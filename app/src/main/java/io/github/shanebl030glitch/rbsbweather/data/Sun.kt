// Sun position, sunrise and sunset (NOAA's solar position equations), for providers that
// don't supply them (MET Norway) and for the animated sky.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

object Sun {
    /** Sunrise/sunset: the Sun's upper edge on the horizon, allowing for refraction. */
    const val HORIZON = -0.833

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI

    /** The Sun's elevation in degrees above the horizon at an instant and place. */
    fun elevation(t: Instant, lat: Double, lon: Double): Double {
        val jd = t.toEpochMilli() / 86_400_000.0 + 2440587.5
        val jc = (jd - 2451545.0) / 36525.0
        val l0 = (280.46646 + jc * (36000.76983 + jc * 0.0003032)) % 360.0
        val m = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
        val e = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
        val c = sin(rad(m)) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) +
            sin(rad(2 * m)) * (0.019993 - 0.000101 * jc) + sin(rad(3 * m)) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * jc
        val appLong = trueLong - 0.00569 - 0.00478 * sin(rad(omega))
        val obliq0 = 23.0 + (26.0 + (21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60.0) / 60.0
        val obliq = obliq0 + 0.00256 * cos(rad(omega))
        val decl = deg(asin(sin(rad(obliq)) * sin(rad(appLong))))
        val y = tan(rad(obliq / 2)) * tan(rad(obliq / 2))
        val eqTime = 4 * deg(y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) -
            0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m)))
        val utcMin = ((t.epochSecond % 86_400 + 86_400) % 86_400) / 60.0
        var trueSolar = (utcMin + eqTime + 4 * lon) % 1440.0
        if (trueSolar < 0) trueSolar += 1440.0
        val hourAngle = if (trueSolar / 4 < 0) trueSolar / 4 + 180 else trueSolar / 4 - 180
        val cosZen = sin(rad(lat)) * sin(rad(decl)) + cos(rad(lat)) * cos(rad(decl)) * cos(rad(hourAngle))
        val zenith = deg(acos(cosZen.coerceIn(-1.0, 1.0)))
        return 90.0 - zenith
    }

    fun isDay(t: Instant, lat: Double, lon: Double) = elevation(t, lat, lon) > HORIZON

    /** Sunrise and sunset on a local day (null in polar day or night), as local times. */
    fun riseSet(day: LocalDate, lat: Double, lon: Double, zone: ZoneId): Pair<ZonedDateTime?, ZonedDateTime?> {
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        fun g(t: Instant) = elevation(t, lat, lon) - HORIZON
        var rise: Instant? = null
        var set: Instant? = null
        var t = start
        var v = g(t)
        val step = 20L * 60
        while (t < end && (rise == null || set == null)) {
            val n = minOf(t.plusSeconds(step), end)
            val nv = g(n)
            if ((v < 0 && nv >= 0) || (v >= 0 && nv < 0)) {
                var a = t
                var b = n
                var fa = v
                repeat(14) {
                    val mid = a.plusMillis((b.toEpochMilli() - a.toEpochMilli()) / 2)
                    val fm = g(mid)
                    if ((fa < 0) == (fm < 0)) { a = mid; fa = fm } else b = mid
                }
                val at = a.plusMillis((b.toEpochMilli() - a.toEpochMilli()) / 2)
                if (v < 0 && nv >= 0 && rise == null) rise = at
                else if (v >= 0 && nv < 0 && set == null) set = at
            }
            t = n
            v = nv
        }
        return Pair(rise?.atZone(zone), set?.atZone(zone))
    }

    /** "Feels like" (Australian apparent temperature, as Open-Meteo uses). wind in m/s. */
    fun apparentTemperature(temp: Double?, rh: Double?, windMs: Double?): Double? {
        if (temp == null || rh == null || windMs == null) return null
        val e = rh / 100.0 * 6.105 * kotlin.math.exp(17.27 * temp / (237.7 + temp))
        return Math.round((temp + 0.33 * e - 0.70 * windMs - 4.0) * 10.0) / 10.0
    }

    /** A place's zone: its IANA name if saved, else the nearest whole hour from the longitude. */
    fun zoneFor(place: Place, utcOffsetSeconds: Int? = null): ZoneId {
        if (place.timezone.isNotBlank() && place.timezone != "auto") {
            try { return ZoneId.of(place.timezone) } catch (e: Exception) { /* fall through */ }
        }
        if (utcOffsetSeconds != null) return ZoneOffset.ofTotalSeconds(utcOffsetSeconds)
        val hours = Math.round(place.longitude / 15.0).toInt().coerceIn(-12, 14)
        return ZoneOffset.ofHours(hours)
    }
}
