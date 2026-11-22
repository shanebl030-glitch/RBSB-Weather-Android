// Pictures of whole screens (app/build/screens/*.png), published with each test build so the
// look can be checked: the sky with a warning banner, the Warnings screen, the Earthquakes list.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.FakeAlertServer
import io.github.shanebl030glitch.rbsbweather.data.FakeQuakeServer
import io.github.shanebl030glitch.rbsbweather.data.ForecastCache
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.Watch
import io.github.shanebl030glitch.rbsbweather.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenPicturesTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()

    private fun save(name: String) {
        val out = File("build/screens").apply { mkdirs() }
        // draw the window's views straight into a bitmap (captureToImage needs a real screen)
        val v = compose.activity.window.decorView.rootView
        val b = Bitmap.createBitmap(maxOf(1, v.width), maxOf(1, v.height), Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { v.draw(android.graphics.Canvas(b)) }
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun warningsScreens() {
        val w = Watch(tmp.newFolder(), FakeAlertServer().client(), FakeQuakeServer().client())
        val m = AppModel(MemoryStore(Settings(animations = false).toJson().toString()), WeatherRepository(listOf(FakeProvider()), ForecastCache(tmp.root)),
            SEARCH, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, watch = w)
        m.add(AUCKLAND)
        compose.setContent { RbsbApp(m) }
        compose.waitUntil(5_000) { m.state.value.alerts[AUCKLAND.key]?.isNotEmpty() == true }
        compose.waitForIdle()
        save("screen_home_warning")
        compose.onNodeWithTag("warningBanner").performClick()
        compose.waitForIdle()
        save("screen_warnings")
        m.go(Screen.QUAKES)
        compose.waitForIdle()
        save("screen_quakes")
        m.go(Screen.ALERT_SETTINGS)
        compose.waitForIdle()
        save("screen_alert_settings")
    }

    @Test fun moonAndEarthScreens() {
        val m = AppModel(MemoryStore(Settings(animations = false, earthClouds = false).toJson().toString()), WeatherRepository(listOf(FakeProvider()), ForecastCache(tmp.root)),
            SEARCH, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
        m.add(AUCKLAND)
        compose.setContent { RbsbApp(m) }
        m.go(Screen.MOON)
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("bigMoon").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        save("screen_moon")
        m.go(Screen.EARTH)
        compose.waitUntil(60_000) { compose.onAllNodesWithText("Drawing the Earth…").fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
        save("screen_earth_flat")
        m.setEarthGlobe(true)
        val drawn = androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "drawn")
        compose.waitUntil(90_000) { compose.onAllNodes(drawn and androidx.compose.ui.test.hasTestTag("globe")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        save("screen_earth_globe")
    }
}
