// Weather warnings, ported from the desktop app's alerts.py: official warnings straight from the
// national weather service that issued them, matched to each saved place by the warning's own
// area, and the user's own limits worked out from the forecast.
//   New Zealand – MetService's public CAP feed (CC BY 4.0)
//   United States – the National Weather Service (api.weather.gov), asked by point
//   Norway – MET Norway's MetAlerts, asked by point
//   Most of Europe – MeteoAlarm's per-country feeds (warnings with an area polygon)
// Warnings are shown as issued: their wording and level are never changed. Own-limit alerts are
// clearly marked as not official. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Alerts {
    const val METSERVICE_URL = "https://alerts.metservice.com/cap/rss"
    const val NWS_URL = "https://api.weather.gov/alerts/active"
    const val METNO_URL = "https://api.met.no/weatherapi/metalerts/2.0/current.json"
    const val METEOALARM_URL = "https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-{slug}"
    const val MAX_CAP_DOCS = 60
    val LEVELS = listOf("red", "orange", "yellow", "watch", "minor")
    val RANK = mapOf("red" to 0, "orange" to 1, "yellow" to 2, "watch" to 2, "minor" to 3, "limit" to 4)
    val BADGE = mapOf("red" to "RED", "orange" to "ORANGE", "yellow" to "YELLOW", "watch" to "WATCH", "minor" to "ADVISORY", "limit" to "MY LIMIT")
    /** background, text (contrast at least 4.5:1). */
    val COLOURS = mapOf("red" to (0xFFB91C1C to 0xFFFFFFFF), "orange" to (0xFFF28C28 to 0xFF1F1300),
        "yellow" to (0xFFFACC15 to 0xFF2A2100), "watch" to (0xFFFACC15 to 0xFF2A2100),
        "minor" to (0xFFDBE4F0 to 0xFF1C2C44), "limit" to (0xFFE6EBF2 to 0xFF1C2C44))
    val MIN_LEVEL_CHOICES = listOf("all", "watch", "orange", "red")
    val INTERVAL_CHOICES = listOf(15, 30, 60)
    const val CREDIT = "Weather warnings from MetService (CC BY 4.0), the US National Weather Service (public domain), " +
        "MET Norway (CC BY 4.0) and MeteoAlarm (EUMETNET and national weather services; UK © Crown copyright, Met Office)"
}

class AlertException(message: String) : Exception(message)

data class Alert(
    val id: String,
    val source: String,
    val level: String,                 // red | orange | yellow | watch | minor | limit
    val event: String,
    val headline: String = "",
    val description: String = "",
    val instruction: String = "",
    val area: String = "",
    val onset: Double? = null,         // unix seconds
    val expires: Double? = null,
    val issued: Double? = null,
    val url: String = "",
    val official: Boolean = true,
    val hazard: String = "other",
) {
    val badge: String get() = Alerts.BADGE[level] ?: "ALERT"
    val rank: Int get() = Alerts.RANK[level] ?: 5

    /** "Orange Heavy Rain Warning", "Strong Wind Watch", "My limit: rain over 50 mm". */
    fun title(): String {
        if (!official) return event
        val ev = event.trim()
        if (level in listOf("red", "orange", "yellow") && !ev.lowercase().startsWith(level)) return level.replaceFirstChar { it.uppercase() } + " " + ev
        return ev
    }

    fun active(now: Double = nowS()): Boolean = expires == null || expires > now
    fun inForce(now: Double = nowS()): Boolean = active(now) && (onset == null || onset <= now)

    /** Changes when the warning is upgraded or reissued (a reissue has a new issue time). */
    fun signature(): String = "$level|${(issued ?: expires ?: 0.0).toLong()}"

    fun toJson(): JSONObject = JSONObject().put("id", id).put("source", source).put("level", level).put("event", event)
        .put("headline", headline).put("description", description).put("instruction", instruction).put("area", area)
        .putN("onset", onset).putN("expires", expires).putN("issued", issued).put("url", url).put("official", official).put("hazard", hazard)

    companion object {
        fun fromJson(d: JSONObject): Alert {
            val level = d.optString("level").takeIf { it in Alerts.RANK } ?: "minor"
            return Alert(d.getString("id").take(300), d.optString("source").take(80), level, d.optString("event").take(200),
                d.optString("headline").take(500), d.optString("description").take(6000), d.optString("instruction").take(4000),
                d.optString("area").take(1000), d.num("onset"), d.num("expires"), d.num("issued"), safeUrl(d.optString("url")),
                d.optBoolean("official", true), d.optString("hazard", "other").take(20))
        }
    }
}

internal fun nowS(): Double = System.currentTimeMillis() / 1000.0

fun sortAlerts(alerts: Iterable<Alert>): List<Alert> = alerts.sortedWith(compareBy({ it.rank }, { it.onset ?: 0.0 }, { it.title() }))

/** The alerts to show: active, at or above the chosen level, not muted. Own limits always show. */
fun shownAlerts(alerts: Iterable<Alert>, minLevel: String, muted: Collection<String> = emptySet(), now: Double = nowS()): List<Alert> {
    val cut = mapOf("all" to 3, "watch" to 2, "orange" to 1, "red" to 0)[minLevel] ?: 2
    return sortAlerts(alerts.filter { it.active(now) && it.id !in muted && (!it.official || it.rank <= cut) })
}

fun worstAlert(alerts: Iterable<Alert>): Alert? = sortAlerts(alerts).firstOrNull()

private val HAZARDS = listOf("thunder" to "thunder", "lightning" to "thunder", "tornado" to "wind", "hurricane" to "wind",
    "cyclone" to "wind", "snow" to "snow", "winter" to "snow", "blizzard" to "snow", "ice" to "snow", "frost" to "cold",
    "freez" to "cold", "cold" to "cold", "chill" to "cold", "heat" to "heat", "high temperature" to "heat",
    "flood" to "flood", "rain" to "rain", "wind" to "wind", "gale" to "wind", "storm" to "wind",
    "fog" to "fog", "fire" to "fire", "swell" to "wind", "surf" to "wind")

fun hazardOf(vararg texts: String?): String {
    val t = texts.filterNotNull().filter { it.isNotEmpty() }.joinToString(" ").lowercase()
    for ((w, hz) in HAZARDS) if (w in t) return hz
    return "other"
}

fun safeUrl(u: String?): String {
    val s = (u ?: "").trim()
    return if (s.startsWith("https://") || s.startsWith("http://")) s.take(500) else ""
}

private val RFC822 = DateTimeFormatter.RFC_1123_DATE_TIME

/** ISO 8601 (with offset or Z) or RFC 822 → unix seconds; null if missing or invalid. */
fun parseAlertTime(s: String?): Double? {
    if (s.isNullOrBlank()) return null
    val t = s.trim()
    try { return OffsetDateTime.parse(t.replace("Z", "+00:00")).toEpochSecond().toDouble() + OffsetDateTime.parse(t.replace("Z", "+00:00")).nano / 1e9 } catch (e: Exception) { }
    try { return java.time.LocalDateTime.parse(t).toEpochSecond(ZoneOffset.UTC).toDouble() } catch (e: Exception) { }
    try { return ZonedDateTime.parse(t, RFC822).toEpochSecond().toDouble() } catch (e: Exception) { }
    try { return ZonedDateTime.parse(t, DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH)).toEpochSecond().toDouble() } catch (e: Exception) { }
    return null
}

fun cleanText(text: String?, limit: Int = 6000): String {
    if (text.isNullOrEmpty()) return ""
    var t = text.replace(Regex("<[^>]{0,200}>"), " ")
    t = t.replace(Regex("[ \\t\\r\\u000c\\u000b]+"), " ")
    t = t.replace(Regex("\\n\\s*\\n\\s*\\n+"), "\n\n")
    return t.trim().take(limit)
}

// ------------------------------------------------------------------ geometry
fun pointInRing(lat: Double, lonIn: Double, ringIn: List<Pair<Double, Double>>): Boolean {
    if (ringIn.size < 3) return false
    var ring = ringIn; var lon = lonIn
    val lons = ring.map { it.second }
    if (lons.max() - lons.min() > 180) {
        ring = ring.map { (a, b) -> a to (if (b < 0) b + 360 else b) }
        if (lon < 0) lon += 360
    }
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val (yi, xi) = ring[i]; val (yj, xj) = ring[j]
        if ((yi > lat) != (yj > lat)) {
            val x = xi + (lat - yi) * (xj - xi) / (yj - yi)
            if (lon < x) inside = !inside
        }
        j = i
    }
    return inside
}

/** CAP polygon "lat,lon lat,lon …" → [(lat, lon)] (empty if invalid). */
fun parseCapPolygon(text: String?): List<Pair<Double, Double>> {
    val out = mutableListOf<Pair<Double, Double>>()
    for (pair in (text ?: "").trim().split(Regex("\\s+"))) {
        if (pair.isEmpty()) continue
        val parts = pair.split(",")
        if (parts.size < 2) return emptyList()
        val la = parts[0].toDoubleOrNull() ?: return emptyList()
        val lo = parts[1].toDoubleOrNull() ?: return emptyList()
        if (la !in -90.0..90.0 || lo !in -180.0..180.0) return emptyList()
        out += la to lo
        if (out.size > 20000) return emptyList()
    }
    return out
}

fun kmBetween(a1: Double, o1: Double, a2: Double, o2: Double): Double {
    val p1 = Math.toRadians(a1); val p2 = Math.toRadians(a2)
    val dp = p2 - p1; val dl = Math.toRadians(o2 - o1)
    val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 6371.0 * 2 * asin(min(1.0, sqrt(h)))
}

/** CAP circle "lat,lon radius_km". */
fun inCircle(lat: Double, lon: Double, text: String): Boolean {
    val parts = text.trim().split(Regex("\\s+"))
    if (parts.size != 2) return false
    val c = parts[0].split(",")
    val la = c.getOrNull(0)?.toDoubleOrNull() ?: return false
    val lo = c.getOrNull(1)?.toDoubleOrNull() ?: return false
    val r = parts[1].toDoubleOrNull() ?: return false
    return kmBetween(lat, lon, la, lo) <= r
}

/** True/false for a GeoJSON Polygon/MultiPolygon; null when there's no usable geometry. */
fun geojsonContains(geom: Any?, lat: Double, lon: Double): Boolean? {
    if (geom !is JSONObject) return null
    val coords = geom.optJSONArray("coordinates") ?: return null
    val polys = when (geom.optString("type")) {
        "Polygon" -> listOf(coords)
        "MultiPolygon" -> (0 until coords.length()).mapNotNull { coords.optJSONArray(it) }
        else -> return null
    }
    try {
        for (poly in polys) {
            val rings = (0 until poly.length()).map { r ->
                val ring = poly.getJSONArray(r)
                (0 until ring.length()).map { i -> val p = ring.getJSONArray(i); p.getDouble(1) to p.getDouble(0) }
            }
            if (rings.isNotEmpty() && pointInRing(lat, lon, rings[0]) && rings.drop(1).none { pointInRing(lat, lon, it) }) return true
        }
    } catch (e: Exception) { return null }
    return false
}

sealed interface Area
data class PolygonArea(val ring: List<Pair<Double, Double>>) : Area
data class CircleArea(val text: String) : Area

/** Does any area include the point? null when the warning has no polygon or circle. */
fun covers(areas: List<Area>, lat: Double, lon: Double): Boolean? {
    if (areas.isEmpty()) return null
    return areas.any { (it is PolygonArea && pointInRing(lat, lon, it.ring)) || (it is CircleArea && inCircle(lat, lon, it.text)) }
}

// ------------------------------------------------------------------- CAP XML
/** Parses XML safely: documents with a DOCTYPE or entities are refused (as on the desktop). */
fun parseXml(data: ByteArray): Element {
    val head = String(data, 0, min(4096, data.size), Charsets.UTF_8).lowercase()
    if ("<!doctype" in head || "<!entity" in String(data, Charsets.UTF_8).lowercase()) {
        throw AlertException("the warning feed contained something unexpected, so it was ignored")
    }
    val f = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (e: Exception) { }
    }
    return try { f.newDocumentBuilder().parse(ByteArrayInputStream(data)).documentElement }
    catch (e: Exception) { throw AlertException("the warning feed couldn't be read (${e.javaClass.simpleName})") }
}

private fun Node.local(): String = (localName ?: nodeName ?: "").substringAfter(':')
private fun Element.kids(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
private fun Element.child(name: String): Element? = kids().firstOrNull { it.local() == name }
private fun Element.children(name: String): List<Element> = kids().filter { it.local() == name }
private fun Element?.text(name: String): String = this?.child(name)?.textContent?.trim() ?: ""
private fun Element.descendants(): List<Element> {
    val out = mutableListOf<Element>()
    fun walk(e: Element) { out += e; e.kids().forEach { walk(it) } }
    walk(this); return out
}

private fun colourLevel(vararg texts: String?): String? {
    for (t in texts) {
        val s = (t ?: "").lowercase()
        for (lvl in listOf("red", "orange", "yellow")) if (Regex("\\b$lvl\\b").containsMatchIn(s)) return lvl
    }
    return null
}

/** The display level: a service's own colour first, then CAP severity. */
fun levelFrom(severity: String?, event: String = "", colour: String = "", headline: String = "", trustSeverity: Boolean = true): String {
    val ev = "$event $headline".lowercase()
    var lvl = colourLevel(colour)
    if (lvl == null && !trustSeverity) lvl = colourLevel(headline, event)
    if (lvl == null) lvl = mapOf("extreme" to "red", "severe" to "orange", "moderate" to "yellow", "minor" to "minor")[(severity ?: "").trim().lowercase()]
    if (lvl == null) lvl = colourLevel(headline, event) ?: "minor"
    if ("watch" in ev && (lvl == "yellow" || lvl == "minor")) return "watch"
    if (lvl == "minor" && "warning" in ev) return "yellow"
    return lvl
}

private fun pickInfo(alert: Element): Element? {
    val infos = alert.children("info")
    if (infos.isEmpty()) return null
    return infos.firstOrNull { val l = it.text("language").lowercase(); l.isEmpty() || l.startsWith("en") } ?: infos[0]
}

/** One CAP <alert> → (Alert, areas); null for tests, exercises, cancellations and non-public alerts. */
fun capAlert(root: Element, source: String, url: String = "", colourParam: String = "", trustSeverity: Boolean = true,
             feedId: String = ""): Pair<Alert, List<Area>>? {
    if (root.local() != "alert") return null
    val status = root.text("status").lowercase(); val msgType = root.text("msgType").lowercase()
    if ((status.isNotEmpty() && status != "actual") || msgType == "cancel") return null
    val info = pickInfo(root) ?: return null
    val params = info.children("parameter").associate { it.text("valueName").lowercase() to it.text("value") }.filterKeys { it.isNotEmpty() }
    val event = info.text("event"); val headline = info.text("headline")
    var colour = if (colourParam.isNotEmpty()) params[colourParam.lowercase()] ?: "" else ""
    if (colour.isEmpty()) colour = params["awareness_level"] ?: params["colourcode"] ?: params["colorcode"] ?: ""
    val level = levelFrom(info.text("severity"), event, colour, headline, trustSeverity)
    val areas = mutableListOf<Area>(); val descs = mutableListOf<String>()
    for (area in info.children("area")) {
        area.text("areaDesc").takeIf { it.isNotEmpty() }?.let { descs += it }
        for (p in area.children("polygon")) parseCapPolygon(p.textContent).takeIf { it.isNotEmpty() }?.let { areas += PolygonArea(it) }
        for (c in area.children("circle")) c.textContent?.trim()?.takeIf { it.isNotEmpty() }?.let { areas += CircleArea(it) }
    }
    var title = if (headline.isNotEmpty() && headline.length < 90) headline else event
    title = title.replace(Regex("\\s*[-–]\\s*(red|orange|yellow)\\s*$", RegexOption.IGNORE_CASE), "").ifEmpty { event }
    val alert = Alert(
        id = (feedId.ifEmpty { root.text("identifier") }.ifEmpty { url }.ifEmpty { headline }).take(300),
        source = source, level = level, event = cleanText(title, 200).ifEmpty { "Weather warning" },
        headline = cleanText(headline, 500), description = cleanText(info.text("description")),
        instruction = cleanText(info.text("instruction"), 4000), area = cleanText(descs.joinToString("; "), 1000),
        onset = parseAlertTime(info.text("onset").ifEmpty { info.text("effective") }),
        expires = parseAlertTime(info.text("expires")), issued = parseAlertTime(root.text("sent")),
        url = safeUrl(info.text("web").ifEmpty { url }), official = true,
        hazard = hazardOf(event, headline, params["awareness_type"]))
    return alert to areas
}

data class FeedEntry(val id: String, val link: String, val updated: String, val el: Element)

/** RSS <item>s or Atom <entry>s. */
fun feedEntries(root: Element): List<FeedEntry> = root.descendants().filter { it.local() == "item" || it.local() == "entry" }.map { el ->
    var link = ""
    for (l in el.children("link")) {
        val href = l.getAttribute("href").ifEmpty { l.textContent?.trim() ?: "" }
        val typ = l.getAttribute("type").lowercase()
        if (href.isNotEmpty() && (link.isEmpty() || "cap" in typ || "xml" in typ)) link = href
    }
    val guid = el.text("guid").ifEmpty { el.text("id") }
    if (link.isEmpty() && guid.startsWith("http")) link = guid
    FeedEntry(guid.ifEmpty { link }, safeUrl(link), el.text("updated").ifEmpty { el.text("pubDate") }.ifEmpty { el.text("published") }, el)
}

/** MeteoAlarm-style Atom entries with the CAP fields inline (cap:event, cap:polygon, …). */
fun inlineCap(entry: Element, source: String, feedId: String): Pair<Alert, List<Area>>? {
    val all = entry.descendants()
    fun t(name: String) = all.firstOrNull { it.local() == name && !it.textContent.isNullOrBlank() }?.textContent?.trim() ?: ""
    if (t("event").isEmpty() && t("severity").isEmpty()) return null
    val status = t("status").lowercase(); val msgType = t("message_type").lowercase().ifEmpty { t("msgType").lowercase() }
    if ((status.isNotEmpty() && status != "actual") || msgType == "cancel") return null
    val areas = mutableListOf<Area>()
    for (c in all) {
        if (c.local() == "polygon" && !c.textContent.isNullOrBlank()) parseCapPolygon(c.textContent).takeIf { it.isNotEmpty() }?.let { areas += PolygonArea(it) }
        else if (c.local() == "circle" && !c.textContent.isNullOrBlank()) areas += CircleArea(c.textContent.trim())
    }
    val title = entry.text("title"); val event = t("event")
    val level = levelFrom(t("severity"), event, "", title, true)
    val link = entry.children("link").map { it.getAttribute("href") }.firstOrNull { it.isNotEmpty() } ?: ""
    val ident = t("identifier")
    val alert = Alert(id = (ident.ifEmpty { feedId }).take(300) + (if (ident.isNotEmpty() && feedId.isNotEmpty()) "#$feedId" else ""),
        source = source, level = level, event = cleanText(event.ifEmpty { title }, 200), headline = cleanText(title, 500),
        description = cleanText(t("description").ifEmpty { entry.text("summary") }), instruction = cleanText(t("instruction"), 4000),
        area = cleanText(t("areaDesc"), 1000), onset = parseAlertTime(t("onset").ifEmpty { t("effective") }),
        expires = parseAlertTime(t("expires")), issued = parseAlertTime(t("sent").ifEmpty { entry.text("updated") }),
        url = safeUrl(link), official = true, hazard = hazardOf(event, title))
    return alert to areas
}

// ------------------------------------------------------------------- sources
val NZ_NAMES = setOf("new zealand", "aotearoa", "nz")
val US_NAMES = setOf("united states", "united states of america", "usa", "us", "puerto rico", "guam",
    "u.s. virgin islands", "american samoa", "northern mariana islands")
val NO_NAMES = setOf("norway", "norge", "svalbard and jan mayen", "svalbard")
val METEOALARM = mapOf(
    "austria" to listOf("austria"), "belgium" to listOf("belgium"), "bosnia and herzegovina" to listOf("bosnia-herzegovina", "bosnia-and-herzegovina"),
    "bulgaria" to listOf("bulgaria"), "croatia" to listOf("croatia"), "cyprus" to listOf("cyprus"),
    "czechia" to listOf("czechia", "czech-republic"), "czech republic" to listOf("czechia", "czech-republic"),
    "denmark" to listOf("denmark"), "estonia" to listOf("estonia"), "finland" to listOf("finland"), "france" to listOf("france"),
    "germany" to listOf("germany"), "greece" to listOf("greece"), "hungary" to listOf("hungary"), "iceland" to listOf("iceland"),
    "ireland" to listOf("ireland"), "israel" to listOf("israel"), "italy" to listOf("italy"), "latvia" to listOf("latvia"),
    "lithuania" to listOf("lithuania"), "luxembourg" to listOf("luxembourg"), "malta" to listOf("malta"),
    "moldova" to listOf("moldova", "republic-of-moldova"), "montenegro" to listOf("montenegro"),
    "netherlands" to listOf("netherlands"), "the netherlands" to listOf("netherlands"),
    "north macedonia" to listOf("republic-of-north-macedonia", "north-macedonia"),
    "poland" to listOf("poland"), "portugal" to listOf("portugal"), "romania" to listOf("romania"), "serbia" to listOf("serbia"),
    "slovakia" to listOf("slovakia"), "slovenia" to listOf("slovenia"), "spain" to listOf("spain"), "sweden" to listOf("sweden"),
    "switzerland" to listOf("switzerland"), "united kingdom" to listOf("united-kingdom"), "uk" to listOf("united-kingdom"),
    "ukraine" to listOf("ukraine"), "andorra" to listOf("andorra"))
val SOURCE_NAMES = mapOf("metservice" to "MetService", "nws" to "US National Weather Service", "metno" to "MET Norway", "meteoalarm" to "MeteoAlarm")
val SOURCE_SITES = mapOf("metservice" to "https://www.metservice.com/warnings/home", "nws" to "https://www.weather.gov/",
    "metno" to "https://www.met.no/en", "meteoalarm" to "https://meteoalarm.org/")

private fun Place.inBox(la0: Double, la1: Double, lo0: Double, lo1: Double) = latitude in la0..la1 && longitude in lo0..lo1

/** Which official source covers a place: metservice | nws | metno | meteoalarm | null. */
fun alertSourceFor(p: Place): String? {
    val c = p.country.trim().lowercase()
    if (c in NZ_NAMES || (c.isEmpty() && (p.inBox(-48.0, -33.0, 165.0, 180.0) || p.inBox(-45.0, -43.0, -177.0, -175.0)))) return "metservice"
    if (c in US_NAMES) return "nws"
    if (c in NO_NAMES) return "metno"
    if (c in METEOALARM) return "meteoalarm"
    return null
}

fun coverageText(p: Place): String = alertSourceFor(p)?.let { SOURCE_NAMES[it] } ?: "Not covered yet"

/** Downloads official warnings. Each source is tried on its own: one failing doesn't stop the others. */
class AlertClient(
    private val http: BytesClient,
    private val metserviceUrl: String = Alerts.METSERVICE_URL,
    private val nwsUrl: String = Alerts.NWS_URL,
    private val metnoUrl: String = Alerts.METNO_URL,
    private val meteoalarmUrl: String = Alerts.METEOALARM_URL,
) {
    private val capCache = mutableMapOf<String, Pair<String, Pair<Alert, List<Area>>?>>()
    private val maSlug = mutableMapOf<String, String>()

    private fun get(url: String, accept: String, limit: Int = 8 * 1024 * 1024): ByteArray {
        if (!url.startsWith("https://") && !url.startsWith("http://")) throw AlertException("bad address")
        return try { http.get(url, mapOf("Accept" to accept), limit) } catch (e: WeatherException) {
            throw AlertException(e.status?.let { "HTTP $it" } ?: (e.message ?: "couldn't connect"))
        }
    }

    /** Warnings for every place: ({place key: alerts}, {source: error}); a place whose source failed is missing. */
    fun fetchAll(places: List<Place>): Pair<Map<String, List<Alert>>, Map<String, String>> {
        val bySource = places.groupBy { alertSourceFor(it) }.filterKeys { it != null }
        val out = mutableMapOf<String, List<Alert>>(); val errors = mutableMapOf<String, String>()
        for ((sid, locs) in bySource) {
            try {
                when (sid) {
                    "metservice" -> out += metservice(locs)
                    "nws" -> out += nws(locs, errors)
                    "metno" -> out += metno(locs, errors)
                    "meteoalarm" -> out += meteoalarm(locs, errors)
                }
            } catch (e: AlertException) {
                errors[sid!!] = e.message ?: "failed"
            } catch (e: Exception) {
                errors[sid!!] = "unexpected problem (${e.javaClass.simpleName})"
            }
        }
        return out to errors
    }

    private fun capFeed(url: String, source: String, colourParam: String = "", trustSeverity: Boolean = true): List<Pair<Alert, List<Area>>> {
        val root = parseXml(get(url, "application/rss+xml, application/atom+xml, application/xml"))
        val entries = feedEntries(root)
        val got = mutableListOf<Pair<Alert, List<Area>>>(); val seen = mutableSetOf<String>()
        for (e in entries.take(Alerts.MAX_CAP_DOCS * 3)) {
            val inl = inlineCap(e.el, source, e.id)
            if (inl != null) { got += inl; continue }
            val link = e.link
            if (link.isEmpty() || link in seen) continue
            seen += link
            if (seen.size > Alerts.MAX_CAP_DOCS) break
            val cached = capCache[link]
            val parsed: Pair<Alert, List<Area>>?
            if (cached != null && cached.first == e.updated) parsed = cached.second
            else {
                val doc = try { parseXml(get(link, "application/cap+xml, application/xml", 1024 * 1024)) } catch (err: AlertException) { continue }
                val hasId = doc.descendants().any { it.local() == "identifier" }
                parsed = capAlert(doc, source, link, colourParam, trustSeverity, if (!hasId) e.id else "")
                capCache[link] = e.updated to parsed
            }
            parsed?.let { got += it }
        }
        val live = entries.map { it.link }.toSet()
        capCache.keys.retainAll(live)
        return got
    }

    private fun match(parsed: List<Pair<Alert, List<Area>>>, locs: List<Place>): Map<String, List<Alert>> =
        locs.associate { p -> p.alertKey to parsed.filter { (_, areas) -> covers(areas, p.latitude, p.longitude) == true }.map { it.first } }

    private fun metservice(locs: List<Place>) =
        match(capFeed(metserviceUrl, "MetService", colourParam = "ColourCode", trustSeverity = false), locs)

    private fun meteoalarm(locs: List<Place>, errors: MutableMap<String, String>): Map<String, List<Alert>> {
        val out = mutableMapOf<String, List<Alert>>()
        for ((country, cl) in locs.groupBy { it.country.trim().lowercase() }) {
            val slugs = maSlug[country]?.let { listOf(it) } ?: METEOALARM[country] ?: emptyList()
            var last: AlertException? = null
            for (slug in slugs) {
                val parsed = try { capFeed(meteoalarmUrl.replace("{slug}", slug), "MeteoAlarm") } catch (e: AlertException) { last = e; continue }
                maSlug[country] = slug
                out += match(parsed, cl)
                last = null
                break
            }
            if (last != null) errors["meteoalarm"] = "${country.replaceFirstChar { it.uppercase() }}: ${last.message}"
        }
        return out
    }

    private fun nws(locs: List<Place>, errors: MutableMap<String, String>): Map<String, List<Alert>> {
        val out = mutableMapOf<String, List<Alert>>()
        for (p in locs) {
            val url = "$nwsUrl?point=" + String.format(Locale.US, "%.4f,%.4f", p.latitude, p.longitude)
            try { out[p.alertKey] = parseNws(JSONObject(String(get(url, "application/geo+json")))) }
            catch (e: Exception) { errors["nws"] = (e as? AlertException)?.message ?: "the reply couldn't be read" }
        }
        return out
    }

    private fun metno(locs: List<Place>, errors: MutableMap<String, String>): Map<String, List<Alert>> {
        val out = mutableMapOf<String, List<Alert>>()
        for (p in locs) {
            val url = "$metnoUrl?" + String.format(Locale.US, "lat=%.4f&lon=%.4f&lang=en", p.latitude, p.longitude)
            try { out[p.alertKey] = parseMetno(JSONObject(String(get(url, "application/json"))), p.latitude, p.longitude) }
            catch (e: Exception) { errors["metno"] = (e as? AlertException)?.message ?: "the reply couldn't be read" }
        }
        return out
    }
}

private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k, "")

/** api.weather.gov /alerts/active?point= GeoJSON (the service already matched the point). */
fun parseNws(data: JSONObject): List<Alert> {
    val feats = data.optJSONArray("features") ?: return emptyList()
    val out = mutableListOf<Alert>()
    for (i in 0 until feats.length()) {
        val f = feats.optJSONObject(i) ?: continue
        val p = f.optJSONObject("properties") ?: continue
        if (p.s("status").ifEmpty { "Actual" }.lowercase() != "actual" || p.s("messageType").lowercase() == "cancel") continue
        val event = p.s("event"); val headline = p.s("headline")
        out += Alert(id = p.s("id").ifEmpty { f.s("id") }.ifEmpty { headline }.take(300), source = "US National Weather Service",
            level = levelFrom(p.s("severity"), event, "", headline), event = cleanText(event, 200).ifEmpty { "Weather alert" },
            headline = cleanText(headline, 500), description = cleanText(p.s("description")), instruction = cleanText(p.s("instruction"), 4000),
            area = cleanText(p.s("areaDesc"), 1000), onset = parseAlertTime(p.s("onset").ifEmpty { p.s("effective") }),
            expires = parseAlertTime(p.s("ends").ifEmpty { p.s("expires") }), issued = parseAlertTime(p.s("sent")),
            url = safeUrl(p.s("@id").ifEmpty { f.s("id") }), official = true, hazard = hazardOf(event))
    }
    return out
}

/** MET Norway MetAlerts current.json (asked by point; the polygon is checked too). */
fun parseMetno(data: JSONObject, lat: Double, lon: Double): List<Alert> {
    val feats = data.optJSONArray("features") ?: return emptyList()
    val out = mutableListOf<Alert>()
    for (i in 0 until feats.length()) {
        val f = feats.optJSONObject(i) ?: continue
        val p = f.optJSONObject("properties") ?: continue
        if (p.s("status").ifEmpty { "Actual" }.lowercase() != "actual") continue
        if (geojsonContains(f.opt("geometry"), lat, lon) == false) continue
        val interval = f.optJSONObject("when")?.optJSONArray("interval")
        val colour = p.s("riskMatrixColor").ifEmpty { p.s("awareness_level") }
        val title = p.s("title")
        val event = p.s("eventAwarenessName").ifEmpty { p.s("event") }
        val name = if (event.isNotEmpty()) event.lowercase().replaceFirstChar { it.uppercase() } else "Weather warning"
        out += Alert(id = p.s("id").ifEmpty { title }.take(300), source = "MET Norway", level = levelFrom(p.s("severity"), name, colour, title),
            event = cleanText(name, 200), headline = cleanText(title, 500),
            description = cleanText(listOf("description", "consequences").joinToString(" ") { p.s(it) }),
            instruction = cleanText(p.s("instruction").ifEmpty { p.s("awarenessResponse") }, 4000), area = cleanText(p.s("area"), 1000),
            onset = parseAlertTime(interval?.optString(0)), expires = parseAlertTime(interval?.optString(1)),
            issued = parseAlertTime(data.s("lastChange")), url = safeUrl(p.s("web")), official = true,
            hazard = hazardOf(event, p.s("awareness_type"), title))
    }
    return out
}

// ---------------------------------------------------------------- own limits
data class Limit(val on: Boolean, val value: Double)

val DEFAULT_LIMITS = linkedMapOf("gust" to Limit(true, 90.0), "rain" to Limit(true, 50.0), "heat" to Limit(false, 30.0),
    "frost" to Limit(true, 0.0), "snow" to Limit(false, 0.0), "thunder" to Limit(true, 0.0))
val LIMIT_RANGES = mapOf("gust" to (20.0 to 250.0), "rain" to (1.0 to 500.0), "heat" to (-20.0 to 60.0), "frost" to (-60.0 to 20.0),
    "snow" to (0.0 to 0.0), "thunder" to (0.0 to 0.0))

fun limitsFromJson(o: JSONObject?): Map<String, Limit> {
    val out = LinkedHashMap(DEFAULT_LIMITS)
    o ?: return out
    for (k in DEFAULT_LIMITS.keys) {
        val v = o.optJSONObject(k) ?: continue
        var l = out[k]!!
        if (v.opt("on") is Boolean) l = l.copy(on = v.getBoolean("on"))
        v.num("value")?.let { val (lo, hi) = LIMIT_RANGES[k]!!; l = l.copy(value = it.coerceIn(lo, hi)) }
        out[k] = l
    }
    return out
}

fun limitsToJson(l: Map<String, Limit>): JSONObject = JSONObject().apply { l.forEach { (k, v) -> put(k, JSONObject().put("on", v.on).put("value", v.value)) } }

val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
val THUNDER_CODES = setOf(95, 96, 99)

private fun limitDayName(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
}

/** The user's own limits checked against the forecast: one alert per limit, the first day it's passed. */
fun ownAlerts(fc: Forecast, limits: Map<String, Limit>, units: UnitPrefs = UnitPrefs(), now: Double = nowS()): List<Alert> {
    val tz = ZoneOffset.ofTotalSeconds(fc.utcOffsetSeconds.coerceIn(-64800, 64800))
    val today = Instant.ofEpochMilli((now * 1000).toLong()).atZone(tz).toLocalDate()
    val days = fc.daily.mapNotNull { d ->
        val date = try { LocalDate.parse(d.date.take(10)) } catch (e: Exception) { return@mapNotNull null }
        if (date.isBefore(today)) return@mapNotNull null
        val g = d.gustMax ?: if (date == today) fc.current.windGusts else null
        Triple(date, d, g)
    }
    data class Rule(val key: String, val hazard: String, val test: (Day, Double?, Double) -> Boolean, val title: (Double) -> String, val what: (Day, Double?) -> String)
    val rules = listOf(
        Rule("gust", "wind", { _, g, v -> g != null && g > v }, { "My limit: wind gusts over ${Units.fmtWind(it, units.wind)}" }, { _, g -> "gusts to ${Units.fmtWind(g, units.wind)}" }),
        Rule("rain", "rain", { d, _, v -> d.precipitationSum != null && d.precipitationSum > v }, { "My limit: rain over ${Units.fmtPrecip(it, units.precip)} in a day" }, { d, _ -> "${Units.fmtPrecip(d.precipitationSum, units.precip)} of rain" }),
        Rule("heat", "heat", { d, _, v -> d.tempMax != null && d.tempMax > v }, { "My limit: temperature above ${Units.fmtTemp(it, units.temp)}" }, { d, _ -> "a high of ${Units.fmtTemp(d.tempMax, units.temp)}" }),
        Rule("frost", "cold", { d, _, v -> d.tempMin != null && d.tempMin < v }, { "My limit: temperature below ${Units.fmtTemp(it, units.temp)}" }, { d, _ -> "a low of ${Units.fmtTemp(d.tempMin, units.temp)}" }),
        Rule("snow", "snow", { d, _, _ -> d.weatherCode in SNOW_CODES }, { "My limit: snow" }, { _, _ -> "snow" }),
        Rule("thunder", "thunder", { d, _, _ -> d.weatherCode in THUNDER_CODES }, { "My limit: thunderstorms" }, { _, _ -> "thunderstorms" }),
    )
    val out = mutableListOf<Alert>()
    for (r in rules) {
        val lim = limits[r.key] ?: continue
        if (!lim.on) continue
        val hits = days.filter { (_, d, g) -> r.test(d, g, lim.value) }
        if (hits.isEmpty()) continue
        val (date, d, g) = hits[0]
        val start = date.atStartOfDay(tz).toEpochSecond().toDouble()
        val name = limitDayName(date, today)
        var desc = "The forecast shows ${r.what(d, g)} " + (if (date == today) name.lowercase() else "on $name") + "."
        if (hits.size > 1) desc += " Also: ${hits.drop(1).joinToString(", ") { limitDayName(it.first, today) }}."
        out += Alert(id = "limit:${r.key}:${fc.place.key}:$date", source = "My limits", level = "limit", event = r.title(lim.value),
            headline = desc, description = "$desc This comes from RBSB-Weather's forecast and your own limit; it is not an official warning.",
            area = fc.place.displayName, onset = start, expires = start + 86400, issued = null, official = false, hazard = r.hazard)
    }
    return out
}

/** Total forecast rain (mm) in [start, end) from the hourly forecast; null if it can't be worked out. */
fun rainBetween(fc: Forecast?, start: Double?, end: Double?): Double? {
    if (fc == null || fc.hourly.isEmpty() || start == null || end == null) return null
    val tz = ZoneOffset.ofTotalSeconds(fc.utcOffsetSeconds.coerceIn(-64800, 64800))
    var total = 0.0; var n = 0
    for (h in fc.hourly) {
        val ts = try { java.time.LocalDateTime.parse(h.time).toEpochSecond(tz).toDouble() } catch (e: Exception) { continue }
        if (ts >= start && ts < end && h.precipitation != null) { total += h.precipitation; n++ }
    }
    return if (n > 0) Math.round(total * 10) / 10.0 else null
}

/** Alerts that are new, upgraded or changed since last notified (advisories never notify). */
fun alertsToNotify(alerts: Iterable<Alert>, notified: Map<String, String>, minLevel: String, muted: Collection<String> = emptySet()): List<Alert> =
    shownAlerts(alerts, minLevel, muted).filter { it.level != "minor" && notified[it.id] != it.signature() }

// --------------------------------------------------------------------- store
/** alerts.json: the last warnings per place, when they were checked, and which have been notified. */
class AlertStore(private val dir: File) {
    var alerts: MutableMap<String, List<Alert>> = mutableMapOf()
    var notified: MutableMap<String, String> = linkedMapOf()
    var checked = 0.0
    var errors: Map<String, String> = emptyMap()
    private val file get() = File(dir, "alerts.json")

    fun load(now: Double = nowS()): AlertStore {
        val o = try { JSONObject(file.readText()) } catch (e: Exception) { return this }
        o.optJSONObject("alerts")?.let { a ->
            for (k in a.keys()) {
                val lst = a.optJSONArray(k) ?: continue
                alerts[k] = (0 until minOf(100, lst.length())).mapNotNull { i ->
                    try { Alert.fromJson(lst.getJSONObject(i)) } catch (e: Exception) { null }
                }.filter { it.official && it.active(now) }
            }
        }
        o.optJSONObject("notified")?.let { n -> n.keys().asSequence().toList().takeLast(500).forEach { notified[it] = n.optString(it) } }
        checked = o.num("checked") ?: 0.0
        return this
    }

    fun save() {
        try {
            dir.mkdirs()
            val o = JSONObject().put("version", 1).put("checked", checked)
                .put("alerts", JSONObject().apply { alerts.forEach { (k, v) -> put(k, JSONArray(v.filter { it.official }.map { it.toJson() })) } })
                .put("notified", JSONObject().apply { notified.entries.toList().takeLast(500).forEach { put(it.key, it.value) } })
            val tmp = File(dir, "alerts.json.${Thread.currentThread().id}.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        } catch (e: Exception) { }
    }

    /** Replace the warnings of the places that were read; drop places no longer saved. */
    fun update(fresh: Map<String, List<Alert>>, keys: Collection<String>, now: Double = nowS()) {
        for ((k, v) in fresh) if (k in keys) alerts[k] = v
        alerts.keys.retainAll(keys.toSet())
        val live = alerts.values.flatten().filter { it.active(now) }.map { it.id }.toSet()
        notified = LinkedHashMap(notified.filter { it.key in live || it.key.startsWith("limit:") })
    }
}
