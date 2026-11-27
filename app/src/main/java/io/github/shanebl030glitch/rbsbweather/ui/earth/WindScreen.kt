// The Wind tab (0.6), ported from the desktop's wind_panel.py: world weather on the globe, with
// moving wind streaks. Layers: 🌬 wind speed, 🌡 air temperature and 🌧 rain & snow; the streaks
// always follow the wind, whichever layer is coloured in. The globe turns and zooms like the Earth
// Globe; tap anywhere for the weather there. Data: NOAA GFS (see data/Wind.kt).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.earth

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.ClockFormat
import io.github.shanebl030glitch.rbsbweather.data.Globe
import io.github.shanebl030glitch.rbsbweather.data.PrecipUnit
import io.github.shanebl030glitch.rbsbweather.data.TempUnit
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.Wind
import io.github.shanebl030glitch.rbsbweather.data.WindClient
import io.github.shanebl030glitch.rbsbweather.data.WindField
import io.github.shanebl030glitch.rbsbweather.data.WindStore
import io.github.shanebl030glitch.rbsbweather.data.WindUnit
import io.github.shanebl030glitch.rbsbweather.ui.AppModel
import io.github.shanebl030glitch.rbsbweather.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object WindRender {
    const val PARTICLES = 1500
    val LIFE = 60..150                   // frames a streak lives
    const val DEG_PER_MS_FRAME = 0.018   // degrees moved per frame per m/s (at 30 frames a second)
    const val TRAIL_FADE = 0.95f         // how much of a trail survives each frame

    val LAYERS = listOf("wind" to "🌬 Wind", "temp" to "🌡 Temperature", "rain" to "🌧 Rain & snow")
    val WHAT = mapOf("wind" to "Wind 10 m above the ground", "temp" to "Air temperature 2 m above the ground (moving streaks: the wind)",
        "rain" to "Rain and snow, as the average rate over the few hours before (moving streaks: the wind)")

    /**
     * The globe picture: the day Earth map dimmed, the layer's colours over it (smoothly scaled up
     * from the 1° grid; rain see-through where dry), and the coastlines traced from the map.
     */
    fun compose(f: WindField, layer: String, w: Int = EarthRender.W, h: Int = EarthRender.H, day: IntArray? = null): Bitmap {
        val cols = Wind.layerPixels(f, layer)
        val ni = f.ni; val nj = f.nj
        val dayPx = day ?: try { EarthRender.dayPixels() } catch (e: Exception) { null }
        val dw = EarthRender.W; val dh = EarthRender.H
        val over = if (layer == "rain") 0.85f else 0.62f
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val lat = 90.0 - (y + 0.5) * 180.0 / h
            val fy = ((f.lat0 - lat) / f.dlat).coerceIn(0.0, nj - 1.000001)
            val j = fy.toInt(); val ty = (fy - j).toFloat()
            val dy = min(dh - 1, (y * dh / h))
            for (x in 0 until w) {
                val fx = (x + 0.5) * ni / w - 0.5
                val i0 = Math.floorMod(kotlin.math.floor(fx).toInt(), ni); val i1 = (i0 + 1) % ni; val tx = (fx - kotlin.math.floor(fx)).toFloat()
                fun ch(sh: Int): Float {
                    fun c(p: Int) = ((p ushr sh) and 0xFF).toFloat()
                    val a = c(cols[j * ni + i0]) * (1 - tx) + c(cols[j * ni + i1]) * tx
                    val b = c(cols[(j + 1) * ni + i0]) * (1 - tx) + c(cols[(j + 1) * ni + i1]) * tx
                    return a * (1 - ty) + b * ty
                }
                val la = ch(24) / 255f * over
                // dimmed Earth under the colours
                var r = 10f; var g = 14f; var b = 24f
                if (dayPx != null) {
                    val d = dayPx[dy * dw + min(dw - 1, x * dw / w)]
                    r += (((d shr 16) and 0xFF) - r) * 0.75f; g += (((d shr 8) and 0xFF) - g) * 0.75f; b += ((d and 0xFF) - b) * 0.75f
                }
                r += (ch(16) - r) * la; g += (ch(8) - g) * la; b += (ch(0) - b) * la
                out[y * w + x] = (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
            }
        }
        if (dayPx != null && w == dw && h == dh) coastlines(dayPx, out, w, h)
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Outline the coasts: the day map's oceans are one even blue, so a coast is where sea meets anything else. */
    fun isSea(p: Int): Boolean {
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
        return abs(r - 30) < 28 && abs(g - 59) < 30 && abs(b - 117) < 40 && b > r + 40
    }

    private fun coastlines(day: IntArray, out: IntArray, w: Int, h: Int) {
        val sea = BooleanArray(w * h) { isSea(day[it]) }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val edge = (x > 0 && sea[i] != sea[i - 1]) || (x == 0 && sea[i] != sea[i + w - 1]) || (y > 0 && sea[i] != sea[i - w])
            if (edge) {
                for (k in listOf(i, if (x + 1 < w) i + 1 else i, if (y + 1 < h) i + w else i)) {
                    val p = out[k]
                    fun mix(c: Int) = (c + (235 - c) * 0.75f).toInt()
                    out[k] = (0xFF shl 24) or (mix((p shr 16) and 0xFF) shl 16) or (mix((p shr 8) and 0xFF) shl 8) or mix(p and 0xFF)
                }
            }
        }
    }

    fun windText(u: Double, v: Double, unit: WindUnit): String {
        val (spd, from) = Wind.speedAndDirection(u, v)
        return "${Units.fmtWind(spd * 3.6, unit)} from the ${Units.compass(from)} (${from.roundToInt()}°)"
    }

    fun rainText(mmH: Double, unit: PrecipUnit): String = when {
        mmH < Wind.RAIN_MIN -> "dry"
        unit == PrecipUnit.IN -> String.format(Locale.US, "%.2f in/h", mmH / 25.4)
        else -> String.format(Locale.US, "%.1f mm/h", mmH)
    }

    /** Everything known at a point: wind, and temperature and rain when downloaded. */
    fun pointText(f: WindField, lat: Double, lon: Double, w: WindUnit, t: TempUnit, p: PrecipUnit): String {
        val (u, v) = f.at(lat, lon)
        val parts = mutableListOf("wind " + windText(u, v, w))
        f.tempAt(lat, lon)?.let { parts += Units.fmtTemp(it, t) }
        f.rainAt(lat, lon)?.let { parts += "rain/snow " + rainText(it, p) }
        return parts.joinToString(" · ")
    }

    fun infoText(f: WindField, layer: String, clock: ClockFormat, note: String = "", zone: ZoneId = ZoneId.systemDefault()): String {
        fun at(i: java.time.Instant): String { val z = i.atZone(zone); return z.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + Units.clock(z.hour, z.minute, clock) }
        return "${WHAT[layer] ?: WHAT.getValue("wind")} for ${at(f.valid)} (your time), from NOAA's GFS forecast run of ${at(f.run)}. Updated every 3 hours." +
            if (note.isNotEmpty()) " $note" else ""
    }
}

/** Streak positions carried along by the wind. */
class Particles(val n: Int = WindRender.PARTICLES, seed: Long = 7) {
    private val rng = Random(seed)
    val lat = DoubleArray(n); val lon = DoubleArray(n); private val age = IntArray(n); private val life = IntArray(n)
    var ready = false; private set

    private fun randomPoint(cLat: Double, cLon: Double): Pair<Double, Double> {
        while (true) {
            val x = rng.nextDouble() * 2 - 1; val y = rng.nextDouble() * 2 - 1
            if (x * x + y * y < 0.98) Globe.inverse(x, y, cLat, cLon)?.let { return it }
        }
    }

    fun seedAll(cLat: Double, cLon: Double) {
        for (i in 0 until n) {
            val (a, o) = randomPoint(cLat, cLon); lat[i] = a; lon[i] = o
            life[i] = WindRender.LIFE.first + rng.nextInt(WindRender.LIFE.last - WindRender.LIFE.first)
            age[i] = rng.nextInt(life[i])
        }
        ready = true
    }

    /** Move every streak one frame. Fills old positions into `oldLat/oldLon`; `alive[i]` false = just reborn (no line). */
    fun step(f: WindField, cLat: Double, cLon: Double, oldLat: DoubleArray, oldLon: DoubleArray, alive: BooleanArray) {
        for (i in 0 until n) {
            oldLat[i] = lat[i]; oldLon[i] = lon[i]
            val (u, v) = f.at(lat[i], lon[i])
            val coslat = max(cos(Math.toRadians(lat[i])), 0.15)
            lon[i] = ((lon[i] + u * WindRender.DEG_PER_MS_FRAME / coslat + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
            lat[i] = (lat[i] + v * WindRender.DEG_PER_MS_FRAME).coerceIn(-89.5, 89.5)
            age[i]++
            if (age[i] >= life[i]) {
                val (a, o) = randomPoint(cLat, cLon); lat[i] = a; lon[i] = o; age[i] = 0
                life[i] = WindRender.LIFE.first + rng.nextInt(WindRender.LIFE.last - WindRender.LIFE.first)
                alive[i] = false
            } else alive[i] = true
        }
    }
}

class WindState(val field: WindField?, val error: String, val note: String, val source: String)

@Composable
private fun rememberWind(): WindState? {
    val ctx = LocalContext.current
    var st by remember { mutableStateOf<WindState?>(null) }
    LaunchedEffect(Unit) {
        val store = WindStore(File(ctx.filesDir, "wind"))
        while (true) {
            st = withContext(Dispatchers.IO) {
                try {
                    val (f, note) = store.update(WindClient())
                    WindState(f, if (f == null) "No wind map yet." else "", note, store.source())
                } catch (e: Exception) {
                    WindState(null, (e.message ?: "The wind map couldn't be downloaded.") + " It will try again in a few minutes.", "", "")
                } catch (e: OutOfMemoryError) { WindState(null, "Not enough memory to read the wind map.", "", "") }
            }
            delay(10 * 60_000L)                        // checks every 10 minutes; downloads only every 3 hours
        }
    }
    return st
}

@Composable
fun WindScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier, wind: WindState? = rememberWind()) {
    val s = state.settings
    val uri = LocalUriHandler.current
    var layer by remember { mutableStateOf("wind") }
    var tapped by remember { mutableStateOf<String?>(null) }
    val fld = wind?.field
    val shown = if (fld != null && layer in fld.layers) layer else "wind"
    val ms = remember(state.settings.places, state.forecasts) { markers(state) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("wind")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WindRender.LAYERS.forEach { (id, label) ->
                FilterChip(selected = shown == id, onClick = { layer = id }, enabled = fld == null || id in fld.layers,
                    label = { Text(label, fontSize = 12.sp) }, modifier = Modifier.testTag("layer-$id"))
            }
        }
        Text(when {
            fld != null -> WindRender.infoText(fld, shown, s.units.clock, wind.note)
            wind == null -> "Getting the world's weather from NOAA…"
            else -> wind.error
        }, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp).testTag("windInfo"))
        WindGlobe(fld, shown, ms, s.settingsSelectedKey(), s.animations, state, model) { tapped = it }
        tapped?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("windPoint")) }
        WindKey(shown, s.units.wind, s.units.temp, s.units.precip)
        Text("Tap the globe for the weather there. Drag to turn it, pinch to zoom.", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 12.dp))
        TextButton(onClick = { try { uri.openUri(Wind.PROJECT_URL) } catch (e: Exception) { } }) {
            Text(Wind.CREDIT + (if (wind?.source.isNullOrEmpty()) "" else " (via ${wind!!.source})"), fontSize = 11.sp)
        }
    }
}

private fun io.github.shanebl030glitch.rbsbweather.data.Settings.settingsSelectedKey() = selectedPlace()?.key

@Composable
private fun WindGlobe(fld: WindField?, layer: String, ms: List<Marker>, selected: String?, animate: Boolean, state: UiState, model: AppModel,
                      onTap: (String?) -> Unit) {
    val sel = ms.firstOrNull { it.key == selected }
    var cLat by remember { mutableDoubleStateOf(sel?.lat?.coerceIn(-60.0, 60.0) ?: -20.0) }
    var cLon by remember { mutableDoubleStateOf(sel?.lon ?: 170.0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val lastGesture = remember { longArrayOf(0L) }
    var globe by remember { mutableStateOf<ImageBitmap?>(null) }
    var frames by remember { mutableIntStateOf(0) }
    var streakFrames by remember { mutableIntStateOf(0) }
    var trails by remember { mutableStateOf<ImageBitmap?>(null) }
    // the coloured map for this field and layer, made off the main thread
    var renderer by remember { mutableStateOf<GlobeRenderer?>(null) }
    val ctx = LocalContext.current
    LaunchedEffect(fld, layer) {
        if (fld == null) return@LaunchedEffect
        EarthRender.init(ctx.applicationContext.assets)
        renderer = withContext(Dispatchers.Default) {
            try { GlobeRenderer(WindRender.compose(fld, layer)) } catch (e: Throwable) { null }
        }
    }
    LaunchedEffect(renderer, box) {
        val r = renderer ?: return@LaunchedEffect
        if (box.width <= 0 || box.height <= 0) return@LaunchedEffect
        snapshotFlow { Triple(cLat to cLon, zoom, dragging) }.conflate().collect { (c, z, moving) ->
            val full = (min(box.width, box.height) * 0.86f * z).roundToInt().coerceIn(64, GlobeRenderer.MAX_SIZE)
            val px = if (moving) (full * 0.5f).roundToInt() else full
            val made = withContext(Dispatchers.Default) { try { r.renderReused(px, c.first, c.second).asImageBitmap() } catch (e: Throwable) { null } }
            if (made != null) { globe = made; frames++ }
        }
    }
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        while (System.nanoTime() - lastGesture[0] < 250_000_000L) delay(60)
        dragging = false
    }
    // moving streaks: ~30 frames a second while showing, drawn into a fading trail picture
    LaunchedEffect(fld, animate, box) {
        val f = fld ?: return@LaunchedEffect
        if (!animate || box.width <= 0 || box.height <= 0) { trails = null; return@LaunchedEffect }
        val parts = Particles()
        val oldLat = DoubleArray(parts.n); val oldLon = DoubleArray(parts.n); val alive = BooleanArray(parts.n)
        val bmp = Bitmap.createBitmap(box.width, box.height, Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val fade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN); color = android.graphics.Color.argb((255 * WindRender.TRAIL_FADE).toInt(), 0, 0, 0) }
        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(150, 255, 255, 255); strokeWidth = 1.6f }
        val lines = FloatArray(parts.n * 4)
        var view = Triple(0.0, 0.0, 0f)
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (now - last < 30_000_000L) return@withFrameNanos
                last = now
                val v = Triple(cLat, cLon, zoom)
                if (v != view || dragging) { bmp.eraseColor(0); view = v; if (dragging) return@withFrameNanos }   // never smear across a turning globe
                if (!parts.ready) parts.seedAll(cLat, cLon)
                parts.step(f, cLat, cLon, oldLat, oldLon, alive)
                cv.drawPaint(fade)
                val rad = min(box.width, box.height) * 0.43f * zoom; val cx = box.width / 2f; val cy = box.height / 2f
                var k = 0
                for (i in 0 until parts.n) {
                    if (!alive[i]) continue
                    val (x0, y0, v0) = Globe.project(oldLat[i], oldLon[i], cLat, cLon)
                    val (x1, y1, v1) = Globe.project(parts.lat[i], parts.lon[i], cLat, cLon)
                    if (!v0 || !v1) continue
                    val ax = cx + x0.toFloat() * rad; val ay = cy - y0.toFloat() * rad; val bx = cx + x1.toFloat() * rad; val by = cy - y1.toFloat() * rad
                    if (abs(bx - ax) + abs(by - ay) > rad * 0.2f) continue
                    lines[k++] = ax; lines[k++] = ay; lines[k++] = bx; lines[k++] = by
                }
                if (k > 0) cv.drawLines(lines, 0, k, pen)
                trails = bmp.asImageBitmap(); streakFrames++
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(420.dp).padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF02030A))
        .onSizeChanged { box = it }.testTag("windGlobe").semantics {
            stateDescription = if (globe != null) "drawn" else "drawing"
            globeView = String.format(Locale.US, "%.3f,%.3f,%d,%.3f,%.2f,%d", cLat, cLon, frames, cLon, zoom, streakFrames)
        }) {
        val rad = min(box.width, box.height) * 0.43f * zoom
        val radNow = rememberUpdatedState(rad)
        val viewNow = rememberUpdatedState(Triple(cLat, cLon, rad))
        val fldNow = rememberUpdatedState(fld)
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, pan, z, _ ->
                lastGesture[0] = System.nanoTime(); dragging = true
                if (z != 1f) zoom = (zoom * z).coerceIn(0.6f, 3f)
                if (pan != Offset.Zero) {
                    val k = 57.3 / max(radNow.value, 1f)
                    cLon = io.github.shanebl030glitch.rbsbweather.data.Earth.normLon(cLon - pan.x * k); cLat = (cLat + pan.y * k).coerceIn(-85.0, 85.0)
                }
            }
        }.pointerInput(Unit) {
            detectTapGestures { at ->
                val (la, lo, r) = viewNow.value
                val f = fldNow.value ?: return@detectTapGestures
                val p = Globe.inverse(((at.x - size.width / 2f) / r).toDouble(), ((size.height / 2f - at.y) / r).toDouble(), la, lo)
                onTap(p?.let { (a, o) ->
                    String.format(Locale.US, "%.1f°%s %.1f°%s: ", abs(a), if (a >= 0) "N" else "S", abs(o), if (o >= 0) "E" else "W") +
                        WindRender.pointText(f, a, o, state.settings.units.wind, state.settings.units.temp, state.settings.units.precip)
                })
            }
        }) {
            if (size.minDimension < 8f || rad < 4f) return@Canvas
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(io.github.shanebl030glitch.rbsbweather.ui.scene.safeRadial(0.86f to Color(90, 150, 255, 0), 0.90f to Color(110, 170, 255, 120),
                1f to Color(90, 150, 255, 0), center = c, radius = rad * 1.12f), rad * 1.12f, c)
            globe?.let { g ->
                drawImage(g, IntOffset.Zero, IntSize(g.width, g.height), IntOffset((c.x - rad).roundToInt(), (c.y - rad).roundToInt()),
                    IntSize((2 * rad).roundToInt(), (2 * rad).roundToInt()))
            }
            trails?.let { drawImage(it) }
            for (m in ms) {
                val (x, y, vis) = Globe.project(m.lat, m.lon, cLat, cLon)
                if (!vis) continue
                val p = Offset(c.x + x.toFloat() * rad, c.y - y.toFloat() * rad)
                drawCircle(if (m.key == selected) Color(255, 210, 60) else Color.White, 4f * density / 1.5f, p)
                drawCircle(Color(20, 20, 20), 4f * density / 1.5f, p, style = Stroke(1.2f))
            }
        }
        if (fld != null && globe == null) Text("Drawing the globe…", color = Color(200, 205, 215), modifier = Modifier.align(Alignment.Center))
        Column(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            TextButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(3f) }, modifier = Modifier.testTag("windZoomIn")) { Text("+", color = Color.White, fontSize = 20.sp) }
            TextButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(0.6f) }) { Text("−", color = Color.White, fontSize = 20.sp) }
        }
    }
}

/** The colour key for the layer, labelled in your units. */
@Composable
private fun WindKey(layer: String, w: WindUnit, t: TempUnit, p: PrecipUnit) {
    val stops = Wind.SCALES.getValue(layer)
    val lo = stops.first().first; val hi = stops.last().first
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("windKey")) {
        Canvas(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(3.dp))) {
            drawRect(Brush.horizontalGradient(*stops.map { (x, c) -> ((x - lo) / (hi - lo)).toFloat() to Color(c[0], c[1], c[2]) }.toTypedArray()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val picks = listOf(stops.first(), stops[stops.size / 3], stops[2 * stops.size / 3], stops.last()).map { it.first }
            picks.forEach { x ->
                Text(when (layer) {
                    "temp" -> Units.fmtTemp(x, t)
                    "rain" -> if (p == PrecipUnit.IN) String.format(Locale.US, "%.2f in/h", x / 25.4) else String.format(Locale.US, "%.1f mm/h", x)
                    else -> Units.fmtWind(x * 3.6, w)
                }, fontSize = 11.sp)
            }
        }
    }
}
