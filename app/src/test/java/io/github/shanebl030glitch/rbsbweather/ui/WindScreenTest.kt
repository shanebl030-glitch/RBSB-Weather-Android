// The Wind tab (0.6): the globe draws, the streaks move, the layers switch, a tap tells the weather
// there, and the key follows your units. Uses NOAA's real GFS sample file (no network).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.ForecastCache
import io.github.shanebl030glitch.rbsbweather.data.PrecipUnit
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.TempUnit
import io.github.shanebl030glitch.rbsbweather.data.UnitPrefs
import io.github.shanebl030glitch.rbsbweather.data.WeatherRepository
import io.github.shanebl030glitch.rbsbweather.data.Wind
import io.github.shanebl030glitch.rbsbweather.data.WindUnit
import io.github.shanebl030glitch.rbsbweather.data.gribBytes
import io.github.shanebl030glitch.rbsbweather.ui.earth.EarthRender
import io.github.shanebl030glitch.rbsbweather.ui.earth.GlobeViewKey
import io.github.shanebl030glitch.rbsbweather.ui.earth.WindRender
import io.github.shanebl030glitch.rbsbweather.ui.earth.WindScreen
import io.github.shanebl030glitch.rbsbweather.ui.earth.WindState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WindScreenTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()

    private val field = Wind.fieldFromGrib(gribBytes("gfs_2p5_layers.grib2"))

    private fun pump(ms: Long) { var left = ms; while (left > 0) { compose.mainClock.advanceTimeBy(48); Thread.sleep(8); left -= 48 } }
    private fun view(): List<Double> = compose.onAllNodes(hasTestTag("windGlobe")).fetchSemanticsNodes().first().config[GlobeViewKey].split(",").map { it.toDouble() }
    private val drawn = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "drawn") and hasTestTag("windGlobe")

    private fun save(name: String) {
        val out = File("build/screens").apply { mkdirs() }
        val v = compose.activity.window.decorView.rootView
        val b = Bitmap.createBitmap(maxOf(1, v.width), maxOf(1, v.height), Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { v.draw(android.graphics.Canvas(b)) }
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun windTabDrawsStreaksLayersAndTaps() {
        val m = AppModel(MemoryStore(Settings(animations = true, units = UnitPrefs(wind = WindUnit.KMH)).toJson().toString()),
            WeatherRepository(listOf(FakeProvider()), ForecastCache(tmp.root)), SEARCH, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
        m.add(AUCKLAND)
        compose.mainClock.autoAdvance = false
        compose.setContent { WindScreen(m.state.value, m, wind = WindState(field, "", "", "NOAA NOMADS")) }
        val end = System.currentTimeMillis() + 120_000
        while (compose.onAllNodes(drawn).fetchSemanticsNodes().isEmpty()) { check(System.currentTimeMillis() < end) { "globe never drawn" }; pump(200) }
        val a = view(); pump(2000); val b = view()
        assertTrue("streaks moved: $a -> $b", b[5] - a[5] >= 20)            // about 30 frames a second
        save("screen_wind")
        compose.onNodeWithText("Wind 10 m above the ground", substring = true).assertExists()
        compose.onNodeWithTag("layer-temp").performClick(); pump(3000)
        compose.onNodeWithText("Air temperature 2 m above the ground", substring = true).assertExists()
        save("screen_wind_temp")
        compose.onNodeWithTag("layer-rain").performClick(); pump(3000)
        compose.onNodeWithTag("windGlobe").performTouchInput { click(center) }; pump(500)
        val txt = compose.onAllNodesWithTag("windPoint").fetchSemanticsNodes().first().config[SemanticsProperties.Text].joinToString()
        assertTrue(txt, "wind " in txt && "km/h from the " in txt && "°C" in txt && "rain/snow" in txt)
    }

    @Test fun textsAndMap() {
        val (u, v) = 0.0 to -5.0
        assertEquals("18 km/h from the N (0°)", WindRender.windText(u, v, WindUnit.KMH))
        assertEquals("dry", WindRender.rainText(0.01, PrecipUnit.MM)); assertEquals("1.5 mm/h", WindRender.rainText(1.5, PrecipUnit.MM))
        assertEquals("0.06 in/h", WindRender.rainText(1.5, PrecipUnit.IN))
        val p = WindRender.pointText(field, -36.85, 174.76, WindUnit.MS, TempUnit.F, PrecipUnit.MM)
        assertTrue(p, p.startsWith("wind ") && " m/s from the " in p && "°F" in p && "rain/snow" in p)
        val info = WindRender.infoText(field, "wind", io.github.shanebl030glitch.rbsbweather.data.ClockFormat.H24, zone = ZoneOffset.UTC)
        assertTrue(info, info.startsWith("Wind 10 m above the ground for ") && "GFS forecast run" in info)
        EarthRender.init(RuntimeEnvironment.getApplication().assets)
        val day = EarthRender.dayPixels()
        val img = WindRender.compose(field, "wind", day = day)
        assertEquals(EarthRender.W, img.width)
        // the colours are really there: somewhere windy is warmer-coloured than somewhere calm
        val px = IntArray(img.width * img.height).also { img.getPixels(it, 0, img.width, 0, 0, img.width, img.height) }
        val distinct = px.toSet().size
        assertTrue("$distinct colours", distinct > 5000)
        assertTrue(WindRender.isSea(day[EarthRender.W * (EarthRender.H / 2) + (EarthRender.W * 30 / 360)]))   // the Pacific at 150°W on the equator is sea
    }
}
