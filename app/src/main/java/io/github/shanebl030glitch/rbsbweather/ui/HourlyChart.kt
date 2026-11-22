// The next 24 hours: temperature line with the chance of rain as bars. Scrolls sideways.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.Hour
import io.github.shanebl030glitch.rbsbweather.data.UnitPrefs
import io.github.shanebl030glitch.rbsbweather.data.Units
import kotlin.math.roundToInt

/** The 24 hours from the current hour at the place. */
fun next24(hours: List<Hour>, currentTime: String): List<Hour> {
    val cur = currentTime.take(13)
    val start = hours.indexOfFirst { it.time.take(13) >= cur }.let { if (it < 0) 0 else it }
    return hours.drop(start).take(24)
}

@Composable
fun HourlyChart(hours: List<Hour>, units: UnitPrefs, modifier: Modifier = Modifier) {
    if (hours.isEmpty()) {
        Text("No hourly forecast", modifier.testTag("chartEmpty"), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val measurer = rememberTextMeasurer()
    val textColour = MaterialTheme.colorScheme.onSurface
    val faint = textColour.copy(alpha = 0.6f)
    val temps = hours.map { Units.temp(it.temperature, units.temp) }
    val valid = temps.filterNotNull()
    val desc = "Next ${hours.size} hours: " + hours.zip(temps).joinToString(", ") { (h, v) ->
        Units.hourLabel(h.time, units.clock) + " " + (v?.roundToInt()?.toString() ?: "–") + "°"
    }
    Box(modifier.horizontalScroll(rememberScrollState()).testTag("chart").semantics { contentDescription = desc }) {
        Canvas(Modifier.width((hours.size * 54).dp).height(180.dp)) {
            val step = size.width / hours.size
            val top = 30f
            val bottom = size.height - 44f
            val h = bottom - top
            var lo = valid.minOrNull() ?: 0.0
            var hi = valid.maxOrNull() ?: 1.0
            if (hi - lo < 4) { val mid = (hi + lo) / 2; lo = mid - 2; hi = mid + 2 }
            fun yFor(v: Double) = (top + h * 0.12f + h * 0.55f * (1 - (v - lo) / (hi - lo))).toFloat()

            // chance of rain
            hours.forEachIndexed { i, p ->
                val pp = p.precipitationProbability ?: 0.0
                if (pp > 0) {
                    val bh = (h * 0.42f * (pp.coerceAtMost(100.0) / 100.0)).toFloat()
                    drawRoundRect(RainBar, Offset(i * step + step * 0.22f, bottom - bh), Size(step * 0.56f, bh),
                        CornerRadius(4f, 4f))
                }
            }
            // temperature
            val path = Path()
            var started = false
            temps.forEachIndexed { i, v ->
                if (v == null) { started = false; return@forEachIndexed }
                val x = i * step + step / 2
                val y = yFor(v)
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            drawPath(path, TempLine, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val small = TextStyle(fontSize = 11.sp, color = faint)
            val label = TextStyle(fontSize = 12.sp, color = textColour)
            hours.forEachIndexed { i, p ->
                val cx = i * step + step / 2
                temps[i]?.let { v ->
                    val y = yFor(v)
                    drawCircle(TempLine, 6f, Offset(cx, y))
                    val tl = measurer.measure("${v.roundToInt()}°", label)
                    drawText(tl, topLeft = Offset(cx - tl.size.width / 2f, y - tl.size.height - 8f))
                }
                val hl = measurer.measure(Units.hourLabel(p.time, units.clock), small)
                drawText(hl, topLeft = Offset(cx - hl.size.width / 2f, bottom + 6f))
                p.precipitationProbability?.takeIf { it > 0 }?.let { pp ->
                    val pl = measurer.measure("${pp.roundToInt()}%", small.copy(color = RainBar))
                    drawText(pl, topLeft = Offset(cx - pl.size.width / 2f, bottom + 6f + hl.size.height))
                }
            }
        }
    }
}
