// World weather for the Wind tab (0.6), ported from the desktop's wind.py: NOAA GFS 10 m wind,
// 2 m temperature and precipitation rate on a 1° grid.
//
// NOAA's Global Forecast System (GFS) runs four times a day (00, 06, 12 and 18 UTC) and is free
// and public domain, with no key. Only the fields needed are downloaded (about 400 KB):
//  1. NOMADS, NOAA's "grib filter" service, which cuts those fields out of the run's file;
//  2. if that fails, NOAA's copy on Amazon: the run's index file says where the fields are, and
//     just those bytes are fetched.
// The newest run is normally out about 4 hours after it starts; if not, the one before is used.
// The forecast hour closest to now is shown (every 3 hours), so it's never more than 90 minutes
// off. It's kept on the phone so the tab works offline, and fetched again every 3 hours.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WindException(message: String) : Exception(message)

/** 10 m wind (m/s) on a regular grid: rows north to south, columns east from lon0. */
class WindField(
    val nj: Int, val ni: Int, val lat0: Double, val lon0: Double, val dlat: Double, val dlon: Double,
    val u: FloatArray, val v: FloatArray, val run: Instant, val valid: Instant,
    val temp: FloatArray? = null,         // 2 m air temperature, °C
    val rain: FloatArray? = null,         // precipitation rate, mm/h
) {
    val layers: List<String> get() = listOf("wind") + listOfNotNull("temp".takeIf { temp != null }, "rain".takeIf { rain != null })
    val fhour: Int get() = ((valid.epochSecond - run.epochSecond) / 3600.0).roundToInt()

    private fun interp(a: FloatArray, lat: Double, lon: Double): Double {
        var fy = (lat0 - lat) / dlat
        fy = min(max(fy, 0.0), nj - 1.000001)
        val fx = (((lon - lon0) % 360.0 + 360.0) % 360.0) / dlon
        val j = fy.toInt(); val i = fx.toInt() % ni
        val ty = fy - j; val tx = fx - fx.toInt()
        val i2 = (i + 1) % ni
        val r0 = j * ni; val r1 = (j + 1) * ni
        val top = a[r0 + i] * (1 - tx) + a[r0 + i2] * tx
        val bot = a[r1 + i] * (1 - tx) + a[r1 + i2] * tx
        return top * (1 - ty) + bot * ty
    }

    /** (u, v) in m/s at a point, between the four nearest grid points. */
    fun at(lat: Double, lon: Double) = interp(u, lat, lon) to interp(v, lat, lon)
    fun tempAt(lat: Double, lon: Double): Double? = temp?.let { interp(it, lat, lon) }
    fun rainAt(lat: Double, lon: Double): Double? = rain?.let { max(0.0, interp(it, lat, lon)) }
}

object Wind {
    const val NOMADS_URL = "https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_1p00.pl"
    const val AWS_URL = "https://noaa-gfs-bdp-pds.s3.amazonaws.com"
    const val CREDIT = "Wind: NOAA Global Forecast System (GFS), public domain"
    const val PROJECT_URL = "https://www.ncei.noaa.gov/products/weather-climate-models/global-forecast"
    const val PUBLISH_DELAY_H = 4
    const val MAX_FHOUR = 384
    const val RETRY_S = 20 * 60
    const val MAX_BYTES = 4 * 1024 * 1024
    const val LAYERS_VERSION = 2

    /** Speed (m/s) and the direction it comes FROM (degrees, 0 = north), as forecasts say it. */
    fun speedAndDirection(u: Double, v: Double) = hypot(u, v) to (Math.toDegrees(atan2(-u, -v)) + 360.0) % 360.0

    private fun find(msgs: List<Grib2.Message>, disc: Int, cat: Int, num: Int, levelType: Int, level: Double) =
        msgs.firstOrNull { it.discipline == disc && it.category == cat && it.number == num && it.levelType == levelType && kotlin.math.abs(it.level - level) < 0.01 }

    private fun rows(m: Grib2.Message, ref: Grib2.Message, scale: Double, offset: Double): FloatArray? {
        val g = m.grid; val r = ref.grid
        if (g.ni != r.ni || g.nj != r.nj || kotlin.math.abs(g.la1 - r.la1) > 1e-6 || kotlin.math.abs(g.lo1 - r.lo1) > 1e-6) return null
        val v = m.rowsNorthToSouth()
        return FloatArray(v.size) { val x = v[it]; if (x.isNaN()) 0f else (x * scale + offset).toFloat() }
    }

    fun fieldFromGrib(buf: ByteArray, run: Instant? = null): WindField {
        val msgs = try { Grib2.messages(buf) } catch (e: Grib2Exception) { throw WindException(e.message ?: "The wind file couldn't be read.") }
        val u = find(msgs, 0, 2, 2, 103, 10.0); val v = find(msgs, 0, 2, 3, 103, 10.0)
        if (u == null || v == null) throw WindException("The wind file doesn't contain the 10 m wind.")
        val g = u.grid
        if (g.ni != v.grid.ni || g.nj != v.grid.nj) throw WindException("The two wind fields use different grids.")
        if (g.ni < 4 || g.nj < 3 || g.di <= 0 || g.dj <= 0) throw WindException("The wind grid is too small to use.")
        val ru = rows(u, u, 1.0, 0.0)!!; val rv = rows(v, u, 1.0, 0.0) ?: throw WindException("The two wind fields use different grids.")
        val lat0 = max(g.la1, g.la2)
        val west = if (g.scan and 0x80 == 0) g.lo1 else g.lo2
        val rt = u.referenceTime
        val ref = run ?: ZonedDateTime.of(rt[0], max(1, rt[1]), max(1, rt[2]), rt[3], rt[4], rt[5], 0, ZoneOffset.UTC).toInstant()
        val valid = ref.plusSeconds(((u.forecastHours ?: 0.0) * 3600).roundToInt().toLong())
        val t = find(msgs, 0, 0, 0, 103, 2.0)?.let { rows(it, u, 1.0, -273.15) }        // TMP 2 m, kelvin → °C
        val p = find(msgs, 0, 1, 7, 1, 0.0)?.let { rows(it, u, 3600.0, 0.0) }            // PRATE, mm/s → mm/h
        return WindField(g.nj, g.ni, lat0, ((west % 360.0) + 360.0) % 360.0, g.dj, g.di, ru, rv, ref, valid, t, p)
    }

    /** (run, forecast hour) to try, newest first: each run's forecast closest to `now`. */
    fun runsToTry(now: Instant, count: Int = 3): List<Pair<Instant, Int>> {
        val newest = now.minus(PUBLISH_DELAY_H.toLong(), ChronoUnit.HOURS).atZone(ZoneOffset.UTC)
        val run = newest.truncatedTo(ChronoUnit.HOURS).withHour(newest.hour / 6 * 6).toInstant()
        return (0 until count).map { k ->
            val r = run.minus(6L * k, ChronoUnit.HOURS)
            val hours = (now.epochSecond - r.epochSecond) / 3600.0
            r to (Math.round(hours / 3.0).toInt() * 3).coerceIn(0, MAX_FHOUR)
        }
    }

    private val DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
    private val HOUR = DateTimeFormatter.ofPattern("HH").withZone(ZoneOffset.UTC)

    fun nomadsUrl(run: Instant, fhour: Int, base: String = NOMADS_URL): String = Http.query(base, linkedMapOf(
        "dir" to "/gfs.${DAY.format(run)}/${HOUR.format(run)}/atmos", "file" to "gfs.t${HOUR.format(run)}z.pgrb2.1p00.f%03d".format(fhour),
        "var_UGRD" to "on", "var_VGRD" to "on", "var_TMP" to "on", "var_PRATE" to "on",
        "lev_10_m_above_ground" to "on", "lev_2_m_above_ground" to "on", "lev_surface" to "on"))

    fun awsUrls(run: Instant, fhour: Int, base: String = AWS_URL): Pair<String, String> {
        val path = "$base/gfs.${DAY.format(run)}/${HOUR.format(run)}/atmos/gfs.t${HOUR.format(run)}z.pgrb2.1p00.f%03d".format(fhour)
        return path to "$path.idx"
    }

    private val WANTED = setOf("UGRD" to "10 m above ground", "VGRD" to "10 m above ground", "TMP" to "2 m above ground", "PRATE" to "surface")

    /** Byte ranges of the fields used, from a GFS .idx file ("n:offset:d=…:VAR:level:…"). End null = to the end. */
    fun byteRangesFromIndex(idx: String): List<Pair<Long, Long?>> {
        val rows = idx.lines().mapNotNull { line ->
            val p = line.split(":")
            if (p.size >= 5 && p[1].isNotEmpty() && p[1].all { it.isDigit() }) Triple(p[1].toLong(), p[3], p[4]) else null
        }
        val offsets = rows.map { it.first }.toSortedSet()
        if (rows.none { (it.second == "UGRD" || it.second == "VGRD") && it.third == "10 m above ground" })
            throw WindException("The GFS index doesn't list the 10 m wind.")
        return rows.filter { (it.second to it.third) in WANTED }.map { it.first }.toSortedSet().map { off ->
            off to offsets.firstOrNull { it > off }?.let { it - 1 }
        }
    }

    // ------------------------------------------------------------------ colours
    val SPEED_COLOURS = listOf(0.0 to intArrayOf(52, 64, 158), 2.0 to intArrayOf(46, 106, 196), 4.0 to intArrayOf(38, 152, 196),
        6.0 to intArrayOf(52, 170, 112), 8.0 to intArrayOf(92, 184, 64), 10.0 to intArrayOf(170, 196, 56), 12.5 to intArrayOf(222, 182, 54),
        15.0 to intArrayOf(226, 128, 48), 18.0 to intArrayOf(206, 74, 64), 22.0 to intArrayOf(176, 46, 104), 27.0 to intArrayOf(150, 52, 168),
        34.0 to intArrayOf(226, 176, 236))
    val TEMP_COLOURS = listOf(-40.0 to intArrayOf(150, 110, 200), -30.0 to intArrayOf(110, 90, 190), -20.0 to intArrayOf(70, 90, 190),
        -10.0 to intArrayOf(50, 130, 210), 0.0 to intArrayOf(70, 180, 220), 5.0 to intArrayOf(80, 190, 170), 10.0 to intArrayOf(90, 185, 100),
        15.0 to intArrayOf(160, 200, 70), 20.0 to intArrayOf(225, 205, 70), 25.0 to intArrayOf(235, 160, 60), 30.0 to intArrayOf(225, 105, 55),
        35.0 to intArrayOf(200, 60, 60), 45.0 to intArrayOf(140, 30, 60))
    val RAIN_COLOURS = listOf(0.0 to intArrayOf(60, 120, 200), 0.1 to intArrayOf(80, 150, 230), 0.5 to intArrayOf(50, 110, 230),
        1.0 to intArrayOf(60, 180, 110), 2.0 to intArrayOf(150, 205, 60), 4.0 to intArrayOf(235, 200, 60), 7.0 to intArrayOf(235, 130, 50),
        12.0 to intArrayOf(215, 60, 60), 20.0 to intArrayOf(180, 50, 150), 35.0 to intArrayOf(235, 190, 240))
    const val RAIN_MIN = 0.05
    val SCALES = mapOf("wind" to SPEED_COLOURS, "temp" to TEMP_COLOURS, "rain" to RAIN_COLOURS)

    fun lerpColour(stops: List<Pair<Double, IntArray>>, x: Double): IntArray {
        if (x <= stops[0].first) return stops[0].second
        for (k in 0 until stops.size - 1) {
            val (s0, c0) = stops[k]; val (s1, c1) = stops[k + 1]
            if (x <= s1) { val t = (x - s0) / (s1 - s0); return IntArray(3) { (c0[it] + (c1[it] - c0[it]) * t).roundToInt() } }
        }
        return stops.last().second
    }

    fun colourFor(speed: Double) = lerpColour(SPEED_COLOURS, speed)

    /** 0 (dry) to 255: light rain fades in, so dry areas show the Earth underneath. */
    fun rainAlpha(mmH: Double): Int = if (mmH < RAIN_MIN) 0 else min(255, (90 + 165 * min(1.0, (mmH - RAIN_MIN) / 1.0)).toInt())

    /** One layer's colours as ARGB pixels (equirectangular, west edge at -180°, north up), one per grid point. */
    fun layerPixels(f: WindField, layerIn: String = "wind"): IntArray {
        val layer = if (layerIn in f.layers) layerIn else "wind"
        val stops = SCALES.getValue(layer)
        val shift = (((((-180.0 - f.lon0) % 360.0) + 360.0) % 360.0) / f.dlon).roundToInt() % f.ni
        val grid = when (layer) { "temp" -> f.temp; "rain" -> f.rain; else -> null }
        return IntArray(f.ni * f.nj) { idx ->
            val j = idx / f.ni; val i = idx % f.ni; val k = j * f.ni + (i + shift) % f.ni
            val x = if (grid == null) hypot(f.u[k].toDouble(), f.v[k].toDouble()) else grid[k].toDouble()
            val c = lerpColour(stops, x)
            val a = if (layer == "rain") rainAlpha(x) else 255
            (a shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2]
        }
    }
}

/** Downloads GFS from NOMADS, or NOAA's copy on Amazon. */
class WindClient(private val bytes: BytesClient = Http.bytes, private val nomads: String = Wind.NOMADS_URL, private val aws: String = Wind.AWS_URL) {
    private fun get(url: String, headers: Map<String, String> = emptyMap(), limit: Int = Wind.MAX_BYTES): ByteArray = try {
        bytes.get(url, headers, limit)
    } catch (e: WeatherException) { throw WindException(e.status?.let { "HTTP $it" } ?: (e.message ?: "couldn't connect")) }

    /** GRIB2 bytes of the newest available run's forecast closest to `now`: (data, run, hour, source). */
    fun fetch(now: Instant = Instant.now()): Triple<ByteArray, Pair<Instant, Int>, String> {
        val problems = mutableListOf<String>()
        val fmt = DateTimeFormatter.ofPattern("HH'Z' d MMM").withZone(ZoneOffset.UTC).withLocale(java.util.Locale.ENGLISH)
        for ((run, fh) in Wind.runsToTry(now)) {
            for (source in listOf("NOAA NOMADS", "NOAA on AWS")) {
                try {
                    val data = if (source == "NOAA NOMADS") get(Wind.nomadsUrl(run, fh, nomads)) else fetchAws(run, fh)
                    if (!Grib2.isGrib2(data)) throw WindException("the reply wasn't wind data")
                    return Triple(data, run to fh, source)
                } catch (e: WindException) { problems += "$source ${fmt.format(run)} +$fh h: ${e.message}" }
            }
        }
        throw WindException("The world wind map couldn't be downloaded from NOAA (" + problems.take(2).joinToString("; ") + ").")
    }

    private fun fetchAws(run: Instant, fh: Int): ByteArray {
        val (dataUrl, idxUrl) = Wind.awsUrls(run, fh, aws)
        val idx = String(get(idxUrl, limit = 512 * 1024), Charsets.UTF_8)
        val out = java.io.ByteArrayOutputStream()
        for ((start, end) in Wind.byteRangesFromIndex(idx)) out.write(get(dataUrl, mapOf("Range" to "bytes=$start-" + (end?.toString() ?: ""))))
        return out.toByteArray()
    }
}

/** wind/gfs_wind10m.grib2 + wind/wind.json (run, forecast hour, when fetched, source). */
class WindStore(val dir: File, private val clock: () -> Double = ::nowS) {
    val dataFile get() = File(dir, "gfs_wind10m.grib2")
    private val metaFile get() = File(dir, "wind.json")
    fun meta(): JSONObject = try { JSONObject(metaFile.readText()) } catch (e: Exception) { JSONObject() }
    private fun writeMeta(o: JSONObject) {
        dir.mkdirs(); val t = File(dir, "wind.json.tmp"); t.writeText(o.toString()); if (!t.renameTo(metaFile)) { metaFile.delete(); t.renameTo(metaFile) }
    }

    fun save(data: ByteArray, run: Instant, fh: Int, source: String) {
        dir.mkdirs()
        val t = File(dir, ".wind.tmp"); t.writeBytes(data)
        if (!t.renameTo(dataFile)) { dataFile.delete(); if (!t.renameTo(dataFile)) throw WindException("Couldn't save the wind map.") }
        writeMeta(JSONObject().put("run", run.toString()).put("fhour", fh).put("fetched", clock()).put("source", source).put("layers", Wind.LAYERS_VERSION))
    }

    fun noteFailure() = try { writeMeta(meta().put("failed", clock())) } catch (e: Exception) { }

    fun load(): WindField? = try {
        val run = Instant.parse(meta().getString("run"))
        Wind.fieldFromGrib(dataFile.readBytes(), run)
    } catch (e: Exception) { null }

    /** A new download is due when the forecast hour closest to now changes (every 3 hours), not within 20 minutes of a failure. */
    fun due(now: Instant = Instant.now()): Boolean {
        val m = meta()
        val failed = m.optDouble("failed", Double.NaN)
        if (!failed.isNaN() && clock() - failed in 0.0..Wind.RETRY_S.toDouble()) return false
        val valid = try { Instant.parse(m.getString("run")).plusSeconds(m.getInt("fhour") * 3600L) } catch (e: Exception) { return true }
        if (!dataFile.exists()) return true
        if (m.optInt("layers", 0) != Wind.LAYERS_VERSION) return true
        return kotlin.math.abs(now.epochSecond - valid.epochSecond) > 90 * 60 + 60
    }

    fun source(): String = meta().optString("source", "")

    /** Download if due. Returns (field, note); throws WindException only when there's nothing to show. */
    fun update(client: WindClient = WindClient(), now: Instant = Instant.now()): Pair<WindField?, String> {
        if (!due(now)) return load() to ""
        return try {
            val (data, rf, source) = client.fetch(now)
            val fld = Wind.fieldFromGrib(data, rf.first)          // check it before replacing the saved one
            save(data, rf.first, rf.second, source)
            fld to ""
        } catch (e: WindException) {
            noteFailure()
            val saved = load() ?: throw e
            saved to "A newer forecast couldn't be downloaded, so the saved one is shown."
        }
    }
}
