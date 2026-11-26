// Regression tests for Shane's 0.7.0 phone test (26 Nov 2026): the widget flickered, and tapping it
// opened the app, which then closed (a native crash in Android's text drawing, SkGlyph::mask, with
// text being drawn into pictures on two threads at once). Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Looper
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

    // wait for everything queued on the widget thread so far, running the main thread meanwhile
    // (the widget thread hands its text pictures to the main thread: MainThreadText.kt)
    private fun drain() = waitFor { done -> Widgets.executor.execute { done.countDown() } }

    private fun waitFor(start: (CountDownLatch) -> Unit) {
        val done = CountDownLatch(1); start(done)
        val until = System.currentTimeMillis() + 30_000
        while (!done.await(20, TimeUnit.MILLISECONDS)) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() >= until) {
                val dump = Thread.getAllStackTraces().entries.filter { it.key.name.contains("rbsb") || it.key.name.contains("Main") || it.key.name.contains("main") || it.key.name.contains("Test") }
                    .joinToString("\n") { (t, st) -> "THREAD ${t.name} ${t.state}\n" + st.take(25).joinToString("\n") { "    at $it" } }
                throw AssertionError("timed out waiting for the widget thread\n$dump")
            }
        }
    }

    private fun sleepRunningMain(ms: Long) {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
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
        sleepRunningMain(Widgets.MERGE_MS + 300); drain()
        val afterBurst = Widgets.sent.get() - sent0
        assertTrue("one redraw for a burst of 10 requests, was $afterBurst", afterBurst <= 1)
        // nothing changed: further requests send nothing
        val sent1 = Widgets.sent.get()
        repeat(3) { Widgets.updateAll(app); sleepRunningMain(Widgets.MERGE_MS + 200); drain() }
        assertEquals("unchanged widget redrawn", sent1, Widgets.sent.get())
    }

    @Test fun textIntoPicturesIsOnlyEverDrawnOnTheMainThread() {
        val mainName = Looper.getMainLooper().thread.name
        // the widget's frames, made on the widget thread: their words are drawn on the main thread
        val s = Settings(places = listOf(AUCKLAND))
        val f = fc()
        var frames: List<android.graphics.Bitmap> = emptyList()
        MainThreadText.lastThread = null
        waitFor { done -> Widgets.executor.execute { frames = WidgetFrames.render(app, s, f, 320, 180, 2f); done.countDown() } }
        assertEquals(WidgetFrames.FRAMES, frames.size)
        assertEquals(mainName, MainThreadText.lastThread)
        // the words really are on the frames (white text in the mini forecast's area, bottom right)
        val b = frames[0]
        var white = 0
        for (y in b.height / 2 until b.height) for (x in b.width / 2 until b.width) {
            val c = b.getPixel(x, y)
            if (android.graphics.Color.red(c) > 235 && android.graphics.Color.green(c) > 235 && android.graphics.Color.blue(c) > 235) white++
        }
        assertTrue("the mini forecast's words are missing from the widget ($white white pixels)", white > 50)

        // the status-bar temperature icon, asked for on the widget thread: drawn on the main thread
        MainThreadText.lastThread = null
        var icon: android.graphics.Bitmap? = null
        waitFor { done -> Widgets.executor.execute { icon = io.github.shanebl030glitch.rbsbweather.background.TempNotification.icon("17"); done.countDown() } }
        assertEquals(mainName, MainThreadText.lastThread)
        assertTrue(icon!!.width == 96)

        // and no code draws text into a picture anywhere but through MainThreadText (or the app's own
        // screens, which Android draws on the main thread): checked in the source
        val root = java.io.File("src/main/java").takeIf { it.isDirectory } ?: java.io.File("app/src/main/java")
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { file ->
            val t = file.readText()
            (t.contains("Canvas(") && (t.contains(".drawText(") || t.contains("paintStrip(")) && !t.contains("MainThreadText.draw"))
                && file.name !in setOf("ForecastStrip.kt", "SkyCard.kt", "HourlyChart.kt", "EarthScreens.kt", "MainThreadText.kt")
        }.map { it.name }.toList()
        assertEquals("text drawn into pictures outside MainThreadText", emptyList<String>(), offenders)
    }

    @Test fun aBusyMainThreadMeansTheWidgetKeepsItsPictureNotACrash() {
        // nothing runs the main thread here: the text can't be drawn, so the caller is told so
        val r = runCatching {
            val out = arrayOfNulls<Throwable>(1); val t = Thread { out[0] = runCatching { MainThreadText.draw(waitMs = 200) { 1 } }.exceptionOrNull() }
            t.start(); t.join(); out[0]?.let { throw it }
        }
        assertTrue(r.exceptionOrNull() is TextNotDrawnInTime)
        shadowOf(Looper.getMainLooper()).idle()   // the late request runs harmlessly
    }
}
