// The GRIB2 reader and the wind data (0.6), mirroring the desktop's test_grib2.py and test_wind.py
// with the same files. The GRIB2 answers come from the desktop's reader, which is itself checked
// against ECMWF's ecCodes. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

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
import kotlin.math.abs
import kotlin.math.hypot

fun gribBytes(name: String): ByteArray = Grib2Test::class.java.classLoader!!.getResourceAsStream("grib2/$name")!!.readBytes()

class Grib2Test {
    @Test fun everyFileMatchesTheDesktopReader() {
        val exp = JSONObject(String(gribBytes("expected.json")))
        for (name in exp.keys()) {
            var msgs = Grib2.messages(gribBytes(name))
            if (name == "gfs_2p5_wind10m.grib2") msgs = msgs.filter { it.levelType == 103 }
            val list = exp.getJSONArray(name)
            assertEquals(name, list.length(), msgs.size)
            for (k in 0 until list.length()) {
                val e = list.getJSONObject(k); val m = msgs[k]
                assertEquals(name, listOf(e.getInt("ni"), e.getInt("nj"), e.getInt("category"), e.getInt("number"), e.getInt("scan")),
                    listOf(m.grid.ni, m.grid.nj, m.category, m.number, m.grid.scan))
                val s = e.getJSONObject("samples")
                for (i in s.keys()) {
                    val got = m.values[i.toInt()]
                    if (s.isNull(i)) assertTrue("$name[$i] missing", got.isNaN()) else assertEquals("$name[$i]", s.getDouble(i), got, 1e-4)
                }
                assertEquals(name, e.getInt("missing"), m.values.count { it.isNaN() })
                var sum = 0.0; var wsum = 0.0
                m.values.forEachIndexed { i, v -> if (!v.isNaN()) { sum += v; wsum += (i % 101 + 1) * v } }
                assertEquals("$name sum", e.getDouble("sum"), sum, 1e-6 * maxOf(1.0, abs(sum)))
                assertEquals("$name weighted sum", e.getDouble("wsum"), wsum, 1e-6 * maxOf(1.0, abs(wsum)))
            }
        }
    }

    @Test fun realGfsWindAndRowOrder() {
        val msgs = Grib2.messages(gribBytes("gfs_2p5_wind10m.grib2")).filter { it.levelType == 103 && it.level == 10.0 }
        assertEquals(listOf(2, 3), msgs.map { it.number }.sorted())
        val m = msgs[0]
        assertEquals(listOf(144, 73), listOf(m.grid.ni, m.grid.nj))
        assertEquals(listOf(90.0, 0.0, -90.0), listOf(m.grid.la1, m.grid.lo1, m.grid.la2))
        assertEquals(120.0, m.forecastHours!!, 0.0)
        val speeds = m.values.indices.map { hypot(msgs[0].values[it], msgs[1].values[it]) }
        assertTrue(speeds.max() < 60); assertTrue(speeds.average() > 2)
        // a file stored south first reads the same as the usual north-first one
        val a = Grib2.messages(gribBytes("syn_small_south_first.grib2"))[0]; val b = Grib2.messages(gribBytes("syn_small_csd1.grib2"))[0]
        val ra = a.rowsNorthToSouth(); val rb = b.rowsNorthToSouth()
        assertEquals(rb.size, ra.size)
        for (i in ra.indices) assertEquals(rb[i], ra[i], 1e-3)
    }

    @Test fun damagedFilesFailCleanly() {
        val good = gribBytes("syn_small_csd1.grib2")
        for (bad in listOf(ByteArray(0), "GRIB".toByteArray(), good.copyOf(good.size - 10), good.copyOf().also { it[it.size - 1] = 0 },
                good.copyOf().also { it[7] = 1 }, ByteArray(100) { 7 })) {
            try { Grib2.messages(bad); fail() } catch (e: Grib2Exception) { }
        }
        assertEquals(-5L, Grib2.s(byteArrayOf(0x80.toByte(), 5), 0, 2))      // sign-magnitude, not two's complement
        assertEquals(5L, Grib2.s(byteArrayOf(0, 5), 0, 2))
        assertTrue(Grib2.isGrib2(good)); assertFalse(Grib2.isGrib2("GRIB".toByteArray()))
    }
}

class WindTest {
    @get:Rule val tmp = TemporaryFolder()
    private val utc = { s: String -> Instant.parse(s) }

    @Test fun newestPublishedRunAndClosestHour() {
        val runs = Wind.runsToTry(utc("2026-11-21T09:40:00Z"))                     // 06Z isn't out until ~10Z
        assertEquals(utc("2026-11-21T00:00:00Z") to 9, runs[0]); assertEquals(utc("2026-11-20T18:00:00Z") to 15, runs[1])
        assertEquals(utc("2026-11-21T06:00:00Z") to 3, Wind.runsToTry(utc("2026-11-21T10:10:00Z"))[0])
    }

    @Test fun urlsAndIndex() {
        val run = utc("2026-11-21T06:00:00Z")
        val u = java.net.URI(Wind.nomadsUrl(run, 9))
        assertEquals("nomads.ncep.noaa.gov", u.host)
        val q = u.rawQuery.split("&").associate { it.substringBefore("=") to java.net.URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        assertEquals("/gfs.20261121/06/atmos", q["dir"]); assertEquals("gfs.t06z.pgrb2.1p00.f009", q["file"])
        for (k in listOf("var_UGRD", "var_VGRD", "var_TMP", "var_PRATE", "lev_10_m_above_ground", "lev_2_m_above_ground", "lev_surface")) assertEquals(k, "on", q[k])
        val (data, idx) = Wind.awsUrls(run, 9)
        assertEquals("https://noaa-gfs-bdp-pds.s3.amazonaws.com/gfs.20261121/06/atmos/gfs.t06z.pgrb2.1p00.f009", data); assertEquals("$data.idx", idx)
        assertEquals(listOf(500L to 899L, 900L to 1399L), Wind.byteRangesFromIndex("1:0:d=x:PRMSL:mean sea level:anl:\n2:500:d=x:UGRD:10 m above ground:anl:\n" +
            "3:900:d=x:VGRD:10 m above ground:anl:\n4:1400:d=x:UGRD:20 m above ground:anl:\n"))
        val full = "1:0:d=x:PRMSL:mean sea level:6 hour fcst:\n2:500:d=x:TMP:surface:6 hour fcst:\n3:800:d=x:TMP:2 m above ground:6 hour fcst:\n" +
            "4:1100:d=x:UGRD:10 m above ground:6 hour fcst:\n5:1500:d=x:VGRD:10 m above ground:6 hour fcst:\n6:1900:d=x:PRATE:surface:0-6 hour ave fcst:\n" +
            "7:2300:d=x:APCP:surface:0-6 hour acc fcst:\n"
        assertEquals(listOf(800L to 1099L, 1100L to 1499L, 1500L to 1899L, 1900L to 2299L), Wind.byteRangesFromIndex(full))
        assertEquals(listOf(300L to null), Wind.byteRangesFromIndex("1:0:d=x:A:b:c:\n2.1:300:d=x:UGRD:10 m above ground:c:\n2.2:300:d=x:VGRD:10 m above ground:c:\n"))
        try { Wind.byteRangesFromIndex("1:0:d=x:TMP:2 m above ground:anl:\n"); fail() } catch (e: WindException) { }
    }

    @Test fun directionConvention() {
        assertEquals(5.0 to 0.0, Wind.speedAndDirection(0.0, -5.0))             // blowing south = from the north
        assertEquals(270.0, Wind.speedAndDirection(5.0, 0.0).second, 1e-9)      // blowing east = from the west
        assertEquals(36.87, Wind.speedAndDirection(-3.0, -4.0).second, 0.01)    // from the NE
    }

    @Test fun realField() {
        val f = Wind.fieldFromGrib(gribBytes("gfs_2p5_wind10m.grib2"))
        assertEquals(listOf(144.0, 73.0, 90.0, 0.0, 2.5, 2.5), listOf(f.ni.toDouble(), f.nj.toDouble(), f.lat0, f.lon0, f.dlat, f.dlon))
        assertEquals(120, f.fhour)
        val (u, v) = f.at(-35.0, 175.0)
        assertEquals(f.u[50 * 144 + 70].toDouble(), u, 1e-4); assertEquals(f.v[50 * 144 + 70].toDouble(), v, 1e-4)
        val um = f.at(-36.25, 176.25).first
        val corners = listOf(50, 51).flatMap { j -> listOf(70, 71).map { i -> f.u[j * 144 + i].toDouble() } }
        assertTrue(um >= corners.min() - 1e-6 && um <= corners.max() + 1e-6)
        assertEquals(f.at(10.0, 0.0), f.at(10.0, 360.0)); assertEquals(f.at(10.0, -180.0), f.at(10.0, 180.0)); assertEquals(f.at(-90.0, 10.0), f.at(-95.0, 10.0))
        assertEquals(listOf("wind"), f.layers)
        val l = Wind.fieldFromGrib(gribBytes("gfs_2p5_layers.grib2"))
        assertEquals(listOf("wind", "temp", "rain"), l.layers)
        val t = l.tempAt(-36.85, 174.76)!!; assertTrue("$t", t in -10.0..40.0)
        assertTrue(l.rainAt(0.0, 0.0)!! >= 0)
        val world = (0 until l.nj * l.ni).map { l.temp!![it] }
        assertTrue(world.max() < 60 && world.min() > -90)
    }

    @Test fun coloursAndPixels() {
        assertEquals(listOf(52, 64, 158), Wind.colourFor(0.0).toList()); assertEquals(listOf(226, 176, 236), Wind.colourFor(99.0).toList())
        assertEquals(listOf(49, 85, 177), Wind.colourFor(1.0).toList())
        assertEquals(0, Wind.rainAlpha(0.01)); assertEquals(90, Wind.rainAlpha(0.05)); assertEquals(255, Wind.rainAlpha(5.0))
        val f = Wind.fieldFromGrib(gribBytes("gfs_2p5_layers.grib2"))
        val px = Wind.layerPixels(f, "wind")
        assertEquals(f.ni * f.nj, px.size)
        assertTrue(px.all { (it ushr 24) == 255 })
        val rain = Wind.layerPixels(f, "rain")
        assertTrue(rain.any { (it ushr 24) == 0 }); assertTrue(rain.any { (it ushr 24) > 0 })            // dry is see-through
        assertTrue(Wind.layerPixels(Wind.fieldFromGrib(gribBytes("gfs_2p5_wind10m.grib2")), "temp").all { (it ushr 24) == 255 })   // falls back to wind
    }

    /** A pretend NOAA: NOMADS answers or fails, AWS serves the index and byte ranges. */
    private class FakeNoaa(var nomads: String = "ok", var aws: String = "ok") {
        val file = gribBytes("gfs_2p5_layers.grib2")
        val asked = mutableListOf<String>()
        val bytes = BytesClient { url, headers, _ ->
            asked += url
            when {
                "nomads" in url -> if (nomads == "ok") file else if (nomads == "garbage") "<html>busy</html>".toByteArray() else throw WeatherException("HTTP 503", status = 503)
                url.endsWith(".idx") -> if (aws == "ok") "1:0:d=x:UGRD:10 m above ground:anl:\n".toByteArray() else throw WeatherException("HTTP 404", status = 404)
                else -> { assertEquals("bytes=0-", headers["Range"]); file }
            }
        }
    }

    @Test fun storeDownloadsKeepsAndWaits() {
        var clock = 1_000_000.0
        val store = WindStore(tmp.root) { clock }
        val noaa = FakeNoaa()
        val now = utc("2026-11-21T09:40:00Z")
        assertTrue(store.due(now))
        val (f, note) = store.update(WindClient(noaa.bytes), now)
        assertNotNull(f); assertEquals("", note); assertEquals("NOAA NOMADS", store.source())
        assertFalse(store.due(now)); assertFalse(store.due(utc("2026-11-21T10:20:00Z")))
        assertTrue(store.due(utc("2026-11-21T12:00:00Z")))                     // the next 3-hour step
        // NOMADS down: NOAA's copy on Amazon
        noaa.nomads = "down"
        store.update(WindClient(noaa.bytes), utc("2026-11-21T12:00:00Z"))
        assertEquals("NOAA on AWS", store.source())
        // everything down: keep the saved map, say so, and wait 20 minutes before trying again
        noaa.nomads = "garbage"; noaa.aws = "down"
        val (kept, why) = store.update(WindClient(noaa.bytes), utc("2026-11-21T18:00:00Z"))
        assertNotNull(kept); assertTrue(why.contains("saved one"))
        assertFalse(store.due(utc("2026-11-21T18:00:00Z")))
        clock += Wind.RETRY_S + 5; assertTrue(store.due(utc("2026-11-21T18:00:00Z")))
        // nothing saved and nothing downloadable: a clear error
        val empty = WindStore(tmp.newFolder("e"))
        try { empty.update(WindClient(noaa.bytes), now); fail() } catch (e: WindException) { assertTrue(e.message!!, "couldn't be downloaded" in e.message!!) }
        assertNull(empty.load())
    }
}
