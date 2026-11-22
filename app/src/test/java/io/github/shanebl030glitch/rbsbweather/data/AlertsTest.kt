// Weather warnings, mirroring the desktop app's test_alerts.py, with the same fixture files
// (written to each service's documented format). Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.ui.resource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val WELLINGTON = Place("Wellington", -41.2866, 174.7756, "New Zealand", "Wellington", "Pacific/Auckland")
val CHRISTCHURCH = Place("Christchurch", -43.5321, 172.6362, "New Zealand", "Canterbury", "Pacific/Auckland")
val LONDON = Place("London", 51.50853, -0.12574, "United Kingdom", "England", "Europe/London")
val PARIS = Place("Paris", 48.85341, 2.3488, "France", "Île-de-France", "Europe/Paris")
val NEW_YORK = Place("New York", 40.71427, -74.00597, "United States", "New York", "America/New_York")
val OSLO = Place("Oslo", 59.91273, 10.74609, "Norway", "Oslo", "Europe/Oslo")
val TOKYO = Place("Tokyo", 35.6895, 139.69171, "Japan", "Tokyo", "Asia/Tokyo")

/** A fixture with its times filled in relative to now (as the desktop's alert_fixtures.fill). */
fun alertFixture(name: String, base: String = "http://fake", onsetH: Double = -1.0, expiresH: Double = 30.0): String {
    val now = Instant.now().epochSecond
    fun iso(h: Double) = Instant.ofEpochSecond(now + (h * 3600).toLong()).atOffset(ZoneOffset.UTC).toString()
    return resource("alerts/$name").replace("{BASE}", "$base/alerts-metservice").replace("{ONSET}", iso(onsetH)).replace("{EXPIRES}", iso(expiresH))
}

/** Serves the warning fixtures like the desktop's fake server. mode[source] = ok | none | http500 | garbage. */
class FakeAlertServer : BytesClient {
    val mode = mutableMapOf("metservice" to "ok", "nws" to "ok", "metno" to "ok", "meteoalarm" to "ok")
    val hits = mutableMapOf<String, Int>()
    val accepts = mutableListOf<String>()
    var expiresH = 30.0
    override fun get(url: String, headers: Map<String, String>, limit: Int): ByteArray {
        val path = url.substringAfter("http://fake").substringBefore("?")
        hits[path] = (hits[path] ?: 0) + 1
        accepts += headers["Accept"] ?: ""
        val src = path.removePrefix("/alerts-").substringBefore("/")
        when (mode[src]) {
            "http500" -> throw WeatherException("The service had a problem (HTTP 500).", status = 500)
            "garbage" -> return "<<<not xml or json".toByteArray()
        }
        val none = mode[src] == "none"
        val body = when (src) {
            "metservice" -> when {
                path.endsWith("/cap/rss") -> alertFixture("metservice_rss.xml").let { if (none) it.substringBefore("<item>") + "</channel></rss>" else it }
                path.endsWith("rain-auckland.xml") -> alertFixture("cap_rain_auckland.xml", expiresH = expiresH)
                path.endsWith("wind-wellington.xml") -> alertFixture("cap_wind_wellington.xml", expiresH = expiresH)
                path.endsWith("test.xml") -> alertFixture("cap_test.xml")
                else -> throw WeatherException("HTTP 404", status = 404)
            }
            "meteoalarm" -> if (!path.endsWith("/united-kingdom")) throw WeatherException("HTTP 404", status = 404)
                else if (none) "<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>" else alertFixture("meteoalarm_uk.xml", expiresH = expiresH)
            "nws" -> if (none) """{"type": "FeatureCollection", "features": []}""" else alertFixture("nws_point.json", expiresH = expiresH)
            "metno" -> if (none) """{"type": "FeatureCollection", "features": []}""" else alertFixture("metno_current.json", expiresH = expiresH)
            else -> throw WeatherException("HTTP 404", status = 404)
        }
        return body.toByteArray()
    }
    fun client() = AlertClient(this, "http://fake/alerts-metservice/cap/rss", "http://fake/alerts-nws", "http://fake/alerts-metno", "http://fake/alerts-meteoalarm/{slug}")
}

class AlertsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun pointInPolygonAndAcrossTheDateLine() {
        val ring = parseCapPolygon("-36.30,174.40 -36.30,175.60 -37.30,175.60 -37.30,174.40 -36.30,174.40")
        assertTrue(pointInRing(-36.85, 174.76, ring)); assertFalse(pointInRing(-41.29, 174.78, ring))
        val chatham = parseCapPolygon("-43,176 -43,-176 -45,-176 -45,176 -43,176")
        assertTrue(pointInRing(-44.0, -176.5, chatham)); assertTrue(pointInRing(-44.0, 179.0, chatham)); assertFalse(pointInRing(-44.0, 170.0, chatham))
        assertEquals(emptyList<Pair<Double, Double>>(), parseCapPolygon("1,2 3"))
        assertEquals(emptyList<Pair<Double, Double>>(), parseCapPolygon("95,0 1,1 2,2"))
        assertNull(covers(emptyList(), 0.0, 0.0))
        assertTrue(inCircle(-36.85, 174.76, "-36.9,174.8 20")); assertFalse(inCircle(-41.3, 174.8, "-36.9,174.8 20"))
        val g = JSONObject("""{"type": "Polygon", "coordinates": [[[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]], [[4, 4], [6, 4], [6, 6], [4, 6], [4, 4]]]}""")
        assertEquals(true, geojsonContains(g, 2.0, 2.0)); assertEquals(false, geojsonContains(g, 5.0, 5.0)); assertNull(geojsonContains(null, 5.0, 5.0))
    }

    @Test fun levels() {
        assertEquals("orange", levelFrom("Moderate", "rain", "Orange", "Heavy Rain Warning - Orange", trustSeverity = false))
        assertEquals("watch", levelFrom("Minor", "wind", "Yellow", "Strong Wind Watch", trustSeverity = false))
        assertEquals("red", levelFrom("Severe", "rain", "Red", "", trustSeverity = false))
        assertEquals("red", levelFrom("Extreme", "Tornado Warning")); assertEquals("orange", levelFrom("Severe", "Flood Warning"))
        assertEquals("watch", levelFrom("Moderate", "Winter Storm Watch")); assertEquals("minor", levelFrom("Minor", "Special Weather Statement"))
        assertEquals("yellow", levelFrom("", "Some Warning")); assertEquals("orange", levelFrom("Moderate", "Rain", "3; orange; Severe"))
        assertEquals("Orange Heavy Rain Warning", Alert("x", "MetService", "orange", "Heavy Rain Warning").title())
        assertEquals("Strong Wind Watch", Alert("y", "s", "watch", "Strong Wind Watch").title())
    }

    @Test fun metserviceCap() {
        val (a, areas) = capAlert(parseXml(alertFixture("cap_rain_auckland.xml").toByteArray()), "MetService", "http://x/cap", "ColourCode", false)!!
        assertEquals("orange", a.level); assertEquals("Heavy Rain Warning", a.event); assertEquals("rain", a.hazard)
        assertTrue("100 to 140 mm" in a.description); assertTrue(a.instruction.startsWith("Streams"))
        assertEquals("Auckland, including the Hauraki Gulf islands", a.area)
        assertEquals(true, covers(areas, AUCKLAND.latitude, AUCKLAND.longitude)); assertEquals(false, covers(areas, WELLINGTON.latitude, WELLINGTON.longitude))
        assertTrue(a.inForce())
        assertNull(capAlert(parseXml(alertFixture("cap_test.xml").toByteArray()), "MetService"))
        assertNull(capAlert(parseXml(alertFixture("cap_rain_auckland.xml").replace("<msgType>Update</msgType>", "<msgType>Cancel</msgType>").toByteArray()), "MetService"))
    }

    @Test fun nwsAndMetNorway() {
        val al = parseNws(JSONObject(alertFixture("nws_point.json")))
        assertEquals(1, al.size)
        assertEquals(Triple("watch", "Winter Storm Watch", "snow"), Triple(al[0].level, al[0].event, al[0].hazard))
        assertTrue("6 to 10 inches" in al[0].description); assertTrue(al[0].url.startsWith("https://api.weather.gov/alerts/"))
        val mn = parseMetno(JSONObject(alertFixture("metno_current.json")), OSLO.latitude, OSLO.longitude)
        assertEquals(1, mn.size)
        assertEquals(Triple("orange", "Wind", "wind"), Triple(mn[0].level, mn[0].event, mn[0].hazard))
        assertTrue("Trees may fall" in mn[0].description)
        for (bad in listOf("{}", """{"features": "x"}""", """{"features": [null, 1, {"properties": null}]}""")) {
            assertEquals(emptyList<Alert>(), parseNws(JSONObject(bad))); assertEquals(emptyList<Alert>(), parseMetno(JSONObject(bad), 0.0, 0.0))
        }
    }

    @Test fun doctypeAndEntitiesAreRefused() {
        try { parseXml("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY a "aaaa">]><x>&a;</x>""".toByteArray()); fail() } catch (e: AlertException) { }
    }

    @Test fun roundTripKeepsOnlyWebLinks() {
        val a = Alert("id1", "MetService", "red", "Heavy Rain Warning", onset = 1.0, expires = 2.0, url = "javascript:alert(1)")
        val b = Alert.fromJson(a.toJson())
        assertEquals("", b.url); assertEquals("red", b.level)
        assertEquals("minor", Alert.fromJson(JSONObject("""{"id": "z", "level": "purple"}""")).level)
    }

    @Test fun sources() {
        assertEquals("metservice", alertSourceFor(AUCKLAND)); assertEquals("nws", alertSourceFor(NEW_YORK))
        assertEquals("metno", alertSourceFor(OSLO)); assertEquals("meteoalarm", alertSourceFor(LONDON)); assertEquals("meteoalarm", alertSourceFor(PARIS))
        assertNull(alertSourceFor(TOKYO)); assertEquals("Not covered yet", coverageText(TOKYO))
        assertEquals("metservice", alertSourceFor(Place("Somewhere", -40.0, 175.0)))
    }

    @Test fun everySourceMatchedToTheRightPlaces() {
        val srv = FakeAlertServer()
        val (got, errors) = srv.client().fetchAll(listOf(AUCKLAND, WELLINGTON, CHRISTCHURCH, LONDON, NEW_YORK, OSLO, TOKYO))
        assertEquals(emptyMap<String, String>(), errors)
        assertEquals(listOf("Orange Heavy Rain Warning"), got[AUCKLAND.key]!!.map { it.title() })
        assertEquals(listOf("Strong Wind Watch"), got[WELLINGTON.key]!!.map { it.title() })
        assertEquals(emptyList<Alert>(), got[CHRISTCHURCH.key])
        assertEquals(listOf("yellow" to "MeteoAlarm"), got[LONDON.key]!!.map { it.level to it.source })
        assertEquals(listOf("Winter Storm Watch"), got[NEW_YORK.key]!!.map { it.event })
        assertEquals(listOf("orange"), got[OSLO.key]!!.map { it.level })
        assertFalse(TOKYO.key in got)
    }

    @Test fun capDocumentsAreFetchedOnce() {
        val srv = FakeAlertServer(); val c = srv.client()
        c.fetchAll(listOf(AUCKLAND)); c.fetchAll(listOf(AUCKLAND))
        assertEquals(2, srv.hits["/alerts-metservice/cap/rss"]); assertEquals(1, srv.hits["/alerts-metservice/cap/rain-auckland.xml"])
    }

    @Test fun oneSourceFailingKeepsTheOthers() {
        val srv = FakeAlertServer().apply { mode["metservice"] = "http500"; mode["nws"] = "garbage" }
        val (got, errors) = srv.client().fetchAll(listOf(AUCKLAND, LONDON, NEW_YORK))
        assertTrue("metservice" in errors && "nws" in errors)
        assertFalse(AUCKLAND.key in got); assertFalse(NEW_YORK.key in got)
        assertEquals(1, got[LONDON.key]!!.size)
        val (_, e2) = FakeAlertServer().apply { mode["metservice"] = "garbage" }.client().fetchAll(listOf(AUCKLAND))
        assertTrue(e2["metservice"]!!, "couldn't be read" in e2["metservice"]!!)
        val (_, e3) = FakeAlertServer().client().fetchAll(listOf(PARIS))
        assertTrue("France" in (e3["meteoalarm"] ?: ""))
        val none = FakeAlertServer().apply { mode.keys.forEach { mode[it] = "none" } }
        val (g4, e4) = none.client().fetchAll(listOf(AUCKLAND, LONDON, NEW_YORK, OSLO))
        assertEquals(emptyMap<String, String>(), e4); assertEquals(4, g4.size); assertTrue(g4.values.all { it.isEmpty() })
    }

    private fun limitsFc(gusts: List<Double?> = listOf(72.0, 95.0, 40.0, 30.0, 30.0, 50.0, 40.0)): Forecast {
        val off = 13 * 3600
        val today = Instant.now().atOffset(ZoneOffset.ofTotalSeconds(off)).toLocalDate()
        val rain = listOf(64.0, 38.0, 0.4, 0.0, 0.0, 0.0, 0.0); val tmin = listOf(12.0, 13.0, 12.0, 11.0, 10.0, -2.0, 9.0)
        val tmax = listOf(16.0, 17.0, 19.0, 21.0, 22.0, 20.0, 18.0); val codes = listOf(65, 63, 3, 0, 1, 95, 2)
        val daily = (0 until 7).map { i -> Day(today.plusDays(i.toLong()).toString(), codes[i], tmax[i], tmin[i], rain[i], 50.0, 30.0, null, null, 3.0, gusts.getOrNull(i)) }
        val start = today.atStartOfDay()
        val hourly = (0 until 48).map { h -> Hour(start.plusHours(h.toLong()).toString(), 14.0, 80.0, 2.0, 63) }
        val cur = Current("", 14.0, 12.0, 90.0, 65, true, 30.0, 45.0, 70.0, 1000.0, 100.0, 2.0)
        return Forecast(AUCKLAND, "Pacific/Auckland", off, cur, hourly, daily, "Open-Meteo", System.currentTimeMillis())
    }

    @Test fun ownLimits() {
        val al = ownAlerts(limitsFc(), DEFAULT_LIMITS)
        val titles = al.map { it.event }
        assertTrue(titles.toString(), "My limit: wind gusts over 90 km/h" in titles)
        assertTrue(titles.toString(), "My limit: rain over 50 mm in a day" in titles || "My limit: rain over 50.0 mm in a day" in titles)
        assertTrue("My limit: temperature below 0°C" in titles); assertTrue("My limit: thunderstorms" in titles)
        assertFalse("My limit: snow" in titles)
        assertTrue(al.all { !it.official && it.level == "limit" })
        val gust = al.first { it.hazard == "wind" }
        assertTrue(gust.headline, "Tomorrow" in gust.headline); assertTrue("not an official warning" in gust.description)
        val lim = limitsFromJson(JSONObject("""{"rain": {"on": true, "value": 30}, "gust": {"on": false, "value": 90}}"""))
        val al2 = ownAlerts(limitsFc(), lim, UnitPrefs(TempUnit.F, WindUnit.MPH, PrecipUnit.IN))
        val rain = al2.first { it.hazard == "rain" }
        assertTrue(rain.event, "1.18 in" in rain.event); assertTrue("Also: Tomorrow" in rain.headline)
        assertFalse(al2.any { it.hazard == "wind" })
    }

    @Test fun limitsAreValidated() {
        val lim = limitsFromJson(JSONObject("""{"gust": {"on": "yes", "value": 10000}, "rain": {"value": true}, "x": 1}"""))
        assertTrue(lim["gust"]!!.on); assertEquals(250.0, lim["gust"]!!.value, 0.0); assertEquals(50.0, lim["rain"]!!.value, 0.0)
        assertEquals(250.0, limitsToJson(lim).getJSONObject("gust").getDouble("value"), 0.0)
    }

    @Test fun gustsMissingUsesTodaysCurrentGustOnly() {
        val fc = limitsFc(emptyList()).let { it.copy(current = it.current.copy(windGusts = 100.0)) }
        val lim = DEFAULT_LIMITS.mapValues { (k, v) -> v.copy(on = k == "gust") }
        val al = ownAlerts(fc, lim)
        assertEquals(1, al.size); assertTrue(al[0].headline, "today" in al[0].headline)
    }

    @Test fun rainBetweenHours() {
        val fc = limitsFc()
        val start = java.time.LocalDateTime.parse(fc.hourly[0].time).toEpochSecond(ZoneOffset.ofTotalSeconds(fc.utcOffsetSeconds)).toDouble()
        assertEquals(20.0, rainBetween(fc, start, start + 10 * 3600)!!, 0.0)
        assertNull(rainBetween(fc, null, start))
    }

    private fun sample(): MutableList<Alert> {
        val now = nowS()
        return mutableListOf(Alert("r", "s", "red", "Red one", expires = now + 3600), Alert("o", "s", "orange", "Orange one", expires = now + 3600),
            Alert("w", "s", "watch", "Some Watch", expires = now + 3600), Alert("m", "s", "minor", "Statement", expires = now + 3600),
            Alert("old", "s", "red", "Expired", expires = now - 10), Alert("lim", "My limits", "limit", "My limit", official = false, expires = now + 3600))
    }

    @Test fun shownLevelsAndMuting() {
        val al = sample()
        fun ids(l: String) = shownAlerts(al, l).map { it.id }
        assertEquals(listOf("r", "o", "w", "m", "lim"), ids("all")); assertEquals(listOf("r", "o", "w", "lim"), ids("watch"))
        assertEquals(listOf("r", "o", "lim"), ids("orange")); assertEquals(listOf("r", "lim"), ids("red"))
        assertEquals(listOf("r", "w", "m", "lim"), shownAlerts(al, "all", setOf("o")).map { it.id })
        assertEquals("r", worstAlert(shownAlerts(al, "watch"))!!.id)
    }

    @Test fun notifyOnceThenAgainWhenUpgradedOrReissued() {
        val al = sample(); val notified = mutableMapOf<String, String>()
        val first = alertsToNotify(al, notified, "watch")
        assertEquals(listOf("r", "o", "w", "lim"), first.map { it.id })
        first.forEach { notified[it.id] = it.signature() }
        assertEquals(emptyList<Alert>(), alertsToNotify(al, notified, "watch"))
        al[1] = al[1].copy(level = "red")
        assertEquals(listOf("o"), alertsToNotify(al, notified, "watch").map { it.id }); notified["o"] = al[1].signature()
        al[2] = al[2].copy(issued = nowS())
        assertEquals(listOf("w"), alertsToNotify(al, notified, "watch").map { it.id }); notified["w"] = al[2].signature()
        al[2] = al[2].copy(expires = al[2].expires!! + 60)
        assertEquals(emptyList<Alert>(), alertsToNotify(al, notified, "watch"))
    }

    @Test fun storeRoundTripCleanUpAndDamage() {
        val st = AlertStore(tmp.root)
        st.alerts[AUCKLAND.key] = listOf(Alert("a1", "MetService", "orange", "Rain", expires = nowS() + 3600), Alert("lim", "My limits", "limit", "x", official = false))
        st.notified["a1"] = "orange|1"; st.notified["gone"] = "red|1"; st.notified["limit:rain:x"] = "limit|1"
        st.update(emptyMap(), listOf(AUCKLAND.key))
        assertEquals(setOf("a1", "limit:rain:x"), st.notified.keys)
        st.checked = 123.0; st.save()
        val back = AlertStore(tmp.root).load()
        assertEquals(listOf("a1"), back.alerts[AUCKLAND.key]!!.map { it.id })                 // own limits aren't saved
        assertEquals(123.0, back.checked, 0.0)
        java.io.File(tmp.root, "alerts.json").writeText("{{{ damaged")
        assertTrue(AlertStore(tmp.root).load().alerts.isEmpty())
    }

    @Test fun settingsRoundTripAndBadValuesFallBack() {
        val s = Settings(alerts = AlertSettings(minLevel = "red", interval = 60, muted = setOf("x"), redAlarm = true),
            quakes = QuakeSettings(period = "week", minMag = 4.5, nearKm = 100))
        val b = Settings.fromJson(s.toJson().toString())
        assertEquals(s.alerts, b.alerts); assertEquals(s.quakes, b.quakes)
        val junk = Settings.fromJson("""{"alerts": {"min_level": "purple", "interval": 7, "official": "yes"}, "quakes": {"period": "year", "min_mag": 3.3, "near_km": 1}}""")
        assertEquals(AlertSettings(), junk.alerts); assertEquals(QuakeSettings(), junk.quakes)
    }

    @Test fun openMeteoDailyGustsParsed() {
        val body = resource("openmeteo_forecast.json").replace("\"uv_index_max\":", "\"wind_gusts_10m_max\": [61, 72, 50, 40, 30, 20, 10], \"uv_index_max\":")
        val fc = parseForecast(body, AUCKLAND)
        assertEquals(61.0, fc.daily[0].gustMax!!, 0.0)
        assertEquals(61.0, Forecast.fromJson(fc.toJson())!!.daily[0].gustMax!!, 0.0)
        assertNotNull(LocalDate.parse(fc.daily[0].date))
    }
}
