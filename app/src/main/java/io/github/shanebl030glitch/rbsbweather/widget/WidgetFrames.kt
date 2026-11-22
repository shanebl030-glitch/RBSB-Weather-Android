// The home-screen widget's animated sky. Android widgets can't run live animation, so a few
// seconds of the real scene (the same SceneEngine and mini forecast as the app, seed 11 like the
// desktop widget) are drawn as frames that the widget loops through. The last frames are blended
// into the first ones so the loop has no visible jump. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.ui.scene.Conditions
import io.github.shanebl030glitch.rbsbweather.ui.scene.MoonRender
import io.github.shanebl030glitch.rbsbweather.ui.scene.SceneEngine
import io.github.shanebl030glitch.rbsbweather.ui.scene.conditionsFromForecast
import io.github.shanebl030glitch.rbsbweather.ui.scene.drawScene
import io.github.shanebl030glitch.rbsbweather.ui.scene.locationToday
import io.github.shanebl030glitch.rbsbweather.ui.scene.paintStrip
import io.github.shanebl030glitch.rbsbweather.ui.scene.planStrip
import io.github.shanebl030glitch.rbsbweather.ui.scene.stripDays
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object WidgetFrames {
    const val FRAMES = 20            // a 3-second loop
    const val FRAME_MS = 150
    const val BLEND = 6              // frames blended across the loop's join
    const val BUDGET_BYTES = 6L * 1024 * 1024   // all frames together (Android limits a widget's pictures)

    /** Pixel scale for a widget of wDp × hDp: as sharp as the screen allows within the budget. */
    fun scale(wDp: Int, hDp: Int, density: Float, frames: Int = FRAMES): Float {
        val perFrame = BUDGET_BYTES.toDouble() / frames
        val fit = sqrt(perFrame / (max(1, wDp).toDouble() * max(1, hDp) * 4)).toFloat()
        return min(density, fit).coerceAtLeast(0.75f)
    }

    /**
     * The frames for a widget of wDp × hDp. One still frame when animation is off.
     * nowMs: the moment shown (the Sun and Moon are placed for it).
     */
    fun render(context: Context, settings: Settings, fc: Forecast?, wDp: Int, hDp: Int, density: Float,
               nowMs: Long = System.currentTimeMillis()): List<Bitmap> {
        try { MoonRender.init(context.applicationContext.assets) } catch (e: Exception) { }
        val animate = settings.animations
        val n = if (animate) FRAMES else 1
        val k = if (animate) BLEND else 0
        val sc = scale(wDp, hDp, density, n)
        val w = max(16, (wDp * sc).roundToInt()); val h = max(16, (hDp * sc).roundToInt())
        val engine = SceneEngine(seed = 11, clock = { nowMs })
        engine.animations = animate
        engine.setSize(w, h)
        engine.setConditions(fc?.let { conditionsFromForecast(it, nowMs) } ?: Conditions())
        if (animate) repeat(16) { engine.advance(0.125) }          // let rain, snow and clouds settle in
        val tm = TextMeasurer(createFontFamilyResolver(context), Density(sc), LayoutDirection.Ltr)
        val days = fc?.let { stripDays(it) } ?: emptyList()
        // the text on the widget (bottom left) the mini forecast must keep clear of
        val pad = 14 * sc
        val textW = if (fc != null) listOf(
            tm.measure(widgetData(settings, fc, nowMs).temp, TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold)).size.width,
            tm.measure(widgetData(settings, fc, nowMs).condition, TextStyle(fontSize = 13.sp)).size.width,
            tm.measure(widgetData(settings, fc, nowMs).hiLo, TextStyle(fontSize = 12.sp)).size.width).max() else 0
        val avoid = listOf(Rect(0f, h * 0.45f, pad + textW, h.toFloat()))
        val plan = if (fc != null) planStrip(w.toFloat(), h.toFloat(), sc, days, locationToday(fc), tm, avoid, settings.units.temp) else null
        val sim = (0 until n + k).map {
            val img = ImageBitmap(w, h)
            CanvasDrawScope().draw(Density(sc), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
                drawScene(engine, rounded = 16 * sc, scrim = true) {
                    plan?.let { paintStrip(it, engine.t, settings.units.temp, tm) }
                }
            }
            if (animate) engine.advance(FRAME_MS / 1000.0)
            img
        }
        // output[j] = sim[j+n] fading into sim[j] over the first k frames, so frame n-1 → 0 flows on
        return (0 until n).map { j ->
            if (j >= k) sim[j].asAndroidBitmap() else {
                val out = ImageBitmap(w, h)
                val c = Canvas(out)
                c.drawImage(sim[j], androidx.compose.ui.geometry.Offset.Zero, Paint())
                c.drawImage(sim[j + n], androidx.compose.ui.geometry.Offset.Zero, Paint().apply { alpha = 1f - j.toFloat() / k })
                out.asAndroidBitmap()
            }
        }
    }
}
