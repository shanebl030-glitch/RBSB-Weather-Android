// Names 📍 My location from OpenStreetMap (Nominatim) when the phone's own place-name lookup can't
// (many phones' built-in geocoder fails or isn't there). Only asked after the phone has moved, or
// while My location is still unnamed; Nominatim's usage policy (an identifying User-Agent, well
// under 1 request a second) is kept. Data © OpenStreetMap contributors (ODbL).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONObject
import java.util.Locale

class OsmNamer(private val http: HttpClient = Http.default,
               private val base: String = "https://nominatim.openstreetmap.org/reverse",
               private val language: () -> String = { Locale.getDefault().toLanguageTag() }) : PlaceNamer {
    override fun name(lat: Double, lon: Double): Naming? = try {
        val url = Http.query(base, mapOf("format" to "jsonv2", "lat" to String.format(Locale.US, "%.3f", lat),
            "lon" to String.format(Locale.US, "%.3f", lon), "zoom" to "14", "addressdetails" to "1"))
        parse(http.get(url, mapOf("Accept-Language" to language(), "Accept" to "application/json")))
    } catch (e: Exception) { null }

    companion object {
        const val CREDIT = "Place names for My location: © OpenStreetMap contributors (Nominatim)"
        private val NAME_KEYS = listOf("suburb", "neighbourhood", "quarter", "city_district", "village", "town", "hamlet", "city", "municipality", "county", "state_district", "state")
        private val REGION_KEYS = listOf("city", "town", "county", "state_district", "state", "region")

        fun parse(body: String): Naming? {
            val o = JSONObject(body)
            if (o.has("error")) return null
            val a = o.optJSONObject("address") ?: return null
            fun get(k: String) = a.optString(k, "").trim().takeIf { it.isNotEmpty() }
            val name = NAME_KEYS.firstNotNullOfOrNull { get(it) } ?: return null
            val region = REGION_KEYS.firstNotNullOfOrNull { k -> get(k)?.takeIf { it != name } } ?: ""
            return Naming(name.take(100), region.take(100), (get("country") ?: "").take(100))
        }
    }
}
