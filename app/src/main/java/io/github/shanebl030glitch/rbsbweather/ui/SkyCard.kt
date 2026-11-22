// The animated sky: colours that follow the real Sun at the place, the Sun or Moon on its
// arc between rise and set, stars at night, drifting clouds, rain, snow, fog and lightning.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.Place
import io.github.shanebl030glitch.rbsbweather.data.Sun
import io.github.shanebl030glitch.rbsbweather.data.UnitPrefs
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What the sky needs to draw a moment. Worked out once per forecast/minute, not per frame. */
data class SkyState(
    val sunElevation: Double,          // degrees
    val arc: Float?,                   // 0..1 along today's Sun (day) or tonight's Moon (night) arc
    val cloudiness: Float,             // 0..1
    val rain: Boolean,
    val snow: Boolean,
    val thunder: Boolean,
    val fog: Boolean,
    val windEast: Float,               // -1..1: which way clouds drift
)

fun skyState(fc: Forecast?, place: Place, now: Instant = Instant.now()): SkyState {
    val elev = Sun.elevation(now, place.latitude, place.longitude)
    val code = fc?.current?.weatherCode
    var arc: Float? = null
    val zone = Sun.zoneFor(place, fc?.utcOffsetSeconds)
    val today = fc?.daily?.firstOrNull()
    try {
        val rise = today?.sunrise?.let { LocalDateTime.parse(it).atZone(zone).toInstant() }
        val set = today?.sunset?.let { LocalDateTime.parse(it).atZone(zone).toInstant() }
        if (rise != null && set != null && set.isAfter(rise)) {
            arc = if (now.isAfter(rise) && now.isBefore(set)) {
                ((now.epochSecond - rise.epochSecond).toFloat() / (set.epochSecond - rise.epochSecond))
            } else {
                // night: from sunset to the next sunrise (about rise + 24 h)
                val nightStart = if (now.isBefore(rise)) set.minusSeconds(86_400) else set
                val nightEnd = if (now.isBefore(rise)) rise else rise.plusSeconds(86_400)
                ((now.epochSecond - nightStart.epochSecond).toFloat() / (nightEnd.epochSecond - nightStart.epochSecond))
            }.coerceIn(0f, 1f)
        }
    } catch (e: Exception) {
        arc = null
    }
    val dir = fc?.current?.windDirection
    // wind FROM the west (270°) blows clouds east
    val east = if (dir == null) 0.4f else (-sin(dir * PI / 180.0)).toFloat()
    return SkyState(elev, arc, max(WeatherCodes.cloudiness(code), ((fc?.current?.cloudCover ?: 0.0) / 100.0).toFloat() * 0.9f),
        WeatherCodes.isRain(code), WeatherCodes.isSnow(code), WeatherCodes.isThunder(code), WeatherCodes.isFog(code), east)
}

/** Sky colours (top, bottom) for a Sun elevation, greyed by cloud. */
fun skyColours(s: SkyState): Pair<Color, Color> {
    val e = s.sunElevation
    val nightTop = Color(0xFF0B1026); val nightBottom = Color(0xFF1E2B4D)
    val duskTop = Color(0xFF2B3A6B); val duskBottom = Color(0xFFE48A5E)
    val dayTop = Color(0xFF2F6FC0); val dayBottom = Color(0xFF9CC8F0)
    val (top, bottom) = when {
        e <= -12 -> nightTop to nightBottom
        e < 0 -> { val f = ((e + 12) / 12).toFloat(); lerp(nightTop, duskTop, f) to lerp(nightBottom, duskBottom, f) }
        e < 8 -> { val f = (e / 8).toFloat(); lerp(duskTop, dayTop, f) to lerp(duskBottom, dayBottom, f) }
        else -> dayTop to dayBottom
    }
    val grey = if (e > 0) Color(0xFF7C8796) else Color(0xFF2A2F3A)
    val k = (s.cloudiness * if (s.rain || s.snow) 0.8f else 0.6f).coerceIn(0f, 0.85f)
    return lerp(top, grey, k) to lerp(bottom, grey, k)
}

@Composable
fun SkyCard(
    fc: Forecast?,
    place: Place,
    units: UnitPrefs,
    animate: Boolean,
    status: String,
    statusIsWarning: Boolean,
    modifier: Modifier = Modifier,
) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> t = (now - start) / 1_000_000_000f }
        }
    }
    // the Sun moves slowly: recompute the sky state each minute (and when the forecast changes)
    var minute by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            minute += 1f
        }
    }
    val sky = remember(fc, place, minute) { skyState(fc, place) }
    val shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 6f)
    val white = TextStyle(color = Color.White, shadow = shadow)

    Box(modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(18.dp)).testTag("sky")) {
        Canvas(Modifier.fillMaxSize()) { drawSky(sky, t) }
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text(place.displayName, style = white.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            val c = fc?.current
            Text(if (c != null) Units.fmtTemp(c.temperature, units.temp) else "–",
                style = white.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold), modifier = Modifier.testTag("temp"))
            Text(if (c != null) WeatherCodes.describe(c.weatherCode) else "No weather yet",
                style = white.copy(fontSize = 18.sp))
            val today = fc?.daily?.firstOrNull()
            if (c != null) {
                Text("Feels like ${Units.fmtTemp(c.apparentTemperature, units.temp)}" +
                    (if (today != null) "  ·  H ${Units.fmtTemp(today.tempMax, units.temp, false)}  L ${Units.fmtTemp(today.tempMin, units.temp, false)}" else ""),
                    style = white.copy(fontSize = 14.sp))
            }
            Spacer(Modifier.weight(1f))
            if (status.isNotBlank()) {
                Text(status, style = white.copy(fontSize = 13.sp, color = if (statusIsWarning) Color(0xFFFFD2A8) else Color.White,
                    fontWeight = if (statusIsWarning) FontWeight.Bold else FontWeight.Normal),
                    modifier = Modifier.testTag("skyStatus"))
            }
        }
    }
}

private fun hash(i: Int, k: Int): Float {
    var x = i * 374761393 + k * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

fun DrawScope.drawSky(s: SkyState, t: Float) {
    val w = size.width
    val h = size.height
    val (top, bottom) = skyColours(s)
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    val night = s.sunElevation < -3

    // stars
    if (s.sunElevation < -6 && s.cloudiness < 0.9f) {
        val vis = ((-6 - s.sunElevation) / 6).coerceIn(0.0, 1.0).toFloat() * (1 - s.cloudiness)
        for (i in 0 until 70) {
            val tw = 0.55f + 0.45f * sin(t * (0.6f + hash(i, 3)) + i).toFloat()
            drawCircle(Color.White.copy(alpha = (0.85f * vis * tw).coerceIn(0f, 1f)), radius = 0.8f + 1.4f * hash(i, 4),
                center = Offset(hash(i, 1) * w, hash(i, 2) * h * 0.75f))
        }
    }

    // the Sun or the Moon on its arc
    s.arc?.let { f ->
        val x = w * (0.08f + 0.84f * f)
        val y = h * (0.86f - 0.62f * sin(PI * f).toFloat())
        val r = min(w, h) * 0.075f
        val dim = (1f - s.cloudiness * 0.75f).coerceIn(0.15f, 1f)
        if (!night) {
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFE9A8), Color(0x00FFE9A8)), Offset(x, y), r * 3.2f),
                r * 3.2f, Offset(x, y), alpha = dim)
            drawCircle(Color(0xFFFFE27A), r, Offset(x, y), alpha = dim)
        } else {
            drawCircle(Brush.radialGradient(listOf(Color(0x44DDE6FF), Color(0x00DDE6FF)), Offset(x, y), r * 2.6f),
                r * 2.6f, Offset(x, y), alpha = dim)
            drawCircle(Color(0xFFE9EDF5), r * 0.85f, Offset(x, y), alpha = dim)
        }
    }

    // clouds drifting with the wind
    val n = (s.cloudiness * 8).toInt() + if (s.cloudiness > 0.05f) 1 else 0
    val cloudColour = when {
        s.rain || s.thunder -> Color(0xFF5B6270)
        night -> Color(0xFF4A5468)
        else -> Color.White
    }
    for (i in 0 until n) {
        val speed = (6f + 10f * hash(i, 5)) * (if (s.windEast < 0) -1f else 1f) * (0.4f + kotlin.math.abs(s.windEast))
        val span = w + 260f
        var x = (hash(i, 6) * span + t * speed) % span
        if (x < 0) x += span
        x -= 130f
        val y = h * (0.08f + 0.45f * hash(i, 7))
        val sc = 0.7f + 0.8f * hash(i, 8)
        val a = (0.55f + 0.35f * s.cloudiness) * (if (night) 0.8f else 1f)
        listOf(Triple(0f, 0f, 34f), Triple(-38f, 10f, 24f), Triple(38f, 8f, 27f), Triple(14f, -16f, 24f)).forEach { (dx, dy, r) ->
            drawCircle(cloudColour.copy(alpha = a.coerceIn(0f, 1f)), r * sc, Offset(x + dx * sc, y + dy * sc))
        }
    }

    // fog
    if (s.fog) {
        for (b in 0 until 4) {
            val y = h * (0.45f + 0.13f * b)
            val dx = sin(t * 0.15f + b).toFloat() * 30f
            drawRect(Color.White.copy(alpha = 0.18f), Offset(dx - 40f, y), androidx.compose.ui.geometry.Size(w + 80f, h * 0.09f))
        }
    }

    // rain or snow
    if (s.rain) {
        val drops = if (s.thunder) 110 else 80
        for (i in 0 until drops) {
            val sp = 420f + 220f * hash(i, 9)
            val y = (hash(i, 10) * (h + 40) + t * sp) % (h + 40) - 20
            val x = (hash(i, 11) * w + y * 0.18f * s.windEast) % w
            drawLine(Color(0xAAD6E6FF), Offset(x, y), Offset(x + 3f * s.windEast, y + 14f), strokeWidth = 1.6f)
        }
    } else if (s.snow) {
        for (i in 0 until 70) {
            val sp = 30f + 30f * hash(i, 12)
            val y = (hash(i, 13) * (h + 20) + t * sp) % (h + 20) - 10
            val x = (hash(i, 14) * w + sin(t * 0.8f + i).toFloat() * 10f + t * 6f * s.windEast) % w
            drawCircle(Color.White.copy(alpha = 0.9f), 1.6f + 2f * hash(i, 15), Offset(if (x < 0) x + w else x, y))
        }
    }

    // lightning, now and then
    if (s.thunder) {
        val phase = t % 7f
        if (phase < 0.12f || phase in 0.25f..0.32f) drawRect(Color.White.copy(alpha = 0.35f))
    }

    // a soft scrim at the top-left keeps the white text readable on bright skies
    drawRect(Brush.linearGradient(listOf(Color(0x55000000), Color(0x00000000)), Offset.Zero, Offset(w * 0.75f, h * 0.9f)))
}
