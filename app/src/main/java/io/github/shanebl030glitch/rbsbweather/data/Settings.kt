// Saved places and settings, stored as one JSON document (in SharedPreferences on the phone).
// Bad values fall back to defaults one by one, so a damaged setting never loses the rest.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONArray
import org.json.JSONObject

const val MAX_PLACES = 50
val DEFAULT_PROVIDERS = listOf("open-meteo", "met.no")
val REFRESH_CHOICES = listOf(0, 15, 30, 60, 120)          // minutes; 0 = only when asked

data class Settings(
    val places: List<Place> = emptyList(),
    val selected: String? = null,                         // Place.key
    val units: UnitPrefs = UnitPrefs(),
    val refreshMinutes: Int = 30,
    val animations: Boolean = true,
    val providers: List<String> = DEFAULT_PROVIDERS,      // first = primary, then backups in order
    val recovery: Int = 0,                                // RECOVERY_CHOICES
    val providerOptions: Map<String, String> = emptyMap(),  // e.g. "wunderground.station" → "IAUCKL123"
) {
    fun option(provider: String, name: String): String? = providerOptions["$provider.$name"]?.takeIf { it.isNotBlank() }

    fun withOption(provider: String, name: String, value: String?): Settings =
        copy(providerOptions = if (value.isNullOrBlank()) providerOptions - "$provider.$name"
            else providerOptions + ("$provider.$name" to value.trim().take(64)))

    fun selectedPlace(): Place? = places.firstOrNull { it.key == selected } ?: places.firstOrNull()

    fun withPlace(p: Place): Settings {
        if (places.any { it.key == p.key }) return copy(selected = p.key)
        require(places.size < MAX_PLACES) { "You can save up to $MAX_PLACES places." }
        return copy(places = places + p, selected = p.key)
    }

    fun withoutPlace(key: String): Settings {
        val left = places.filterNot { it.key == key }
        return copy(places = left, selected = if (selected == key) left.firstOrNull()?.key else selected)
    }

    fun moved(key: String, delta: Int): Settings {
        val i = places.indexOfFirst { it.key == key }
        if (i < 0) return this
        val j = (i + delta).coerceIn(0, places.size - 1)
        if (i == j) return this
        val l = places.toMutableList()
        l.add(j, l.removeAt(i))
        return copy(places = l)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("places", JSONArray(places.map { it.toJson() }))
        .put("selected", selected ?: JSONObject.NULL)
        .put("temp", units.temp.name).put("wind", units.wind.name)
        .put("precip", units.precip.name).put("clock", units.clock.name)
        .put("refreshMinutes", refreshMinutes)
        .put("animations", animations)
        .put("providers", JSONArray(providers))
        .put("recovery", recovery)
        .put("providerOptions", JSONObject(providerOptions as Map<*, *>))

    companion object {
        fun fromJson(text: String?): Settings {
            if (text.isNullOrBlank()) return Settings()
            val o = try { JSONObject(text) } catch (e: Exception) { return Settings() }
            val places = mutableListOf<Place>()
            o.optJSONArray("places")?.let { a ->
                for (i in 0 until a.length()) {
                    val p = a.optJSONObject(i)?.let { Place.fromJson(it) } ?: continue
                    if (places.none { it.key == p.key } && places.size < MAX_PLACES) places += p
                }
            }
            val sel = o.str("selected")?.takeIf { s -> places.any { it.key == s } } ?: places.firstOrNull()?.key
            fun <T : Enum<T>> enumOr(values: Array<T>, k: String, d: T): T =
                values.firstOrNull { it.name == o.optString(k, "") } ?: d
            val units = UnitPrefs(
                enumOr(TempUnit.values(), "temp", TempUnit.C), enumOr(WindUnit.values(), "wind", WindUnit.KMH),
                enumOr(PrecipUnit.values(), "precip", PrecipUnit.MM), enumOr(ClockFormat.values(), "clock", ClockFormat.H24))
            val refresh = o.optInt("refreshMinutes", 30).takeIf { it in REFRESH_CHOICES } ?: 30
            val anim = if (o.opt("animations") is Boolean) o.getBoolean("animations") else true
            val chain = mutableListOf<String>()
            o.optJSONArray("providers")?.let { a ->
                for (i in 0 until a.length()) a.str(i)?.let { id -> if (providerInfo(id) != null && id !in chain) chain += id }
            }
            val rec = o.optInt("recovery", 0).takeIf { it in RECOVERY_CHOICES } ?: 0
            val opts = mutableMapOf<String, String>()
            o.optJSONObject("providerOptions")?.let { po ->
                po.keys().forEach { k -> po.str(k)?.takeIf { k.length <= 64 && it.isNotBlank() }?.let { opts[k] = it.take(64) } }
            }
            return Settings(places, sel, units, refresh, anim, chain.ifEmpty { DEFAULT_PROVIDERS }, rec, opts)
        }
    }
}
