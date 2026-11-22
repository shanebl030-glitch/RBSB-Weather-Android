// What the animated sky shows, worked out from a forecast: the kind of weather, daylight and
// twilight, where the Sun and Moon are on their arcs, the Moon's real phase and tilt, and the
// wind the clouds drift with. A port of the desktop app's scene.py (conditions part), so both
// apps draw the same sky for the same forecast. Plain Kotlin (unit tested).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.scene

import io.github.shanebl030glitch.rbsbweather.data.Day
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.Hour
import io.github.shanebl030glitch.rbsbweather.data.Moon
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

const val FPS = 24

// How long the front cloud layer takes to cross the whole scene, by wind speed (km/h), in scene
// widths, so the big card and a small widget show the same wind the same way.
val CLOUD_CROSSING_S = listOf(0.0 to 150.0, 5.0 to 100.0, 10.0 to 70.0, 20.0 to 50.0, 40.0 to 32.0,
    60.0 to 25.0, 80.0 to 21.0, 100.0 to 19.0)
const val MIN_EAST_WEST = 0.3
const val WIND_EASE_S = 3.0
const val CLOUD_FADE_S = 0.8
const val DAY_MOON_FADE = 0.3
const val PLAYBACK_MAX_S = 24 * 3600.0

/** Front-layer cloud speed in scene widths per second for a wind speed in km/h. */
fun cloudSpeed(windKmh: Double?): Double {
    val v = if (windKmh != null && windKmh.isFinite() && windKmh > 0) windKmh else 0.0
    val pts = CLOUD_CROSSING_S
    if (v >= pts.last().first) return 1.0 / pts.last().second
    for (i in 0 until pts.size - 1) {
        val (k0, t0) = pts[i]; val (k1, t1) = pts[i + 1]
        if (v <= k1) {
            val f = (v - k0) / (k1 - k0)
            return (1.0 / t0) + ((1.0 / t1) - (1.0 / t0)) * f
        }
    }
    return 1.0 / pts.last().second
}

/** +1 drift right (towards east), -1 left, for the direction the wind blows FROM (degrees). */
fun windDrift(directionFrom: Double?): Double {
    if (directionFrom == null || !directionFrom.isFinite()) return 1.0
    var east = -sin(Math.toRadians(directionFrom))
    if (abs(east) < 1e-9) east = 0.0
    val side = if (east >= 0) 1.0 else -1.0
    return side * (MIN_EAST_WEST + (1.0 - MIN_EAST_WEST) * abs(east))
}

enum class Kind { CLEAR, PARTLY, OVERCAST, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

private val CODE_MAP: Map<Int, Pair<Kind, Double>> = mapOf(
    0 to (Kind.CLEAR to 0.0), 1 to (Kind.PARTLY to 0.25), 2 to (Kind.PARTLY to 0.55), 3 to (Kind.OVERCAST to 1.0),
    45 to (Kind.FOG to 0.7), 48 to (Kind.FOG to 0.9),
    51 to (Kind.DRIZZLE to 0.3), 53 to (Kind.DRIZZLE to 0.55), 55 to (Kind.DRIZZLE to 0.85), 56 to (Kind.DRIZZLE to 0.4), 57 to (Kind.DRIZZLE to 0.7),
    61 to (Kind.RAIN to 0.35), 63 to (Kind.RAIN to 0.6), 65 to (Kind.RAIN to 1.0), 66 to (Kind.RAIN to 0.4), 67 to (Kind.RAIN to 0.8),
    80 to (Kind.RAIN to 0.4), 81 to (Kind.RAIN to 0.7), 82 to (Kind.RAIN to 1.0),
    71 to (Kind.SNOW to 0.3), 73 to (Kind.SNOW to 0.6), 75 to (Kind.SNOW to 1.0), 77 to (Kind.SNOW to 0.3), 85 to (Kind.SNOW to 0.5), 86 to (Kind.SNOW to 0.9),
    95 to (Kind.THUNDER to 0.7), 96 to (Kind.THUNDER to 0.85), 99 to (Kind.THUNDER to 1.0),
)

/** WMO weather code → (kind, intensity 0..1). Unknown codes look partly cloudy. */
fun classify(code: Int?): Pair<Kind, Double> = code?.let { CODE_MAP[it] } ?: (Kind.PARTLY to 0.4)

data class Conditions(
    val kind: Kind = Kind.PARTLY,
    val intensity: Double = 0.4,
    val daylight: Double = 1.0,
    val twilight: Double = 0.0,
    val sunProgress: Double = 0.5,
    val windKmh: Double = 10.0,
    val windEast: Double = 1.0,
    val fromForecast: Boolean = false,
    val cloudCover: Double? = null,
    val moonAge: Double = 0.5,
    val southern: Boolean = false,
    val moonPhaseAngle: Double? = null,
    val moonLimb: Double = 270.0,
    val moonRotation: Double? = null,
    val moonUp: Boolean = true,
    val moonAltitude: Double = 45.0,
    val moonProgress: Double? = null,
    val sunRiseMs: Long? = null,
    val sunSetMs: Long? = null,
    val moonRiseMs: Long? = null,
    val moonSetMs: Long? = null,
) {
    fun sunProgressAt(nowMs: Long): Double =
        if (sunRiseMs != null && sunSetMs != null && sunSetMs > sunRiseMs) (nowMs - sunRiseMs).toDouble() / (sunSetMs - sunRiseMs)
        else sunProgress

    fun moonProgressAt(nowMs: Long): Double? {
        if (moonRiseMs != null && moonSetMs != null && moonSetMs > moonRiseMs) return (nowMs - moonRiseMs).toDouble() / (moonSetMs - moonRiseMs)
        if (moonProgress != null) return moonProgress
        if (!moonUp) return null
        return 0.5 + 0.5 * (1 - moonAltitude.coerceIn(0.0, 90.0) / 90.0)
    }

    val cloudAmount: Double get() {
        var base = when (kind) {
            Kind.CLEAR -> 0.0; Kind.PARTLY -> 0.25 + 0.4 * intensity; Kind.OVERCAST -> 1.0; Kind.FOG -> 0.55
            Kind.DRIZZLE -> 0.85; Kind.RAIN -> 0.9; Kind.SNOW -> 0.85; Kind.THUNDER -> 1.0
        }
        if (kind == Kind.CLEAR && cloudCover != null && cloudCover > 0) base = minOf(0.25, cloudCover / 300.0)
        return base
    }

    /** How grey and heavy the clouds are. */
    val darkness: Double get() = when (kind) {
        Kind.CLEAR -> 0.0; Kind.PARTLY -> 0.05; Kind.OVERCAST -> 0.3; Kind.FOG -> 0.1; Kind.DRIZZLE -> 0.35
        Kind.RAIN -> 0.35 + 0.25 * intensity; Kind.SNOW -> 0.2; Kind.THUNDER -> 0.65 + 0.2 * intensity
    }
}

/** Where the Sun or Moon sits on its arc: 0 rising bottom-left, 0.5 top middle, 1 setting bottom-right. */
fun arcPoint(progress: Double?, left: Float, top: Float, w: Float, h: Float, rad: Float): Pair<Float, Float>? {
    if (progress == null || progress < 0.0 || progress > 1.0) return null
    val x0 = left + w * 0.05f; val x1 = left + w - w * 0.05f
    val horizon = top + h + rad * 1.05f
    val apex = top + maxOf(rad * 1.35f, h * 0.2f)
    val x = x0 + (x1 - x0) * progress.toFloat()
    val y = horizon - (horizon - apex) * sin(PI * progress).toFloat()
    return x to y
}

/** The place's local time now: its observation time moved on by the time since download (at most 6 hours). */
fun localTime(fc: Forecast, nowMs: Long = System.currentTimeMillis()): LocalDateTime? {
    val t = try { LocalDateTime.parse(fc.current.time) } catch (e: Exception) { return null }
    return if (fc.fetchedAt > 0) t.plusSeconds(((nowMs - fc.fetchedAt) / 1000).coerceIn(0, 6 * 3600)) else t
}

/** The forecast hour that contains a local time. */
fun hourlyAt(fc: Forecast, local: LocalDateTime?): Hour? {
    local ?: return null
    val key = local.toString().take(13)
    return fc.hourly.firstOrNull { it.time.take(13) == key }
}

/** How far ahead playback can go: to the end of the last forecast hour, at most 24 hours. */
fun playbackSeconds(fc: Forecast, nowMs: Long = System.currentTimeMillis()): Double {
    val t = localTime(fc, nowMs) ?: return 0.0
    val last = fc.hourly.filter { it.weatherCode != null }
        .mapNotNull { try { LocalDateTime.parse(it.time) } catch (e: Exception) { null } }.maxOrNull() ?: return 0.0
    val ahead = java.time.Duration.between(t, last.plusHours(1)).seconds - 1.0
    return ahead.coerceIn(0.0, PLAYBACK_MAX_S)
}

private fun dayFor(fc: Forecast, date: LocalDate?): Day? =
    date?.toString()?.let { iso -> fc.daily.firstOrNull { it.date == iso } } ?: fc.daily.firstOrNull()

private object MoonPassCache {
    private val map = LinkedHashMap<String, Pair<Instant?, Instant?>>()
    @Synchronized fun get(now: Instant, lat: Double, lon: Double): Pair<Instant?, Instant?> {
        val key = String.format(java.util.Locale.US, "%.3f,%.3f", lat, lon)
        map[key]?.let { (r, s) -> if (r != null && s != null && !now.isBefore(r) && !now.isAfter(s)) return r to s }
        val p = Moon.moonPass(now, lat, lon)
        if (map.size > 64) map.clear()
        map[key] = p.rise to p.set
        return p.rise to p.set
    }
}

/**
 * Scene conditions from a forecast, moving the place's clock on by the time since download so
 * the Sun keeps moving. offsetS > 0 (forecast playback): the sky that many seconds from now:
 * that hour's weather, the Sun for that day, the Moon at that moment; the wind stays as now.
 */
fun conditionsFromForecast(fc: Forecast, nowMs: Long = System.currentTimeMillis(), offsetS: Double = 0.0,
                           withMoon: Boolean = true): Conditions {
    val c = fc.current
    val atMs = nowMs + (offsetS * 1000).toLong()
    val localNow = localTime(fc, nowMs)?.plusSeconds(offsetS.toLong())
    var code = c.weatherCode
    var cover = c.cloudCover
    if (offsetS > 0) {
        val hp = hourlyAt(fc, localNow)
        if (hp?.weatherCode != null) { code = hp.weatherCode; cover = null }
    }
    val (kind, intensity) = classify(code)
    val east = if (c.windDirection != null) windDrift(c.windDirection) else 1.0
    var daylight: Double; var twilight = 0.0; var sunProg: Double
    var riseMs: Long? = null; var setMs: Long? = null
    val today = dayFor(fc, localNow?.toLocalDate())
    val rise = try { today?.sunrise?.let { LocalDateTime.parse(it) } } catch (e: Exception) { null }
    val sset = try { today?.sunset?.let { LocalDateTime.parse(it) } } catch (e: Exception) { null }
    if (localNow != null && rise != null && sset != null && sset.isAfter(rise)) {
        val m = localNow.hour * 60 + localNow.minute + localNow.second / 60.0
        val r = (rise.hour * 60 + rise.minute).toDouble()
        val s = (sset.hour * 60 + sset.minute).toDouble()
        val tw = 40.0
        if (m >= r + tw && m <= s - tw) daylight = 1.0
        else if (m < r - tw || m > s + tw) daylight = 0.0
        else {
            val edgeIsRise = abs(m - r) < abs(m - s)
            val edge = if (edgeIsRise) r else s
            daylight = (0.5 + (m - edge) / (2 * tw) * (if (edgeIsRise) 1 else -1)).coerceIn(0.0, 1.0)
            twilight = 1.0 - abs(m - edge) / tw
        }
        sunProg = ((m - r) / maxOf(1.0, s - r)).coerceIn(-0.2, 1.2)
        riseMs = atMs - ((m - r) * 60_000).toLong()
        setMs = atMs + ((s - m) * 60_000).toLong()
    } else {
        daylight = if (c.isDay) 1.0 else 0.0
        sunProg = 0.35
    }
    var cond = Conditions(kind = kind, intensity = intensity, daylight = daylight, twilight = twilight, sunProgress = sunProg,
        windKmh = c.windSpeed ?: 0.0, windEast = east, fromForecast = true, cloudCover = cover,
        sunRiseMs = riseMs, sunSetMs = setMs, southern = fc.place.latitude < 0)
    val at = Instant.ofEpochMilli(atMs)
    cond = cond.copy(moonAge = Moon.ageFraction(at))
    if (withMoon) try {
        val ph = Moon.phase(at)
        val g = Moon.skyGeometry(Moon.julianDay(at), fc.place.latitude, fc.place.longitude)
        val up = g.altitude > g.h0
        cond = cond.copy(moonPhaseAngle = ph.phaseAngle, moonLimb = ph.brightLimb, moonRotation = g.q, moonUp = up,
            moonAltitude = g.altitude)
        if (up) {
            val (mr, ms) = MoonPassCache.get(at, fc.place.latitude, fc.place.longitude)
            if (mr != null && ms != null) {
                cond = cond.copy(moonRiseMs = mr.toEpochMilli(), moonSetMs = ms.toEpochMilli())
                cond = cond.copy(moonProgress = cond.moonProgressAt(atMs))
            }
        }
    } catch (e: Exception) { /* the drawn moon is used */ }
    return cond
}
