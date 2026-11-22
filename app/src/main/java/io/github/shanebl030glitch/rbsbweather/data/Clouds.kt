// Live clouds for the Earth map and globe, as on the desktop (clouds.py). Source: Live Cloud
// Maps by Matt Eason (CC0), a near real-time global cloud map built from EUMETSAT satellite
// images every 3 hours ("Contains modified EUMETSAT data"). Checked at most once an hour with a
// conditional request (an unchanged map costs a few hundred bytes), 15 minutes after a failure;
// cached, so the last map shows offline; a download that isn't a valid 2:1 map never replaces it.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Clouds {
    const val URL = "https://clouds.matteason.co.uk/images/2048x1024/clouds.jpg"
    const val PROJECT_URL = "https://github.com/matteason/live-cloud-maps"
    const val CREDIT = "Live clouds: contains modified EUMETSAT data, via Live Cloud Maps by Matt Eason (CC0)"
    const val CHECK_EVERY_S = 3600
    const val RETRY_AFTER_FAIL_S = 900
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MIN_WIDTH = 256

    fun validSize(w: Int, h: Int) = w >= MIN_WIDTH && h > 0 && kotlin.math.abs(w.toDouble() / h - 2.0) < 0.02
}

class CloudException(message: String) : Exception(message)

/** A GET that can be conditional: (status, body, response headers). */
fun interface ConditionalGet {
    fun get(url: String, headers: Map<String, String>, limit: Int): Triple<Int, ByteArray, Map<String, String>>
}

val defaultConditionalGet = ConditionalGet { url, headers, limit ->
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 20_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", Http.USER_AGENT)
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        val code = c.responseCode
        val h = mapOf("Last-Modified" to c.getHeaderField("Last-Modified"), "ETag" to c.getHeaderField("ETag")).filterValues { it != null }.mapValues { it.value!! }
        if (code !in 200..299) return@ConditionalGet Triple(code, ByteArray(0), h)
        val out = java.io.ByteArrayOutputStream()
        c.inputStream.use { inp ->
            val buf = ByteArray(32 * 1024)
            while (true) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > limit) throw CloudException("the cloud map download was unexpectedly large") }
        }
        Triple(code, out.toByteArray(), h)
    } finally { c.disconnect() }
}

/** clouds.jpg + clouds.json (when fetched, Last-Modified, ETag, last attempt). */
class CloudStore(val dir: File, private val clock: () -> Double = ::nowS) {
    val imageFile get() = File(dir, "clouds.jpg")
    private val metaFile get() = File(dir, "clouds.json")

    fun meta(): JSONObject = try { JSONObject(metaFile.readText()) } catch (e: Exception) { JSONObject() }
    private fun writeMeta(o: JSONObject) {
        dir.mkdirs()
        val tmp = File(dir, "clouds.json.tmp"); tmp.writeText(o.toString())
        if (!tmp.renameTo(metaFile)) { metaFile.delete(); tmp.renameTo(metaFile) }
    }
    fun hasImage() = imageFile.isFile

    fun due(now: Double = clock()): Boolean {
        val m = meta()
        val failed = m.optBoolean("failed")
        if (!hasImage()) return !failed || now - m.optDouble("attempted", 0.0) >= Clouds.RETRY_AFTER_FAIL_S
        if (failed) return now - m.optDouble("attempted", 0.0) >= Clouds.RETRY_AFTER_FAIL_S
        return now - m.optDouble("checked", 0.0) >= Clouds.CHECK_EVERY_S
    }

    /** When the satellite map was made (Last-Modified), else when it was downloaded. */
    fun imageTime(): Instant? {
        val m = meta()
        m.optString("last_modified").takeIf { it.isNotEmpty() }?.let {
            try { return ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() } catch (e: Exception) { }
        }
        if (hasImage() && m.has("fetched")) return Instant.ofEpochMilli((m.optDouble("fetched") * 1000).toLong())
        return null
    }

    fun save(data: ByteArray, lastModified: String?, etag: String?) {
        dir.mkdirs()
        val tmp = File(dir, "clouds.jpg.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(imageFile)) { imageFile.delete(); tmp.renameTo(imageFile) }
        val now = clock()
        writeMeta(JSONObject().put("fetched", now).put("checked", now).put("attempted", now).put("failed", false)
            .put("last_modified", lastModified ?: JSONObject.NULL).put("etag", etag ?: JSONObject.NULL).put("bytes", data.size))
    }

    fun markChecked() { val now = clock(); writeMeta(meta().put("checked", now).put("attempted", now).put("failed", false)) }
    fun markFailed(reason: String) = writeMeta(meta().put("attempted", clock()).put("failed", true).put("error", reason.take(300)))
    fun lastError(): String = meta().takeIf { it.optBoolean("failed") }?.optString("error") ?: ""

    /**
     * Check for a newer map and save it: "updated" or "unchanged"; throws CloudException (the
     * cached map is kept). size(bytes) returns the decoded (width, height), or null if unreadable.
     */
    fun update(get: ConditionalGet, size: (ByteArray) -> Pair<Int, Int>?, url: String = Clouds.URL): String {
        val m = if (hasImage()) meta() else JSONObject()
        val headers = mutableMapOf("Accept" to "image/jpeg,image/*")
        m.optString("last_modified").takeIf { it.isNotEmpty() && it != "null" }?.let { headers["If-Modified-Since"] = it }
        m.optString("etag").takeIf { it.isNotEmpty() && it != "null" }?.let { headers["If-None-Match"] = it }
        val err: CloudException
        try {
            val (code, body, h) = get.get(url, headers, Clouds.MAX_BYTES)
            if (code == 304) { markChecked(); return "unchanged" }
            if (code !in 200..299) throw CloudException("the cloud map server returned HTTP $code")
            val wh = size(body) ?: throw CloudException("the cloud map download was not a readable image")
            if (!Clouds.validSize(wh.first, wh.second)) throw CloudException("the cloud map has an unexpected size (${wh.first}x${wh.second})")
            save(body, h["Last-Modified"], h["ETag"])
            return "updated"
        } catch (e: CloudException) { err = e } catch (e: Exception) { err = CloudException("could not download the cloud map (${e.javaClass.simpleName})") }
        markFailed(err.message ?: "failed")
        throw err
    }

    /** One line saying how fresh the clouds are. */
    fun note(haveImage: Boolean, error: String, clock: ClockFormat, now: Instant = Instant.now()): String {
        if (!haveImage) return if (error.isNotEmpty()) "Live clouds are unavailable right now, so the Earth is shown without them." else "Live clouds will appear once they've downloaded."
        val w = imageTime() ?: return "Live clouds shown."
        val zone = java.time.ZoneId.systemDefault()
        val local = w.atZone(zone)
        val sameDay = local.toLocalDate() == now.atZone(zone).toLocalDate()
        val stamp = (if (sameDay) "" else local.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " ") + Units.clock(local.hour, local.minute, clock)
        var text = "Clouds: satellite map from $stamp (your time)"
        if (now.epochSecond - w.epochSecond > 6 * 3600) text += if (error.isNotEmpty()) " – couldn't get a newer one, showing the last" else " – the newest available"
        return "$text."
    }
}
