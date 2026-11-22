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
    val alerts: AlertSettings = AlertSettings(),
    val quakes: QuakeSettings = QuakeSettings(),
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
        .put("alerts", alerts.toJson())
        .put("quakes", quakes.toJson())

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
            return Settings(places, sel, units, refresh, anim, chain.ifEmpty { DEFAULT_PROVIDERS }, rec, opts,
                AlertSettings.fromJson(o.optJSONObject("alerts")), QuakeSettings.fromJson(o.optJSONObject("quakes")))
        }
    }
}

/** Settings → Weather Alerts (the desktop's alerts_* settings). Own-limit values are in °C, km/h and mm. */
data class AlertSettings(
    val official: Boolean = true,
    val minLevel: String = "watch",             // all | watch | orange | red
    val interval: Int = 15,                     // minutes between checks
    val own: Boolean = true,
    val limits: Map<String, Limit> = DEFAULT_LIMITS,
    val banner: Boolean = true,                 // banner in the sky, badges in My places
    val notify: Boolean = true,
    val redAlarm: Boolean = false,              // the desktop's "beep for Red": Red warnings use an alarm sound
    val selectedOnly: Boolean = false,          // notify only for the place on screen
    val muted: Set<String> = emptySet(),
) {
    fun toJson(): JSONObject = JSONObject().put("official", official).put("min_level", minLevel).put("interval", interval)
        .put("own", own).put("limits", limitsToJson(limits)).put("banner", banner).put("notify", notify)
        .put("red_alarm", redAlarm).put("selected_only", selectedOnly).put("muted", JSONArray(muted.toList().takeLast(500)))

    companion object {
        fun fromJson(o: JSONObject?): AlertSettings {
            o ?: return AlertSettings()
            fun b(k: String, d: Boolean) = if (o.opt(k) is Boolean) o.getBoolean(k) else d
            val muted = o.optJSONArray("muted")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotEmpty() }?.take(300) } }?.takeLast(500)?.toSet() ?: emptySet()
            return AlertSettings(b("official", true), o.optString("min_level").takeIf { it in Alerts.MIN_LEVEL_CHOICES } ?: "watch",
                o.optInt("interval", 15).takeIf { it in Alerts.INTERVAL_CHOICES } ?: 15, b("own", true),
                o.optJSONObject("limits")?.takeIf { it.length() > 0 }?.let { limitsFromJson(it) } ?: DEFAULT_LIMITS,
                b("banner", true), b("notify", true), b("red_alarm", false), b("selected_only", false), muted)
        }
    }
}

/** Settings → Earthquakes (the desktop's quakes_* settings). */
data class QuakeSettings(
    val show: Boolean = true,
    val period: String = "day",                 // day | week
    val minMag: Double = 2.5,                   // 2.5 | 4.5
    val interval: Int = 5,                      // minutes between checks while the app is open
    val notifyNear: Boolean = true,
    val nearMag: Double = 4.5,
    val nearKm: Int = 300,
    val notifyMajor: Boolean = true,
    val majorMag: Double = 7.0,
) {
    val rules: NotifyRules get() = NotifyRules(notifyNear, nearMag, nearKm.toDouble(), notifyMajor, majorMag)

    fun toJson(): JSONObject = JSONObject().put("show", show).put("period", period).put("min_mag", minMag).put("interval", interval)
        .put("notify_near", notifyNear).put("near_mag", nearMag).put("near_km", nearKm).put("notify_major", notifyMajor).put("major_mag", majorMag)

    companion object {
        val NEAR_MAGS = listOf(3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0)
        val NEAR_KMS = listOf(50, 100, 200, 300, 500, 1000)
        val MAJOR_MAGS = listOf(6.0, 6.5, 7.0, 7.5, 8.0)

        fun fromJson(o: JSONObject?): QuakeSettings {
            o ?: return QuakeSettings()
            fun b(k: String, d: Boolean) = if (o.opt(k) is Boolean) o.getBoolean(k) else d
            fun d(k: String, def: Double, allowed: List<Double>) = o.num(k)?.takeIf { v -> allowed.any { kotlin.math.abs(it - v) < 1e-9 } } ?: def
            return QuakeSettings(b("show", true), o.optString("period").takeIf { it in Quakes.PERIODS } ?: "day",
                d("min_mag", 2.5, Quakes.MIN_MAGS), o.optInt("interval", 5).takeIf { it in Quakes.INTERVALS } ?: 5,
                b("notify_near", true), d("near_mag", 4.5, NEAR_MAGS), o.optInt("near_km", 300).takeIf { it in NEAR_KMS } ?: 300,
                b("notify_major", true), d("major_mag", 7.0, MAJOR_MAGS))
        }
    }
}
