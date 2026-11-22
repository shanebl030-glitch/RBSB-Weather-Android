// Weather providers (as in the desktop app's Settings → Weather Providers): Open-Meteo and
// MET Norway need nothing; OpenWeather, WeatherAPI.com, Visual Crossing, Tomorrow.io and
// Weather Underground use the user's own free API key. Every provider's forecast goes
// through normalise(), so the rest of the app sees the same shape whoever supplied it.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.round

data class ProviderInfo(
    val id: String,
    val name: String,
    val needsKey: Boolean,
    val website: String,
    val credit: String,
    val coverage: String,
    val forecastDays: String,
    val hourly: String,
    val limits: String,
    val signupUrl: String = "",
    val keyHelp: String = "",
    val minIntervalMinutes: Int = 0,
    val maxDays: Int = 7,
    val option: Triple<String, String, Regex>? = null,      // (id, label, allowed) e.g. Weather Underground station
)

class KeyMissing(name: String) : WeatherException("$name needs an API key (Settings → Weather Providers).")

// ------------------------------------------------------------------ helpers
internal val LOCAL_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

internal fun localStr(t: Instant, zone: ZoneId): String = t.atZone(zone).format(LOCAL_FMT)

internal fun quarterHour(t: Instant, zone: ZoneId): String {
    val z = t.atZone(zone)
    return z.withMinute(z.minute - z.minute % 15).format(LOCAL_FMT)
}

internal fun fromUnix(v: Double?): Instant? = v?.takeIf { it > 0 }?.let { Instant.ofEpochSecond(it.toLong()) }

internal fun fromIso(s: String?): Instant? {
    if (s.isNullOrBlank()) return null
    return try { OffsetDateTime.parse(s).toInstant() } catch (e: Exception) {
        try { Instant.parse(s) } catch (e2: Exception) {
            // "2026-11-22T06:01:00+1300" (no colon in the offset)
            try { OffsetDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")).toInstant() }
            catch (e3: Exception) { null }
        }
    }
}

internal fun pct(v: Double?, fraction: Boolean = false): Double? =
    v?.let { (if (fraction) it * 100.0 else it).coerceIn(0.0, 100.0) }

internal fun kmh(ms: Double?): Double? = ms?.let { round(it * 3.6 * 10.0) / 10.0 }

/** A place's zone: the service's zone name, else the saved one, else a whole-hour offset. */
internal fun zoneOf(place: Place, name: String? = null, offsetSeconds: Double? = null): ZoneId {
    for (n in listOf(name, place.timezone)) {
        if (!n.isNullOrBlank() && n != "auto") try { return ZoneId.of(n) } catch (e: Exception) { }
    }
    if (offsetSeconds != null) return ZoneOffset.ofTotalSeconds(offsetSeconds.toInt().coerceIn(-64800, 64800))
    return Sun.zoneFor(place)
}

internal fun json(body: String, who: String): JSONObject =
    try { JSONObject(body) } catch (e: JSONException) { throw WeatherException("$who's reply couldn't be read.") }

internal fun JSONObject.arr(k: String): JSONArray? = optJSONArray(k)
internal fun JSONObject.obj(k: String): JSONObject? = optJSONObject(k)

// ---------------------------------------------------------------- normalise
private val TEMP = -100.0..70.0
private val WIND = 0.0..600.0
private val PRESSURE = 800.0..1100.0
private val PRECIP = 0.0..1000.0
private val UV = 0.0..25.0
private fun Double?.inRange(r: ClosedFloatingPointRange<Double>) = this?.takeIf { it in r }
private fun known(code: Int?) = code?.takeIf { it in WeatherCodes.known }

/** A WMO code from cloud cover (%) and rain (mm/h), for services that don't give a usable one. */
fun codeFrom(cloud: Double?, precip: Double?): Int? {
    if (precip != null && precip > 0.05) return if (precip < 2.5) 61 else if (precip < 7.6) 63 else 65
    if (cloud == null) return null
    return if (cloud < 12) 0 else if (cloud < 38) 1 else if (cloud < 75) 2 else 3
}

/**
 * Makes any provider's forecast safe for the app. Never invents weather: impossible values
 * are dropped; sunrise/sunset, day/night and "feels like" are worked out when missing.
 */
fun normalise(fc: Forecast, place: Place, days: Int = 7, now: Instant = Instant.now()): Forecast {
    val c0 = fc.current
    val temp = c0.temperature.inRange(TEMP) ?: throw WeatherException("${fc.source} didn't supply a current temperature.")
    val zone = Sun.zoneFor(place, fc.utcOffsetSeconds).let { if (fc.timezone != "auto") zoneOf(place, fc.timezone, fc.utcOffsetSeconds.toDouble()) else it }
    val hum = c0.humidity?.coerceIn(0.0, 100.0)
    val wind = c0.windSpeed.inRange(WIND)
    var code = known(c0.weatherCode)
    val cloud = c0.cloudCover?.coerceIn(0.0, 100.0)
    val precip = c0.precipitation.inRange(PRECIP)
    if (code == null) code = codeFrom(cloud, precip)
    val feels = c0.apparentTemperature.inRange(TEMP)
        ?: if (hum != null && wind != null) Sun.apparentTemperature(temp, hum, wind / 3.6).inRange(TEMP) else null
    val time = if (c0.time.length >= 16) c0.time else quarterHour(now, zone)
    val current = c0.copy(time = time, temperature = temp, apparentTemperature = feels, humidity = hum,
        weatherCode = code, windSpeed = wind, windDirection = c0.windDirection?.let { ((it % 360) + 360) % 360 },
        windGusts = c0.windGusts.inRange(WIND), pressure = c0.pressure.inRange(PRESSURE), cloudCover = cloud,
        precipitation = precip)
    val hours = fc.hourly.filter { it.time.length >= 16 }.distinctBy { it.time }.sortedBy { it.time }.map {
        it.copy(temperature = it.temperature.inRange(TEMP), precipitation = it.precipitation.inRange(PRECIP),
            precipitationProbability = it.precipitationProbability?.coerceIn(0.0, 100.0), weatherCode = known(it.weatherCode))
    }
    val daily = fc.daily.filter { it.date.length == 10 }.distinctBy { it.date }.sortedBy { it.date }.take(max(1, days)).map { d ->
        var hi = d.tempMax.inRange(TEMP); var lo = d.tempMin.inRange(TEMP)
        if (hi != null && lo != null && lo > hi) { val t = hi; hi = lo; lo = t }
        val dayHours = hours.filter { it.time.startsWith(d.date) }
        var dcode = known(d.weatherCode)
        if (dcode == null) dcode = dayHours.mapNotNull { it.weatherCode }.maxOrNull()
        var pp = d.precipitationProbabilityMax?.coerceIn(0.0, 100.0)
        if (pp == null) {
            val pps = dayHours.mapNotNull { it.precipitationProbability }
            if (pps.size >= 12) pp = pps.max()
        }
        var rise = d.sunrise
        var set = d.sunset
        if (rise == null || set == null) {
            try {
                val (r, s) = Sun.riseSet(LocalDate.parse(d.date), place.latitude, place.longitude, zone)
                if (rise == null) rise = r?.format(LOCAL_FMT)
                if (set == null) set = s?.format(LOCAL_FMT)
            } catch (e: Exception) { }
        }
        d.copy(weatherCode = dcode, tempMax = hi, tempMin = lo, precipitationSum = d.precipitationSum.inRange(PRECIP),
            precipitationProbabilityMax = pp, windSpeedMax = d.windSpeedMax.inRange(WIND), sunrise = rise, sunset = set,
            uvIndexMax = d.uvIndexMax.inRange(UV))
    }
    val offset = if (fc.utcOffsetSeconds != 0 || fc.timezone == "UTC") fc.utcOffsetSeconds
        else zone.rules.getOffset(now).totalSeconds
    return fc.copy(current = current, hourly = hours, daily = daily, utcOffsetSeconds = offset)
}

// ------------------------------------------------------------------ keyed base
/** A service that needs the user's API key. Errors never include the key or the address. */
abstract class KeyedProvider(
    val info: ProviderInfo,
    protected val http: HttpClient,
    private val key: () -> String?,
    protected val base: String,
) : Provider {
    override val name: String get() = info.name
    override val id: String get() = info.id
    /** The time used for "now" (tests set a fixed one to match their saved replies). */
    var clock: () -> Instant = Instant::now
    protected val apiKey: String get() = key()?.trim()?.takeIf { it.isNotEmpty() } ?: throw KeyMissing(info.name)

    protected fun get(url: String, headers: Map<String, String> = emptyMap()): String = try {
        http.get(url, headers)
    } catch (e: WeatherException) {
        throw when (e.status) {
            401, 403 -> WeatherException("${info.name} didn't accept the API key (HTTP ${e.status}). Check it in Settings → Weather Providers.", status = e.status)
            429 -> WeatherException("${info.name}'s allowance has run out for now (too many requests). It will be tried again later.", retryable = true, status = 429)
            else -> e
        }
    }

    abstract fun fetch(place: Place, now: Instant): Forecast

    override fun forecast(place: Place): Forecast {
        val now = clock()
        val fc = try { fetch(place, now) } catch (e: WeatherException) { throw e } catch (e: Exception) {
            throw WeatherException("${info.name}'s reply wasn't in the expected format.")
        }
        return normalise(fc.copy(source = info.name), place, 7, now).copy(fetchedAt = System.currentTimeMillis())
    }
}

private fun f2(v: Double) = String.format(Locale.US, "%.4f", v)

// ---------------------------------------------------------------- OpenWeather
fun owmCode(id: Int?): Int? {
    val c = id ?: return null
    return when {
        c in 200..299 -> 95
        c in 300..399 -> mapOf(300 to 51, 301 to 53, 302 to 55, 310 to 51, 311 to 53, 312 to 55, 313 to 80, 314 to 81, 321 to 80)[c] ?: 53
        c in 500..599 -> mapOf(500 to 61, 501 to 63, 502 to 65, 503 to 65, 504 to 65, 511 to 66, 520 to 80, 521 to 81, 522 to 82, 531 to 81)[c] ?: 63
        c in 600..699 -> mapOf(600 to 71, 601 to 73, 602 to 75, 611 to 73, 612 to 71, 613 to 85, 615 to 71, 616 to 73, 620 to 85, 621 to 85, 622 to 86)[c] ?: 73
        c == 701 || c == 741 -> 45
        c == 771 -> 82
        c == 781 -> 95
        c in 700..799 -> null
        else -> mapOf(800 to 0, 801 to 1, 802 to 2, 803 to 3, 804 to 3)[c]
    }
}

private fun JSONObject.owmId(): Int? = arr("weather")?.optJSONObject(0)?.int("id")

private fun JSONObject.owmPrecip(period: String): Double? {
    var total = 0.0; var found = false
    for (k in listOf("rain", "snow")) {
        val v = when (val x = opt(k)) { is JSONObject -> x.num(period); else -> num(k) }
        if (v != null) { total += v; found = true }
    }
    return if (found) round(total * 100) / 100 else null
}

class OpenWeatherProvider(http: HttpClient, key: () -> String?, base: String = "https://api.openweathermap.org/data") :
    KeyedProvider(INFO, http, key, base) {
    companion object {
        val INFO = ProviderInfo("openweather", "OpenWeather", true, "https://openweathermap.org/",
            "Weather data by OpenWeather (CC BY-SA 4.0)",
            "Worldwide, from OpenWeather's own model blend of national weather services and observations.",
            "8 days with One Call 3.0; 5 days with a free key on its own",
            "48 hours with One Call 3.0; 3-hourly with a free key",
            "Free key: 60 calls a minute, 1,000,000 a month (current weather + 5-day/3-hour forecast). One Call 3.0: first 1,000 calls a day free, then pay per call; a separate subscription that needs a payment card.",
            "https://home.openweathermap.org/users/sign_up",
            "After signing in: your name → My API keys. New keys can take up to 2 hours to work.", 5, 8)
    }
    private var oneCallOffUntil = 0L

    override fun fetch(place: Place, now: Instant): Forecast {
        val q = mapOf("lat" to f2(place.latitude), "lon" to f2(place.longitude), "appid" to apiKey, "units" to "metric")
        if (System.currentTimeMillis() >= oneCallOffUntil) {
            try {
                return parseOneCall(get(Http.query("$base/3.0/onecall", q + ("exclude" to "minutely,alerts"))), place, now)
            } catch (e: WeatherException) {
                if (e.status != 401) throw e
                oneCallOffUntil = System.currentTimeMillis() + 24 * 3_600_000L     // not subscribed: free endpoints for a day
            }
        }
        return parseOwmFree(get(Http.query("$base/2.5/weather", q)), get(Http.query("$base/2.5/forecast", q)), place, now)
    }
}

fun parseOneCall(body: String, place: Place, now: Instant = Instant.now()): Forecast {
    val o = json(body, "OpenWeather")
    val c = o.obj("current") ?: throw WeatherException("OpenWeather's reply has no current weather.")
    val zone = zoneOf(place, o.optString("timezone", ""), o.num("timezone_offset"))
    val rise = fromUnix(c.num("sunrise")); val set = fromUnix(c.num("sunset"))
    val current = Current(quarterHour(now, zone), c.num("temp"), c.num("feels_like"), pct(c.num("humidity")),
        owmCode(c.owmId()), if (rise != null && set != null) (!now.isBefore(rise) && now.isBefore(set)) else Sun.isDay(now, place.latitude, place.longitude),
        kmh(c.num("wind_speed")), c.num("wind_deg"), kmh(c.num("wind_gust")), c.num("pressure"), pct(c.num("clouds")), c.owmPrecip("1h"))
    val hours = mutableListOf<Hour>()
    o.arr("hourly")?.let { a -> for (i in 0 until a.length()) { val h = a.optJSONObject(i) ?: continue
        val t = fromUnix(h.num("dt")) ?: continue
        hours += Hour(localStr(t, zone), h.num("temp"), pct(h.num("pop"), true), h.owmPrecip("1h"), owmCode(h.owmId())) } }
    val days = mutableListOf<Day>()
    o.arr("daily")?.let { a -> for (i in 0 until a.length()) { val d = a.optJSONObject(i) ?: continue
        val t = fromUnix(d.num("dt")) ?: continue
        val temp = d.obj("temp") ?: JSONObject()
        val r = fromUnix(d.num("sunrise")); val s = fromUnix(d.num("sunset"))
        val rain = (d.num("rain") ?: 0.0) + (d.num("snow") ?: 0.0)
        days += Day(t.atZone(zone).toLocalDate().toString(), owmCode(d.owmId()), temp.num("max"), temp.num("min"),
            round(rain * 10) / 10, pct(d.num("pop"), true), kmh(d.num("wind_speed")), r?.let { localStr(it, zone) },
            s?.let { localStr(it, zone) }, d.num("uvi")) } }
    return Forecast(place, zone.id, (o.num("timezone_offset") ?: 0.0).toInt(), current, hours, days, "OpenWeather")
}

fun parseOwmFree(curBody: String, fcBody: String, place: Place, now: Instant = Instant.now()): Forecast {
    val cur = json(curBody, "OpenWeather"); val fc = json(fcBody, "OpenWeather")
    val m = cur.obj("main") ?: throw WeatherException("OpenWeather's reply has no current weather.")
    val off = cur.num("timezone") ?: fc.obj("city")?.num("timezone")
    val zone = zoneOf(place, null, off)
    val w = cur.obj("wind") ?: JSONObject()
    val sys = cur.obj("sys") ?: JSONObject()
    val rise = fromUnix(sys.num("sunrise")); val set = fromUnix(sys.num("sunset"))
    val current = Current(quarterHour(now, zone), m.num("temp"), m.num("feels_like"), pct(m.num("humidity")), owmCode(cur.owmId()),
        if (rise != null && set != null) (!now.isBefore(rise) && now.isBefore(set)) else Sun.isDay(now, place.latitude, place.longitude),
        kmh(w.num("speed")), w.num("deg"), kmh(w.num("gust")), m.num("pressure"), pct(cur.obj("clouds")?.num("all")), cur.owmPrecip("1h"))
    val steps = mutableListOf<Pair<Instant, JSONObject>>()
    fc.arr("list")?.let { a -> for (i in 0 until a.length()) { val s = a.optJSONObject(i) ?: continue
        fromUnix(s.num("dt"))?.let { steps += it to s } } }
    // each 3-hour step spread across its hours, temperature eased towards the next step
    val hours = mutableListOf<Hour>()
    steps.forEachIndexed { i, (t, s) ->
        val t0 = s.obj("main")?.num("temp"); val t1 = steps.getOrNull(i + 1)?.second?.obj("main")?.num("temp")
        val amt = s.owmPrecip("3h")
        for (k in 0 until 3) {
            val temp = if (t0 == null || t1 == null) t0 else round((t0 + (t1 - t0) * k / 3) * 10) / 10
            hours += Hour(localStr(t.plusSeconds(3600L * k), zone), temp, pct(s.num("pop"), true), amt?.let { round(it / 3 * 100) / 100 }, owmCode(s.owmId()))
        }
    }
    val today = now.atZone(zone).toLocalDate().toString()
    val byDay = linkedMapOf<String, MutableList<JSONObject>>()
    steps.forEach { (t, s) -> byDay.getOrPut(t.atZone(zone).toLocalDate().toString()) { mutableListOf() } += s }
    val days = mutableListOf<Day>()
    for ((d, ss) in byDay) {
        if (d != today && ss.size < 4) continue                 // a part day at the end would mislead
        val hi = ss.mapNotNull { it.obj("main")?.let { mm -> mm.num("temp_max") ?: mm.num("temp") } }.toMutableList()
        val lo = ss.mapNotNull { it.obj("main")?.let { mm -> mm.num("temp_min") ?: mm.num("temp") } }.toMutableList()
        if (hi.isEmpty()) continue
        if (d == today) current.temperature?.let { hi += it; lo += it }
        days += Day(d, ss.mapNotNull { owmCode(it.owmId()) }.maxOrNull(), hi.max(), lo.min(),
            round(ss.sumOf { it.owmPrecip("3h") ?: 0.0 } * 10) / 10, ss.mapNotNull { pct(it.num("pop"), true) }.maxOrNull(),
            ss.mapNotNull { kmh(it.obj("wind")?.num("speed")) }.maxOrNull(),
            if (d == today) rise?.let { localStr(it, zone) } else null, if (d == today) set?.let { localStr(it, zone) } else null, null)
    }
    return Forecast(place, zone.id, (off ?: 0.0).toInt(), current, hours, days, "OpenWeather")
}

// -------------------------------------------------------------- WeatherAPI.com
private val WAPI = mapOf(1000 to 0, 1003 to 2, 1006 to 3, 1009 to 3, 1030 to 45, 1063 to 80, 1066 to 85, 1069 to 85, 1072 to 56,
    1087 to 95, 1114 to 73, 1117 to 75, 1135 to 45, 1147 to 48, 1150 to 51, 1153 to 51, 1168 to 56, 1171 to 57,
    1180 to 61, 1183 to 61, 1186 to 63, 1189 to 63, 1192 to 65, 1195 to 65, 1198 to 66, 1201 to 67,
    1204 to 71, 1207 to 73, 1210 to 71, 1213 to 71, 1216 to 73, 1219 to 73, 1222 to 75, 1225 to 75, 1237 to 77,
    1240 to 80, 1243 to 81, 1246 to 82, 1249 to 85, 1252 to 86, 1255 to 85, 1258 to 86, 1261 to 77, 1264 to 77,
    1273 to 95, 1276 to 95, 1279 to 95, 1282 to 95)

fun wapiCode(cond: JSONObject?): Int? = cond?.int("code")?.let { WAPI[it] }

class WeatherApiProvider(http: HttpClient, key: () -> String?, base: String = "https://api.weatherapi.com/v1") :
    KeyedProvider(INFO, http, key, base) {
    companion object {
        val INFO = ProviderInfo("weatherapi", "WeatherAPI.com", true, "https://www.weatherapi.com/",
            "Weather data by WeatherAPI.com", "Worldwide, blending ECMWF, GFS and HRRR models with observations.",
            "3 days on the free plan; up to 14 on paid plans", "every hour for each forecast day",
            "Free plan: 100,000 calls a month, commercial use allowed, no card needed. Paid plans add 14-day forecasts.",
            "https://www.weatherapi.com/signup.aspx", "After signing in, the key is on your account dashboard.", 0, 14)
    }
    override fun fetch(place: Place, now: Instant): Forecast = parseWeatherApi(get(Http.query("$base/forecast.json",
        mapOf("key" to apiKey, "q" to "${f2(place.latitude)},${f2(place.longitude)}", "days" to "7", "aqi" to "no", "alerts" to "no"))), place, now)
}

private fun wapiClock(date: String, s: String?): String? {
    if (s == null) return null
    return try {
        val t = java.time.LocalTime.parse(s.trim().uppercase(Locale.US), DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
        "${date}T" + t.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) { null }
}

fun parseWeatherApi(body: String, place: Place, now: Instant = Instant.now()): Forecast {
    val o = json(body, "WeatherAPI.com")
    val c = o.obj("current") ?: throw WeatherException("WeatherAPI.com's reply has no current weather.")
    val loc = o.obj("location") ?: JSONObject()
    val zone = zoneOf(place, loc.optString("tz_id", ""))
    val t = fromUnix(loc.num("localtime_epoch")) ?: now
    val isd = c.num("is_day")
    val current = Current(quarterHour(t, zone), c.num("temp_c"), c.num("feelslike_c"), pct(c.num("humidity")), wapiCode(c.obj("condition")),
        if (isd != null) isd != 0.0 else Sun.isDay(now, place.latitude, place.longitude), c.num("wind_kph"), c.num("wind_degree"),
        c.num("gust_kph"), c.num("pressure_mb"), pct(c.num("cloud")), c.num("precip_mm"))
    val hours = mutableListOf<Hour>(); val days = mutableListOf<Day>()
    o.obj("forecast")?.arr("forecastday")?.let { a -> for (i in 0 until a.length()) {
        val fd = a.optJSONObject(i) ?: continue
        val date = fd.str("date") ?: continue
        val day = fd.obj("day") ?: JSONObject(); val astro = fd.obj("astro") ?: JSONObject()
        fd.arr("hour")?.let { ha -> for (j in 0 until ha.length()) { val h = ha.optJSONObject(j) ?: continue
            val ht = fromUnix(h.num("time_epoch")) ?: continue
            val probs = listOfNotNull(pct(h.num("chance_of_rain")), pct(h.num("chance_of_snow")))
            hours += Hour(localStr(ht, zone), h.num("temp_c"), probs.maxOrNull(), h.num("precip_mm"), wapiCode(h.obj("condition"))) } }
        val probs = listOfNotNull(pct(day.num("daily_chance_of_rain")), pct(day.num("daily_chance_of_snow")))
        days += Day(date, wapiCode(day.obj("condition")), day.num("maxtemp_c"), day.num("mintemp_c"), day.num("totalprecip_mm"),
            probs.maxOrNull(), day.num("maxwind_kph"), wapiClock(date, astro.str("sunrise")), wapiClock(date, astro.str("sunset")), day.num("uv")) } }
    return Forecast(place, zone.id, zone.rules.getOffset(now).totalSeconds, current, hours, days, "WeatherAPI.com")
}

// ------------------------------------------------------------- Visual Crossing
fun vcCode(icon: String?, precip: Double?, snow: Double?): Int? {
    icon ?: return null
    val p = precip ?: 0.0; val s = snow ?: 0.0
    val lvl = if (p < 2.5) 0 else if (p < 10) 1 else 2
    return when {
        icon.startsWith("thunder") -> 95
        icon.startsWith("snow-showers") -> if (s >= 5) 86 else 85
        icon == "snow" -> if (s >= 5) 75 else if (s >= 1) 73 else 71
        icon.startsWith("showers") -> intArrayOf(80, 81, 82)[lvl]
        icon == "rain" -> intArrayOf(61, 63, 65)[lvl]
        icon == "fog" -> 45
        icon.startsWith("clear") -> 0
        icon.startsWith("partly-cloudy") -> 2
        icon == "cloudy" -> 3
        else -> null
    }
}

class VisualCrossingProvider(http: HttpClient, key: () -> String?,
                             base: String = "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline") :
    KeyedProvider(INFO, http, key, base) {
    companion object {
        val INFO = ProviderInfo("visualcrossing", "Visual Crossing", true, "https://www.visualcrossing.com/",
            "Weather data by Visual Crossing Weather", "Worldwide, combining several national weather models with station observations.",
            "15 days (RBSB-Weather uses 7)", "every hour, all 15 days",
            "Free plan: 1,000 records a day (one forecast = 1 record), commercial use allowed.",
            "https://www.visualcrossing.com/sign-up", "After signing in: Account → Your details → Key.", 5, 15)
    }
    override fun fetch(place: Place, now: Instant): Forecast = parseVisualCrossing(get(Http.query("$base/${f2(place.latitude)},${f2(place.longitude)}",
        mapOf("unitGroup" to "metric", "include" to "current,hours,days", "iconSet" to "icons2", "contentType" to "json", "key" to apiKey))), place, now)
}

fun parseVisualCrossing(body: String, place: Place, now: Instant = Instant.now()): Forecast {
    val o = json(body, "Visual Crossing")
    val c = o.obj("currentConditions") ?: throw WeatherException("Visual Crossing's reply has no current weather.")
    val offH = o.num("tzoffset")
    val zone = zoneOf(place, o.optString("timezone", ""), offH?.times(3600))
    val rise = fromUnix(c.num("sunriseEpoch")); val set = fromUnix(c.num("sunsetEpoch"))
    val current = Current(quarterHour(now, zone), c.num("temp"), c.num("feelslike"), pct(c.num("humidity")),
        vcCode(c.str("icon"), c.num("precip"), c.num("snow")),
        if (rise != null && set != null) (!now.isBefore(rise) && now.isBefore(set)) else Sun.isDay(now, place.latitude, place.longitude),
        c.num("windspeed"), c.num("winddir"), c.num("windgust"), c.num("pressure"), pct(c.num("cloudcover")), c.num("precip"))
    val hours = mutableListOf<Hour>(); val days = mutableListOf<Day>()
    o.arr("days")?.let { a -> for (i in 0 until a.length()) {
        val d = a.optJSONObject(i) ?: continue
        val date = d.str("datetime") ?: continue
        d.arr("hours")?.let { ha -> for (j in 0 until ha.length()) { val h = ha.optJSONObject(j) ?: continue
            val ht = fromUnix(h.num("datetimeEpoch")) ?: continue
            hours += Hour(localStr(ht, zone), h.num("temp"), pct(h.num("precipprob")), h.num("precip"), vcCode(h.str("icon"), h.num("precip"), h.num("snow"))) } }
        val dr = fromUnix(d.num("sunriseEpoch")); val ds = fromUnix(d.num("sunsetEpoch"))
        days += Day(date, vcCode(d.str("icon"), d.num("precip"), d.num("snow")), d.num("tempmax"), d.num("tempmin"), d.num("precip"),
            pct(d.num("precipprob")), d.num("windspeed"), dr?.let { localStr(it, zone) } ?: d.str("sunrise")?.take(5)?.let { "${date}T$it" },
            ds?.let { localStr(it, zone) } ?: d.str("sunset")?.take(5)?.let { "${date}T$it" }, d.num("uvindex")) } }
    return Forecast(place, zone.id, offH?.times(3600)?.toInt() ?: zone.rules.getOffset(now).totalSeconds, current, hours, days, "Visual Crossing")
}

// ----------------------------------------------------------------- Tomorrow.io
private val TIO = mapOf(1000 to 0, 1100 to 1, 1101 to 2, 1102 to 3, 1001 to 3, 2000 to 45, 2100 to 45, 4000 to 53, 4200 to 61, 4001 to 63,
    4201 to 65, 5001 to 71, 5100 to 71, 5000 to 73, 5101 to 75, 6000 to 56, 6200 to 66, 6001 to 67, 6201 to 67, 7102 to 77, 7000 to 77, 7101 to 77, 8000 to 95)

fun tioCode(v: Double?): Int? {
    var c = v?.toInt() ?: return null
    if (c > 9999) c /= 10                          // day/night variants: base code + a final 0 or 1
    return TIO[c]
}

class TomorrowProvider(http: HttpClient, key: () -> String?, base: String = "https://api.tomorrow.io/v4") :
    KeyedProvider(INFO, http, key, base) {
    companion object {
        val INFO = ProviderInfo("tomorrow", "Tomorrow.io", true, "https://www.tomorrow.io/", "Weather data by Tomorrow.io",
            "Worldwide, from Tomorrow.io's own model plus its satellite and radar data.", "5 days on the free plan", "120 hours",
            "Free plan: 500 requests a day, 25 an hour (personal, non-commercial). RBSB-Weather asks at most every 10 minutes per place.",
            "https://app.tomorrow.io/signup", "After signing in: Development → API Management.", 10, 5)
    }
    override fun fetch(place: Place, now: Instant): Forecast = parseTomorrow(get(Http.query("$base/weather/forecast",
        mapOf("location" to "${f2(place.latitude)},${f2(place.longitude)}", "units" to "metric", "apikey" to apiKey))), place, now)
}

private fun JSONObject.first(vararg names: String): Double? = names.firstNotNullOfOrNull { num(it) }

fun parseTomorrow(body: String, place: Place, now: Instant = Instant.now()): Forecast {
    val o = json(body, "Tomorrow.io")
    val tl = o.obj("timelines") ?: throw WeatherException("Tomorrow.io's reply has no forecast.")
    val zone = zoneOf(place)
    val steps = mutableListOf<Pair<Instant, JSONObject>>()
    tl.arr("hourly")?.let { a -> for (i in 0 until a.length()) { val it2 = a.optJSONObject(i) ?: continue
        val t = fromIso(it2.str("time")) ?: continue
        it2.obj("values")?.let { v -> steps += t to v } } }
    if (steps.isEmpty()) throw WeatherException("Tomorrow.io's reply has no hourly forecast.")
    steps.sortBy { it.first }
    var cur = 0
    steps.forEachIndexed { i, (t, _) -> if (!t.isAfter(now)) cur = i }
    val v = steps[cur].second
    val precip = listOfNotNull(v.num("rainIntensity"), v.num("snowIntensity"), v.num("sleetIntensity"), v.num("freezingRainIntensity"))
    val current = Current(quarterHour(now, zone), v.num("temperature"), v.num("temperatureApparent"), pct(v.num("humidity")),
        tioCode(v.num("weatherCode")), Sun.isDay(now, place.latitude, place.longitude), kmh(v.num("windSpeed")), v.num("windDirection"),
        kmh(v.num("windGust")), v.first("pressureSeaLevel", "pressureSurfaceLevel"), pct(v.num("cloudCover")),
        if (precip.isEmpty()) null else round(precip.sum() * 100) / 100)
    val hours = steps.drop(cur).map { (t, hv) ->
        val amt = listOfNotNull(hv.num("rainIntensity"), hv.num("snowIntensity"))
        Hour(localStr(t, zone), hv.num("temperature"), pct(hv.num("precipitationProbability")),
            if (amt.isEmpty()) null else round(amt.sum() * 100) / 100, tioCode(hv.num("weatherCode")))
    }
    val days = mutableListOf<Day>()
    tl.arr("daily")?.let { a -> for (i in 0 until a.length()) { val item = a.optJSONObject(i) ?: continue
        val t = fromIso(item.str("time")) ?: continue
        val dv = item.obj("values") ?: continue
        val rain = listOfNotNull(dv.num("rainAccumulationSum"), dv.num("snowAccumulationSum"), dv.num("sleetAccumulationLweSum"), dv.num("iceAccumulationSum"))
        days += Day(t.atZone(zone).toLocalDate().toString(), tioCode(dv.num("weatherCodeMax") ?: dv.num("weatherCode")),
            dv.num("temperatureMax"), dv.num("temperatureMin"), if (rain.isEmpty()) null else round(rain.sum() * 10) / 10,
            pct(dv.first("precipitationProbabilityMax", "precipitationProbabilityAvg")), kmh(dv.first("windSpeedMax", "windSpeedAvg")),
            fromIso(dv.str("sunriseTime"))?.let { localStr(it, zone) }, fromIso(dv.str("sunsetTime"))?.let { localStr(it, zone) },
            dv.first("uvIndexMax", "uvIndexAvg")) } }
    return Forecast(place, zone.id, zone.rules.getOffset(now).totalSeconds, current, hours, days, "Tomorrow.io")
}

// --------------------------------------------------------- Weather Underground
private val WU = mapOf(0 to 95, 1 to 95, 2 to 95, 3 to 95, 4 to 95, 5 to 71, 6 to 71, 7 to 71, 8 to 56, 9 to 53, 10 to 67, 11 to 81,
    12 to 63, 13 to 71, 14 to 85, 15 to 73, 16 to 73, 17 to 96, 18 to 71, 20 to 45, 26 to 3, 27 to 3, 28 to 3, 29 to 2, 30 to 2,
    31 to 0, 32 to 0, 33 to 1, 34 to 1, 35 to 96, 36 to 0, 37 to 95, 38 to 95, 39 to 80, 40 to 65, 41 to 85, 42 to 75, 43 to 75,
    45 to 80, 46 to 85, 47 to 95)

fun wuCode(v: Double?): Int? = v?.toInt()?.let { WU[it] }

class WundergroundProvider(http: HttpClient, key: () -> String?, private val station: () -> String?,
                           base: String = "https://api.weather.com") : KeyedProvider(INFO, http, key, base) {
    companion object {
        val INFO = ProviderInfo("wunderground", "Weather Underground", true, "https://www.wunderground.com/",
            "Weather data by Weather Underground / The Weather Company",
            "Worldwide forecast from The Weather Company; current conditions from your own or the nearest personal weather station.",
            "5 days plus today", "none with a personal weather station key (the 24-hour chart stays empty)",
            "Free API key for people who upload data from their own weather station (personal, non-commercial use). About 1,500 calls a day; each update uses 2.",
            "https://www.wunderground.com/member/api-keys",
            "Sign in at wunderground.com → My Profile → API Keys (for members who send data from a weather station).", 5, 6,
            Triple("station", "Your station ID (optional, e.g. IAUCKL123)", Regex("[A-Za-z0-9]{3,24}")))
    }
    private val nearby = mutableMapOf<String, Pair<Long, List<String>>>()

    override fun fetch(place: Place, now: Instant): Forecast {
        val geo = "${f2(place.latitude)},${f2(place.longitude)}"
        val fc = get(Http.query("$base/v3/wx/forecast/daily/5day", mapOf("geocode" to geo, "format" to "json", "units" to "m", "language" to "en-US", "apiKey" to apiKey)))
        val own = station()?.trim()?.takeIf { INFO.option!!.third.matches(it) }
        val candidates = if (own != null) listOf(own) else nearbyStations(place, geo).take(3)
        val problems = mutableListOf<String>()
        for (sid in candidates) {
            val body = try {
                get(Http.query("$base/v2/pws/observations/current", mapOf("stationId" to sid, "format" to "json", "units" to "m", "numericPrecision" to "decimal", "apiKey" to apiKey)))
            } catch (e: WeatherException) { if (e.status == 204) { problems += "$sid: not reporting"; continue } else throw e }
            val ob = try { JSONObject(body).arr("observations")?.optJSONObject(0) } catch (e: JSONException) { null }
            if (ob?.obj("metric") == null) { problems += "$sid: no data"; continue }
            val age = now.epochSecond - (ob.num("epoch") ?: 0.0).toLong()
            if (age > 2 * 3600) { problems += "$sid: last report ${age / 3600} h ago"; continue }
            return parseWunderground(fc, ob, place, now)
        }
        throw WeatherException(if (own != null) "Your weather station $own isn't reporting (${problems.joinToString("; ").ifEmpty { "no data" }})."
            else "No nearby weather station is reporting current conditions.")
    }

    private fun nearbyStations(place: Place, geo: String): List<String> {
        nearby[place.key]?.let { if (System.currentTimeMillis() - it.first < 24 * 3_600_000L) return it.second }
        val o = json(get(Http.query("$base/v3/location/near", mapOf("geocode" to geo, "product" to "pws", "format" to "json", "apiKey" to apiKey))), "Weather Underground")
        val ids = o.obj("location")?.arr("stationId")
        val qc = o.obj("location")?.arr("qcStatus")
        val out = (0 until (ids?.length() ?: 0)).mapNotNull { i -> ids!!.str(i)?.takeIf { it.isNotBlank() && (qc?.num(i) ?: 0.0) >= 0 } }
        if (out.isEmpty()) throw WeatherException("Weather Underground found no personal weather stations near this place.")
        nearby[place.key] = System.currentTimeMillis() to out
        return out
    }
}

fun parseWunderground(fcBody: String, ob: JSONObject, place: Place, now: Instant = Instant.now()): Forecast {
    val f = json(fcBody, "Weather Underground")
    val valid = f.arr("validTimeLocal") ?: throw WeatherException("Weather Underground's reply has no forecast.")
    val zone = zoneOf(place)
    val parts = f.arr("daypart")?.optJSONObject(0) ?: JSONObject()
    val m = ob.obj("metric") ?: JSONObject()
    val curPart = if (parts.arr("iconCode")?.num(0) != null) 0 else 1
    var code = wuCode(parts.arr("iconCode")?.num(curPart))
    val cloud = pct(parts.arr("cloudCover")?.num(curPart))
    val rate = m.num("precipRate")
    val wet = code != null && code >= 51
    if (rate != null) {
        if (wet && rate <= 0.05) code = codeFrom(cloud, null) else if (!wet && rate > 0.05) code = codeFrom(null, rate)
    }
    val current = Current(quarterHour(now, zone), m.num("temp"), null, pct(ob.num("humidity")), code,
        Sun.isDay(now, place.latitude, place.longitude), m.num("windSpeed"), ob.num("winddir"), m.num("windGust"),
        m.num("pressure"), cloud, rate)
    val days = mutableListOf<Day>()
    for (i in 0 until valid.length()) {
        val date = (valid.str(i) ?: continue).take(10)
        val di = 2 * i; val ni = 2 * i + 1
        val dcode = wuCode(parts.arr("iconCode")?.num(di)) ?: wuCode(parts.arr("iconCode")?.num(ni))
        val chances = listOfNotNull(pct(parts.arr("precipChance")?.num(di)), pct(parts.arr("precipChance")?.num(ni)))
        val winds = listOfNotNull(parts.arr("windSpeed")?.num(di), parts.arr("windSpeed")?.num(ni))
        var hi = f.arr("calendarDayTemperatureMax")?.num(i) ?: f.arr("temperatureMax")?.num(i)
        var lo = f.arr("calendarDayTemperatureMin")?.num(i) ?: f.arr("temperatureMin")?.num(i)
        if (i == 0) current.temperature?.let { ct -> if (hi != null && ct > hi!!) hi = ct; if (lo != null && ct < lo!!) lo = ct }
        days += Day(date, dcode, hi, lo, f.arr("qpf")?.num(i), chances.maxOrNull(), winds.maxOrNull(),
            fromIso(f.arr("sunriseTimeLocal")?.str(i))?.let { localStr(it, zone) }, fromIso(f.arr("sunsetTimeLocal")?.str(i))?.let { localStr(it, zone) },
            parts.arr("uvIndex")?.num(di))
    }
    return Forecast(place, zone.id, zone.rules.getOffset(now).totalSeconds, current, emptyList(), days, "Weather Underground")
}

// ---------------------------------------------------------------- the catalogue
val OPEN_METEO_INFO = ProviderInfo("open-meteo", "Open-Meteo", false, "https://open-meteo.com/", OpenMeteo.CREDIT,
    "Worldwide. Blends national weather models (ECMWF, GFS, ICON, and in NZ/Australia the ACCESS-G model) at up to 1–11 km resolution.",
    "up to 16 days (RBSB-Weather uses 7)", "every hour, all 7 days", "Free, no key, for non-commercial use: fair use under 10,000 requests a day.")
val MET_NORWAY_INFO = ProviderInfo("met.no", "MET Norway", false, "https://api.met.no/", MetNorway.CREDIT,
    "Worldwide (yr.no's forecast), most detailed in the Nordic countries.", "about 9 days", "every hour for about 2.5 days, then every 6 hours",
    "Free, no key. Asks apps to identify themselves and not to ask too often.")

val ALL_PROVIDERS: List<ProviderInfo> = listOf(OPEN_METEO_INFO, MET_NORWAY_INFO, OpenWeatherProvider.INFO, WeatherApiProvider.INFO,
    VisualCrossingProvider.INFO, TomorrowProvider.INFO, WundergroundProvider.INFO)

fun providerInfo(id: String): ProviderInfo? = ALL_PROVIDERS.firstOrNull { it.id == id }
