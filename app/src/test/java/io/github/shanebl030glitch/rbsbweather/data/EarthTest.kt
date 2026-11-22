// The Earth's day and night, the globe and the sky behind it, and live clouds, mirroring the
// desktop's test_earth.py, test_globe.py, test_sky.py and test_clouds.py (same PyEphem reference).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.ui.resource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.tan

class EarthTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ref = JSONObject(resource("earth_reference.json"))
    private fun t(s: String) = OffsetDateTime.parse(s).toInstant()

    @Test fun subsolarPointAndSunElevationMatchReference() {
        val a = ref.getJSONArray("subsolar")
        var wl = 0.0; var wo = 0.0
        for (i in 0 until a.length()) {
            val r = a.getJSONArray(i)
            val (lat, lon) = Earth.subsolarPoint(t(r.getString(0)))
            wl = maxOf(wl, abs(lat - r.getDouble(1))); wo = maxOf(wo, abs(((lon - r.getDouble(2) + 540) % 360) - 180))
        }
        assertTrue("lat $wl", wl < 0.05); assertTrue("lon $wo", wo < 0.05)
        val b = ref.getJSONArray("sun_alt")
        var worst = 0.0
        for (i in 0 until b.length()) {
            val r = b.getJSONArray(i)
            worst = maxOf(worst, abs(Earth.solarElevation(t(r.getString(3)), r.getDouble(1), r.getDouble(2)) - r.getDouble(4)))
        }
        assertTrue("elevation $worst", worst < 0.1)
    }

    @Test fun equinoxSolsticesAndDayStates() {
        assertEquals(0.0, Earth.subsolarPoint(Instant.parse("2026-03-20T14:46:00Z")).first, 0.05)
        assertEquals(23.44, Earth.subsolarPoint(Instant.parse("2026-06-21T08:24:00Z")).first, 0.05)
        assertEquals(-23.44, Earth.subsolarPoint(Instant.parse("2026-12-21T20:50:00Z")).first, 0.05)
        assertTrue(abs(Earth.subsolarPoint(Instant.parse("2026-11-15T12:00:00Z")).second) < 5)
        assertEquals("Day", Earth.dayState(30.0)); assertEquals("Day", Earth.dayState(-0.5))
        assertEquals("Twilight", Earth.dayState(-3.0)); assertEquals("Night", Earth.dayState(-12.0))
        val noon = Instant.parse("2026-11-15T00:15:00Z")
        assertEquals("Day", Earth.dayState(Earth.solarElevation(noon, -36.85, 174.76)))
        assertEquals("Night", Earth.dayState(Earth.solarElevation(noon, 51.51, -0.13)))
        // London at fixed times (the desktop's card test)
        assertEquals("Day", Earth.dayState(Earth.solarElevation(Instant.parse("2026-11-15T12:00:00Z"), 51.51, -0.13)))
        assertEquals("Twilight", Earth.dayState(Earth.solarElevation(Instant.parse("2026-11-15T16:45:00Z"), 51.51, -0.13)))
        assertEquals("Night", Earth.dayState(Earth.solarElevation(Instant.parse("2026-11-15T23:00:00Z"), 51.51, -0.13)))
    }

    @Test fun mapProjectionAndMasks() {
        val (x, y) = Earth.toMapXY(-36.85, 174.76, 3600.0, 1800.0)
        assertEquals(3547.6, x, 0.1); assertEquals(1268.5, y, 0.1)
        val (la, lo) = Earth.fromMapXY(x, y, 3600.0, 1800.0)
        assertEquals(-36.85, la, 1e-6); assertEquals(174.76, lo, 1e-6)
        assertEquals(Earth.toMapXY(0.0, 190.0, 360.0, 180.0).first, Earth.toMapXY(0.0, -170.0, 360.0, 180.0).first, 1e-9)
        val tm = Instant.parse("2026-11-15T00:15:00Z")
        val (day, shade) = Earth.masks(tm, 360, 180)
        val (slat, slon) = Earth.subsolarPoint(tm)
        fun at(a: Double, o: Double) = Earth.toMapXY(a, o, 360.0, 180.0).let { (mx, my) -> day[my.toInt() * 360 + (mx.toInt() % 360)] }
        assertEquals(1f, at(slat, slon), 1e-6f); assertEquals(0f, at(-slat, slon + 180), 1e-6f)
        assertEquals(360 * 180, shade.size)
        assertTrue(day.any { it > 0f && it < 1f })
    }

    @Test fun globeProjection() {
        val (x, y, vis) = Globe.project(51.5, -0.1, 51.5, -0.1)
        assertEquals(0.0, x, 1e-9); assertEquals(0.0, y, 1e-9); assertTrue(vis)
        assertFalse(Globe.project(-51.5, 179.9, 51.5, -0.1).third)
        assertEquals(1.0, Globe.project(0.0, 90.0, 0.0, 0.0).first, 1e-9)
        assertEquals(1.0, Globe.project(90.0, 0.0, 0.0, 0.0).second, 1e-9)
        val rng = Random(1); var worst = 0.0
        repeat(3000) {
            val cLat = rng.nextDouble() * 170 - 85; val cLon = rng.nextDouble() * 360 - 180
            val lat = rng.nextDouble() * 178 - 89; val lon = rng.nextDouble() * 360 - 180
            val (px, py, v) = Globe.project(lat, lon, cLat, cLon)
            if (!v || hypot(px, py) > 0.999) return@repeat
            val (a, o) = Globe.inverse(px, py, cLat, cLon)!!
            worst = maxOf(worst, abs(a - lat), abs(((o - lon + 540) % 360) - 180) * cos(Math.toRadians(lat)))
        }
        assertTrue("$worst", worst < 1e-6)
        assertNull(Globe.inverse(0.8, 0.8, 0.0, 0.0))
    }

    @Test fun skyBehindTheGlobe() {
        val b = Sky.basis(10.0, 20.0)
        assertNull(Sky.skyXY(Sky.ecef(10.0, 20.0), b))
        val xy = Sky.skyXY(Sky.ecef(-10.0, -160.0), b)!!
        assertEquals(0.0, xy.first, 1e-9); assertEquals(0.0, xy.second, 1e-9)
        assertTrue(Sky.hiddenByEarth(xy, 0.05))
        val b0 = Sky.basis(0.0, 0.0)
        for (theta in listOf(10.0, Sky.RHO, 55.0)) {
            val (sx, sy) = Sky.skyXY(Sky.ecef(0.0, 180 - theta), b0)!!
            assertEquals(tan(Math.toRadians(theta)) / Sky.TAN_RHO, hypot(sx, sy), 1e-9)
        }
        assertTrue(Sky.skyXY(Sky.ecef(0.0, 150.0), b0)!!.first > 0)      // east on the right
        assertTrue(Sky.skyXY(Sky.ecef(30.0, 180.0), b0)!!.second > 0)    // north up
        // the Sun touches the globe's edge exactly when the view's centre has the Sun RHO from its nadir
        val tm = Instant.parse("2026-11-15T00:15:00Z")
        val (slat, slon) = Earth.subsolarPoint(tm)
        val sun = Sky.ecef(slat, slon)
        for ((cl, co) in listOf(0.0 to slon + 180, 40.0 to slon + 150, -30.0 to slon - 140)) {
            val elev = Earth.solarElevation(tm, cl, co)
            val s = Sky.skyXY(sun, Sky.basis(cl, co))
            if (s == null) { assertTrue(elev >= -0.01); continue }
            val r = hypot(s.first, s.second)
            assertEquals(tan(Math.toRadians(90 + elev)) / Sky.TAN_RHO, r, 1e-6)
            assertEquals(90 + elev < Sky.RHO, r < 1)
        }
    }

    @Test fun moonLitSideFacesTheSunOnScreen() {
        for (row in listOf(doubleArrayOf(5.0, 170.0, -15.0, 150.0, 0.0, -10.0), doubleArrayOf(-20.0, -100.0, 18.0, -60.0, 30.0, 90.0),
            doubleArrayOf(40.0, 0.0, -10.0, 40.0, -50.0, 200.0), doubleArrayOf(0.0, 90.0, 0.0, 140.0, 60.0, -80.0))) {
            val m = Sky.ecef(row[0], row[1]); val s = Sky.ecef(row[2], row[3]); val b = Sky.basis(row[4], row[5])
            val (limb, rot) = Sky.moonOrientation(m, s, b)!!
            val mxy = Sky.skyXY(m, b)!!; val sxy = Sky.skyXY(s, b) ?: continue
            val want = ((Math.toDegrees(kotlin.math.atan2(sxy.first - mxy.first, sxy.second - mxy.second)) % 360) + 360) % 360
            // the lit side on screen: the bright limb (from north, through east = left in a north-up picture) turned by the rotation
            val lit = ((rot - limb) % 360 + 360) % 360
            val d = abs(((lit - want + 540) % 360) - 180)
            assertTrue("$d", d < 1.0)
        }
    }

    @Test fun starsTurnWithSiderealTime() {
        val b = Sky.basis(20.0, 30.0)
        val f = Sky.StarField(600, 3)
        val a = f.positions(100.0, b, 3.0); val c = f.positions(130.0, b, 3.0)
        assertTrue(a.isNotEmpty())
        assertTrue(a.all { hypot(it[0].toDouble(), it[1].toDouble()) in 1.0..3.0 })
        assertFalse(a.map { it[0] } == c.map { it[0] })
    }

    // --------------------------------------------------------------- clouds
    private class FakeCloud(var mode: String = "ok", var lastModified: String = "Sun, 15 Nov 2026 00:00:00 GMT") : ConditionalGet {
        val headers = mutableListOf<Map<String, String>>()
        override fun get(url: String, headers: Map<String, String>, limit: Int): Triple<Int, ByteArray, Map<String, String>> {
            this.headers += headers
            return when (mode) {
                "http404" -> Triple(404, ByteArray(0), emptyMap())
                "garbage" -> Triple(200, "nope".toByteArray(), mapOf("Last-Modified" to lastModified))
                "square" -> Triple(200, "SQUARE".toByteArray(), mapOf("Last-Modified" to lastModified))
                else -> if (headers["If-Modified-Since"] == lastModified) Triple(304, ByteArray(0), emptyMap())
                    else Triple(200, "JPEG$lastModified".toByteArray(), mapOf("Last-Modified" to lastModified, "ETag" to "\"e1\""))
            }
        }
    }
    private val size: (ByteArray) -> Pair<Int, Int>? = { b -> val s = String(b); when { s.startsWith("JPEG") -> 2048 to 1024; s == "SQUARE" -> 400 to 400; else -> null } }

    @Test fun cloudsDownloadThenNotModifiedThenNew() {
        var now = 1_000_000.0
        val st = CloudStore(tmp.root) { now }
        val srv = FakeCloud()
        assertTrue(st.due())
        assertEquals("updated", st.update(srv, size))
        assertEquals(Instant.parse("2026-11-15T00:00:00Z"), st.imageTime())
        assertFalse(st.due())
        val before = st.imageFile.lastModified()
        assertEquals("unchanged", st.update(srv, size))
        assertEquals("Sun, 15 Nov 2026 00:00:00 GMT", srv.headers.last()["If-Modified-Since"]); assertEquals("\"e1\"", srv.headers.last()["If-None-Match"])
        assertEquals(before, st.imageFile.lastModified())
        srv.lastModified = "Sun, 15 Nov 2026 03:00:00 GMT"
        assertEquals("updated", st.update(srv, size))
        assertEquals(3, st.imageTime()!!.atZone(java.time.ZoneOffset.UTC).hour)
        assertFalse(st.due(now + Clouds.CHECK_EVERY_S - 5)); assertTrue(st.due(now + Clouds.CHECK_EVERY_S + 5))
        // bad downloads keep the cached map; after a failure wait 15 minutes
        val good = st.imageFile.readBytes()
        srv.lastModified = "Sun, 15 Nov 2026 06:00:00 GMT"
        for ((mode, words) in listOf("garbage" to "not a readable image", "http404" to "HTTP 404", "square" to "unexpected size")) {
            srv.mode = mode
            try { st.update(srv, size); fail(mode) } catch (e: CloudException) { assertTrue(e.message!!, words in e.message!!) }
            assertTrue(good.contentEquals(st.imageFile.readBytes()))
        }
        assertFalse(st.due(now + 60)); assertTrue(st.due(now + Clouds.RETRY_AFTER_FAIL_S + 5))
        assertTrue(st.note(true, "x", ClockFormat.H24, Instant.parse("2026-11-16T00:00:00Z")).contains("couldn't get a newer one"))
        assertTrue(st.note(false, "", ClockFormat.H24).contains("once they've downloaded"))
    }

    @Test fun wrongShapeFirstTimeSavesNothingAndCorruptMetaIgnored() {
        val st = CloudStore(tmp.root)
        try { st.update(FakeCloud("square"), size); fail() } catch (e: CloudException) { }
        assertFalse(st.imageFile.exists())
        java.io.File(tmp.root, "clouds.json").writeText("{{{")
        assertTrue(st.due())
    }

    @Test fun moonAgeDays() {
        val age = Moon.ageDays(Instant.parse("2026-11-15T00:00:00Z"))
        assertTrue("$age", age > 5 && age < 6.5)
    }
}
