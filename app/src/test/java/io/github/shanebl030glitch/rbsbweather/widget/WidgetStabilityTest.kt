// Regression tests for Shane's 0.7.0 phone test (26 Nov 2026): the widget flickered, and tapping it
// opened the app, which then closed (a native crash in Android's text drawing, SkGlyph::mask, with
// text being drawn into pictures on two threads at once). Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.parseForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetStabilityTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun fc() = parseForecast(javaClass.classLoader!!.getResourceAsStream("openmeteo_forecast.json")!!.bufferedReader().readText(), AUCKLAND)

    private fun drain() {   // wait for everything queued on the widget thread so far
        val done = CountDownLatch(1); Widgets.executor.execute { done.countDown() }; assertTrue(done.await(30, TimeUnit.SECONDS))
    }

    @Test fun theSameContentIsntSentTwiceAndBurstsBecomeOneRedraw() {
        val s = Settings(places = listOf(AUCKLAND))
        val f = fc()
        val d = widgetData(s, f, 1_790_000_000_000L)
        val a = Widgets.signature(d, f, s, 320, 180, 1_790_000_000_000L)
        assertEquals(a, Widgets.signature(d, f, s, 320, 180, 1_790_000_000_000L + 60_000))            // a minute later: same picture
        assertNotEquals(a, Widgets.signature(d, f, s, 320, 180, 1_790_000_000_000L + 11 * 60_000))     // 11 minutes: the Sun has moved
        assertNotEquals(a, Widgets.signature(d, f.copy(fetchedAt = f.fetchedAt + 1), s, 320, 180, 1_790_000_000_000L))   // new weather
        assertNotEquals(a, Widgets.signature(d, f, s, 400, 180, 1_790_000_000_000L))                   // resized
        assertNotEquals(a, Widgets.signature(d.copy(place = "📍 Flat Bush"), f, s, 320, 180, 1_790_000_000_000L))   // My location moved

        // a real widget on the home screen; 10 requests in a burst (what 0.7.0 did when the app opened)
        val mgr = AppWidgetManager.getInstance(app)
        val id = shadowOf(mgr).createWidget(LargeWeatherWidget::class.java, io.github.shanebl030glitch.rbsbweather.R.layout.widget_scene)
        assertTrue(Widgets.allIds(app, mgr).contains(id))
        drain()
        val sent0 = Widgets.sent.get()
        repeat(10) { Widgets.updateAll(app) }
        Thread.sleep(Widgets.MERGE_MS + 300); drain()
        val afterBurst = Widgets.sent.get() - sent0
        assertTrue("one redraw for a burst of 10 requests, was $afterBurst", afterBurst <= 1)
        // nothing changed: further requests send nothing
        val sent1 = Widgets.sent.get()
        repeat(3) { Widgets.updateAll(app); Thread.sleep(Widgets.MERGE_MS + 200); drain() }
        assertEquals("unchanged widget redrawn", sent1, Widgets.sent.get())
    }

    @Test fun textIntoPicturesIsOnlyEverDrawnOnTheWidgetThread() {
        // the status-bar temperature icon is drawn with text into a picture: never on the caller's thread
        val callerThreads = mutableSetOf<String>()
        val latch = CountDownLatch(1)
        Widgets.updateTempNotification(app)
        Widgets.executor.execute { callerThreads += Thread.currentThread().name; latch.countDown() }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        assertEquals(setOf("rbsb-widgets"), callerThreads)
        // and the app's callback for it goes there too (MainActivity wiring checked in the source)
        val src = java.io.File("src/main/java/io/github/shanebl030glitch/rbsbweather/MainActivity.kt").takeIf { it.isFile }
            ?: java.io.File("app/src/main/java/io/github/shanebl030glitch/rbsbweather/MainActivity.kt")
        val text = src.readText()
        assertTrue(text.contains("onTempNotification = { Widgets.updateTempNotification(app) }"))
        assertTrue("no text drawn into pictures on the main thread", !text.contains("TempNotification.update(app) }"))
    }
}
