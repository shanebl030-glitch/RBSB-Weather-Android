// The Earth pictures really draw: the day/night map, and the globe (not just its outline).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.earth

import android.graphics.Bitmap
import android.graphics.Color
import io.github.shanebl030glitch.rbsbweather.data.Earth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EarthRenderTest {
    /** A test map: red west of 0°, green east, so the globe's colours say where it's facing. */
    private fun halves(w: Int = 360, h: Int = 180): Bitmap {
        val px = IntArray(w * h) { i -> if (i % w < w / 2) Color.rgb(220, 30, 30) else Color.rgb(30, 220, 30) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    @Test fun globeIsFilledAndFacesTheRightWay() {
        val r = GlobeRenderer(halves())
        val east = r.render(200, 0.0, 90.0)     // facing 90°E: all green in the middle
        val c = east.getPixel(100, 100)
        assertEquals(255, Color.alpha(c)); assertTrue(Color.green(c) > 150 && Color.red(c) < 60)
        assertEquals(0, Color.alpha(east.getPixel(2, 2)))           // outside the disc
        var opaque = 0
        for (y in 0 until 200) for (x in 0 until 200) if (Color.alpha(east.getPixel(x, y)) == 255) opaque++
        val disc = Math.PI * 100 * 100
        assertTrue("$opaque of ${disc.toInt()}", abs(opaque - disc) / disc < 0.03)
        val west = r.render(200, 0.0, -90.0)
        assertTrue(Color.red(west.getPixel(100, 100)) > 150)
        // facing 0°: red on the left (west), green on the right (east)
        val front = r.render(200, 0.0, 0.0)
        assertTrue(Color.red(front.getPixel(60, 100)) > 150); assertTrue(Color.green(front.getPixel(140, 100)) > 150)
        // the limb is darker than the middle (as on the desktop)
        assertTrue(Color.green(east.getPixel(100, 197)) < Color.green(c))
    }

    @Test fun earthMapDrawsDayAndNightFromTheRealTextures() {
        EarthRender.init(RuntimeEnvironment.getApplication().assets)
        val noon = EarthRender.compose(Instant.parse("2026-11-15T11:00:00Z"), null)     // midday over the Sahara
        val night = EarthRender.compose(Instant.parse("2026-11-15T23:00:00Z"), null)    // midnight there
        assertEquals(EarthRender.W, noon.width); assertEquals(EarthRender.H, noon.height)
        fun lum(img: Bitmap, lat: Double, lon: Double): Int {
            val (x, y) = Earth.toMapXY(lat, lon, img.width.toDouble(), img.height.toDouble())
            val p = img.getPixel(x.toInt().coerceIn(0, img.width - 1), y.toInt().coerceIn(0, img.height - 1))
            return Color.red(p) + Color.green(p) + Color.blue(p)
        }
        val day = lum(noon, 22.0, 12.0); val dark = lum(night, 22.0, 12.0)
        assertTrue("day $day night $dark", day > 3 * dark + 60)                         // the sunlit desert is much brighter
        val t = Instant.parse("2026-11-15T11:00:00Z"); val img = noon
        val (slat, slon) = Earth.subsolarPoint(t)
        // and the globe made from it is filled
        val g = GlobeRenderer(img).render(120, slat, slon)
        assertEquals(255, Color.alpha(g.getPixel(60, 60)))
        assertTrue(Color.red(g.getPixel(60, 60)) + Color.green(g.getPixel(60, 60)) + Color.blue(g.getPixel(60, 60)) > 60)
    }
}
