// Earthquakes, mirroring the desktop app's test_quakes.py with the same fixtures (fixed time).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.ui.resource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

const val QNOW = 1795308960.0

class FakeQuakeServer : BytesClient {
    val mode = mutableMapOf("usgs" to "ok", "geonet" to "ok")
    val usgsPaths = mutableListOf<String>(); val geonetAccepts = mutableListOf<String>()
    override fun get(url: String, headers: Map<String, String>, limit: Int): ByteArray {
        val src = if ("geonet" in url) "geonet" else "usgs"
        if (src == "usgs") usgsPaths += url.substringAfter("/usgs/") else geonetAccepts += headers["Accept"] ?: ""
        return when (mode[src]) {
            "http500" -> throw WeatherException("HTTP 500", status = 500)
            "garbage" -> "<html>nope".toByteArray()
            "empty" -> """{"type": "FeatureCollection", "features": []}""".toByteArray()
            else -> resource(if (src == "usgs") "quakes_usgs.json" else "quakes_geonet.json").toByteArray()
        }
    }
    fun client() = QuakeClient(this, "http://fake/usgs/{feed}", "http://fake/geonet")
}

class QuakesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val usgs = parseUsgs(JSONObject(resource("quakes_usgs.json")))
    private val geonet = parseGeonet(JSONObject(resource("quakes_geonet.json")))
    private val all = mergeQuakes(usgs, geonet)

    @Test fun usgs() {
        assertEquals(5, usgs.size)                                   // the quarry blast and the broken one dropped
        val jp = usgs.first { "Japan" in it.place }
        assertEquals(Triple(38.2, 142.9, 30.0), Triple(jp.lat, jp.lon, jp.depthKm))
        assertTrue(jp.tsunami); assertEquals("yellow", jp.alert); assertTrue(jp.url.startsWith("https://earthquake.usgs.gov/"))
        assertEquals(1500.0, jp.age(QNOW), 5.0)
    }

    @Test fun geonet() {
        assertEquals(3, geonet.size)                                 // "deleted" dropped
        val s = geonet.first { "Seddon" in it.place }
        assertEquals("GeoNet", s.source); assertEquals(5.3, s.mag, 0.0); assertEquals(5.0, s.mmi!!, 0.0)
        assertEquals("https://www.geonet.org.nz/earthquake/2026p881001", s.url)
        assertEquals(620.0, s.age(QNOW), 5.0)
    }

    @Test fun mergePrefersGeonet() {
        assertEquals(listOf("GeoNet"), all.filter { kotlin.math.abs(it.lat + 41.7) < 0.1 }.map { it.source })
        assertEquals(7, all.size)
        assertEquals(all.sortedByDescending { it.time }, all)
        for (bad in listOf("{}", """{"features": "x"}""", """{"features": [null, 1, {"properties": {}}]}""")) {
            assertEquals(emptyList<Quake>(), parseUsgs(JSONObject(bad))); assertEquals(emptyList<Quake>(), parseGeonet(JSONObject(bad)))
        }
    }

    @Test fun roundTripAndValidation() {
        val q = all[0]
        assertEquals(q, Quake.fromJson(q.toJson()))
        try { Quake.fromJson(q.toJson().put("lat", 200)); fail() } catch (e: IllegalArgumentException) { }
        assertEquals("", Quake.fromJson(q.toJson().put("url", "file:///etc/passwd")).url)
    }

    @Test fun colourRadiusAndText() {
        val q = Quake("x", "USGS", QNOW - 600, 0.0, 0.0, 12.0, 5.4, "Somewhere")
        assertEquals(0xFFFF3B30, quakeColour(q, QNOW)); assertEquals(0xFFFF9500, quakeColour(q, QNOW + 3 * 3600))
        assertEquals(0xFFFFD60A, quakeColour(q, QNOW + 12 * 3600)); assertEquals(0xFFC7C9D1, quakeColour(q, QNOW + 3 * 86400))
        assertTrue(quakeRadius(2.5) < quakeRadius(5.0)); assertEquals(14.0, quakeRadius(9.5), 0.0)
        assertEquals("M5.4 · Somewhere · 12 km deep · 3 days ago (USGS)", describeQuake(q, QNOW + 3 * 86400))
        assertEquals("just now", quakeAgo(30.0)); assertEquals("5 hours ago", quakeAgo(3600 * 5.0))
    }

    @Test fun within() {
        assertEquals(5, quakesWithin(all, Quakes.PERIODS["day"]!!, 2.5, QNOW).size)
        assertEquals(7, quakesWithin(all, Quakes.PERIODS["week"]!!, 2.5, QNOW).size)
        assertEquals(listOf(5.3, 7.3), quakesWithin(all, Quakes.PERIODS["day"]!!, 4.5, QNOW).map { it.mag })
    }

    @Test fun nearAPlaceAndMajorAnywhere() {
        val got = quakesToNotify(all, listOf(WELLINGTON), NotifyRules(), emptyList(), QNOW)
        assertEquals(listOf(7.3, 5.3), got.map { it.first.mag })
        val (q, near) = got[1]
        assertEquals("Wellington", near!!.first.name)
        val (title, body) = quakeNotice(q, near, QNOW)
        assertTrue(title, Regex("^M5\\.3 earthquake \\d+ km from Wellington$").matches(title))
        assertTrue("20 km south-east of Seddon" in body)
        val (jt, jb) = quakeNotice(got[0].first, got[0].second, QNOW)
        assertTrue("tsunami advice" in jb); assertEquals("M7.3 earthquake: 90 km E of Ishinomaki, Japan", jt)
        fun mags(r: NotifyRules) = quakesToNotify(all, listOf(WELLINGTON), r, emptyList(), QNOW).map { it.first.mag }
        assertEquals(listOf(5.3), mags(NotifyRules(major = false))); assertEquals(listOf(7.3), mags(NotifyRules(near = false)))
        assertEquals(emptyList<Double>(), mags(NotifyRules(nearMag = 5.5, major = false))); assertEquals(emptyList<Double>(), mags(NotifyRules(nearKm = 20.0, major = false)))
        assertEquals(listOf(5.3), mags(NotifyRules(nearMag = 3.0, major = false)))
        assertTrue("from Tokyo" in quakeNotice(quakesToNotify(all, listOf(TOKYO), NotifyRules(), emptyList(), QNOW)[0].first,
            quakesToNotify(all, listOf(TOKYO), NotifyRules(), emptyList(), QNOW)[0].second, QNOW).first)
        // once only, and only recent quakes
        assertEquals(emptyList<Any>(), quakesToNotify(all, listOf(WELLINGTON), NotifyRules(), got.map { it.first.id }, QNOW))
        assertEquals(emptyList<Any>(), quakesToNotify(all, listOf(WELLINGTON), NotifyRules(), emptyList(), QNOW + 3 * 3600))
    }

    @Test fun fetchAndOneSourceFailing() {
        val srv = FakeQuakeServer()
        val (qs, errors) = srv.client().fetch()
        assertEquals(emptyMap<String, String>(), errors); assertEquals(7, qs.size)
        assertEquals(listOf("2.5_day"), srv.usgsPaths); assertEquals(listOf(Quakes.GEONET_ACCEPT), srv.geonetAccepts)
        srv.client().fetch("week", 4.5); assertEquals("4.5_week", srv.usgsPaths.last())
        val bad = FakeQuakeServer().apply { mode["geonet"] = "http500" }
        assertEquals(listOf("GeoNet"), bad.client().fetch().second.keys.toList())
    }

    @Test fun storeKeepsAFailedSourcesQuakes() {
        val st = QuakeStore(tmp.root)
        st.update(all, emptyMap(), QNOW)
        st.update(all.filter { it.source == "USGS" && "Japan" !in it.place }, mapOf("GeoNet" to "HTTP 500"), QNOW)
        assertEquals(3, st.quakes.count { it.source == "GeoNet" })
        assertTrue(st.quakes.none { "Japan" in it.place })
        st.notified += "a"; st.save()
        val back = QuakeStore(tmp.root).load()
        assertEquals(st.quakes, back.quakes); assertEquals(listOf("a"), back.notified)
        java.io.File(tmp.root, "quakes.json").writeText("[1,2")
        assertTrue(QuakeStore(tmp.root).load().quakes.isEmpty())
    }
}
