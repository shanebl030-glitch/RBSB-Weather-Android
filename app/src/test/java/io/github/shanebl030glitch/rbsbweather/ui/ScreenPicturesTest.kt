// Pictures of whole screens (app/build/screens/*.png), published with each test build so the
// look can be checked: the sky with a warning banner, the Warnings screen, the Earthquakes list.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private fun save(name: String) {
        val out = File("build/screens").apply { mkdirs() }
        val b = compose.onRoot().captureToImage().asAndroidBitmap()
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
}
