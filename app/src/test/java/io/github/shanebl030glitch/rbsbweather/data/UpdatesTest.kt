// Update checks (0.6), mirroring the desktop's test_updates.py. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/** A pretend GitHub: the release reply, SHA256SUMS and the APK bytes. */
class FakeGitHub(var version: String = "0.7.0", var apk: ByteArray = "PK-fake-apk-${"x".repeat(5000)}".toByteArray()) {
    var sumsOverride: String? = null
    var latestStatus = 200
    fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    val name get() = "rbsb-weather-android_$version.apk"
    fun release(extra: (JSONObject) -> Unit = {}) = JSONObject().put("tag_name", "v$version").put("name", "RBSB-Weather for Android $version")
        .put("html_url", "https://github.com/${Updates.REPO}/releases/tag/v$version").put("body", "### Added\n- The wind globe")
        .put("published_at", "2026-12-01T09:00:00Z").put("draft", false).put("prerelease", false)
        .put("assets", JSONArray()
            .put(JSONObject().put("name", name).put("size", apk.size).put("browser_download_url", "https://github.com/${Updates.REPO}/releases/download/v$version/$name"))
            .put(JSONObject().put("name", "SHA256SUMS").put("size", 100).put("browser_download_url", "https://github.com/${Updates.REPO}/releases/download/v$version/SHA256SUMS")))
        .also(extra)
    val bytes = BytesClient { url, _, _ ->
        when {
            "api.github.com" in url -> if (latestStatus == 200) release().toString().toByteArray() else throw WeatherException("HTTP $latestStatus", status = latestStatus)
            url.endsWith("SHA256SUMS") -> (sumsOverride ?: "${sha(apk)}  $name\n0000000000000000000000000000000000000000000000000000000000000000  other.apk\n").toByteArray()
            else -> throw WeatherException("unexpected $url")
        }
    }
    fun client() = UpdateClient(bytes)
}

class UpdatesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun versions() {
        assertEquals("0.6.0", Updates.versionFromTag("v0.6.0")); assertEquals("2.10.1", Updates.versionFromTag("2.10.1"))
        assertNull(Updates.versionFromTag("latest")); assertNull(Updates.versionFromTag("v1.2.3-beta"))
        assertTrue(Updates.compareVersions("2.10.0", "2.9.0") > 0); assertEquals(0, Updates.compareVersions("1.0", "1.0.0"))
        assertTrue(Updates.compareVersions("0.5.2", "0.6.0") < 0)
    }

    @Test fun readsGitHubsReleaseReply() {
        val gh = FakeGitHub()
        val info = Updates.parseRelease(gh.release().toString())
        assertEquals("0.7.0", info.version); assertTrue(info.installable)
        assertEquals("rbsb-weather-android_0.7.0.apk", info.apkName); assertEquals(gh.apk.size.toLong(), info.apkSize)
        assertTrue(Updates.isNewer(info, "0.6.0")); assertFalse(Updates.isNewer(info, "0.7.0")); assertFalse(Updates.isNewer(info, "1.0.0"))
        // a desktop .deb or a renamed file isn't offered; only the exact APK name for that version
        val other = Updates.parseRelease(gh.release { o -> o.getJSONArray("assets").getJSONObject(0).put("name", "rbsb-weather_0.7.0_all.deb") }.toString())
        assertFalse(other.installable)
        for (bad in listOf("<html>", "[]", gh.release { it.put("draft", true) }.toString(), gh.release { it.put("prerelease", true) }.toString(),
                gh.release { it.put("tag_name", "nightly") }.toString())) {
            try { Updates.parseRelease(bad); fail(bad) } catch (e: UpdateException) { }
        }
        // a page link that isn't GitHub falls back to the releases page; huge notes are cut
        val odd = Updates.parseRelease(gh.release { it.put("html_url", "https://evil.example/x").put("body", "y".repeat(50_000)) }.toString())
        assertEquals(Updates.RELEASES_PAGE, odd.pageUrl); assertTrue(odd.notes.length < 21_000)
    }

    @Test fun sumsAndHostsAndSchedule() {
        val h = "a".repeat(64)
        assertEquals(h, Updates.parseSums("$h  x.apk\n${"b".repeat(64)} *y.apk", "x.apk"))
        assertEquals("b".repeat(64), Updates.parseSums("$h  x.apk\n${"B".repeat(64)} *y.apk", "y.apk"))
        assertNull(Updates.parseSums("$h  x.apk", "z.apk")); assertNull(Updates.parseSums("nonsense", "x.apk"))
        assertTrue(Updates.hostAllowed("https://github.com/a")); assertTrue(Updates.hostAllowed("https://objects.githubusercontent.com/x"))
        assertFalse(Updates.hostAllowed("http://github.com/a")); assertFalse(Updates.hostAllowed("https://github.com.evil.example/a"))
        assertFalse(Updates.hostAllowed("not a url"))
        val now = 1_000_000_000.0
        assertTrue(Updates.due(0.0, 0.0, now))
        assertFalse(Updates.due(now - 3600, now - 3600, now))                         // checked an hour ago
        assertTrue(Updates.due(now - 25 * 3600, now - 25 * 3600, now))               // a day ago
        assertFalse(Updates.due(now - 30 * 3600, now - 2 * 3600, now))                // failed 2 h ago: wait 3 h
        assertTrue(Updates.due(now - 30 * 3600, now - 4 * 3600, now))
        assertTrue(Updates.due(now + 7200, 0.0, now))                                  // the clock went back
    }

    /** 0.6.1: the app never downloads or installs an APK; it gives the published checksum and the browser does the rest. */
    @Test fun checksumForTheBrowserDownload() {
        val gh = FakeGitHub()
        val cl = gh.client()
        val info = cl.latest()
        assertEquals(gh.sha(gh.apk), cl.checksum(info))
        assertTrue(Updates.hostAllowed(info.apkUrl))                               // only GitHub's own address is opened
        gh.sumsOverride = "${"0".repeat(64)}  something-else.apk\n"
        assertNull(cl.checksum(info))                                            // not listed: no checksum shown
        assertNull(cl.checksum(info.copy(sumsUrl = "")))
        gh.latestStatus = 404
        try { cl.latest(); fail() } catch (e: UpdateException) { assertTrue("no release" in e.message!!) }
    }

    /** Play Protect blocked 0.6.0 for downloading and installing APKs itself: none of that is in the app now. */
    @Test fun noInstallPermissionOrInstaller() {
        val root = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val manifest = File(root, "AndroidManifest.xml").readText()
        assertFalse("REQUEST_INSTALL_PACKAGES" in manifest); assertFalse("FileProvider" in manifest)
        val code = root.walkTopDown().filter { it.name.endsWith(".kt") }.map { it.readText() }.joinToString("\n")
        for (bad in listOf("application/vnd.android.package-archive", "canRequestPackageInstalls", "ACTION_INSTALL_PACKAGE", "PackageInstaller"))
            assertFalse(bad, bad in code)
    }

    @Test fun storeRemembersChecksAndSkips() {
        var now = 5_000_000.0
        val st = UpdateStore(tmp.root) { now }
        assertTrue(st.due())
        st.markAttempt(); assertFalse(st.due())
        st.markOk("0.7.0"); now += 23 * 3600; assertFalse(st.due()); now += 2 * 3600; assertTrue(st.due())
        st.skipped = "0.7.0"; st.notified = "0.7.0"
        val again = UpdateStore(tmp.root) { now }
        assertEquals("0.7.0", again.skipped); assertEquals("0.7.0", again.notified)
        File(tmp.root, "updates.json").writeText("{{{"); assertTrue(again.due())
    }
}
