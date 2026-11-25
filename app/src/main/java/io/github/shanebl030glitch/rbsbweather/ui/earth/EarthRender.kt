// Drawing the live Earth (the desktop's earth.compose_day_night and globe.GlobeRenderer): the day
// and night maps blended along the real terminator with a soft twilight band, satellite clouds
// laid over (white by day, hiding city lights by night), and the globe drawn by inverse-projecting
// every pixel of the disc onto that same map, with gentle limb darkening.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.earth

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.shanebl030glitch.rbsbweather.data.Earth
import io.github.shanebl030glitch.rbsbweather.data.Globe
import java.io.File
import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object EarthRender {
    /**
     * The maps' size: 2048×1024, or half that when Android gives the app less than 96 MB to work
     * with. The two maps take 16 MB at full size, and an emulator that allowed only 16 MB crashed
     * on the Earth tab (stability audit, Android 10). Real phones allow 192–512 MB.
     */
    val SMALL_HEAP: Boolean = try { Runtime.getRuntime().maxMemory() < 96L * 1024 * 1024 } catch (e: Exception) { false }
    val W = if (SMALL_HEAP) 1024 else 2048
    val H = W / 2
    private var day: IntArray? = null
    private var night: IntArray? = null
    @Volatile private var assets: AssetManager? = null

    fun init(a: AssetManager) { if (assets == null) assets = a }
    val available get() = assets != null

    private fun load(name: String): IntArray {
        val a = assets ?: throw IllegalStateException("no Earth maps")
        val full = a.open("textures/$name").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) }
            ?: throw IllegalStateException("Earth map unreadable: $name")
        val s = Bitmap.createScaledBitmap(full, W, H, true)
        val px = IntArray(W * H); s.getPixels(px, 0, W, 0, 0, W, H)
        if (s !== full) s.recycle(); full.recycle()
        return px
    }

    /** The day map's pixels (W×H), for the Wind tab. */
    fun dayPixels(): IntArray = textures().first

    @Synchronized private fun textures(): Pair<IntArray, IntArray> {
        if (day == null) day = load("earth_day.jpg")
        if (night == null) night = load("earth_night.jpg")
        return day!! to night!!
    }

    /** The cloud map (any 2:1 size) as W×H alpha 0..255, thin haze and sensor noise trimmed. */
    fun cloudAlpha(file: File): ByteArray? {
        val bmp = try { BitmapFactory.decodeFile(file.absolutePath) } catch (e: Exception) { null } ?: return null
        if (!io.github.shanebl030glitch.rbsbweather.data.Clouds.validSize(bmp.width, bmp.height)) { bmp.recycle(); return null }
        val s = Bitmap.createScaledBitmap(bmp, W, H, true)
        val px = IntArray(W * H); s.getPixels(px, 0, W, 0, 0, W, H)
        if (s !== bmp) s.recycle(); bmp.recycle()
        return ByteArray(W * H) { i ->
            val p = px[i]
            val g = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000.0
            ((g - 18.0) * (255.0 / 200.0)).coerceIn(0.0, 255.0).toInt().toByte()
        }
    }

    /** The Earth at time t: night lights where it's dark, daylight where it's day, blended through twilight. */
    fun compose(t: Instant, clouds: ByteArray? = null, maskW: Int = 512): Bitmap {
        val (dayPx, nightPx) = textures()
        val mw = maskW; val mh = maskW / 2
        val (dm, sm) = Earth.masks(t, mw, mh)
        val out = IntArray(W * H)
        for (y in 0 until H) {
            val fy = ((y + 0.5) * mh / H - 0.5).coerceIn(0.0, mh - 1.0)
            val y0 = fy.toInt(); val y1 = min(y0 + 1, mh - 1); val ty = (fy - y0).toFloat()
            for (x in 0 until W) {
                val fx = ((x + 0.5) * mw / W - 0.5).coerceIn(0.0, mw - 1.0)
                val x0 = fx.toInt(); val x1 = min(x0 + 1, mw - 1); val tx = (fx - x0).toFloat()
                fun bil(m: FloatArray) = (m[y0 * mw + x0] * (1 - tx) + m[y0 * mw + x1] * tx) * (1 - ty) + (m[y1 * mw + x0] * (1 - tx) + m[y1 * mw + x1] * tx) * ty
                val dw = bil(dm); val sh = bil(sm)
                val i = y * W + x
                val d = dayPx[i]; val n = nightPx[i]
                var dr = (d shr 16 and 0xFF).toFloat(); var dg = (d shr 8 and 0xFF).toFloat(); var db = (d and 0xFF).toFloat()
                var nr = (n shr 16 and 0xFF).toFloat(); var ng = (n shr 8 and 0xFF).toFloat(); var nb = (n and 0xFF).toFloat()
                if (clouds != null) {
                    val c = (clouds[i].toInt() and 0xFF) / 255f
                    if (c > 0f) {
                        dr += (246 - dr) * c; dg += (248 - dg) * c; db += (252 - db) * c         // sunlit cloud tops
                        val dim = 0.88f * c                                                        // cloud hides city lights
                        nr += (6 - nr) * dim; ng += (8 - ng) * dim; nb += (14 - nb) * dim
                        val glow = 0.16f * c                                                       // faint moonlit cloud
                        nr += (120 - nr) * glow; ng += (130 - ng) * glow; nb += (150 - nb) * glow
                    }
                }
                var r = nr + (dr - nr) * dw; var g = ng + (dg - ng) * dw; var b = nb + (db - nb) * dw
                r += (8 - r) * sh; g += (10 - g) * sh; b += (30 - b) * sh
                out[i] = (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
            }
        }
        return Bitmap.createBitmap(out, W, H, Bitmap.Config.ARGB_8888)
    }
}

/** Renders the globe from a composed Earth map (the same day/night as the flat map). */
class GlobeRenderer(src: Bitmap) {
    companion object { const val MAX_SIZE = 1024 }   // bigger zooms are scaled up on screen: memory stays modest
    private val w = src.width; private val h = src.height
    private val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
    private class Grid(val x: FloatArray, val y: FloatArray, val rho: FloatArray, val sinc: FloatArray, val cosc: FloatArray, val alpha: IntArray, val k: FloatArray)
    private val grids = LinkedHashMap<Int, Grid>()

    @Synchronized private fun grid(size: Int): Grid = grids[size] ?: run {
        val n = size * size
        val g = Grid(FloatArray(n), FloatArray(n), FloatArray(n), FloatArray(n), FloatArray(n), IntArray(n), FloatArray(n))
        for (j in 0 until size) for (i in 0 until size) {
            val idx = j * size + i
            val x = (i + 0.5) / size * 2 - 1; val y = 1 - (j + 0.5) / size * 2
            val r0 = sqrt(x * x + y * y)
            val a = ((1 - r0) * size / 2 + 0.5).coerceIn(0.0, 1.0)
            val rho = min(r0, 1.0)
            val z = sqrt((1 - rho * rho).coerceAtLeast(0.0))
            g.x[idx] = x.toFloat(); g.y[idx] = y.toFloat(); g.rho[idx] = (if (rho < 1e-12) 1.0 else rho).toFloat()
            g.sinc[idx] = rho.toFloat(); g.cosc[idx] = z.toFloat()
            g.alpha[idx] = (a * 255).toInt(); g.k[idx] = ((1 - Globe.LIMB * (1 - z)) * a).toFloat()
        }
        if (grids.size >= 2) grids.remove(grids.keys.first())          // each grid is 28 bytes a pixel: keep two at most
        grids[size] = g; g
    }

    /** A size × size globe (transparent outside the disc) centred on (cLat, cLon), as a new picture. */
    fun render(sizeIn: Int, cLat: Double, cLon: Double): Bitmap {
        val size = sizeIn.coerceIn(16, MAX_SIZE)
        val out = IntArray(size * size)
        fill(out, size, cLat, cLon)
        return Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888)
    }

    // For the screens, which draw a new globe every frame while it turns: one pixel buffer and three
    // pictures used in turn (one on screen, one waiting, one being drawn), so turning the globe makes
    // no garbage. Making a new 4 MB picture each frame caused constant garbage collection, slow
    // frames and an ANR under random use on Android 12 (stability audit).
    private var buf: IntArray? = null
    private val pool = arrayOfNulls<Bitmap>(3)
    private var next = 0

    /** As [render], but into a reused picture: valid until two more calls. One caller at a time. */
    @Synchronized fun renderReused(sizeIn: Int, cLat: Double, cLon: Double): Bitmap {
        val size = sizeIn.coerceIn(16, MAX_SIZE)
        val out = buf?.takeIf { it.size == size * size } ?: IntArray(size * size).also { buf = it }
        fill(out, size, cLat, cLon)
        val b = pool[next]?.takeIf { it.width == size && it.height == size && !it.isRecycled }
            ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { pool[next] = it }
        b.setPixels(out, 0, size, 0, 0, size, size)
        next = (next + 1) % pool.size
        return b
    }

    private fun fill(out: IntArray, size: Int, cLat: Double, cLon: Double) {
        val g = grid(size)
        val p0 = Math.toRadians(cLat); val l0 = Math.toRadians(cLon)
        val sp0 = sin(p0); val cp0 = cos(p0)
        for (i in out.indices) {
            val a = g.alpha[i]
            if (a == 0) { out[i] = 0; continue }
            val x = g.x[i].toDouble(); val y = g.y[i].toDouble(); val rho = g.rho[i].toDouble(); val sc = g.sinc[i].toDouble(); val cc = g.cosc[i].toDouble()
            val lat = asin((cc * sp0 + y * sc * cp0 / rho).coerceIn(-1.0, 1.0))
            val lon = l0 + atan2(x * sc, rho * cc * cp0 - y * sc * sp0)
            var u = (((Math.toDegrees(lon) + 180.0) % 360.0 + 360.0) % 360.0 / 360.0 * w).toInt(); if (u >= w) u = w - 1
            val v = ((90.0 - Math.toDegrees(lat)) / 180.0 * h).toInt().coerceIn(0, h - 1)
            val p = px[v * w + u]; val k = g.k[i]
            // premultiplied by coverage at the rim, as on the desktop
            out[i] = (a shl 24) or (((p shr 16 and 0xFF) * k / (a / 255f).coerceAtLeast(1e-3f)).toInt().coerceIn(0, 255) shl 16) or
                (((p shr 8 and 0xFF) * k / (a / 255f).coerceAtLeast(1e-3f)).toInt().coerceIn(0, 255) shl 8) or
                ((p and 0xFF) * k / (a / 255f).coerceAtLeast(1e-3f)).toInt().coerceIn(0, 255)
        }
    }
}
