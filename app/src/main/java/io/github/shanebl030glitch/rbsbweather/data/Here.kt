// 📍 My location (0.7): decides when the phone's position counts as a new place. Travel to another
// suburb, city or country and My location (and the widget) follow; small wobbles of the location
// fix don't cause a new download. Pure logic, so it's fully tested without a phone.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import kotlin.math.roundToLong

/** A position from the phone: degrees, accuracy in metres, when (ms since 1970), and which provider. */
data class Fix(val lat: Double, val lon: Double, val accuracyM: Float = 0f, val timeMs: Long = 0L, val provider: String = "")

/** A place's name from its coordinates (suburb or town, region, country), or null if it can't be named. */
data class Naming(val name: String, val admin1: String = "", val country: String = "")

fun interface PlaceNamer { fun name(lat: Double, lon: Double): Naming? }

object Here {
    /** Moving further than this makes a new My location (and a new forecast). */
    const val MOVE_KM = 1.0
    /** Position fixes older than this aren't used. */
    const val MAX_AGE_MS = 30 * 60_000L
    /** Fixes less accurate than this aren't used (a coarse network fix is usually 100 m to 2 km). */
    const val MAX_ACCURACY_M = 5_000f
    const val UNNAMED = "My location"

    /** Coordinates kept to 3 decimals (about 100 m): enough for the weather, and nothing more precise is stored or sent. */
    fun round3(v: Double) = (v * 1000).roundToLong() / 1000.0

    fun usable(f: Fix, now: Long): Boolean =
        !f.lat.isNaN() && !f.lon.isNaN() && f.lat in -90.0..90.0 && f.lon in -180.0..180.0 &&
            (f.timeMs == 0L || now - f.timeMs <= MAX_AGE_MS) && (f.accuracyM <= 0f || f.accuracyM <= MAX_ACCURACY_M)

    /**
     * The new My location for `fix`, or null when nothing should change (it hasn't moved far enough,
     * or the fix isn't usable). Naming is asked only when it has moved, so the phone's geocoder isn't
     * called every 15 minutes.
     */
    fun next(old: Place?, fix: Fix, now: Long, namer: PlaceNamer): Place? {
        if (!usable(fix, now)) return null
        val lat = round3(fix.lat); val lon = round3(fix.lon)
        if (old != null && kmBetween(old.latitude, old.longitude, lat, lon) < MOVE_KM) return null
        val n = try { namer.name(lat, lon) } catch (e: Exception) { null }
        val name = n?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: old?.name?.takeIf { kmBetween(old.latitude, old.longitude, lat, lon) < 5.0 }       // unnamed: keep the old name nearby
            ?: UNNAMED
        return Place(name.take(100), lat, lon, n?.country.orEmpty().take(100), n?.admin1.orEmpty().take(100), "auto", current = true)
    }
}
