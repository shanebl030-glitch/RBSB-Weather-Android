// Earthquakes, ported from the desktop app's quakes.py: recent quakes worldwide from USGS (public
// domain) and New Zealand quakes likely to have been felt from GeoNet (CC BY 3.0 NZ; GeoNet's
// version is kept when both report one), and a notification when one is near a saved place or
// is major anywhere. Nothing about the user is sent: the same feeds are downloaded whoever asks,
// and distances are worked out on the phone. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.OffsetDateTime
import java.util.Locale

object Quakes {
    const val USGS_URL = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/{feed}.geojson"
    const val GEONET_URL = "https://api.geonet.org.nz/quake?MMI=3"
    const val GEONET_ACCEPT = "application/vnd.geo+json;version=2"
    const val MAX_BYTES = 6 * 1024 * 1024
    const val CREDIT = "Earthquakes: USGS (public domain) and GeoNet (CC BY 3.0 NZ)"
    val PERIODS = mapOf("day" to 86400.0, "week" to 7 * 86400.0)
    val MIN_MAGS = listOf(2.5, 4.5)
    val INTERVALS = listOf(5, 10, 30)
    const val PULSE_S = 3600.0
    const val NOTIFY_WITHIN_S = 2 * 3600.0
}

class QuakeException(message: String) : Exception(message)

data class Quake(
    val id: String,
    val source: String,               // USGS | GeoNet
    val time: Double,                 // unix seconds
    val lat: Double,
    val lon: Double,
    val depthKm: Double?,
    val mag: Double,
    val place: String = "",
    val url: String = "",
    val mmi: Double? = null,
    val tsunami: Boolean = false,     // USGS: a large event in an oceanic region (not a warning)
    val alert: String = "",           // USGS PAGER: green | yellow | orange | red
) {
    fun age(now: Double = nowS()) = now - time

    fun toJson(): JSONObject = JSONObject().put("id", id).put("source", source).put("time", time).put("lat", lat).put("lon", lon)
        .putN("depth_km", depthKm).put("mag", mag).put("place", place).put("url", url).putN("mmi", mmi).put("tsunami", tsunami).put("alert", alert)

    companion object {
        fun fromJson(d: JSONObject): Quake {
            val lat = d.num("lat"); val lon = d.num("lon"); val mag = d.num("mag"); val t = d.num("time")
            require(lat != null && lon != null && mag != null && t != null && lat in -90.0..90.0 && lon in -180.0..180.0) { "bad quake" }
            return Quake(d.getString("id").take(120), d.optString("source").take(20), t, lat, lon, d.num("depth_km"), mag,
                d.optString("place").take(200), safeUrl(d.optString("url")), d.num("mmi"), d.optBoolean("tsunami"),
                d.optString("alert").takeIf { it in setOf("green", "yellow", "orange", "red") } ?: "")
        }
    }
}

private fun round1(v: Double) = Math.round(v * 10) / 10.0

/** A USGS summary GeoJSON feed: coordinates [lon, lat, depth km]; time in ms. */
fun parseUsgs(data: JSONObject?): List<Quake> {
    val feats = data?.optJSONArray("features") ?: return emptyList()
    val out = mutableListOf<Quake>()
    for (i in 0 until feats.length()) {
        try {
            val f = feats.getJSONObject(i)
            val p = f.getJSONObject("properties"); val g = f.getJSONObject("geometry").getJSONArray("coordinates")
            if (p.optString("type", "earthquake").ifEmpty { "earthquake" } != "earthquake" || p.optString("status") == "deleted") continue
            val lon = g.getDouble(0); val lat = g.getDouble(1)
            val depth = if (g.length() > 2) g.num(2) else null
            val mag = p.num("mag") ?: continue; val ms = p.num("time") ?: continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val alert = p.optString("alert").takeIf { it in setOf("green", "yellow", "orange", "red") } ?: ""
            val fid = f.optString("id").ifEmpty { p.optString("code") }
            out += Quake("usgs:$fid".take(120), "USGS", ms / 1000.0, lat, lon, depth, round1(mag), p.optString("place").let { if (p.isNull("place")) "" else it }.take(200),
                safeUrl(if (p.isNull("url")) "" else p.optString("url")), p.num("mmi"), p.optInt("tsunami", 0) == 1, alert)
        } catch (e: Exception) { continue }
    }
    return out
}

/** GeoNet /quake GeoJSON (version 2): coordinates [lon, lat]; ISO 8601 time. */
fun parseGeonet(data: JSONObject?): List<Quake> {
    val feats = data?.optJSONArray("features") ?: return emptyList()
    val out = mutableListOf<Quake>()
    for (i in 0 until feats.length()) {
        try {
            val f = feats.getJSONObject(i)
            val p = f.getJSONObject("properties"); val g = f.getJSONObject("geometry").getJSONArray("coordinates")
            if (p.optString("quality").lowercase() == "deleted") continue
            var lon = g.getDouble(0); val lat = g.getDouble(1)
            if (lon > 180) lon -= 360
            val mag = p.num("magnitude") ?: continue
            val t = OffsetDateTime.parse(p.getString("time").replace("Z", "+00:00"))
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val pid = p.optString("publicID").take(60)
            out += Quake("geonet:$pid", "GeoNet", t.toEpochSecond() + t.nano / 1e9, lat, lon, p.num("depth"), round1(mag),
                p.optString("locality").take(200), if (pid.isNotEmpty()) "https://www.geonet.org.nz/earthquake/$pid" else "",
                p.num("mmi") ?: p.num("MMI"))
        } catch (e: Exception) { continue }
    }
    return out
}

/** Both lists, newest first, USGS copies of GeoNet quakes removed (within 2 minutes and 100 km). */
fun mergeQuakes(usgs: List<Quake>, geonet: List<Quake>): List<Quake> {
    val keep = geonet.toMutableList()
    for (q in usgs) {
        if (geonet.any { g -> kotlin.math.abs(q.time - g.time) < 120 && kmBetween(q.lat, q.lon, g.lat, g.lon) < 100 }) continue
        keep += q
    }
    return keep.sortedByDescending { it.time }
}

fun quakesWithin(quakes: Iterable<Quake>, periodS: Double, minMag: Double, now: Double = nowS()): List<Quake> =
    quakes.filter { now - it.time in 0.0..(periodS + 300) && it.mag >= minMag - 1e-9 }

/** By age: red under an hour, orange under 6 hours, yellow under a day, then grey. */
fun quakeColour(q: Quake, now: Double = nowS()): Long {
    val a = q.age(now)
    return if (a < 3600) 0xFFFF3B30 else if (a < 6 * 3600) 0xFFFF9500 else if (a < 86400) 0xFFFFD60A else 0xFFC7C9D1
}

fun quakeRadius(mag: Double): Double = (2.5 + (mag - 2.5) * 1.8).coerceIn(2.5, 14.0)

fun quakeAgo(seconds: Double): String {
    val s = maxOf(0L, seconds.toLong())
    return when {
        s < 90 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 2 * 86400 -> { val h = s / 3600; "$h hour${if (h != 1L) "s" else ""} ago" }
        else -> "${s / 86400} days ago"
    }
}

/** "M5.4 · 20 km north-east of Seddon · 12 km deep · 3 min ago (GeoNet)". */
fun describeQuake(q: Quake, now: Double = nowS()): String {
    val parts = mutableListOf(String.format(Locale.US, "M%.1f", q.mag))
    if (q.place.isNotEmpty()) parts += q.place
    q.depthKm?.let { parts += String.format(Locale.US, "%.0f km deep", it) }
    parts += quakeAgo(q.age(now))
    return parts.joinToString(" · ") + " (${q.source})"
}

/** The nearest saved place and its distance in km. */
fun nearestPlace(q: Quake, places: Iterable<Place>): Pair<Place, Double>? =
    places.map { it to kmBetween(q.lat, q.lon, it.latitude, it.longitude) }.minByOrNull { it.second }

data class NotifyRules(val near: Boolean = true, val nearMag: Double = 4.5, val nearKm: Double = 300.0, val major: Boolean = true, val majorMag: Double = 7.0)

/** Recent quakes that pass the rules and haven't been notified yet, biggest first. */
fun quakesToNotify(quakes: Iterable<Quake>, places: List<Place>, rules: NotifyRules, notified: Collection<String>, now: Double = nowS()): List<Pair<Quake, Pair<Place, Double>?>> {
    val done = notified.toSet()
    val out = mutableListOf<Pair<Quake, Pair<Place, Double>?>>()
    for (q in quakes) {
        if (q.id in done || q.age(now) > Quakes.NOTIFY_WITHIN_S || q.age(now) < -300) continue
        val near = nearestPlace(q, places)
        if (rules.near && near != null && q.mag >= rules.nearMag && near.second <= rules.nearKm) out += q to near
        else if (rules.major && q.mag >= rules.majorMag) out += q to near
    }
    return out.sortedByDescending { it.first.mag }
}

/** Notification title and text. */
fun quakeNotice(q: Quake, near: Pair<Place, Double>?, now: Double = nowS()): Pair<String, String> {
    val title = if (near != null && near.second < 1000) String.format(Locale.US, "M%.1f earthquake %.0f km from %s", q.mag, near.second, near.first.name)
        else String.format(Locale.US, "M%.1f earthquake", q.mag) + (if (q.place.isNotEmpty()) ": ${q.place}" else "")
    var body = describeQuake(q, now)
    if (q.tsunami && q.mag >= 6.5) body += "\nA large quake under the sea. If you're near the coast, check your official tsunami advice (in New Zealand: Civil Defence, civildefence.govt.nz)."
    return title to body
}

class QuakeClient(private val http: BytesClient, private val usgsUrl: String = Quakes.USGS_URL, private val geonetUrl: String = Quakes.GEONET_URL) {
    private fun json(url: String, accept: String): JSONObject {
        val data = try { http.get(url, mapOf("Accept" to accept), Quakes.MAX_BYTES) } catch (e: WeatherException) {
            throw QuakeException(e.status?.let { "HTTP $it" } ?: "couldn't connect (${e.message})")
        }
        return try { JSONObject(String(data, Charsets.UTF_8)) } catch (e: Exception) { throw QuakeException("the reply couldn't be read") }
    }

    /** Recent quakes (merged, newest first) and {source: error}. */
    fun fetch(period: String = "day", minMag: Double = 2.5, nz: Boolean = true): Pair<List<Quake>, Map<String, String>> {
        val feed = (if (minMag < 4.5) "2.5" else "4.5") + "_" + (if (period == "week") "week" else "day")
        val errors = mutableMapOf<String, String>()
        var usgs = emptyList<Quake>(); var geonet = emptyList<Quake>()
        try { usgs = parseUsgs(json(usgsUrl.replace("{feed}", feed), "application/geo+json, application/json")) } catch (e: QuakeException) { errors["USGS"] = e.message ?: "failed" }
        if (nz) try { geonet = parseGeonet(json(geonetUrl, Quakes.GEONET_ACCEPT)) } catch (e: QuakeException) { errors["GeoNet"] = e.message ?: "failed" }
        return mergeQuakes(usgs, geonet) to errors
    }
}

/** quakes.json: the last quakes (shown offline) and which have been notified (so a restart doesn't repeat them). */
class QuakeStore(private val dir: File) {
    var quakes: List<Quake> = emptyList()
    var notified: MutableList<String> = mutableListOf()
    var checked = 0.0
    var errors: Map<String, String> = emptyMap()
    private val file get() = File(dir, "quakes.json")

    fun load(): QuakeStore {
        val o = try { JSONObject(file.readText()) } catch (e: Exception) { return this }
        quakes = o.optJSONArray("quakes")?.let { a -> (0 until minOf(2000, a.length())).mapNotNull { try { Quake.fromJson(a.getJSONObject(it)) } catch (e: Exception) { null } } } ?: emptyList()
        notified = o.optJSONArray("notified")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotEmpty() }?.take(120) }.takeLast(500).toMutableList() } ?: mutableListOf()
        checked = o.num("checked") ?: 0.0
        return this
    }

    fun save() {
        try {
            dir.mkdirs()
            val o = JSONObject().put("version", 1).put("checked", checked)
                .put("quakes", JSONArray(quakes.take(2000).map { it.toJson() })).put("notified", JSONArray(notified.takeLast(500)))
            val tmp = File(dir, "quakes.json.${Thread.currentThread().id}.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        } catch (e: Exception) { }
    }

    /** Take the new list; a failed source keeps its quakes from before. */
    fun update(fresh: List<Quake>, errors: Map<String, String>, now: Double = nowS()) {
        val lists = listOf("USGS", "GeoNet").associateWith { src -> (if (src in errors) quakes else fresh).filter { it.source == src } }
        quakes = mergeQuakes(lists["USGS"]!!, lists["GeoNet"]!!).filter { now - it.time <= Quakes.PERIODS["week"]!! + 3600 }
        this.errors = errors
        checked = now
    }
}
