// The mini forecast inside the sky (the desktop's forecast_strip.py): 1–7 days fixed in the
// bottom-right corner, each with its name, an animated icon, the high and low and the chance of
// rain. How many days fit comes from the scene's real size (small 1–2, medium 3–5, large up to
// 7), never squeezed, and it keeps clear of the text on the scene. The Sun and Moon pass behind
// it over its soft shade. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.TempUnit
import io.github.shanebl030glitch.rbsbweather.data.Units
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val STRIP_MAX_DAYS = 7
const val WINDY_KMH = 40.0
private const val MARGIN = 0.035
private const val GAP_TEXT = 14.0f
private val LONG_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val SHORT_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

data class StripDay(val date: LocalDate, val code: Int?, val tempMax: Double?, val tempMin: Double?, val rain: Double?, val windMax: Double? = null) {
    val windy: Boolean get() = windMax != null && windMax >= WINDY_KMH && classify(code).first in setOf(Kind.CLEAR, Kind.PARTLY, Kind.OVERCAST)
}

fun locationToday(fc: Forecast): LocalDate? = try { LocalDateTime.parse(fc.current.time).toLocalDate() } catch (e: Exception) { null }

/** Today onwards (at the place), from the daily forecast. */
fun stripDays(fc: Forecast, limit: Int = STRIP_MAX_DAYS): List<StripDay> {
    val today = locationToday(fc)
    val out = mutableListOf<StripDay>()
    for (d in fc.daily) {
        val date = try { LocalDate.parse(d.date) } catch (e: Exception) { continue }
        if (today != null && date.isBefore(today)) continue
        out += StripDay(date, d.weatherCode, d.tempMax, d.tempMin, d.precipitationProbabilityMax, d.windSpeedMax)
        if (out.size >= limit) break
    }
    return out
}

fun dayName(date: LocalDate, today: LocalDate?, long: Boolean): String {
    if (today != null) {
        if (date == today) return "Today"
        if (date == today.plusDays(1)) return if (long) "Tomorrow" else "Tmrw"
    }
    return (if (long) LONG_NAMES else SHORT_NAMES)[date.dayOfWeek.value - 1]
}

fun tempText(c: Double?, unit: TempUnit): String {
    val v = Units.temp(c, unit) ?: return "–"
    val s = v.roundToInt().toString()
    return (if (s == "-0") "0" else s) + "°"
}

/** Small scenes show at most 2 days, medium 5, large 7 (sizes in dp, as the desktop's pixels). */
fun sizeCap(w: Float, h: Float): Int {
    val side = min(w, h)
    if (side < 150 || w < 280) return 2
    if (side < 200 || w < 420) return 5
    return STRIP_MAX_DAYS
}

data class StripCell(val day: StripDay, val name: String, val rect: Rect, val icon: Rect, val nameTop: Float, val tempTop: Float, val rainTop: Float)
data class StripLayout(val cells: List<StripCell>, val rect: Rect, val fontPx: Float, val smallPx: Float, val iconPx: Float, val long: Boolean, val cap: Int,
                       val styles: Triple<TextStyle, TextStyle, TextStyle>)

/** Where the forecast goes in a w × h px scene, or null if not even one day fits readably. */
fun planStrip(w: Float, h: Float, density: Float, days: List<StripDay>, today: LocalDate?, tm: TextMeasurer,
              avoid: List<Rect> = emptyList(), unit: TempUnit = TempUnit.C, fontScale: Float = 1f): StripLayout? {
    val wd = w / density; val hd = h / density
    if (days.isEmpty() || wd < 120 || hd < 80) return null
    val fontDp = (hd * 0.066f).coerceIn(9.5f, 13f) * fontScale
    val f = TextStyle(color = Color.White, fontSize = (fontDp / 1f).sp)
    val fb = f.copy(fontWeight = FontWeight.Bold)
    val fs = TextStyle(color = Color.White, fontSize = max(9f * fontScale, fontDp * 0.88f).sp)
    fun hgt(st: TextStyle) = tm.measure("Ag", st).size.height.toFloat()
    fun adv(s: String, st: TextStyle) = tm.measure(s, st).size.width.toFloat()
    val fontPx = fontDp * density; val smallPx = max(9f * fontScale, fontDp * 0.88f) * density
    val icon = (hd * 0.18f).coerceIn(18f, 40f) * density * fontScale.coerceAtMost(1.2f)
    val pad = max(2f, hd * 0.012f) * density
    val cellH = hgt(fb) + pad + icon + pad + hgt(f) + hgt(fs) + pad
    val margin = max(6f, min(wd, hd) * MARGIN.toFloat()) * density
    val bottom = h - margin
    val top = bottom - cellH
    if (top < margin) return null
    val shown = days.take(STRIP_MAX_DAYS)
    val tempsW = shown.maxOf { adv(tempText(it.tempMax, unit), fb) + adv(" " + tempText(it.tempMin, unit), f) }
    val rainW = (shown.filter { it.rain != null }.maxOfOrNull { adv("${it.rain!!.roundToInt()}%", fs) } ?: 0f) + smallPx * 0.9f
    val cap = sizeCap(wd, hd)
    var xMin = w * 0.3f
    val band = Rect(0f, top - 4, w, top + cellH + 4)
    for (r in avoid) if (r.overlaps(band)) xMin = max(xMin, r.right + GAP_TEXT * density)
    val xMax = w - margin
    var best: Triple<Boolean, List<String>, Pair<Rect, Float>>? = null
    for (long in listOf(true, false)) {
        val names = shown.take(cap).map { dayName(it.date, today, long) }
        val nameW = names.maxOf { adv(it, fb) }
        val cellW = maxOf(icon * 1.2f, nameW, tempsW, rainW) + max(6f * density, fontPx * 0.7f)
        val nFit = minOf(cap, days.size, ((xMax - xMin) / cellW).toInt())
        for (n in nFit downTo 1) {
            val width = n * cellW
            val strip = Rect(xMax - width, top, xMax, top + cellH)
            if (strip.left < xMin || avoid.any { it.overlaps(strip) }) continue
            if (best == null || n > best.second.size) best = Triple(long, names.take(n), strip to cellW)
            break
        }
        if (best != null && best.second.size >= minOf(cap, days.size)) break
    }
    val (long, names, sc) = best ?: return null
    val (strip, cellW) = sc
    val cells = names.mapIndexed { i, name ->
        val x = strip.left + i * cellW
        val iconR = Rect(x + (cellW - icon) / 2, top + hgt(fb) + pad, x + (cellW + icon) / 2, top + hgt(fb) + pad + icon)
        StripCell(shown[i], name, Rect(x, top, x + cellW, top + cellH), iconR, top, iconR.bottom + pad, iconR.bottom + pad + hgt(f))
    }
    return StripLayout(cells, strip, fontPx, smallPx, icon, long, cap, Triple(f, fb, fs))
}

private fun DrawScope.shadowText(tm: TextMeasurer, s: String, st: TextStyle, x: Float, y: Float, color: Color, center: Boolean = true): Float {
    val lay = tm.measure(s, st.copy(color = color))
    val tw = lay.size.width.toFloat()
    val tx = if (center) x - tw / 2 else x
    drawText(tm.measure(s, st.copy(color = Color(0, 0, 0, 170))), topLeft = Offset(tx + 1, y + 1))
    drawText(lay, topLeft = Offset(tx, y))
    return tw
}

/** Paints a planned mini forecast at animation time t. */
fun DrawScope.paintStrip(lay: StripLayout, t: Double, unit: TempUnit, tm: TextMeasurer) {
    val (f, fb, fs) = lay.styles
    val r = Rect(lay.rect.left - lay.iconPx * 0.6f, lay.rect.top - lay.iconPx * 0.5f, lay.rect.right + lay.iconPx * 0.6f, lay.rect.bottom + lay.iconPx * 0.4f)
    val rad = max(r.width, r.height) * 0.62f
    // a feathered oval shade, so it blends into the sky rather than looking like a panel
    drawOval(Brush.radialGradient(0f to Color(0, 10, 30, 95), 0.7f to Color(0, 10, 30, 55), 1f to Color(0, 10, 30, 0),
        center = r.center, radius = rad), Offset(r.center.x - rad, r.center.y - rad * r.height / r.width), Size(2 * rad, 2 * rad * r.height / r.width))
    lay.cells.forEachIndexed { i, c ->
        val cx = c.rect.center.x
        shadowText(tm, c.name, fb, cx, c.nameTop, Color.White)
        weatherIcon(c.icon, c.day.code, true, t + i * 0.37, c.day.windy)
        val hi = tempText(c.day.tempMax, unit); val lo = " " + tempText(c.day.tempMin, unit)
        val total = tm.measure(hi, fb).size.width + tm.measure(lo, f).size.width
        val x0 = cx - total / 2f
        val w1 = shadowText(tm, hi, fb, x0, c.tempTop, Color.White, center = false)
        shadowText(tm, lo, f, x0 + w1, c.tempTop, Color(232, 238, 248, 225), center = false)
        c.day.rain?.let { rain ->
            val pct = "${rain.roundToInt()}%"
            val dw = lay.smallPx * 0.55f
            val total2 = dw + 3 + tm.measure(pct, fs).size.width
            val x1 = cx - total2 / 2
            val th = tm.measure(pct, fs).size.height
            drop(Offset(x1 + dw / 2, c.rainTop + th * 0.5f), dw)
            shadowText(tm, pct, fs, x1 + dw + 3, c.rainTop, if (rain >= 20) Color(195, 225, 255) else Color(225, 232, 242, 220), center = false)
        }
    }
}

private fun DrawScope.drop(c: Offset, w: Float) {
    val p = Path().apply {
        moveTo(c.x, c.y - w * 0.95f)
        cubicTo(c.x + w * 0.6f, c.y - w * 0.1f, c.x + w * 0.55f, c.y + w * 0.55f, c.x, c.y + w * 0.55f)
        cubicTo(c.x - w * 0.55f, c.y + w * 0.55f, c.x - w * 0.6f, c.y - w * 0.1f, c.x, c.y - w * 0.95f)
    }
    drawPath(p, Color(120, 180, 255))
}
