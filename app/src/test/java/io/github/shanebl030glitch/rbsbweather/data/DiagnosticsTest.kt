// Test connection (0.6), mirroring the desktop's diagnostics. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DiagnosticsTest {
    private val v4 = InetAddress.getByAddress("api.example", byteArrayOf(10, 0, 0, 1))
    private val v6 = InetAddress.getByAddress("api.example", ByteArray(16).also { it[15] = 1 })

    @Test fun reportsEachStepAndSummary() {
        val lines = mutableListOf<String>()
        val report = Diagnostics.run(listOf("RBSB-Weather for Android test"),
            Diagnostics.hostsOf(listOf("https://api.open-meteo.com/v1/forecast", "https://api.open-meteo.com/v1/x", "https://geocoding-api.open-meteo.com/v1/search")),
            listOf("Place search" to {}, "Open-Meteo forecast" to { throw WeatherException("The weather service took too long to answer.") }),
            resolve = { h -> if (h.startsWith("geocoding")) arrayOf(v4) else arrayOf(v4, v6) },
            prober = { _, _, a, tls -> assertTrue(tls); if (a == v6) "NOT WORKING – no reply after 5 s" else "OK – connected in 20 ms, secure handshake 40 ms" },
            out = { lines += it })
        assertEquals(report, lines.joinToString("\n"))
        assertEquals(2, report.lines().count { it.startsWith("[") })                    // each server once
        assertTrue("[api.open-meteo.com]" in report && "IPv6: NOT WORKING" in report)
        assertTrue("IPv6: no address (not offered by DNS)" in report)
        assertTrue("Place search: OK in" in report && "Open-Meteo forecast: FAILED" in report)
        assertTrue("Some services failed" in report); assertTrue("IPv6 doesn't work properly" in report)
    }

    @Test fun noNetworkAndKeysHidden() {
        val r = Diagnostics.run(listOf("x"), listOf(Triple("api.example", 443, true)), listOf("Place search" to { throw WeatherException("No internet connection.") }),
            resolve = { throw java.net.UnknownHostException(it) })
        assertTrue("DNS lookup FAILED" in r); assertTrue("couldn't look up the weather services" in r)
        assertEquals("https://x/y?apikey=•••&lat=1", Diagnostics.redact("https://x/y?apikey=SECRET123&lat=1"))
        assertEquals("appid=••• key=•••", Diagnostics.redact("appid=abc key=def"))
        assertFalse("SECRET" in Diagnostics.run(listOf("key=SECRET"), emptyList(), emptyList()))
        assertEquals("network unreachable", Diagnostics.reason(java.net.ConnectException("failed to connect to x/2a03:4000::1 (port 443) from /fec0::1 (port 36144) after 5000ms: isConnected failed: ENETUNREACH (Network is unreachable)")))
        assertEquals("connection refused", Diagnostics.reason(java.net.ConnectException("x: ECONNREFUSED (Connection refused)")))
        assertTrue("Everything works" in Diagnostics.run(listOf("x"), emptyList(), listOf("a" to {})))
    }
}
