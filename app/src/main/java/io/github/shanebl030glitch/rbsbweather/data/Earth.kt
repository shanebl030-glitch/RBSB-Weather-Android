// The Earth's day and night, and the sky around the globe, ported from the desktop's earth.py,
// globe.py and sky.py: where the Sun and Moon are overhead, day/twilight/night at a place, the
// day/night blend weights, map and globe projections, and the stars, Sun and Moon behind the
// globe (a perspective view from space, so the Sun sets behind the Earth just as the middle of
// the view goes into night). Plain Kotlin. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import java.time.Instant
import java.util.Random
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object Earth {
    const val CREDIT = "Earth maps: Solar System Scope (CC BY 4.0), based on NASA imagery"
    const val SUNRISE_ALT = -0.833
    const val CIVIL_TWILIGHT = -6.0
    const val NIGHT_LIGHTS_FULL = -9.0
    const val DAY_FULL = 4.0
    const val RECOMPOSE_S = 300

    private fun r(d: Double) = Math.toRadians(d)
    private fun d(x: Double) = Math.toDegrees(x)
    fun normLon(lon: Double) = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    /** (latitude, longitude) where the Sun is overhead. */
    fun subsolarPoint(t: Instant): Pair<Double, Double> {
        val jd = Moon.julianDay(t); val s = Moon.sunPosition(jd)
        return s.dec to normLon(s.ra - Moon.gmst(jd))
    }

    /** (latitude, longitude) where the Moon is overhead (geocentric). */
    fun sublunarPoint(t: Instant): Pair<Double, Double> {
        val jd = Moon.julianDay(t); val m = Moon.moonPosition(jd)
        return m.dec to normLon(m.ra - Moon.gmst(jd))
    }

    /** Elevation (degrees) of a body overhead at (subLat, subLon), seen from (lat, lon). */
    fun elevationFrom(subLat: Double, subLon: Double, lat: Double, lon: Double): Double {
        val s = sin(r(lat)) * sin(r(subLat)) + cos(r(lat)) * cos(r(subLat)) * cos(r(lon - subLon))
        return d(asin(s.coerceIn(-1.0, 1.0)))
    }

    fun solarElevation(t: Instant, lat: Double, lon: Double): Double = subsolarPoint(t).let { (a, o) -> elevationFrom(a, o, lat, lon) }

    fun dayState(elevation: Double): String = when {
        elevation > SUNRISE_ALT -> "Day"
        elevation > CIVIL_TWILIGHT -> "Twilight"
        else -> "Night"
    }
    val STATE_SYMBOL = mapOf("Day" to "☀", "Twilight" to "◐", "Night" to "☾")

    fun smoothstep(e0: Double, e1: Double, x: Double): Double {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** 0 = night map, 1 = day map; a smooth band through twilight. */
    fun dayWeight(elev: Double) = smoothstep(NIGHT_LIGHTS_FULL, DAY_FULL, elev)

    /** Gentle darkening of the day side towards the terminator (low Sun), 0..0.45. */
    fun duskShade(elev: Double) = 0.45 * (1 - smoothstep(0.0, 25.0, elev)) * smoothstep(-6.0, 0.0, elev)

    /** (day weight, dusk shade) grids w × h for time t (equirectangular; longitude −180..180, latitude 90..−90). */
    fun masks(t: Instant, w: Int = 512, h: Int = 256): Pair<FloatArray, FloatArray> {
        val (slat, slon) = subsolarPoint(t)
        val sd = sin(r(slat)); val cd = cos(r(slat))
        val cols = DoubleArray(w) { i -> cos(r(-180.0 + (i + 0.5) * 360.0 / w - slon)) }
        val day = FloatArray(w * h); val shade = FloatArray(w * h)
        for (j in 0 until h) {
            val lat = 90.0 - (j + 0.5) * 180.0 / h
            val a = sin(r(lat)) * sd; val b = cos(r(lat)) * cd
            for (i in 0 until w) {
                val e = d(asin((a + b * cols[i]).coerceIn(-1.0, 1.0)))
                day[j * w + i] = dayWeight(e).toFloat()
                shade[j * w + i] = duskShade(e).toFloat()
            }
        }
        return day to shade
    }

    /** Equirectangular projection: map pixel for a latitude/longitude. */
    fun toMapXY(lat: Double, lon: Double, w: Double, h: Double) = (normLon(lon) + 180.0) / 360.0 * w to (90.0 - lat) / 180.0 * h
    fun fromMapXY(x: Double, y: Double, w: Double, h: Double) = 90.0 - y / h * 180.0 to x / w * 360.0 - 180.0

    /** "Day and night as of 17:45 (your time). The Sun is overhead at 20.1°S, 95.2°W." */
    fun infoText(t: Instant, clock: ClockFormat): String {
        val (slat, slon) = subsolarPoint(t)
        val z = t.atZone(java.time.ZoneId.systemDefault())
        return "Day and night as of ${Units.clock(z.hour, z.minute, clock)} (your time). The Sun is overhead at " +
            String.format(java.util.Locale.US, "%.1f°%s, %.1f°%s.", kotlin.math.abs(slat), if (slat >= 0) "N" else "S", kotlin.math.abs(slon), if (slon >= 0) "E" else "W")
    }
}

/** Orthographic globe projection (globe.py). x right, y up, both −1..1 on the disc. */
object Globe {
    const val LIMB = 0.25
    private fun r(d: Double) = Math.toRadians(d)

    fun project(lat: Double, lon: Double, cLat: Double, cLon: Double): Triple<Double, Double, Boolean> {
        val p = r(lat); val l = r(lon); val p0 = r(cLat); val l0 = r(cLon)
        val cosC = sin(p0) * sin(p) + cos(p0) * cos(p) * cos(l - l0)
        val x = cos(p) * sin(l - l0)
        val y = cos(p0) * sin(p) - sin(p0) * cos(p) * cos(l - l0)
        return Triple(x, y, cosC > 0)
    }

    /** Latitude/longitude under disc point (x, y), or null outside the disc. */
    fun inverse(x: Double, y: Double, cLat: Double, cLon: Double): Pair<Double, Double>? {
        val rho = hypot(x, y)
        if (rho > 1) return null
        if (rho < 1e-12) return cLat to cLon
        val c = asin(rho); val p0 = r(cLat); val l0 = r(cLon)
        val lat = asin(cos(c) * sin(p0) + y * sin(c) * cos(p0) / rho)
        val lon = l0 + atan2(x * sin(c), rho * cos(c) * cos(p0) - y * sin(c) * sin(p0))
        return Math.toDegrees(lat) to Earth.normLon(Math.toDegrees(lon))
    }
}

/** The sky behind the globe (sky.py): a perspective view from space about RHO° around the Earth's edge. */
object Sky {
    const val RHO = 40.0
    val TAN_RHO = tan(Math.toRadians(RHO))
    const val SUN_SIZE = 0.11          // globe radii (not to scale, so it's visible)
    const val MOON_SIZE = 0.32

    data class V(val x: Double, val y: Double, val z: Double)
    private fun dot(a: V, b: V) = a.x * b.x + a.y * b.y + a.z * b.z
    private fun norm(a: V): V { val k = sqrt(dot(a, a)).takeIf { it > 0 } ?: 1.0; return V(a.x / k, a.y / k, a.z / k) }

    fun ecef(lat: Double, lon: Double): V {
        val p = Math.toRadians(lat); val l = Math.toRadians(lon)
        return V(cos(p) * cos(l), cos(p) * sin(l), sin(p))
    }

    /** (east, north, up) for a view centred on cLat, cLon. */
    fun basis(cLat: Double, cLon: Double): Triple<V, V, V> {
        val p = Math.toRadians(cLat); val l = Math.toRadians(cLon)
        return Triple(V(-sin(l), cos(l), 0.0), V(-sin(p) * cos(l), -sin(p) * sin(l), cos(p)), V(cos(p) * cos(l), cos(p) * sin(l), sin(p)))
    }

    fun toView(d: V, b: Triple<V, V, V>) = V(dot(d, b.first), dot(d, b.second), dot(d, b.third))

    /** Screen offset (globe radii; x right, y up) of a far-away object, or null if behind the camera. */
    fun skyXY(d: V, b: Triple<V, V, V>): Pair<Double, Double>? {
        val v = toView(d, b)
        if (v.z > -1e-3) return null
        val k = -1.0 / (v.z * TAN_RHO)
        return v.x * k to v.y * k
    }

    fun hiddenByEarth(xy: Pair<Double, Double>, radius: Double = 0.0) = hypot(xy.first, xy.second) + radius < 1.0

    private fun screenAngle(from: Pair<Double, Double>, to: Pair<Double, Double>) =
        ((Math.toDegrees(atan2(to.first - from.first, to.second - from.second)) % 360) + 360) % 360

    /** (bright limb, rotation) so the Moon on screen has its lit side towards the Sun and north matching the view. */
    fun moonOrientation(moon: V, sun: V, b: Triple<V, V, V>): Pair<Double, Double>? {
        val m = norm(moon)
        val here = skyXY(m, b) ?: return null
        val eps = 0.01
        fun towards(target: V): Double? {
            val t = dot(target, m)
            var tang = V(target.x - t * m.x, target.y - t * m.y, target.z - t * m.z)
            if (dot(tang, tang) < 1e-12) return null
            tang = norm(tang)
            val q = skyXY(norm(V(m.x + eps * tang.x, m.y + eps * tang.y, m.z + eps * tang.z)), b) ?: return null
            return screenAngle(here, q)
        }
        val north = towards(V(0.0, 0.0, 1.0)) ?: 0.0
        val sunDir = towards(norm(sun)) ?: north
        return ((north - sunDir) % 360 + 360) % 360 to north
    }

    /** Stars fixed in space, turning with sidereal time. */
    class StarField(n: Int = 2400, seed: Long = 7) {
        val ra = DoubleArray(n); val dec = DoubleArray(n); val size = FloatArray(n); val bright = IntArray(n)
        init {
            val rng = Random(seed)
            for (i in 0 until n) {
                ra[i] = rng.nextDouble() * 360
                dec[i] = Math.toDegrees(asin(rng.nextDouble() * 2 - 1))
                val mag = rng.nextDouble().let { it * it * it }
                size[i] = (0.5 + 1.4 * mag).toFloat(); bright[i] = (70 + 185 * mag).toInt()
            }
        }

        /** Stars beyond the Earth within `limit` radii of the centre: (x, y, size, brightness). */
        fun positions(gmstDeg: Double, b: Triple<V, V, V>, limit: Double): List<FloatArray> {
            val g = Math.toRadians(gmstDeg)
            val out = ArrayList<FloatArray>()
            for (i in ra.indices) {
                val lon = Math.toRadians(ra[i]) - g; val cd = cos(Math.toRadians(dec[i]))
                val xy = skyXY(V(cd * cos(lon), cd * sin(lon), sin(Math.toRadians(dec[i]))), b) ?: continue
                val rr = hypot(xy.first, xy.second)
                if (rr > 1.0 && rr < limit) out += floatArrayOf(xy.first.toFloat(), xy.second.toFloat(), size[i], bright[i].toFloat())
            }
            return out
        }
    }

    @Suppress("unused") private const val TWO_PI = 2 * PI
}
