// Update checks, ported from the desktop's updates.py: once a day (3 hours after a failed check)
// RBSB-Weather asks GitHub for the newest release of RBSB-Weather for Android. A newer version is
// offered with its notes; "Download and install" fetches the APK, checks it against the release's
// SHA256SUMS and that it really is this app, newer, and then hands it to Android's installer
// (Android itself refuses an APK signed with a different key). Nothing is installed by itself.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

object Updates {
    const val REPO = "shanebl030-glitch/RBSB-Weather-Android"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    const val CHECK_EVERY_S = 24 * 3600.0
    const val RETRY_AFTER_FAIL_S = 3 * 3600.0
    const val MAX_RELEASE_BYTES = 1024 * 1024
    const val MAX_SUMS_BYTES = 64 * 1024
    const val MAX_APK_BYTES = 150L * 1024 * 1024
    const val MAX_NOTES_CHARS = 20_000
    private val VERSION_RE = Regex("""\d{1,4}(\.\d{1,4}){0,3}""")
    private val APK_RE = Regex("""rbsb-weather-android_\d{1,4}(\.\d{1,4}){0,3}\.apk""")
    /** GitHub sends release downloads through its own download servers. */
    private val ALLOWED_HOSTS = listOf("github.com", "api.github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    fun versionFromTag(tag: String?): String? {
        var t = tag?.trim() ?: return null
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1)
        return t.takeIf { VERSION_RE.matches(it) }
    }

    /** -1, 0 or 1, comparing dotted numbers (2.10.0 is newer than 2.9.0). */
    fun compareVersions(a: String, b: String): Int {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }; val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun isNewer(info: UpdateInfo, current: String = Version.NAME) = compareVersions(info.version, current) > 0

    /** Read GitHub's "latest release" reply. Throws UpdateException if it isn't usable. */
    fun parseRelease(body: String): UpdateInfo {
        val o = try { JSONObject(body) } catch (e: Exception) { throw UpdateException("GitHub sent release information that couldn't be read.") }
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) throw UpdateException("The newest release on GitHub is not a finished release.")
        val tag = o.optString("tag_name", "")
        val version = versionFromTag(tag) ?: throw UpdateException("The newest release on GitHub has no version number RBSB-Weather understands.")
        val page = o.optString("html_url", "").takeIf { it.startsWith("https://github.com/") } ?: RELEASES_PAGE
        var notes = if (o.opt("body") is String) o.getString("body") else ""
        if (notes.length > MAX_NOTES_CHARS) notes = notes.take(MAX_NOTES_CHARS) + "\n\n… (see the full notes on GitHub)"
        val title = o.optString("name", "").trim().ifEmpty { "RBSB-Weather for Android $version" }.take(200)
        var info = UpdateInfo(version, tag, title, notes, page, o.optString("published_at", "").take(40))
        val want = "rbsb-weather-android_$version.apk"
        val assets = o.optJSONArray("assets")
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.optJSONObject(i) ?: continue
            val name = a.optString("name", ""); val url = a.optString("browser_download_url", "")
            if (name.isEmpty() || !url.startsWith("https://")) continue
            if (name == want && APK_RE.matches(name)) info = info.copy(apkName = name, apkUrl = url, apkSize = a.optLong("size", 0L).coerceAtLeast(0L))
            else if (name == "SHA256SUMS") info = info.copy(sumsUrl = url)
        }
        return info
    }

    /** The SHA-256 for `name` in a sha256sum-style file ("<hex>  <name>" or "<hex> *<name>"). */
    fun parseSums(text: String, name: String): String? = text.lineSequence().firstNotNullOfOrNull { line ->
        Regex("""\s*([0-9a-fA-F]{64})\s+\*?(.+?)\s*""").matchEntire(line)?.takeIf { it.groupValues[2] == name }?.groupValues?.get(1)?.lowercase()
    }

    /** Is an automatic check due? A day after a successful check, 3 hours after a failed one;
     *  a stored time in the future (the clock jumped back) counts as due. */
    fun due(lastOk: Double, lastAttempt: Double, now: Double = nowS()): Boolean {
        if (lastAttempt > now + 3600 || lastOk > now + 3600) return true
        if (now - lastAttempt < RETRY_AFTER_FAIL_S) return false
        return now - lastOk >= CHECK_EVERY_S
    }

    fun hostAllowed(url: String): Boolean = try {
        val u = URI(url); u.scheme == "https" && (u.host ?: "").lowercase() in ALLOWED_HOSTS
    } catch (e: Exception) { false }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

class UpdateException(message: String) : Exception(message)

data class UpdateInfo(
    val version: String, val tag: String, val title: String, val notes: String, val pageUrl: String,
    val published: String = "", val apkName: String = "", val apkUrl: String = "", val apkSize: Long = 0L, val sumsUrl: String = "",
) {
    val installable get() = apkUrl.isNotEmpty() && sumsUrl.isNotEmpty()
}

/** When the last check happened, what it found, and a version the user chose to skip. */
class UpdateStore(dir: File, private val clock: () -> Double = ::nowS) {
    private val file = File(dir, "updates.json")
    private fun read(): JSONObject = try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
    private fun write(o: JSONObject) {
        try { file.parentFile?.mkdirs(); val t = File(file.path + ".tmp"); t.writeText(o.toString()); if (!t.renameTo(file)) { file.delete(); t.renameTo(file) } } catch (e: Exception) { }
    }
    fun due() = read().let { Updates.due(it.optDouble("last_ok", 0.0), it.optDouble("last_attempt", 0.0), clock()) }
    fun markAttempt() = write(read().put("last_attempt", clock()))
    fun markOk(found: String?) = write(read().put("last_ok", clock()).put("found", found ?: ""))
    var notified: String
        get() = read().optString("notified", "")
        set(v) = write(read().put("notified", v))
    var skipped: String
        get() = read().optString("skipped", "")
        set(v) = write(read().put("skipped", v))
    val lastOk: Double get() = read().optDouble("last_ok", 0.0)
}

/** A download with progress: (bytes so far, total or 0 if unknown). Return false to cancel. */
fun interface DownloadProgress { fun update(done: Long, total: Long): Boolean }

/** Streams a file from GitHub, following redirects only to GitHub's own hosts. */
fun interface Downloader { fun download(url: String, to: File, limit: Long, progress: DownloadProgress) }

val githubDownloader = Downloader { start, to, limit, progress ->
    var url = start
    var hops = 0
    while (true) {
        if (!Updates.hostAllowed(url)) throw UpdateException("The download pointed somewhere other than GitHub, so it was stopped.")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw UpdateException("GitHub's download link didn't lead anywhere.")
                url = URL(URL(url), loc).toString()
                if (++hops > 5) throw UpdateException("GitHub's download link went round in circles.")
                continue
            }
            if (code !in 200..299) throw UpdateException("GitHub returned an error (HTTP $code) for the download.")
            val total = c.contentLengthLong.coerceAtLeast(0L)
            if (total > limit) throw UpdateException("The download is far bigger than expected, so it was stopped.")
            to.parentFile?.mkdirs()
            val part = File(to.path + ".part")
            try {
                var done = 0L
                c.inputStream.use { input -> part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (done > limit) throw UpdateException("The download is far bigger than expected, so it was stopped.")
                        if (!progress.update(done, total)) throw UpdateException("Download cancelled.")
                    }
                } }
                if (total > 0 && done != total) throw UpdateException("The download was cut short. Please try again.")
                if (to.exists()) to.delete()
                if (!part.renameTo(to)) throw UpdateException("Couldn't save the download.")
            } finally { part.delete() }
            return@Downloader
        } catch (e: UpdateException) { throw e
        } catch (e: IOException) { throw UpdateException("The download didn't finish (${e.javaClass.simpleName}). Please try again.")
        } finally { c.disconnect() }
    }
}

/** Checks GitHub and downloads a verified APK. The network parts are swappable for tests. */
class UpdateClient(
    private val bytes: BytesClient = Http.bytes,
    private val downloader: Downloader = githubDownloader,
    private val latestUrl: String = Updates.LATEST_URL,
) {
    fun latest(): UpdateInfo {
        val body = try {
            String(bytes.get(latestUrl, mapOf("Accept" to "application/vnd.github+json"), Updates.MAX_RELEASE_BYTES), Charsets.UTF_8)
        } catch (e: WeatherException) {
            throw UpdateException(if (e.status == 404) "There's no release of RBSB-Weather for Android on GitHub yet."
                else "Couldn't reach GitHub to check for updates (${e.message}).")
        }
        return Updates.parseRelease(body)
    }

    /**
     * Download the release's APK into `dir`, check its SHA-256 against SHA256SUMS, and (via
     * `checkApk`) that it's this app and newer. Returns the verified file; a failed check deletes it.
     */
    fun download(info: UpdateInfo, dir: File, progress: DownloadProgress, checkApk: (File) -> String? = { null }): File {
        if (!info.installable) throw UpdateException("This release has no Android app file to install. Open it on GitHub instead.")
        val sums = try { String(bytes.get(info.sumsUrl, emptyMap(), Updates.MAX_SUMS_BYTES), Charsets.UTF_8) }
            catch (e: WeatherException) { throw UpdateException("Couldn't download the release's checksums (${e.message}).") }
        val want = Updates.parseSums(sums, info.apkName) ?: throw UpdateException("The release's SHA256SUMS doesn't list ${info.apkName}, so it can't be checked.")
        dir.mkdirs()
        dir.listFiles()?.forEach { if (it.name.endsWith(".apk") || it.name.endsWith(".part")) it.delete() }   // old downloads
        val f = File(dir, info.apkName)
        downloader.download(info.apkUrl, f, Updates.MAX_APK_BYTES, progress)
        val got = Updates.sha256(f)
        if (got != want) { f.delete(); throw UpdateException("The download doesn't match its published checksum, so it wasn't installed. Please try again.") }
        checkApk(f)?.let { problem -> f.delete(); throw UpdateException(problem) }
        return f
    }
}
