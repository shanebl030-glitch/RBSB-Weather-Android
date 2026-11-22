// The animated sky card, as on the desktop: the live scene (SceneEngine/SceneDraw), the place,
// temperature and conditions over it, the mini forecast in its bottom-right corner, and the
// forecast playback bar underneath. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.Place
import io.github.shanebl030glitch.rbsbweather.data.UnitPrefs
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.ui.scene.Conditions
import io.github.shanebl030glitch.rbsbweather.ui.scene.MoonRender
import io.github.shanebl030glitch.rbsbweather.ui.scene.SceneEngine
import io.github.shanebl030glitch.rbsbweather.ui.scene.conditionsFromForecast
import io.github.shanebl030glitch.rbsbweather.ui.scene.drawScene
import io.github.shanebl030glitch.rbsbweather.ui.scene.locationToday
import io.github.shanebl030glitch.rbsbweather.ui.scene.paintStrip
import io.github.shanebl030glitch.rbsbweather.ui.scene.planStrip
import io.github.shanebl030glitch.rbsbweather.ui.scene.playbackSeconds
import io.github.shanebl030glitch.rbsbweather.ui.scene.stripDays
import kotlin.math.roundToInt

@Composable
fun SkyCard(
    fc: Forecast?,
    place: Place,
    units: UnitPrefs,
    animate: Boolean,
    status: String,
    statusIsWarning: Boolean,
    modifier: Modifier = Modifier,
    alert: io.github.shanebl030glitch.rbsbweather.data.Alert? = null,
    moreAlerts: Int = 0,
    onAlert: () -> Unit = {},
) {
    val ctx = LocalContext.current
    remember { try { MoonRender.init(ctx.applicationContext.assets) } catch (e: Exception) { } }
    val density = LocalDensity.current.density
    val tm = rememberTextMeasurer()
    val engine = remember(place.key) { SceneEngine(seed = 7) }
    engine.animations = animate
    var frame by remember { mutableLongStateOf(0L) }
    var offset by remember(place.key) { mutableDoubleStateOf(0.0) }
    var playing by remember(place.key) { mutableStateOf(false) }
    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000) }
    val maxS = remember(fc, minute) { fc?.let { playbackSeconds(it) } ?: 0.0 }
    if (offset > maxS) offset = maxS
    engine.clock = { System.currentTimeMillis() + (offset * 1000).toLong() }

    // the sky's conditions: on a new forecast, each minute, and as playback moves
    // (during play at most ten times a second; the Moon's pass is cached)
    var lastCond by remember { mutableLongStateOf(0L) }
    fun updateConditions(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCond < 100) return
        lastCond = now
        engine.setConditions(fc?.let { conditionsFromForecast(it, now, offset) } ?: Conditions())
    }
    LaunchedEffect(fc, minute) { updateConditions(true) }

    LaunchedEffect(animate, playing) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9).coerceIn(0.0, 0.25)
                last = now
                if (playing) {
                    val (nxt, end) = playTick(offset, dt, maxS)
                    offset = nxt
                    if (end) playing = false
                    updateConditions(false)
                }
                if (animate) engine.advance(dt)
                val m = System.currentTimeMillis() / 60_000
                if (m != minute) minute = m
                frame++
            }
            if (!animate && !playing) { kotlinx.coroutines.delay(30_000); frame++ }
        }
    }
    val boxRect = remember { mutableStateOf(Rect.Zero) }
    val textRects = remember { mutableStateMapOf<String, Rect>() }
    fun Modifier.avoid(key: String) = onGloballyPositioned { c: LayoutCoordinates -> textRects[key] = c.boundsInRoot() }

    val shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 6f)
    val white = TextStyle(color = Color.White, shadow = shadow)
    val hp = if (fc != null && offset > 0) playbackHour(fc, offset) else null
    val stamp = if (fc != null && offset > 0) playbackStamp(fc, offset, units.clock) else ""

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)).testTag("sky")
            .onGloballyPositioned { boxRect.value = it.boundsInRoot() }) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") frame
                engine.setSize(size.width.roundToInt(), size.height.roundToInt())
                drawScene(engine, rounded = 16.dp.toPx(), scrim = true) {
                    if (fc != null) {
                        val days = stripDays(fc)
                        val origin = boxRect.value.topLeft
                        val avoid = textRects.values.map { it.translate(-origin.x, -origin.y) }
                        planStrip(size.width, size.height, density, days, locationToday(fc), tm, avoid, units.temp)?.let {
                            paintStrip(it, engine.t, units.temp, tm)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                if (alert != null) {
                    WarningBanner(alert, moreAlerts, io.github.shanebl030glitch.rbsbweather.data.Sun.zoneFor(place, fc?.utcOffsetSeconds), units.clock, onAlert,
                        Modifier.padding(bottom = 6.dp).avoid("banner"))
                }
                Text(place.displayName, style = white.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold), maxLines = 2,
                    modifier = Modifier.widthIn(max = 260.dp).avoid("place"))
                val c = fc?.current
                val temp = if (hp != null) hp.temperature else c?.temperature
                Text(if (c != null) Units.fmtTemp(temp, units.temp) else "–",
                    style = white.copy(fontSize = 60.sp, fontWeight = FontWeight.Bold), modifier = Modifier.testTag("temp").avoid("temp"))
                Text(when {
                    hp != null -> WeatherCodes.describe(hp.weatherCode) + (hp.precipitationProbability?.let { "  ·  💧 ${it.roundToInt()}%" } ?: "")
                    c != null -> WeatherCodes.describe(c.weatherCode)
                    else -> "No weather yet"
                }, style = white.copy(fontSize = 17.sp), modifier = Modifier.testTag("skyCondition").avoid("cond"))
                val today = fc?.daily?.firstOrNull()
                if (offset > 0) {
                    Text("▶ Forecast for $stamp", style = white.copy(fontSize = 14.sp), modifier = Modifier.testTag("playbackTime").avoid("feels"))
                } else if (c != null) {
                    Text("Feels like ${Units.fmtTemp(c.apparentTemperature, units.temp)}" +
                        (if (today != null) "  ·  H ${Units.fmtTemp(today.tempMax, units.temp, false)}  L ${Units.fmtTemp(today.tempMin, units.temp, false)}" else ""),
                        style = white.copy(fontSize = 14.sp), modifier = Modifier.avoid("feels"))
                }
            }
        }
        if (status.isNotBlank()) {
            Text(status, fontSize = 12.sp, color = if (statusIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                fontWeight = if (statusIsWarning) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp).testTag("skyStatus"))
        }
        if (fc != null) {
            val steps = playbackSteps(maxS)
            Row(Modifier.fillMaxWidth().testTag("playbackBar"), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (playing) playing = false
                    else if (maxS > 0) { if (offset >= maxS - 1) offset = 0.0; playing = true }
                }, enabled = steps > 0, modifier = Modifier.testTag("play")) {
                    if (playing) PauseIcon("Pause")
                    else Icon(Icons.Filled.PlayArrow, contentDescription = "Play the coming hours in the sky (an hour a second)")
                }
                Slider(value = (offset / (STEP_MIN * 60)).toFloat().coerceIn(0f, maxOf(1, steps).toFloat()),
                    onValueChange = { v -> playing = false; offset = minOf(maxS, v.roundToInt() * STEP_MIN * 60.0); updateConditions(true) },
                    valueRange = 0f..maxOf(1, steps).toFloat(), steps = maxOf(0, steps - 1), enabled = steps > 0,
                    modifier = Modifier.weight(1f).testTag("playback"))
                Text(when {
                    steps <= 0 -> playbackUnavailable(fc)
                    offset <= 0 -> "Now"
                    else -> stamp
                }, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp).widthIn(max = 150.dp).testTag("playbackLabel"))
                TextButton(onClick = { playing = false; offset = 0.0; updateConditions(true) }, enabled = offset > 0 || playing,
                    modifier = Modifier.testTag("playbackNow")) { Text("Now") }
            }
            if (offset > 0) Text(WIND_NOTE, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Two bars (material-icons-core has no pause icon). */
@Composable
private fun PauseIcon(description: String) {
    val col = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(24.dp).semantics { contentDescription = description }) {
        val w = size.width; val h = size.height
        drawRect(col, Offset(w * 0.25f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.17f, h * 0.6f))
        drawRect(col, Offset(w * 0.58f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.17f, h * 0.6f))
    }
}
