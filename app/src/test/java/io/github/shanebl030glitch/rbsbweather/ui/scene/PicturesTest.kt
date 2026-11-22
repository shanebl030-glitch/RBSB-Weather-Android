// Saves pictures of the sky and widget frames (app/build/screens/*.png) so they can be looked at
// after each build; CI publishes them with the test build. Also checks they aren't blank.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.scene

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.TempUnit
import io.github.shanebl030glitch.rbsbweather.widget.WidgetFrames
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PicturesTest {
    private val out = File("build/screens").apply { mkdirs() }

    private fun save(b: Bitmap, name: String) {
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val seen = HashSet<Int>()
        for (x in 0 until b.width step 17) for (y in 0 until b.height step 17) seen += b.getPixel(x, y)
        assertTrue("$name is blank", seen.size > 10)
    }

    private fun sky(code: Int, local: String, name: String) {
        val app = RuntimeEnvironment.getApplication()
        MoonRender.init(app.assets)
        val fc = sceneFc(code = code, now = local, windDir = 260.0)
        val nowMs = LocalDateTime.parse(local).atZone(ZoneId.of("Pacific/Auckland")).toInstant().toEpochMilli()
        val e = SceneEngine(seed = 7, clock = { nowMs })
        val d = 2f; val w = (379 * d).toInt(); val h = (300 * d).toInt()
        e.setSize(w, h)
        e.setConditions(conditionsFromForecast(fc, nowMs))
        repeat(30) { e.advance(0.1) }
        val tm = TextMeasurer(createFontFamilyResolver(app), Density(d), LayoutDirection.Ltr)
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(d), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawScene(e, 16 * d) {
                planStrip(w.toFloat(), h.toFloat(), d, stripDays(fc), locationToday(fc), tm, emptyList(), TempUnit.C)?.let { paintStrip(it, e.t, TempUnit.C, tm) }
            }
        }
        save(img.asAndroidBitmap(), name)
    }

    @Test fun skies() {
        sky(2, "2026-11-15T10:15", "sky_partly_morning")
        sky(0, "2026-11-15T21:30", "sky_clear_night_moon")
        sky(63, "2026-11-15T14:00", "sky_rain_afternoon")
        sky(95, "2026-11-15T16:00", "sky_thunder")
        sky(73, "2026-11-15T12:00", "sky_snow")
        sky(45, "2026-11-15T07:00", "sky_fog_dawn")
        sky(3, "2026-11-15T19:55", "sky_overcast_dusk")
    }

    @Test fun widgetFrames() {
        val app = RuntimeEnvironment.getApplication()
        val fc = sceneFc(code = 2, now = "2026-11-15T10:15")
        val nowMs = LocalDateTime.parse("2026-11-15T10:15").atZone(ZoneId.of("Pacific/Auckland")).toInstant().toEpochMilli()
        val frames = WidgetFrames.render(app, Settings(places = listOf(AUCKLAND)), fc, 320, 150, 2f, nowMs)
        save(frames[0], "widget_large_frame0")
        save(frames[10], "widget_large_frame10")
        save(WidgetFrames.render(app, Settings(places = listOf(AUCKLAND)), fc, 160, 160, 2f, nowMs)[0], "widget_small_frame0")
    }
}
