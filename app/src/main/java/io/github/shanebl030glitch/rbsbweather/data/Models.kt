// Plain data classes. Values are in metric base units (°C, km/h, mm, hPa); conversion to the
// user's units happens only when shown (Units.kt).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "",
    val admin1: String = "",
    val timezone: String = "auto",
) {
    /** Stable identifier: used to tell places apart and to name cache files. */
    val key: String get() = String.format(Locale.US, "%.4f,%.4f", latitude, longitude)

    val displayName: String
        get() = listOf(name, admin1.takeIf { it.isNotBlank() && it != name }, country.takeIf { it.isNotBlank() })
            .filterNotNull().joinToString(", ")

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("latitude", latitude).put("longitude", longitude)
        .put("country", country).put("admin1", admin1).put("timezone", timezone)

    companion object {
        fun fromJson(o: JSONObject): Place? {
            val lat = o.optDouble("latitude", Double.NaN)
            val lon = o.optDouble("longitude", Double.NaN)
            val name = o.optString("name", "")
            if (name.isBlank() || lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return Place(name.take(100), lat, lon, o.optString("country", "").take(100),
                o.optString("admin1", "").take(100), o.optString("timezone", "auto").ifBlank { "auto" }.take(64))
        }
    }
}

data class Current(
    val time: String,                 // local time at the place, "2026-11-22T10:15"
    val temperature: Double?,
    val apparentTemperature: Double?,
    val humidity: Double?,
    val weatherCode: Int?,
    val isDay: Boolean,
    val windSpeed: Double?,
    val windDirection: Double?,
    val windGusts: Double?,
    val pressure: Double?,
    val cloudCover: Double?,
    val precipitation: Double?,
)

data class Hour(
    val time: String,                 // local, "2026-11-22T13:00"
    val temperature: Double?,
    val precipitationProbability: Double?,
    val precipitation: Double?,
    val weatherCode: Int?,
)

data class Day(
    val date: String,                 // "2026-11-22"
    val weatherCode: Int?,
    val tempMax: Double?,
    val tempMin: Double?,
    val precipitationSum: Double?,
    val precipitationProbabilityMax: Double?,
    val windSpeedMax: Double?,
    val sunrise: String?,
    val sunset: String?,
    val uvIndexMax: Double?,
    val gustMax: Double? = null,       // km/h (Open-Meteo gives it for every day; others only today's current gust)
)

data class Forecast(
    val place: Place,
    val timezone: String,
    val utcOffsetSeconds: Int,
    val current: Current,
    val hourly: List<Hour>,
    val daily: List<Day>,
    val source: String,               // "Open-Meteo" or "MET Norway"
    val fetchedAt: Long = 0L,         // unix ms
    val fromCache: Boolean = false,
) {
    fun toJson(): JSONObject {
        val c = current
        return JSONObject()
            .put("version", 1)
            .put("place", place.toJson())
            .put("timezone", timezone)
            .put("utcOffsetSeconds", utcOffsetSeconds)
            .put("source", source)
            .put("fetchedAt", fetchedAt)
            .put("current", JSONObject()
                .put("time", c.time).putN("temperature", c.temperature)
                .putN("apparentTemperature", c.apparentTemperature).putN("humidity", c.humidity)
                .putI("weatherCode", c.weatherCode).put("isDay", c.isDay).putN("windSpeed", c.windSpeed)
                .putN("windDirection", c.windDirection).putN("windGusts", c.windGusts)
                .putN("pressure", c.pressure).putN("cloudCover", c.cloudCover).putN("precipitation", c.precipitation))
            .put("hourly", JSONArray(hourly.map {
                JSONObject().put("time", it.time).putN("temperature", it.temperature)
                    .putN("pp", it.precipitationProbability).putN("precipitation", it.precipitation)
                    .putI("code", it.weatherCode)
            }))
            .put("daily", JSONArray(daily.map {
                JSONObject().put("date", it.date).putI("code", it.weatherCode).putN("max", it.tempMax)
                    .putN("min", it.tempMin).putN("rain", it.precipitationSum)
                    .putN("pp", it.precipitationProbabilityMax).putN("wind", it.windSpeedMax)
                    .put("sunrise", it.sunrise ?: JSONObject.NULL).put("sunset", it.sunset ?: JSONObject.NULL)
                    .putN("uv", it.uvIndexMax).putN("gust", it.gustMax)
            }))
    }

    companion object {
        /** Reads a saved forecast; null if it's damaged in any way. */
        fun fromJson(o: JSONObject): Forecast? = try {
            val place = Place.fromJson(o.getJSONObject("place")) ?: throw IllegalArgumentException("place")
            val c = o.getJSONObject("current")
            val current = Current(c.getString("time"), c.num("temperature"), c.num("apparentTemperature"),
                c.num("humidity"), c.int("weatherCode"), c.optBoolean("isDay", true), c.num("windSpeed"),
                c.num("windDirection"), c.num("windGusts"), c.num("pressure"), c.num("cloudCover"),
                c.num("precipitation"))
            val h = o.getJSONArray("hourly")
            val hours = (0 until h.length()).map { i ->
                val x = h.getJSONObject(i)
                Hour(x.getString("time"), x.num("temperature"), x.num("pp"), x.num("precipitation"), x.int("code"))
            }
            val d = o.getJSONArray("daily")
            val days = (0 until d.length()).map { i ->
                val x = d.getJSONObject(i)
                Day(x.getString("date"), x.int("code"), x.num("max"), x.num("min"), x.num("rain"), x.num("pp"),
                    x.num("wind"), x.str("sunrise"), x.str("sunset"), x.num("uv"), x.num("gust"))
            }
            Forecast(place, o.optString("timezone", "auto"), o.optInt("utcOffsetSeconds", 0), current, hours, days,
                o.optString("source", "Open-Meteo"), o.optLong("fetchedAt", 0L), fromCache = true)
        } catch (e: Exception) {
            null
        }
    }
}

// ---- small JSON helpers (missing, null, NaN and infinity all read as null)
internal fun JSONObject.num(k: String): Double? {
    if (!has(k) || isNull(k)) return null
    val v = opt(k)
    val d = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    } ?: return null
    return if (d.isFinite()) d else null
}

internal fun JSONObject.int(k: String): Int? = num(k)?.toInt()

internal fun JSONObject.str(k: String): String? =
    if (!has(k) || isNull(k)) null else (opt(k) as? String)

internal fun JSONObject.putN(k: String, v: Double?): JSONObject = put(k, v ?: JSONObject.NULL)
internal fun JSONObject.putI(k: String, v: Int?): JSONObject = put(k, v ?: JSONObject.NULL)

internal fun JSONArray.num(i: Int): Double? {
    if (i < 0 || i >= length() || isNull(i)) return null
    val d = when (val v = opt(i)) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    } ?: return null
    return if (d.isFinite()) d else null
}

internal fun JSONArray.str(i: Int): String? =
    if (i < 0 || i >= length() || isNull(i)) null else (opt(i) as? String)
