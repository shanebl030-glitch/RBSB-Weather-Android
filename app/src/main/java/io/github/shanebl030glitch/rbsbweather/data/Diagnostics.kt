// Menu → Test connection (0.6), ported from the desktop's diagnostics.py: shows where any delay
// comes from. Checks DNS, then IPv4 and IPv6 connections and the secure handshake separately for
// each weather service, then times a real place search and a forecast from each provider in use.
// Plain text so it can be copied or shared; API keys never appear in it. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object Diagnostics {
    const val PROBE_TIMEOUT_MS = 5000
    const val SLOW_MS = 1500

    /** One way to reach a server: (label, address) → a line of the report. */
    fun interface Prober { fun probe(host: String, port: Int, address: InetAddress, tls: Boolean): String }

    val realProber = Prober { host, port, address, tls ->
        val s = Socket()
        val t1 = System.nanoTime()
        try {
            s.connect(InetSocketAddress(address, port), PROBE_TIMEOUT_MS)
        } catch (e: SocketTimeoutException) {
            s.close(); return@Prober "NOT WORKING – no reply after ${PROBE_TIMEOUT_MS / 1000} s (${address.hostAddress})"
        } catch (e: Exception) {
            s.close(); return@Prober "NOT WORKING – ${e.message ?: e.javaClass.simpleName} (${address.hostAddress})"
        }
        val connectMs = ms(t1)
        if (!tls) { s.close(); return@Prober "OK – connected in $connectMs ms (${address.hostAddress})" }
        val t2 = System.nanoTime()
        try {
            s.soTimeout = PROBE_TIMEOUT_MS
            (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(s, host, port, true).use { ss ->
                (ss as SSLSocket).startHandshake()
                "OK – connected in $connectMs ms, secure handshake ${ms(t2)} ms (${address.hostAddress})"
            }
        } catch (e: SocketTimeoutException) {
            "CONNECTS ($connectMs ms) but the secure handshake STALLS – no reply after ${PROBE_TIMEOUT_MS / 1000} s (${address.hostAddress})"
        } catch (e: Exception) {
            "CONNECTS ($connectMs ms) but the secure handshake FAILS – ${e.message ?: e.javaClass.simpleName} (${address.hostAddress})"
        } finally { try { s.close() } catch (e: Exception) { } }
    }

    fun ms(t0: Long) = ((System.nanoTime() - t0) / 1_000_000).toInt()

    /** Remove anything that looks like an API key from a line (key=…, apikey=…, appid=…). */
    fun redact(line: String): String = line.replace(Regex("""(?i)((?:api_?key|appid|key|token)=)[^&\s]+"""), "$1•••")

    /**
     * Run the test. `hosts` are the servers to probe (host, port, https); `checks` are timed
     * real requests (label → action). Each line goes to `out` as it's ready; returns the report.
     */
    fun run(header: List<String>, hosts: List<Triple<String, Int, Boolean>>, checks: List<Pair<String, () -> Unit>>,
            resolve: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) }, prober: Prober = realProber,
            out: (String) -> Unit = {}): String {
        val lines = mutableListOf<String>()
        fun say(l: String = "") { val r = redact(l); lines += r; out(r) }
        header.forEach { say(it) }
        say()
        var v6Problem = false; var tlsStall = false; var dnsFailed = false
        for ((host, port, tls) in hosts) {
            say("[$host]")
            val t0 = System.nanoTime()
            val addrs = try { resolve(host) } catch (e: Exception) { null }
            if (addrs.isNullOrEmpty()) { say("  DNS lookup FAILED after ${ms(t0)} ms"); say(); dnsFailed = true; continue }
            val fams = listOfNotNull("IPv4".takeIf { addrs.any { it is Inet4Address } }, "IPv6".takeIf { addrs.any { it is Inet6Address } })
            say("  DNS lookup: ${ms(t0)} ms – ${fams.joinToString(", ")} addresses")
            for ((name, pick) in listOf("IPv4" to { a: InetAddress -> a is Inet4Address }, "IPv6" to { a: InetAddress -> a is Inet6Address })) {
                val a = addrs.firstOrNull(pick)
                if (a == null) { say("  $name: no address (not offered by DNS)"); continue }
                val r = try { prober.probe(host, port, a, tls) } catch (e: Exception) { "NOT WORKING – ${e.javaClass.simpleName}" }
                if (name == "IPv6" && (r.startsWith("NOT WORKING") || "STALLS" in r)) v6Problem = true
                if ("STALLS" in r) tlsStall = true
                say("  $name: $r")
            }
            say()
        }
        val results = linkedMapOf<String, Int?>()
        for ((label, fn) in checks) {
            val t0 = System.nanoTime()
            try {
                fn(); val m = ms(t0); results[label] = m
                say("$label: OK in $m ms" + if (m > SLOW_MS) "  ← slow" else "")
            } catch (e: Exception) {
                results[label] = null
                say("$label: FAILED after ${ms(t0)} ms – ${e.message ?: e.javaClass.simpleName}")
            }
        }
        say()
        say("Summary:")
        when {
            dnsFailed && results.values.all { it == null } -> say("  The phone couldn't look up the weather services: check the internet connection (try a web page).")
            results.values.all { it == null } -> say("  Nothing could be downloaded. Check the internet connection, a VPN, or a data-saving or firewall app.")
            results.values.any { it == null } -> say("  Some services failed (above). The others work; RBSB-Weather uses the next provider in your list when one fails.")
            results.values.filterNotNull().any { it > SLOW_MS } -> say("  Everything works, but some answers are slow (marked ← slow). That's usually the network or the service, not the phone.")
            else -> say("  Everything works and answers quickly.")
        }
        if (v6Problem) say("  IPv6 doesn't work properly on this network; Android normally falls back to IPv4, which can add a delay.")
        if (tlsStall) say("  A secure handshake stalled: often a network or router problem with large packets. Try another network (Wi-Fi or mobile data) to compare.")
        return lines.joinToString("\n")
    }

    /** host/port/https for each address, without repeats. */
    fun hostsOf(urls: List<String>): List<Triple<String, Int, Boolean>> = urls.mapNotNull { u ->
        try { val x = URI(u); val https = x.scheme == "https"; Triple(x.host ?: return@mapNotNull null, if (x.port > 0) x.port else if (https) 443 else 80, https) }
        catch (e: Exception) { null }
    }.distinct()
}
