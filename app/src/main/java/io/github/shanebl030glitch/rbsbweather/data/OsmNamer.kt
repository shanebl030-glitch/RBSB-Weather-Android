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
            "lon" to String.format(Locale.US, "%.3f", lon), "zoom" to "17", "addressdetails" to "1"))
        parse(http.get(url, mapOf("Accept-Language" to language(), "Accept" to "application/json")))
    } catch (e: Exception) { null }

    companion object {
        const val CREDIT = "Place names for My location: © OpenStreetMap contributors (Nominatim)"

        /**
         * Local areas, in the order Shane set (countries use different words: suburb in New Zealand and
         * Australia, quarter in Japan and Italy, neighbourhood in North America, city_district in Kenya…).
         */
        val LOCAL_KEYS = listOf("suburb", "neighbourhood", "quarter", "city_district", "district", "borough")
        /** A named settlement: villages and hamlets are where you are; a municipality only when nothing smaller is named. */
        private val SMALL_SETTLEMENT_KEYS = listOf("village", "hamlet")
        private val SETTLEMENT_KEYS = listOf("town", "city")
        private val STATE_KEYS = listOf("state", "province", "state_district", "region")

        /**
         * Administrative units OpenStreetMap sometimes files as a suburb, which nobody would call
         * where they are ("Cape Town Ward 54", "Municipio Roma I", "Mumbai Zone 3"): skipped, so the
         * next, real name is used.
         */
        private val ADMINISTRATIVE = Regex("(?i)\\bward\\s*\\d|\\bward\\b.*\\d|^municipio\\b|\\bzone\\s*\\d|^district\\s*\\d")

        fun administrative(name: String) = ADMINISTRATIVE.containsMatchIn(name)

        /**
         * The most specific meaningful local area in a Nominatim reply, worldwide:
         * 1. a local area (suburb, neighbourhood, quarter, city district, district, borough), not an
         *    administrative unit and not just the city's own name again: Naming.SUBURB;
         * 2. else the village or hamlet;
         * 3. else a municipality that isn't the city again;
         * 4. else the town, then the city: Naming.TOWN, so a town or city is never passed off as a suburb;
         * 5. else only a county or state: Naming.REGION.
         * After the name: the town or city it's in and the state or province (or, for a town, the
         * larger area and state); then the country.
         */
        fun parse(body: String): Naming? {
            val o = JSONObject(body)
            if (o.has("error")) return null
            val a = o.optJSONObject("address") ?: return null
            fun get(k: String) = a.optString(k, "").trim().takeIf { it.isNotEmpty() }
            val settlement = (SMALL_SETTLEMENT_KEYS + SETTLEMENT_KEYS).firstNotNullOfOrNull { get(it) }
            val cityLike = SETTLEMENT_KEYS.mapNotNull { get(it) } + listOfNotNull(get("municipality"))
            fun same(x: String, y: String?) = y != null && x.equals(y, ignoreCase = true)

            val local = LOCAL_KEYS.firstNotNullOfOrNull { k ->
                get(k)?.takeIf { v -> !administrative(v) && cityLike.none { same(v, it) } }
            }
            val (name, level) = when {
                local != null -> local to Naming.SUBURB
                else -> SMALL_SETTLEMENT_KEYS.firstNotNullOfOrNull { k -> get(k)?.let { it to Naming.TOWN } }
                    ?: get("municipality")?.takeIf { m -> SETTLEMENT_KEYS.none { same(m, get(it)) } && !administrative(m) }?.let { it to Naming.TOWN }
                    ?: SETTLEMENT_KEYS.firstNotNullOfOrNull { k -> get(k)?.let { it to Naming.TOWN } }
                    ?: (listOf("county") + STATE_KEYS).firstNotNullOfOrNull { k -> get(k)?.let { it to Naming.REGION } }
                    ?: return null
            }
            val state = STATE_KEYS.firstNotNullOfOrNull { get(it) }
            val county = get("county")
            val parts = mutableListOf<String>()
            fun add(v: String?) {
                if (v == null || same(name, v) || parts.any { same(v, it) }) return
                if (v.contains(name, ignoreCase = true) && level != Naming.SUBURB) return   // "City of Cape Town" after "Cape Town"
                parts += v
            }
            if (level == Naming.SUBURB) {
                add(settlement); add(state)
                if (parts.isEmpty()) add(county)
            } else {
                // a town or village: the larger area it belongs to, else its county; then the state
                val larger = SETTLEMENT_KEYS.mapNotNull { get(it) }.firstOrNull { !same(name, it) }
                if (larger != null) add(larger) else add(county)
                add(state)
            }
            return Naming(name.take(100), parts.take(2).joinToString(", ").take(100), (get("country") ?: "").take(100), level)
        }
    }
}
