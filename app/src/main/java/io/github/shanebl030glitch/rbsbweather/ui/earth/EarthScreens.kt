// Earth Flat and Earth Globe, as on the desktop: the live Earth with day and night as they are
// now (city lights on the night side, the real sunrise/sunset line), live satellite clouds, the
// Sun and Moon overhead points, your places with the selected one pulsing and its card (local
// time, day/twilight/night with the Sun's height, temperature and an animated icon), and recent
// earthquakes as dots (colour by age, size by magnitude, pulsing in the last hour; tap for a card).
// The globe turns by dragging, zooms 60–300 %, faces the selected place, can rotate slowly, and
// has the real Sun, Moon and stars behind it. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.earth

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.CloudStore
import io.github.shanebl030glitch.rbsbweather.data.Clouds
import io.github.shanebl030glitch.rbsbweather.data.Earth
import io.github.shanebl030glitch.rbsbweather.data.Globe
import io.github.shanebl030glitch.rbsbweather.data.Moon
import io.github.shanebl030glitch.rbsbweather.data.Quake
import io.github.shanebl030glitch.rbsbweather.data.Quakes
import io.github.shanebl030glitch.rbsbweather.data.Sky
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.data.defaultConditionalGet
import io.github.shanebl030glitch.rbsbweather.data.describeQuake
import io.github.shanebl030glitch.rbsbweather.data.nearestPlace
import io.github.shanebl030glitch.rbsbweather.data.nowS
import io.github.shanebl030glitch.rbsbweather.data.quakeColour
import io.github.shanebl030glitch.rbsbweather.data.quakeRadius
import io.github.shanebl030glitch.rbsbweather.ui.AppModel
import io.github.shanebl030glitch.rbsbweather.ui.UiState
import io.github.shanebl030glitch.rbsbweather.ui.scene.AnimatedWeatherIcon
import io.github.shanebl030glitch.rbsbweather.ui.scene.MoonRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A saved place on the maps. */
data class Marker(val key: String, val name: String, val lat: Double, val lon: Double, val zone: java.time.ZoneId,
                  val tempC: Double?, val code: Int?, val desc: String)

fun markers(state: UiState): List<Marker> = state.settings.places.map { p ->
    val fc = state.forecasts[p.key]
    Marker(p.key, p.name, p.latitude, p.longitude, Moon.zoneFor(p.timezone.takeIf { it != "auto" } ?: fc?.timezone, fc?.utcOffsetSeconds),
        fc?.current?.temperature, fc?.current?.weatherCode, fc?.current?.let { WeatherCodes.describe(it.weatherCode) } ?: "")
}

/** The composed Earth (day/night and clouds), redrawn every 5 minutes while showing; clouds checked hourly. */
class EarthImage(val bitmap: Bitmap, val time: Instant, val cloudNote: String)

@Composable
fun rememberEarthImage(clouds: Boolean, clock: io.github.shanebl030glitch.rbsbweather.data.ClockFormat): EarthImage? {
    val ctx = LocalContext.current
    var img by remember { mutableStateOf<EarthImage?>(null) }
    LaunchedEffect(clouds) {
        EarthRender.init(ctx.applicationContext.assets)
        MoonRender.init(ctx.applicationContext.assets)
        val store = CloudStore(File(ctx.filesDir, "clouds"))
        while (true) {
            val result = withContext(Dispatchers.Default) {
                try {
                    var err = ""
                    if (clouds && store.due()) try {
                        withContext(Dispatchers.IO) {
                            store.update(defaultConditionalGet, { b ->
                                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, o)
                                if (o.outWidth > 0) o.outWidth to o.outHeight else null
                            })
                        }
                    } catch (e: Exception) { err = e.message ?: "failed" }
                    if (err.isEmpty()) err = store.lastError()
                    val alpha = if (clouds && store.hasImage()) EarthRender.cloudAlpha(store.imageFile) else null
                    val t = Instant.now()
                    EarthImage(EarthRender.compose(t, alpha), t, if (clouds) store.note(alpha != null, err, clock) else "")
                } catch (e: Exception) { null }
            }
            if (result != null) img = result
            delay(Earth.RECOMPOSE_S * 1000L)
        }
    }
    return img
}

/** Card position next to an anchor: the corner covering the fewest other markers, then the closest (place_card). */
fun placeCard(cw: Float, ch: Float, ax: Float, ay: Float, others: List<Offset>, width: Float, height: Float): Offset {
    fun clamp(x: Float, y: Float) = Offset(x.coerceIn(4f, maxOf(4f, width - cw - 4)), y.coerceIn(4f, maxOf(4f, height - ch - 4)))
    val cands = listOf(clamp(ax + 16, ay - ch - 10), clamp(ax - 16 - cw, ay - ch - 10), clamp(ax + 16, ay + 14), clamp(ax - 16 - cw, ay + 14))
    return cands.minWith(compareBy({ o ->
        val covers = others.count { it.x in (o.x - 8)..(o.x + cw + 8) && it.y in (o.y - 8)..(o.y + ch + 8) }
        val self = ax in o.x..(o.x + cw) && ay in o.y..(o.y + ch)
        covers + if (self) 10 else 0
    }, { o -> hypot(o.x + cw / 2 - ax, o.y + ch / 2 - ay) }))
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EarthScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    val s = state.settings
    val img = rememberEarthImage(s.earthClouds, s.units.clock)
    val ms = remember(state.settings.places, state.forecasts) { markers(state) }
    val quakes = if (s.quakes.show) state.quakes else emptyList()
    var quake by remember { mutableStateOf<Quake?>(state.focusQuake?.let { id -> state.quakes.firstOrNull { it.id == id } }) }
    // one clock for pulses (selected marker, recent quakes) and the card's local time
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(s.animations) {
        if (!s.animations) return@LaunchedEffect
        val start = withFrameNanos { it }
        var last = 0L
        while (true) withFrameNanos { now -> if (now - last > 80_000_000L) { last = now; t = (now - start) / 1e9f } }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("earth")) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            SegmentedButton(!state.earthGlobe, { model.setEarthGlobe(false) }, SegmentedButtonDefaults.itemShape(0, 2), Modifier.testTag("earthFlat")) { Text("Earth Flat") }
            SegmentedButton(state.earthGlobe, { model.setEarthGlobe(true) }, SegmentedButtonDefaults.itemShape(1, 2), Modifier.testTag("earthGlobe")) { Text("Earth Globe") }
        }
        Text(img?.let { Earth.infoText(it.time, s.units.clock) + (if (it.cloudNote.isNotEmpty()) " " + it.cloudNote else "") } ?: "Drawing the Earth…",
            fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        if (state.earthGlobe) GlobeView(img, ms, s.settingsSelected(), quakes, t, state, model, { quake = it })
        else FlatMap(img, ms, s.settingsSelected(), quakes, t, state, model, { quake = it })
        quake?.let { q -> QuakeCard(q, state, onClose = { quake = null }) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(s.earthClouds, { model.setEarthClouds(it) }, Modifier.testTag("liveClouds")); Text("Live clouds", fontSize = 13.sp)
            Checkbox(s.quakes.show, { model.setEarthQuakes(it) }, Modifier.testTag("quakesOnMap"))
            Text("Earthquakes" + if (s.quakes.show) " (${state.quakes.size})" else "", fontSize = 13.sp)
        }
        Text("☀ Sun overhead  ·  ● Moon overhead  ·  quakes: red in the last hour (pulsing), orange 6 hours, yellow a day; bigger dots are bigger quakes. Tap a place or a quake.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), modifier = Modifier.padding(horizontal = 12.dp))
        Text(Earth.CREDIT + "\n" + Clouds.CREDIT + "\n" + Quakes.CREDIT, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), modifier = Modifier.padding(12.dp))
    }
}

private fun io.github.shanebl030glitch.rbsbweather.data.Settings.settingsSelected() = selectedPlace()?.key

// --------------------------------------------------------------------- drawing helpers
private fun DrawScope.body(p: Offset, sun: Boolean) {
    val rad = if (sun) 7f else 5f
    val col = if (sun) Color(255, 210, 80) else Color(210, 220, 255)
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 150 / 255f), col.copy(alpha = 0f)), p, rad * 3), rad * 3, p)
    drawCircle(col, rad, p)
    drawCircle(if (sun) Color(60, 40, 0) else Color(40, 40, 60), rad, p, style = Stroke(1f))
}

private fun DrawScope.quakeDots(quakes: List<Quake>, t: Float, selected: String?, project: (Double, Double) -> Offset?) {
    val now = nowS()
    for (q in quakes.sortedBy { it.mag }) {
        val p = project(q.lat, q.lon) ?: continue
        val col = Color(quakeColour(q, now))
        val r = quakeRadius(q.mag).toFloat() * density / 1.5f
        if (q.age(now) < Quakes.PULSE_S || q.id == selected) for (k in listOf(0f, 0.5f)) {
            val ph = (t * 0.7f + k) % 1f
            drawCircle(col.copy(alpha = 0.9f * (1 - ph)), r + (10 + r * 1.6f) * ph, p, style = Stroke(2f))
        }
        drawCircle(col.copy(alpha = if (q.age(now) < 86400) 0.84f else 0.6f), r, p)
        drawCircle(Color(20, 20, 20, 200), r, p, style = Stroke(1f))
        if (q.id == selected) drawCircle(Color.White, r + 3, p, style = Stroke(1.6f))
    }
}

private fun DrawScope.markerDots(ms: List<Marker>, selected: String?, t: Float, tm: androidx.compose.ui.text.TextMeasurer, project: (Double, Double) -> Offset?) {
    val label = TextStyle(color = Color.White, fontSize = 11.sp)
    val shadow = TextStyle(color = Color(0, 0, 0, 170), fontSize = 11.sp)
    for (m in ms) {
        val p = project(m.lat, m.lon) ?: continue
        if (m.key == selected) {
            val ph = (t * 0.8f) % 1f
            drawCircle(Color(255, 220, 90).copy(alpha = 0.86f * (1 - ph)), 4 + 12 * ph * density / 1.5f, p, style = Stroke(2f))
            drawCircle(Color(255, 210, 60), 4.5f * density / 1.5f, p); drawCircle(Color(20, 20, 20), 4.5f * density / 1.5f, p, style = Stroke(1.5f))
            continue
        }
        drawCircle(Color.White, 3.5f * density / 1.5f, p); drawCircle(Color(20, 20, 20), 3.5f * density / 1.5f, p, style = Stroke(1.2f))
        val tl = tm.measure(m.name, label)
        val dx = if (p.x + 6 + tl.size.width > size.width - 2) -6f - tl.size.width else 6f
        drawText(tm.measure(m.name, shadow), topLeft = Offset(p.x + dx + 1, p.y - tl.size.height / 2 + 1))
        drawText(tl, topLeft = Offset(p.x + dx, p.y - tl.size.height / 2))
    }
}

@Composable
private fun PlaceCard(m: Marker, state: UiState, modifier: Modifier = Modifier) {
    val now = Instant.now()
    val lt = now.atZone(m.zone)
    val elev = Earth.solarElevation(now, m.lat, m.lon)
    val st = Earth.dayState(elev)
    Card(modifier.testTag("earthCard"), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(10, 14, 28, 215))) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(m.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(lt.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + Units.clock(lt.hour, lt.minute, state.settings.units.clock) +
                "  " + lt.zone.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("${Earth.STATE_SYMBOL[st]} $st · sun " + String.format(Locale.US, "%+.0f°", elev), color = Color.White, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.tempC != null) {
                    AnimatedWeatherIcon(m.code, st == "Day", 28.dp, 0.2, state.settings.animations)
                    Text(" " + Units.fmtTemp(m.tempC, state.settings.units.temp) + " ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(m.desc, color = Color.White, fontSize = 12.sp)
                } else Text("– no weather yet", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun QuakeCard(q: Quake, state: UiState, onClose: () -> Unit) {
    val uri = LocalUriHandler.current
    Card(Modifier.fillMaxWidth().padding(12.dp).testTag("quakeCard")) {
        Column(Modifier.padding(12.dp)) {
            Text(describeQuake(q), fontWeight = FontWeight.Bold)
            nearestPlace(q, state.settings.places)?.let { (p, km) -> Text("${km.roundToInt()} km from ${p.name}", fontSize = 13.sp) }
            if (q.tsunami && q.mag >= 6.5) Text("A large quake under the sea: if you're near the coast, check your official tsunami advice.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            Row {
                if (q.url.isNotEmpty()) TextButton(onClick = { try { uri.openUri(q.url) } catch (e: Exception) { } }) { Text("Details on ${q.source}") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}

private fun nearest(points: List<Pair<String, Offset>>, at: Offset, radius: Float): String? =
    points.map { it.first to hypot(it.second.x - at.x, it.second.y - at.y) }.filter { it.second <= radius }.minByOrNull { it.second }?.first

// ------------------------------------------------------------------------- Earth Flat
@Composable
private fun FlatMap(img: EarthImage?, ms: List<Marker>, selected: String?, quakes: List<Quake>, t: Float, state: UiState, model: AppModel, onQuake: (Quake?) -> Unit) {
    val tm = rememberTextMeasurer()
    val bmp = remember(img) { img?.bitmap?.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val sel = ms.firstOrNull { it.key == selected }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val h = maxWidth / 2
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(8.dp)).background(Color(0xFF05070D)).onSizeChanged { box = it }.testTag("flatMap")) {
            fun point(lat: Double, lon: Double): Offset {
                val (x, y) = Earth.toMapXY(lat, lon, box.width.toDouble(), box.height.toDouble())
                return Offset(x.toFloat() * zoom + pan.x, y.toFloat() * zoom + pan.y)
            }
            Canvas(Modifier.fillMaxSize().pointerInput(box) {
                detectTransformGestures { _, p, z, _ ->
                    val nz = (zoom * z).coerceIn(1f, 4f)
                    pan = Offset((pan.x + p.x).coerceIn(box.width * (1 - nz), 0f), (pan.y + p.y).coerceIn(box.height * (1 - nz), 0f))
                    zoom = nz
                }
            }.pointerInput(box, ms, quakes, zoom, pan) {
                detectTapGestures(onDoubleTap = { zoom = 1f; pan = Offset.Zero }) { at ->
                    val key = nearest(ms.map { it.key to point(it.lat, it.lon) }, at, 24f)
                    if (key != null) { model.select(key); onQuake(null) }
                    else {
                        val q = quakes.map { it to point(it.lat, it.lon) }.filter { (q, p) -> hypot(p.x - at.x, p.y - at.y) <= quakeRadius(q.mag) * density / 1.5f + 12 }
                            .minByOrNull { (_, p) -> hypot(p.x - at.x, p.y - at.y) }?.first
                        onQuake(q)
                    }
                }
            }) {
                if (bmp == null) return@Canvas
                drawImage(bmp, IntOffset.Zero, IntSize(bmp.width, bmp.height), IntOffset(pan.x.roundToInt(), pan.y.roundToInt()),
                    IntSize((size.width * zoom).roundToInt(), (size.height * zoom).roundToInt()))
                val now = img!!.time
                Earth.subsolarPoint(now).let { (a, o) -> body(point(a, o), true) }
                Earth.sublunarPoint(now).let { (a, o) -> body(point(a, o), false) }
                quakeDots(quakes, t, state.focusQuake) { a, o -> point(a, o) }
                markerDots(ms, selected, t, tm) { a, o -> point(a, o) }
                if (sel != null && cardSize != IntSize.Zero) {
                    val p = point(sel.lat, sel.lon)
                    val c = placeCard(cardSize.width.toFloat(), cardSize.height.toFloat(), p.x, p.y, ms.filter { it.key != sel.key }.map { point(it.lat, it.lon) }, size.width, size.height)
                    val target = Offset(p.x.coerceIn(c.x, c.x + cardSize.width), p.y.coerceIn(c.y, c.y + cardSize.height))
                    drawLine(Color.White.copy(alpha = 0.6f), p, target, 1f)
                }
            }
            if (bmp == null) Text("Drawing the Earth…", color = Color(200, 205, 215), modifier = Modifier.align(Alignment.Center))
            if (sel != null && box != IntSize.Zero) {
                val p = point(sel.lat, sel.lon)
                val c = placeCard(cardSize.width.toFloat(), cardSize.height.toFloat(), p.x, p.y, ms.filter { it.key != sel.key }.map { point(it.lat, it.lon) }, box.width.toFloat(), box.height.toFloat())
                PlaceCard(sel, state, Modifier.offset { IntOffset(c.x.roundToInt(), c.y.roundToInt()) }.onSizeChanged { cardSize = it })
            }
        }
    }
    Text("Pinch to zoom, double-tap to see the whole world.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 12.dp))
}

// ------------------------------------------------------------------------ Earth Globe
private val stars = Sky.StarField()

@Composable
private fun GlobeView(img: EarthImage?, ms: List<Marker>, selected: String?, quakes: List<Quake>, t: Float, state: UiState, model: AppModel, onQuake: (Quake?) -> Unit) {
    val tm = rememberTextMeasurer()
    val sel = ms.firstOrNull { it.key == selected }
    var cLat by remember { mutableDoubleStateOf(sel?.lat?.coerceIn(-60.0, 60.0) ?: -20.0) }
    var cLon by remember { mutableDoubleStateOf(sel?.lon ?: 170.0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var spin by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var globe by remember { mutableStateOf<ImageBitmap?>(null) }
    val renderer = remember(img) { img?.let { GlobeRenderer(it.bitmap) } }
    var turnTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var turnTick by remember { mutableIntStateOf(0) }
    // turn smoothly to face the selected place
    LaunchedEffect(selected) { sel?.let { turnTarget = it.lat.coerceIn(-70.0, 70.0) to it.lon; turnTick++ } }
    // from an earthquake notification: turn to the quake
    LaunchedEffect(state.focusQuake) { quakes.firstOrNull { it.id == state.focusQuake }?.let { turnTarget = it.lat.coerceIn(-70.0, 70.0) to it.lon; turnTick++ } }
    LaunchedEffect(turnTick) {
        val target = turnTarget ?: return@LaunchedEffect
        val (fromLat, fromLon) = cLat to cLon
        val dLon = ((target.second - fromLon + 540) % 360) - 180
        val start = withFrameNanos { it }
        while (true) {
            val done = withFrameNanos { now ->
                val f = ((now - start) / 700e6).coerceIn(0.0, 1.0)
                val e = if (f < 0.5) 4 * f * f * f else 1 - Math.pow(-2 * f + 2, 3.0) / 2
                cLat = fromLat + (target.first - fromLat) * e; cLon = Earth.normLon(fromLon + dLon * e)
                f >= 1.0
            }
            if (done) break
        }
    }
    LaunchedEffect(spin) {
        if (!spin) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) withFrameNanos { now -> cLon = Earth.normLon(cLon + 8.0 * (now - last) / 1e9); last = now }
    }
    // render the globe off the main thread; lower resolution while it moves, sharp when it settles
    LaunchedEffect(renderer, box) {
        val r = renderer ?: return@LaunchedEffect
        snapshotFlow { Triple(cLat to cLon, zoom, dragging || spin) }.collectLatest { (c, z, moving) ->
            val full = (min(box.width, box.height) * 0.86f * z).roundToInt().coerceIn(64, 1300)
            val px = if (moving) (full * 0.6f).roundToInt() else full
            globe = withContext(Dispatchers.Default) { r.render(px, c.first, c.second).asImageBitmap() }
        }
    }
    Box(Modifier.fillMaxWidth().height(420.dp).padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF02030A))
        .onSizeChanged { box = it }.testTag("globe")) {
        val rad = min(box.width, box.height) * 0.43f * zoom
        val c = Offset(box.width / 2f, box.height / 2f)
        fun point(lat: Double, lon: Double): Offset? {
            val (x, y, vis) = Globe.project(lat, lon, cLat, cLon)
            return if (vis) Offset(c.x + x.toFloat() * rad, c.y - y.toFloat() * rad) else null
        }
        Canvas(Modifier.fillMaxSize().pointerInput(box) {
            detectDragGestures(onDragStart = { dragging = true; spin = false }, onDragEnd = { dragging = false }, onDragCancel = { dragging = false }) { ch, d ->
                ch.consume()
                val k = 57.3 / maxOf(rad, 1f)
                cLon = Earth.normLon(cLon - d.x * k); cLat = (cLat + d.y * k).coerceIn(-85.0, 85.0)
            }
        }.pointerInput(box) {
            detectTransformGestures { _, _, z, _ -> if (z != 1f) zoom = (zoom * z).coerceIn(0.6f, 3f) }
        }.pointerInput(box, ms, quakes, cLat, cLon, zoom) {
            detectTapGestures(onDoubleTap = { sel?.let { turnTarget = it.lat.coerceIn(-70.0, 70.0) to it.lon; turnTick++ } }) { at ->
                val key = nearest(ms.mapNotNull { m -> point(m.lat, m.lon)?.let { m.key to it } }, at, 24f)
                if (key != null) { model.select(key); onQuake(null) }
                else onQuake(quakes.mapNotNull { q -> point(q.lat, q.lon)?.let { q to it } }
                    .filter { (q, p) -> hypot(p.x - at.x, p.y - at.y) <= quakeRadius(q.mag) * density / 1.5f + 12 }.minByOrNull { (_, p) -> hypot(p.x - at.x, p.y - at.y) }?.first)
            }
        }) {
            val now = img?.time ?: Instant.now()
            paintSky(now, c, rad, cLat, cLon)
            // atmosphere glow
            drawCircle(Brush.radialGradient(0.86f to Color(90, 150, 255, 0), 0.90f to Color(110, 170, 255, 120), 1f to Color(90, 150, 255, 0), center = c, radius = rad * 1.12f), rad * 1.12f, c)
            val g = globe ?: return@Canvas
            drawImage(g, IntOffset.Zero, IntSize(g.width, g.height), IntOffset((c.x - rad).roundToInt(), (c.y - rad).roundToInt()), IntSize((2 * rad).roundToInt(), (2 * rad).roundToInt()))
            sunriseRim(now, c, rad, cLat, cLon)
            Earth.subsolarPoint(now).let { (a, o) -> point(a, o)?.let { body(it, true) } }
            Earth.sublunarPoint(now).let { (a, o) -> point(a, o)?.let { body(it, false) } }
            quakeDots(quakes, t, state.focusQuake) { a, o -> point(a, o) }
            markerDots(ms, selected, t, tm) { a, o -> point(a, o) }
            if (sel != null && cardSize != IntSize.Zero) point(sel.lat, sel.lon)?.let { p ->
                val cc = placeCard(cardSize.width.toFloat(), cardSize.height.toFloat(), p.x, p.y, ms.filter { it.key != sel.key }.mapNotNull { point(it.lat, it.lon) }, size.width, size.height)
                drawLine(Color.White.copy(alpha = 0.6f), p, Offset(p.x.coerceIn(cc.x, cc.x + cardSize.width), p.y.coerceIn(cc.y, cc.y + cardSize.height)), 1f)
            }
        }
        if (img == null) Text("Drawing the Earth…", color = Color(200, 205, 215), modifier = Modifier.align(Alignment.Center))
        if (sel != null && box != IntSize.Zero) point(sel.lat, sel.lon)?.let { p ->
            val cc = placeCard(cardSize.width.toFloat(), cardSize.height.toFloat(), p.x, p.y, ms.filter { it.key != sel.key }.mapNotNull { point(it.lat, it.lon) }, box.width.toFloat(), box.height.toFloat())
            PlaceCard(sel, state, Modifier.offset { IntOffset(cc.x.roundToInt(), cc.y.roundToInt()) }.onSizeChanged { cardSize = it })
        }
        Column(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            TextButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(3f) }, modifier = Modifier.testTag("zoomIn")) { Text("+", color = Color.White, fontSize = 20.sp) }
            TextButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(0.6f) }, modifier = Modifier.testTag("zoomOut")) { Text("−", color = Color.White, fontSize = 20.sp) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(spin, { spin = it }, Modifier.testTag("rotateSlowly")); Text("Rotate slowly", fontSize = 13.sp)
        TextButton(onClick = { sel?.let { turnTarget = it.lat.coerceIn(-70.0, 70.0) to it.lon; turnTick++ } }, enabled = sel != null) { Text("Centre on selected") }
        Text(String.format(Locale.US, "%.0f%%", zoom * 100), fontSize = 12.sp)
    }
    Text("Drag to spin, pinch or +/− to zoom, double-tap to turn back to the selected place.", fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 12.dp))
}

private val moonCache = object : LinkedHashMap<String, ImageBitmap>(4, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 3
}

/** Stars, the Sun and the Moon where they really are behind the Earth, and edge markers when off-screen. */
private fun DrawScope.paintSky(t: Instant, c: Offset, rad: Float, cLat: Double, cLon: Double) {
    val b = Sky.basis(cLat, cLon)
    val limit = hypot(size.width, size.height) / 2 / rad + 0.1
    for (st in stars.positions(Moon.gmst(Moon.julianDay(t)), b, limit.toDouble()))
        drawCircle(Color(255, 255, 255, st[3].toInt()), st[2] * 0.6f * density / 1.5f, Offset(c.x + st[0] * rad, c.y - st[1] * rad))
    val sun = Earth.subsolarPoint(t).let { Sky.ecef(it.first, it.second) }
    val moon = Earth.sublunarPoint(t).let { Sky.ecef(it.first, it.second) }
    var sunShown = false; var moonShown = false
    Sky.skyXY(sun, b)?.let { xy ->
        val p = Offset(c.x + xy.first.toFloat() * rad, c.y - xy.second.toFloat() * rad)
        val sr = maxOf(7f, rad * Sky.SUN_SIZE.toFloat())
        if (p.x in -sr * 6..size.width + sr * 6 && p.y in -sr * 6..size.height + sr * 6) {
            val glow = sr * 6f
            drawCircle(Brush.radialGradient(0f to Color(255, 246, 225, 170), 0.12f to Color(255, 228, 175, 95), 0.35f to Color(255, 200, 130, 30),
                0.7f to Color(255, 180, 110, 8), 1f to Color(255, 170, 100, 0), center = p, radius = glow), glow, p)
            for ((ang, len) in listOf(0f to 0.8f, 90f to 0.8f, 45f to 0.45f, 135f to 0.45f)) rotate(ang, p) {
                drawOval(Brush.horizontalGradient(0f to Color(255, 245, 225, 0), 0.5f to Color(255, 250, 235, 55), 1f to Color(255, 245, 225, 0), startX = p.x - glow * len, endX = p.x + glow * len),
                    Offset(p.x - glow * len, p.y - sr * 0.15f), Size(2 * glow * len, sr * 0.3f))
            }
            drawCircle(Brush.radialGradient(0f to Color(255, 255, 250), 0.4f to Color(255, 252, 238, 235), 0.55f to Color(255, 236, 190, 110), 1f to Color(255, 210, 140, 0), center = p, radius = sr * 2.4f), sr * 2.4f, p)
            drawCircle(Brush.radialGradient(0f to Color(255, 255, 253), 0.7f to Color(255, 251, 236), 1f to Color(255, 228, 165), center = p, radius = sr), sr, p)
            sunShown = p.x in 0f..size.width && p.y in 0f..size.height
        }
    }
    if (!sunShown) indicator(sun, b, "sun")
    Sky.skyXY(moon, b)?.let { xy ->
        val p = Offset(c.x + xy.first.toFloat() * rad, c.y - xy.second.toFloat() * rad)
        val sz = (rad * Sky.MOON_SIZE.toFloat() * 2).coerceIn(24f, 360f * density / 1.5f)
        if (p.x in -sz..size.width + sz && p.y in -sz..size.height + sz && !Sky.hiddenByEarth(xy, Sky.MOON_SIZE)) {
            Sky.moonOrientation(moon, sun, b)?.let { (limb, north) ->
                val ph = Moon.phase(t)
                val pxSize = maxOf(16, (sz / 8).roundToInt() * 8)
                val key = "$pxSize/${ph.phaseAngle.roundToInt()}/${(limb / 2).roundToInt() * 2}"
                val im = synchronized(moonCache) { moonCache[key] } ?: MoonRender.render(pxSize, ph.phaseAngle, (limb / 2).roundToInt() * 2.0, rotation = 0.0)?.asImageBitmap()?.also { synchronized(moonCache) { moonCache[key] = it } }
                if (im != null) rotate(north.toFloat(), p) {
                    drawImage(im, IntOffset.Zero, IntSize(im.width, im.height), IntOffset((p.x - sz / 2).roundToInt(), (p.y - sz / 2).roundToInt()), IntSize(sz.roundToInt(), sz.roundToInt()))
                } else drawCircle(Color(200, 205, 215), sz / 2, p)
                moonShown = p.x in 0f..size.width && p.y in 0f..size.height
            }
        }
    }
    if (!moonShown) indicator(moon, b, "moon")
}

/** A small marker at the edge pointing to an off-screen Sun or Moon (paler when it's behind you). */
private fun DrawScope.indicator(d: Sky.V, b: Triple<Sky.V, Sky.V, Sky.V>, kind: String) {
    val v = Sky.toView(d, b)
    val len = hypot(v.x, v.y)
    if (len < 1e-6) return
    val ux = (v.x / len).toFloat(); val uy = (-v.y / len).toFloat()
    val m = 16f
    val sx = if (ux != 0f) (size.width / 2 - m) / abs(ux) else Float.MAX_VALUE
    val sy = if (uy != 0f) (size.height / 2 - m) / abs(uy) else Float.MAX_VALUE
    val s = min(sx, sy)
    val p = Offset(size.width / 2 + ux * s, size.height / 2 + uy * s)
    val alpha = if (v.z > 0) 0.45f else 0.85f
    val col = if (kind == "sun") Color(255, 214, 90) else Color(215, 222, 240)
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.63f * alpha), col.copy(alpha = 0f)), p, 12f), 12f, p)
    drawCircle(col.copy(alpha = alpha), 4.5f, p)
    if (kind == "moon") drawCircle(Color(2, 3, 10), 3.8f, p + Offset(2.2f, -1.2f))
}

/** When the Sun is just behind the Earth, its light blazes round that edge, like sunrise from orbit. */
private fun DrawScope.sunriseRim(t: Instant, c: Offset, rad: Float, cLat: Double, cLon: Double) {
    val b = Sky.basis(cLat, cLon)
    val xy = Sky.skyXY(Earth.subsolarPoint(t).let { Sky.ecef(it.first, it.second) }, b) ?: return
    val rr = hypot(xy.first, xy.second)
    if (rr <= 0.5 || rr >= 1.6) return
    val s = (1.0 - abs(rr - 1.0) / 0.6).toFloat()
    if (s <= 0) return
    val lp = Offset(c.x + (xy.first / rr).toFloat() * rad, c.y - (xy.second / rr).toFloat() * rad)
    drawCircle(Brush.radialGradient(0f to Color(255, 240, 210, (230 * s).toInt()), 0.12f to Color(255, 205, 140, (150 * s).toInt()),
        0.45f to Color(255, 150, 80, (40 * s).toInt()), 1f to Color(255, 120, 60, 0), center = lp, radius = rad * 0.5f), rad * 0.5f, lp, blendMode = BlendMode.Plus)
}

