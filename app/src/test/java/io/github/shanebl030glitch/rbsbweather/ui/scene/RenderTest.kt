// Drawing the sky, the Moon picture and the widget frames for real (Robolectric native graphics).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.scene

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.robolectric.RuntimeEnvironment
import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.widget.WidgetFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {
    private fun render(e: SceneEngine, w: Int = 400, h: Int = 200, drawn: Drawn? = null): android.graphics.Bitmap {
        val img = ImageBitmap(w, h)
        e.setSize(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) { drawScene(e, 10f, true, drawn) }
        return img.asAndroidBitmap()
    }

    private fun colours(b: android.graphics.Bitmap): Int {
        val seen = HashSet<Int>()
        for (x in 0 until b.width step 23) for (y in 0 until b.height step 23) seen += b.getPixel(x, y)
        return seen.size
    }

    @Test fun everyCodeDayAndNightDraws() {
        for (code in WeatherCodes.known) for (daylight in listOf(1.0, 0.0)) {
            val e = SceneEngine(seed = 5)
            val (k, i) = classify(code)
            e.setSize(300, 150)
            e.setConditions(Conditions(k, i, daylight = daylight, sunProgress = 0.4, moonProgress = 0.6))
            repeat(5) { e.advance(0.2) }
            val b = render(e, 300, 150)
            assertTrue("code $code", colours(b) > 3)
        }
    }

    @Test fun dayAndNightLookDifferent() {
        val day = render(SceneEngine().apply { setSize(400, 200); setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 1.0)) })
        val night = render(SceneEngine().apply { setSize(400, 200); setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 0.0)) })
        val d = day.getPixel(200, 20); val n = night.getPixel(200, 20)
        assertTrue((d and 0xFF) > (n and 0xFF))
    }

    @Test fun sunAndMoonFollowTheirArcs() {
        val drawn = Drawn()
        val e = SceneEngine(clock = { 0L })
        e.setSize(400, 200)
        e.setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 1.0, sunProgress = 0.5, moonUp = true, moonProgress = 0.25))
        render(e, drawn = drawn)
        assertNotNull(drawn.sun); assertNotNull(drawn.moon)
        assertTrue(drawn.sun!!.second < drawn.moon!!.second)             // the Sun at its highest, the Moon lower
        e.setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 1.0, sunProgress = 0.5, moonUp = false))
        render(e, drawn = drawn)
        assertEquals(null, drawn.moon)                                    // below the horizon: not drawn
    }

    @Test fun moonPictureFromTheNasaTextures() {
        MoonRender.init(RuntimeEnvironment.getApplication().assets)
        val full = MoonRender.render(64, 0.0, 270.0)!!
        val newMoon = MoonRender.render(64, 179.0, 270.0)!!
        assertEquals(0, full.getPixel(1, 1) ushr 24)                     // transparent outside the disc
        fun bright(b: android.graphics.Bitmap) = (b.getPixel(32, 32) shr 8) and 0xFF
        assertTrue(bright(full) > bright(newMoon) + 40)
    }

    @Test fun widgetFramesLoop() {
        val ctx = RuntimeEnvironment.getApplication()
        val fc = sceneFc(code = 63, now = "2026-11-15T13:00")
        val s = Settings(places = listOf(AUCKLAND))
        val frames = WidgetFrames.render(ctx, s, fc, 300, 140, 2f)
        assertEquals(WidgetFrames.FRAMES, frames.size)
        assertTrue(frames.zipWithNext().any { (a, b) -> !a.sameAs(b) })   // it moves
        val still = WidgetFrames.render(ctx, s.copy(animations = false), fc, 300, 140, 2f)
        assertEquals(1, still.size)
    }
}
