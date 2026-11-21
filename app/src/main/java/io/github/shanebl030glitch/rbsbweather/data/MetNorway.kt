// MET Norway (yr.no): the backup provider. Free, no key; asks for an identifying User-Agent.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class MetNorway(private val http: HttpClient = Http.default, private val url: String = URL) {
    companion object {
        const val NAME = "MET Norway"
        const val URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
        const val CREDIT = "Weather data by MET Norway (CC BY 4.0)"
    }

    fun forecast(place: Place, days: Int = 7, now: Instant = Instant.now()): Forecast {
        val u = Http.query(url, mapOf(
            "lat" to String.format(Locale.US, "%.4f", place.latitude),
            "lon" to String.format(Locale.US, "%.4f", place.longitude)))
        return parseMetNorway(http.get(u, emptyMap()), place, days, now).copy(fetchedAt = System.currentTimeMillis())
    }
}

private val LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

private class Step(val t: Instant, val inst: JSONObject, val n1: JSONObject?, val n6: JSONObject?)

private fun JSONObject.symbol(): Int? = WeatherCodes.fromMetSymbol(optJSONObject("summary")?.optString("symbol_code"))
private fun kmh(ms: Double?) = ms?.let { Math.round(it * 3.6 * 10.0) / 10.0 }

fun parseMetNorway(body: String, place: Place, days: Int = 7, now: Instant = Instant.now()): Forecast {
    val o = try { JSONObject(body) } catch (e: JSONException) { throw WeatherException("MET Norway's forecast couldn't be read.") }
    val series = o.optJSONObject("properties")?.optJSONArray("timeseries")
        ?: throw WeatherException("MET Norway's forecast has no data.")
    val steps = mutableListOf<Step>()
    for (i in 0 until series.length()) {
        val item = series.optJSONObject(i) ?: continue
        val t = try { Instant.parse(item.optString("time")) } catch (e: Exception) { continue }
        val d = item.optJSONObject("data") ?: continue
        val inst = d.optJSONObject("instant")?.optJSONObject("details") ?: continue
        if (inst.num("air_temperature") == null) continue
        steps += Step(t, inst, d.optJSONObject("next_1_hours"), d.optJSONObject("next_6_hours"))
    }
    if (steps.isEmpty()) throw WeatherException("MET Norway's forecast is missing temperatures.")
    steps.sortBy { it.t }

    val zone: ZoneId = Sun.zoneFor(place)
    val lat = place.latitude
    val lon = place.longitude
    var curI = 0
    steps.forEachIndexed { i, s -> if (!s.t.isAfter(now)) curI = i }
    val s0 = steps[curI]
    val temp = s0.inst.num("air_temperature")
    val rh = s0.inst.num("relative_humidity")
    val wind = s0.inst.num("wind_speed")
    val localNow = now.atZone(zone)
    val current = Current(
        time = localNow.truncatedTo(ChronoUnit.MINUTES).withMinute(localNow.minute - localNow.minute % 15).format(LOCAL),
        temperature = temp, apparentTemperature = Sun.apparentTemperature(temp, rh, wind), humidity = rh,
        weatherCode = (s0.n1 ?: s0.n6)?.symbol(), isDay = Sun.isDay(now, lat, lon), windSpeed = kmh(wind),
        windDirection = s0.inst.num("wind_from_direction"), windGusts = kmh(s0.inst.num("wind_speed_of_gust")),
        pressure = s0.inst.num("air_pressure_at_sea_level"), cloudCover = s0.inst.num("cloud_area_fraction"),
        precipitation = s0.n1?.optJSONObject("details")?.num("precipitation_amount"))

    // hourly: the steps one hour apart (about the first 2.5 days)
    val hours = mutableListOf<Hour>()
    for (s in steps.drop(curI)) {
        val n1 = s.n1 ?: break
        val det = n1.optJSONObject("details") ?: JSONObject()
        hours += Hour(s.t.atZone(zone).format(LOCAL), s.inst.num("air_temperature"),
            det.num("probability_of_precipitation"), det.num("precipitation_amount"), n1.symbol())
    }

    // daily: everything from now on, grouped by local date
    val first = localNow.toLocalDate()
    val want = (0 until days).map { first.plusDays(it.toLong()) }
    class Agg {
        val temps = mutableListOf<Double>(); val codes = mutableListOf<Int>(); val pp = mutableListOf<Double>()
        val wind = mutableListOf<Double>(); val uv = mutableListOf<Double>(); var rain = 0.0; var hasRain = false
    }
    val agg = want.associateWith { Agg() }
    val later = steps.drop(curI)
    later.forEachIndexed { i, s ->
        val a = agg[s.t.atZone(zone).toLocalDate()] ?: return@forEachIndexed
        val gapH = if (i + 1 < later.size) (later[i + 1].t.epochSecond - s.t.epochSecond) / 3600.0 else 6.0
        val block = if (gapH <= 1.01 && s.n1 != null) s.n1 else (s.n6 ?: s.n1)
        s.inst.num("air_temperature")?.let { a.temps += it }
        s.inst.num("wind_speed")?.let { a.wind += it }
        s.inst.num("ultraviolet_index_clear_sky")?.let { a.uv += it }
        if (s.n6 != null && gapH > 1.01) {
            val d6 = s.n6.optJSONObject("details")
            d6?.num("air_temperature_max")?.let { a.temps += it }
            d6?.num("air_temperature_min")?.let { a.temps += it }
        }
        if (block != null) {
            val det = block.optJSONObject("details")
            block.symbol()?.let { a.codes += it }
            det?.num("probability_of_precipitation")?.let { a.pp += it }
            det?.num("precipitation_amount")?.let { a.rain += it; a.hasRain = true }
        }
    }
    val daysOut = mutableListOf<Day>()
    for (day in want) {
        val a = agg.getValue(day)
        if (a.temps.isEmpty()) break                      // past the end of MET's forecast
        val (rise, set) = Sun.riseSet(day, lat, lon, zone)
        daysOut += Day(day.toString(), a.codes.maxOrNull(), a.temps.max(), a.temps.min(),
            if (a.hasRain) Math.round(a.rain * 10.0) / 10.0 else null, a.pp.maxOrNull(),
            kmh(a.wind.maxOrNull()), rise?.format(LOCAL), set?.format(LOCAL), a.uv.maxOrNull())
    }
    val offset = zone.rules.getOffset(now).totalSeconds
    return Forecast(place, if (place.timezone.isBlank()) "auto" else place.timezone, offset, current, hours, daysOut,
        MetNorway.NAME)
}

