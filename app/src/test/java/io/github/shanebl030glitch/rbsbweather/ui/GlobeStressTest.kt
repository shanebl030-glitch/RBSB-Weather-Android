// Shane: "when I click on earth globe it crashes". The earlier tests ran with the sky animation off,
// no earthquakes and a mid-density screen. This one is set up like a phone: a 3x (xxhdpi) screen,
// animations on, earthquakes, four places, live clouds on, the Moon tab visited first, and then
// works the globe hard (tap, zoom to the limit, spin, drag, pinch, double-tap, switch back and forth).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import io.github.shanebl030glitch.rbsbweather.CrashDialog
import io.github.shanebl030glitch.rbsbweather.CrashLog
import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.FakeAlertServer
import io.github.shanebl030glitch.rbsbweather.data.FakeQuakeServer
import io.github.shanebl030glitch.rbsbweather.data.ForecastCache
import io.github.shanebl030glitch.rbsbweather.data.LONDON
import io.github.shanebl030glitch.rbsbweather.data.NEW_YORK
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.WELLINGTON
import io.github.shanebl030glitch.rbsbweather.data.Watch
import io.github.shanebl030glitch.rbsbweather.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlobeStressTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()

    /** Let time pass as on a phone: frames at 60 per second, with the background threads running. */
    private fun pump(ms: Long) {
        var left = ms
        while (left > 0) { compose.mainClock.advanceTimeBy(48); Thread.sleep(8); left -= 48 }
    }

    private fun pumpUntil(what: String, timeoutMs: Long = 120_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timed out waiting for $what" }
            pump(100)
        }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private val drawn = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "drawn") and hasTestTag("globe")
    private fun globeDrawn() = compose.onAllNodes(drawn).fetchSemanticsNodes().isNotEmpty()

    /** (rendered lat, rendered lon, pictures drawn, view lon, zoom) from the globe's semantics. */
    private fun view(): List<Double> {
        val node = compose.onAllNodes(hasTestTag("globe")).fetchSemanticsNodes().first()
        return node.config[io.github.shanebl030glitch.rbsbweather.ui.earth.GlobeViewKey].split(",").map { it.toDouble() }
    }
    private fun lonDiff(a: Double, b: Double) = ((b - a + 540) % 360) - 180

    private fun save(name: String) {
        val out = File("build/screens").apply { mkdirs() }
        val v = compose.activity.window.decorView.rootView
        val b = Bitmap.createBitmap(maxOf(1, v.width), maxOf(1, v.height), Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { v.draw(android.graphics.Canvas(b)) }
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun globeLikeOnAPhone() {
        val w = Watch(tmp.newFolder(), FakeAlertServer().client(), FakeQuakeServer().client())
        val m = AppModel(MemoryStore(Settings(animations = true, earthClouds = true).toJson().toString()),
            WeatherRepository(listOf(FakeProvider()), ForecastCache(tmp.root)), SEARCH, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, watch = w)
        for (p in listOf(AUCKLAND, WELLINGTON, LONDON, NEW_YORK)) m.add(p)
        m.select(AUCKLAND.key)
        compose.mainClock.autoAdvance = false
        compose.setContent { RbsbApp(m) }
        pump(1500)

        compose.onNodeWithTag("tab-Moon").performClick()                  // the Moon tab first (its big picture changes the texture cache)
        pumpUntil("the Moon") { exists("moonPhase") }
        pump(1000)
        compose.onNodeWithTag("tab-Earth").performClick()
        pumpUntil("the flat map") { exists("flatMap") }
        pump(3000)
        compose.onNodeWithTag("earthGlobe").performClick()                // what crashed on Shane's phone
        assertTrue(m.state.value.earthGlobe)
        pump(500)                                                           // the turn to the selected place, with animation on
        pumpUntil("the globe to draw") { globeDrawn() }
        pump(1500)
        save("stress_globe")

        // Shane (0.5.1): "the touch and drag to rotate the globe doesn't work" and "the globe itself doesn't
        // rotate when you tick rotate slowly". The picture itself must turn, not just the numbers.
        pump(1500)
        val v0 = view()
        compose.onNodeWithTag("rotateSlowly").performClick()
        pump(4000)                                                         // 8° a second
        val v1 = view()
        assertTrue("pictures drawn while spinning: $v0 -> $v1", v1[2] - v0[2] >= 4)
        val turned = lonDiff(v0[1], v1[1])
        assertTrue("the shown globe turned east by $turned°", turned in 12.0..45.0)
        compose.onNodeWithTag("rotateSlowly").performClick()               // stop
        pump(1500)
        save("stress_globe_spun")
        val v2 = view()
        compose.onNodeWithTag("globe").performTouchInput { swipe(center, center + Offset(300f, 0f), 500) }   // one finger, to the right
        pump(1500)
        val v3 = view()
        val dragged = lonDiff(v2[3], v3[3])
        assertTrue("dragging right turns the globe west: $dragged°", dragged < -10)
        assertTrue("and the picture follows: ${lonDiff(v3[3], v3[1])}°", kotlin.math.abs(lonDiff(v3[3], v3[1])) < 0.5)
        assertTrue("zoom unchanged by a one-finger drag", kotlin.math.abs(v3[4] - v2[4]) < 1e-6)
        compose.onNodeWithTag("globe").performTouchInput { swipe(center, center + Offset(0f, -250f), 400) }  // up and down works too (the page doesn't scroll instead)
        pump(1200)
        assertTrue(view()[0] != v3[0])
        repeat(6) { compose.onNodeWithTag("zoomIn").performClick(); pump(400) }     // to the 300% limit
        pumpUntil("the zoomed globe") { globeDrawn() }
        save("stress_globe_zoomed")
        repeat(8) { compose.onNodeWithTag("zoomOut").performClick(); pump(300) }    // to the 60% limit
        compose.onNodeWithTag("rotateSlowly").performClick(); pump(3000)
        compose.onNodeWithTag("rotateSlowly").performClick(); pump(500)
        compose.onNodeWithTag("globe").performTouchInput { swipe(center, center + Offset(300f, 120f), 600) }; pump(1000)
        compose.onNodeWithTag("globe").performTouchInput { swipe(center, center - Offset(500f, -400f), 300) }; pump(1000)
        compose.onNodeWithTag("globe").performTouchInput { pinch(center - Offset(60f, 0f), center - Offset(250f, 0f), center + Offset(60f, 0f), center + Offset(250f, 0f), 400) }; pump(1000)
        compose.onNodeWithTag("globe").performTouchInput { doubleClick(center) }; pump(1500)
        compose.onNodeWithTag("globe").performTouchInput { click(center) }; pump(500)
        compose.onNodeWithTag("earthFlat").performClick(); pump(1000)
        compose.onNodeWithTag("earthGlobe").performClick(); pump(1500)
        compose.onNodeWithTag("tab-Forecast").performClick(); pump(1000)
        compose.onNodeWithTag("tab-Earth").performClick()                  // back to the globe it was on
        pumpUntil("the globe again") { globeDrawn() }
        m.openEarth(globe = true, focusQuake = m.state.value.quakes.firstOrNull()?.id)   // as from a quake notification
        pump(2000)
        save("stress_globe_end")
        assertTrue(m.state.value.earthGlobe)
    }

    @Test fun crashReportIsSavedAndShownOnce() {
        val ctx = compose.activity
        val report = CrashLog.report(IllegalStateException("test crash"), "main")
        assertTrue("test crash" in report && "RBSB-Weather for Android" in report && "GlobeStressTest" in report)
        CrashLog.file(ctx).writeText(report)
        val pending = CrashLog.pending(ctx)
        compose.setContent { CrashDialog(ctx, pending) }
        compose.onNodeWithTag("crashDialog").assertExists()
        compose.onNodeWithTag("crashClose").performClick()
        compose.waitForIdle()
        assertFalse(CrashLog.file(ctx).exists())
        assertTrue(compose.onAllNodesWithTag("crashDialog").fetchSemanticsNodes().isEmpty())
    }
}
