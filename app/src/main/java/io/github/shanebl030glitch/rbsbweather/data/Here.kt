// 📍 My location (0.7): decides when the phone's position counts as a new place. Travel to another
// suburb, city or country and My location (and the widget) follow; small wobbles of the location
// fix don't cause a new download. Pure logic, so it's fully tested without a phone.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import kotlin.math.roundToLong

/** A position from the phone: degrees, accuracy in metres, when (ms since 1970), and which provider. */
data class Fix(val lat: Double, val lon: Double, val accuracyM: Float = 0f, val timeMs: Long = 0L, val provider: String = "")

/**
 * A place's name from its coordinates (suburb or town, region, country), or null if it can't be
 * named. `level` says how exact the name is: a suburb (or a village or small town with no suburbs),
 * a city, or only a region.
 */
data class Naming(val name: String, val admin1: String = "", val country: String = "", val level: Int = SUBURB) {
    companion object { const val REGION = 1; const val TOWN = 2; const val SUBURB = 3 }
}

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
        // moved: further than 1 km, and further than this fix's own uncertainty, so an approximate
        // (±2 km) position wobbling between checks doesn't make My location hop between suburbs
        val threshold = maxOf(MOVE_KM, fix.accuracyM / 1000.0)
        if (old != null && kmBetween(old.latitude, old.longitude, lat, lon) < threshold) {
            // hasn't moved; but if its name isn't down to the suburb yet, try again (and keep its spot)
            if (old.name != UNNAMED && old.detail >= Naming.SUBURB) return null
            val n = ask(namer, old.latitude, old.longitude) ?: return null
            if (old.name != UNNAMED && n.level <= old.detail) return null
            if (n.name == old.name && n.level == old.detail) return null
            return named(old.latitude, old.longitude, n)
        }
        val n = ask(namer, lat, lon)
        if (n != null) return named(lat, lon, n)
        // unnamed: keep the old name nearby
        val keep = old?.takeIf { kmBetween(it.latitude, it.longitude, lat, lon) < 5.0 && it.name != UNNAMED }
        return Place(keep?.name ?: UNNAMED, lat, lon, keep?.country.orEmpty(), keep?.admin1.orEmpty(), "auto", current = true,
            detail = if (keep != null) minOf(keep.detail, Naming.TOWN) else 0)     // ask again next time
    }

    private fun ask(namer: PlaceNamer, lat: Double, lon: Double): Naming? =
        (try { namer.name(lat, lon) } catch (e: Exception) { null })?.takeIf { it.name.isNotBlank() }

    private fun named(lat: Double, lon: Double, n: Naming) =
        Place(n.name.trim().take(100), lat, lon, n.country.trim().take(100), n.admin1.trim().take(100), "auto", current = true,
            detail = n.level.coerceIn(Naming.REGION, Naming.SUBURB))
}

/**
 * The phone's name when it's down to the suburb; otherwise OpenStreetMap's when that's more exact
 * (keeping the phone's spelling of the country). Pure, so it's tested without a phone.
 */
fun bestNaming(phone: Naming?, osm: () -> Naming?): Naming? {
    if (phone != null && phone.level >= Naming.SUBURB) return phone
    val o = try { osm() } catch (e: Exception) { null }
    return when {
        o == null -> phone
        phone == null -> o
        o.level > phone.level -> o.copy(country = phone.country.ifBlank { o.country })
        else -> phone
    }
}

/**
 * Remembers names by spot (about 100 m) for a while, so asking again while you stay put doesn't
 * call the network each time: 6 hours for a name, 30 minutes when none was found.
 */
class CachedNamer(private val inner: PlaceNamer, private val clock: () -> Long = System::currentTimeMillis) : PlaceNamer {
    private val seen = java.util.concurrent.ConcurrentHashMap<String, Pair<Naming?, Long>>()
    override fun name(lat: Double, lon: Double): Naming? {
        val key = String.format(java.util.Locale.US, "%.3f,%.3f", lat, lon)
        val now = clock()
        seen[key]?.let { (n, at) -> if (now - at < (if (n != null) 6 * 3_600_000L else 30 * 60_000L)) return n }
        val n = try { inner.name(lat, lon) } catch (e: Exception) { null }
        if (seen.size > 200) seen.clear()
        seen[key] = n to now
        return n
    }
}
