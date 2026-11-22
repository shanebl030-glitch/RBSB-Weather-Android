// 0.6 screens and helpers: the Updates screen end to end with a pretend GitHub, Test connection,
// the temperature notification, provider usage in Weather Providers, and the new settings.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.shanebl030glitch.rbsbweather.background.TempNotification
import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.FakeGitHub
import io.github.shanebl030glitch.rbsbweather.data.ForecastCache
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.TempUnit
import io.github.shanebl030glitch.rbsbweather.data.UpdateStore
import io.github.shanebl030glitch.rbsbweather.data.WeatherRepository
import io.github.shanebl030glitch.rbsbweather.data.parseForecast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SixTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private fun model(s: Settings = Settings(animations = false)) = AppModel(MemoryStore(s.toJson().toString()),
        WeatherRepository(listOf(FakeProvider()), ForecastCache(tmp.root)), SEARCH, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    private fun waitFor(tag: String) = compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun updateFoundAndOpenedInTheBrowser() {
        val gh = FakeGitHub("9.9.0")
        val store = UpdateStore(tmp.newFolder("st"))
        val opened = mutableListOf<String>()
        val m = model()
        compose.setContent { UpdatesScreen(m.state.value, m, client = gh.client(), store = store, openUrl = { opened += it }) }
        compose.onNodeWithTag("checkNow").performClick()
        waitFor("updateAvailable")
        compose.onNodeWithText("RBSB-Weather for Android 9.9.0 is available").assertExists()
        compose.onNodeWithTag("updateSha").assertExists()
        compose.onNodeWithText(gh.sha(gh.apk), substring = true).assertExists()        // the published checksum to compare
        compose.onNodeWithTag("download").performScrollTo().performClick()
        assertEquals(listOf("https://github.com/${io.github.shanebl030glitch.rbsbweather.data.Updates.REPO}/releases/download/v9.9.0/rbsb-weather-android_9.9.0.apk"), opened)
        assertTrue(store.lastOk > 0)
    }

    @Test fun upToDateAndFailure() {
        val gh = FakeGitHub(io.github.shanebl030glitch.rbsbweather.data.Version.NAME)
        val store = UpdateStore(tmp.newFolder("st"))
        val m = model()
        compose.setContent { UpdatesScreen(m.state.value, m, client = gh.client(), store = store, openUrl = { }) }
        compose.onNodeWithTag("checkNow").performClick()
        waitFor("upToDate")
        gh.latestStatus = 503
        compose.onNodeWithText("Check again").performClick()
        waitFor("updateError")
    }

    @Test fun skipVersionIsRemembered() {
        val gh = FakeGitHub("9.9.2")
        val store = UpdateStore(tmp.newFolder("st"))
        val m = model()
        compose.setContent { UpdatesScreen(m.state.value, m, client = gh.client(), store = store, openUrl = { }) }
        compose.onNodeWithTag("checkNow").performClick()
        waitFor("updateAvailable")
        compose.onNodeWithTag("skipVersion").performScrollTo().performClick()
        assertEquals("9.9.2", store.skipped)
    }

    @Test fun connectionTestRunsAndReports() {
        val m = model()
        m.add(AUCKLAND)
        compose.setContent { ConnectionScreen(m.state.value, m) }
        m.testConnection(listOf("RBSB-Weather for Android test header"),
            resolve = { arrayOf(java.net.InetAddress.getByAddress(it, byteArrayOf(10, 0, 0, 1))) }, prober = { _, _, _, _ -> "OK – connected in 5 ms" })
        compose.waitUntil(60_000) { !m.state.value.connectionRunning }
        val r = m.state.value.connection.joinToString("\n")
        assertTrue(r, r.startsWith("RBSB-Weather for Android test header"))
        assertTrue(r, "Place search:" in r && "Summary:" in r)
    }

    @Test fun temperatureNotificationTextAndIcon() {
        assertEquals("17", TempNotification.iconText(16.6, TempUnit.C)); assertEquals("62", TempNotification.iconText(16.6, TempUnit.F))
        assertEquals("-3", TempNotification.iconText(-3.2, TempUnit.C)); assertEquals("–", TempNotification.iconText(null, TempUnit.C))
        val fc = parseForecast(resource("openmeteo_forecast.json"), AUCKLAND)
        val (title, text) = TempNotification.texts(Settings(), fc, "Auckland")
        assertTrue(title, title.matches(Regex("""-?\d+°C · .+""")))
        assertTrue(text, text.startsWith("Auckland · H ") && " L " in text)
        assertTrue(TempNotification.texts(Settings(), null, "Auckland").first.contains("no weather yet"))
        val b = TempNotification.icon("17")
        var lit = 0
        for (y in 0 until b.height) for (x in 0 until b.width) if (android.graphics.Color.alpha(b.getPixel(x, y)) > 128) lit++
        assertTrue("$lit", lit > 300)                                       // the digits are really drawn
        assertEquals(0, android.graphics.Color.alpha(b.getPixel(0, 0)))
    }

    @Test fun newSettingsSaveAndShow() {
        val s = Settings(updateCheck = false, tempNotification = true)
        val back = Settings.fromJson(s.toJson().toString())
        assertFalse(back.updateCheck); assertTrue(back.tempNotification)
        val old = Settings.fromJson(Settings().toJson().apply { remove("update_check"); remove("temp_notification") }.toString())
        assertTrue(old.updateCheck); assertFalse(old.tempNotification)        // older settings get the defaults
        val m = model()
        compose.setContent { RbsbApp(m) }
        m.go(Screen.SETTINGS)
        compose.onNodeWithTag("tempSwitch").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(m.state.value.settings.tempNotification)
        compose.onNodeWithTag("updateSwitch").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(m.state.value.settings.updateCheck)
        m.go(Screen.HOME)
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("menu-Test connection").assertExists()
        compose.onNodeWithTag("menu-Check for updates").performClick()
        compose.onNodeWithTag("updates").assertExists()
    }
}
