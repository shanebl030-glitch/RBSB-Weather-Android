// Regression tests for the "not responding" the 0.7.1 stability audit caught (and Shane asked to be
// fixed before a stable release): the warnings check held the lock the screen reads warnings
// under for the whole download, so a slow or failing connection froze the screen. Created By
// Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class SlowNetworkTest {
    @get:Rule val tmp = TemporaryFolder()
    private val s = Settings(places = listOf(AUCKLAND, WELLINGTON), alerts = AlertSettings(own = false))

    /** A connection that hangs until released (or for good), then answers like [inner], or fails. */
    private class Hanging(val inner: BytesClient, val fail: Boolean = false) : BytesClient {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val calls = AtomicInteger()
        override fun get(url: String, headers: Map<String, String>, limit: Int): ByteArray {
            calls.incrementAndGet(); entered.countDown()
            release.await(60, TimeUnit.SECONDS)
            if (fail) throw WeatherException("The weather service took too long to answer.", retryable = true)
            return inner.get(url, headers, limit)
        }
    }

    /** How long the screen's reads take while a download hangs (ms, the slowest of many). */
    private fun screenReadMs(w: Watch): Long {
        var worst = 0L
        repeat(20) {
            val t0 = System.nanoTime()
            w.alertsFor(AUCKLAND, s, null); w.allAlertsFor(AUCKLAND, s, null); w.shownQuakes(s)
            w.alertsDue(s); w.quakesDue(s)
            worst = maxOf(worst, (System.nanoTime() - t0) / 1_000_000)
        }
        return worst
    }

    @Test fun theScreenNeverWaitsForASlowWarningsDownload() {
        val net = Hanging(FakeAlertServer())
        val w = Watch(tmp.root, AlertClient(net, "http://fake/alerts-metservice/cap/rss", "http://fake/alerts-nws",
            "http://fake/alerts-metno", "http://fake/alerts-meteoalarm/{slug}"), FakeQuakeServer().client())
        var notices: List<AlertNotice>? = null
        val checker = thread { notices = w.checkAlerts(s, emptyMap(), force = true) }
        assertTrue(net.entered.await(10, TimeUnit.SECONDS))                // the check is now stuck downloading
        val ms = screenReadMs(w)
        assertTrue("the screen waited $ms ms for the download (before 0.7.2: until it finished)", ms < 200)
        net.release.countDown(); checker.join(30_000)
        assertEquals(setOf("Orange Heavy Rain Warning", "Strong Wind Watch"), notices!!.map { it.alert.title() }.toSet())
        assertEquals(listOf("orange"), w.alertsFor(AUCKLAND, s, null).map { it.level })   // and the result is shown
    }

    @Test fun theScreenNeverWaitsForASlowEarthquakeDownload() {
        val net = Hanging(FakeQuakeServer())
        val w = Watch(tmp.root, FakeAlertServer().client(), QuakeClient(net, "http://fake/usgs/{feed}", "http://fake/geonet"))
        val checker = thread { w.checkQuakes(s, force = true) }
        assertTrue(net.entered.await(10, TimeUnit.SECONDS))
        val ms = screenReadMs(w)
        assertTrue("the screen waited $ms ms for the quake download", ms < 200)
        net.release.countDown(); checker.join(30_000)
        assertTrue("the quakes arrive", w.quakeStore.quakes.isNotEmpty())
    }

    @Test fun aTimedOutDownloadIsReportedAndTheOldWarningsStay() {
        // a good check first, then one whose downloads all time out
        val ok = Watch(tmp.root, FakeAlertServer().client(), FakeQuakeServer().client())
        ok.checkAlerts(s, emptyMap(), force = true)
        val net = Hanging(FakeAlertServer(), fail = true).also { it.release.countDown() }
        val w = Watch(tmp.root, AlertClient(net, "http://fake/alerts-metservice/cap/rss", "http://fake/alerts-nws",
            "http://fake/alerts-metno", "http://fake/alerts-meteoalarm/{slug}"), FakeQuakeServer().client())
        w.checkAlerts(s, emptyMap(), force = true)
        assertTrue("the failure is reported", w.alertStore.errors.values.any { "too long" in it })
        assertEquals(listOf("orange"), w.alertsFor(AUCKLAND, s, null).map { it.level })   // not wiped by the failure
    }

    @Test fun theAppAndTheBackgroundCheckDontDownloadTwiceOrNotifyTwice() {
        val net = Hanging(FakeAlertServer())
        val w = Watch(tmp.root, AlertClient(net, "http://fake/alerts-metservice/cap/rss", "http://fake/alerts-nws",
            "http://fake/alerts-metno", "http://fake/alerts-meteoalarm/{slug}"), FakeQuakeServer().client())
        val got = java.util.Collections.synchronizedList(mutableListOf<AlertNotice>())
        val a = thread { got += w.checkAlerts(s, emptyMap()) }               // due (never checked)
        assertTrue(net.entered.await(10, TimeUnit.SECONDS))
        val b = thread { got += w.checkAlerts(s, emptyMap()) }               // the other one, meanwhile
        Thread.sleep(300)
        val callsWhileWaiting = net.calls.get()
        net.release.countDown(); a.join(30_000); b.join(30_000)
        assertEquals("the second check waited, not downloading at the same time", 1, callsWhileWaiting)
        assertEquals("each warning notified once", got.map { it.alert.id }.toSet().size, got.size)
        assertEquals(2, got.size)
    }

    @Test fun realConnectionsThatHangTrickleOrAreRefusedEndInTime() {
        val fast = Http.client(connectMs = 1_000, readMs = 1_000, totalMs = 2_500)
        // a server that accepts and never answers
        ServerSocket(0, 5, InetAddress.getLoopbackAddress()).use { server ->
            val held = mutableListOf<java.net.Socket>()
            thread(isDaemon = true) { try { while (true) held += server.accept() } catch (e: Exception) { } }
            val t0 = System.currentTimeMillis()
            val e = runCatching { fast.get("http://127.0.0.1:${server.localPort}/x", emptyMap(), 1000) }.exceptionOrNull()
            assertTrue("hang: $e", e is WeatherException && "too long" in e.message!!)
            assertTrue(System.currentTimeMillis() - t0 < 5_000)
            held.forEach { it.close() }
        }
        // a server that answers, then sends a byte every 0.5 s for ever (each read is quick)
        ServerSocket(0, 5, InetAddress.getLoopbackAddress()).use { server ->
            thread(isDaemon = true) {
                try {
                    val c = server.accept()
                    c.getInputStream().read(ByteArray(4096))
                    val out = c.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\n".toByteArray()); out.flush()
                    while (true) { out.write('x'.code); out.flush(); Thread.sleep(500) }
                } catch (e: Exception) { }
            }
            val t0 = System.currentTimeMillis()
            val e = runCatching { fast.get("http://127.0.0.1:${server.localPort}/x", emptyMap(), 1_000_000) }.exceptionOrNull()
            assertTrue("trickle: $e", e is WeatherException && "too long" in e.message!!)
            assertTrue("stopped by the whole-download limit", System.currentTimeMillis() - t0 < 6_000)
        }
        // nothing listening
        val port = ServerSocket(0).use { it.localPort }
        val e = runCatching { fast.get("http://127.0.0.1:$port/x", emptyMap(), 1000) }.exceptionOrNull()
        assertTrue("refused: $e", e is WeatherException)
    }
}
