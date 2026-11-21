// Open-Meteo: the main forecast provider (free, no key) and place search.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

class OpenMeteo(
    private val http: HttpClient = Http.default,
    private val forecastUrl: String = FORECAST_URL,
    private val geocodeUrl: String = GEOCODE_URL,
) {
    companion object {
        const val NAME = "Open-Meteo"
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search"
        const val CREDIT = "Weather data by Open-Meteo.com (CC BY 4.0)"
        val CURRENT = listOf("temperature_2m", "relative_humidity_2m", "apparent_temperature", "is_day",
            "precipitation", "weather_code", "cloud_cover", "pressure_msl", "wind_speed_10m",
            "wind_direction_10m", "wind_gusts_10m")
        val HOURLY = listOf("temperature_2m", "precipitation_probability", "precipitation", "weather_code")
        val DAILY = listOf("weather_code", "temperature_2m_max", "temperature_2m_min", "precipitation_sum",
            "precipitation_probability_max", "wind_speed_10m_max", "sunrise", "sunset", "uv_index_max")
    }

    fun forecast(place: Place, days: Int = 7): Forecast {
        val url = Http.query(forecastUrl, mapOf(
            "latitude" to String.format(Locale.US, "%.4f", place.latitude),
            "longitude" to String.format(Locale.US, "%.4f", place.longitude),
            "current" to CURRENT.joinToString(","),
            "hourly" to HOURLY.joinToString(","),
            "daily" to DAILY.joinToString(","),
            "timezone" to place.timezone.ifBlank { "auto" },
            "forecast_days" to days.coerceIn(1, 16).toString(),
        ))
        return parseForecast(http.get(url, emptyMap()), place).copy(fetchedAt = System.currentTimeMillis())
    }

    fun search(query: String, count: Int = 10): List<Place> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val url = Http.query(geocodeUrl, mapOf("name" to q, "count" to count.coerceIn(1, 20).toString(),
            "language" to "en", "format" to "json"))
        return parseGeocode(http.get(url, emptyMap()))
    }
}

fun parseForecast(body: String, place: Place): Forecast {
    val o = try { JSONObject(body) } catch (e: JSONException) { throw WeatherException("The forecast couldn't be read.") }
    if (o.optBoolean("error", false)) throw WeatherException("The weather service said: ${o.optString("reason", "an error")}")
    val c = o.optJSONObject("current") ?: throw WeatherException("The forecast has no current weather.")
    val temp = c.num("temperature_2m") ?: throw WeatherException("The forecast is missing the temperature.")
    val current = Current(
        time = c.optString("time", ""), temperature = temp, apparentTemperature = c.num("apparent_temperature"),
        humidity = c.num("relative_humidity_2m"), weatherCode = c.int("weather_code"),
        isDay = (c.num("is_day") ?: 1.0) >= 0.5, windSpeed = c.num("wind_speed_10m"),
        windDirection = c.num("wind_direction_10m"), windGusts = c.num("wind_gusts_10m"),
        pressure = c.num("pressure_msl"), cloudCover = c.num("cloud_cover"), precipitation = c.num("precipitation"))

    val hours = mutableListOf<Hour>()
    o.optJSONObject("hourly")?.let { h ->
        val t = h.optJSONArray("time")
        if (t != null) {
            val temp2 = h.optJSONArray("temperature_2m")
            val pp = h.optJSONArray("precipitation_probability")
            val pr = h.optJSONArray("precipitation")
            val wc = h.optJSONArray("weather_code")
            for (i in 0 until t.length()) {
                val time = t.str(i) ?: continue
                hours += Hour(time, temp2?.num(i), pp?.num(i), pr?.num(i), wc?.num(i)?.toInt())
            }
        }
    }
    val days = mutableListOf<Day>()
    o.optJSONObject("daily")?.let { d ->
        val t = d.optJSONArray("time")
        if (t != null) {
            fun col(k: String) = d.optJSONArray(k)
            val code = col("weather_code"); val mx = col("temperature_2m_max"); val mn = col("temperature_2m_min")
            val rain = col("precipitation_sum"); val pp = col("precipitation_probability_max")
            val wind = col("wind_speed_10m_max"); val rise = col("sunrise"); val set = col("sunset")
            val uv = col("uv_index_max")
            for (i in 0 until t.length()) {
                val date = t.str(i) ?: continue
                days += Day(date, code?.num(i)?.toInt(), mx?.num(i), mn?.num(i), rain?.num(i), pp?.num(i),
                    wind?.num(i), rise?.str(i), set?.str(i), uv?.num(i))
            }
        }
    }
    return Forecast(place, o.optString("timezone", place.timezone).ifBlank { "auto" },
        o.optInt("utc_offset_seconds", 0), current, hours, days, OpenMeteo.NAME)
}

fun parseGeocode(body: String): List<Place> {
    val o = try { JSONObject(body) } catch (e: JSONException) { throw WeatherException("The search results couldn't be read.") }
    val res = o.optJSONArray("results") ?: return emptyList()
    val out = mutableListOf<Place>()
    val seen = mutableSetOf<String>()
    for (i in 0 until res.length()) {
        val r = res.optJSONObject(i) ?: continue
        val lat = r.num("latitude") ?: continue
        val lon = r.num("longitude") ?: continue
        val name = r.optString("name", "").trim()
        if (name.isEmpty() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
        val p = Place(name, lat, lon, r.optString("country", ""), r.optString("admin1", ""),
            r.optString("timezone", "auto").ifBlank { "auto" })
        if (seen.add(p.key)) out += p
    }
    return out
}
