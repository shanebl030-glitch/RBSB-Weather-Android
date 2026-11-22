// High-resolution Moon pictures lit for any phase, as on the desktop (moon_render.py): NASA's
// near-side colour and height maps, lit per pixel with Lommel–Seeliger reflectance (why a full
// moon looks evenly bright) blended with a little Lambert, crater relief along the terminator,
// faint blue earthshine on the night side, the real phase angle and bright-limb direction, and
// turned to match the place's sky. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.scene

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object MoonRender {
    const val MAX_SIZE = 2048
    const val CACHE_MAX_SIZE = 720
    const val CREDIT_DEFAULT = "Moon imagery: NASA's Scientific Visualization Studio, CGI Moon Kit (LRO LROC WAC and LOLA data)"
    @Volatile private var assets: AssetManager? = null
    private val texCache = LinkedHashMap<Int, Pair<IntArray, IntArray>>()
    private val imgCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 40
    }
    private var full: Pair<Bitmap, Bitmap>? = null
    var bump = 0.008498; private set
    var tint = doubleArrayOf(1.0, 1.0, 1.0); private set
    var credit = CREDIT_DEFAULT; private set

    fun init(a: AssetManager) {
        if (assets != null) return
        assets = a
        try {
            val o = JSONObject(a.open("textures/moon_textures.json").bufferedReader().readText())
            bump = o.optDouble("bump", bump)
            o.optJSONArray("tint")?.let { t -> if (t.length() == 3) tint = doubleArrayOf(t.getDouble(0), t.getDouble(1), t.getDouble(2)) }
            credit = o.optString("credit", credit).ifBlank { CREDIT_DEFAULT }
        } catch (e: Exception) { }
    }

    val available: Boolean get() = assets != null

    /** Albedo (ARGB) and height (0..255) pixels at size × size. */
    @Synchronized private fun textures(size: Int): Pair<IntArray, IntArray>? {
        texCache[size]?.let { return it }
        val a = assets ?: return null
        val (alb, hgt) = full ?: try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val x = BitmapFactory.decodeStream(a.open("textures/moon_albedo.jpg"), null, opts)!!
            val y = BitmapFactory.decodeStream(a.open("textures/moon_height.jpg"), null, opts)!!
            (x to y).also { full = it }
        } catch (e: Exception) { return null }
        val sa = Bitmap.createScaledBitmap(alb, size, size, true)
        val sh = Bitmap.createScaledBitmap(hgt, size, size, true)
        val pa = IntArray(size * size); sa.getPixels(pa, 0, size, 0, 0, size, size)
        val ph = IntArray(size * size); sh.getPixels(ph, 0, size, 0, 0, size, size)
        for (i in ph.indices) ph[i] = ph[i] and 0xFF
        if (sa !== alb) sa.recycle()
        if (sh !== hgt) sh.recycle()
        if (size >= 512) { full?.first?.recycle(); full?.second?.recycle(); full = null }   // don't keep 32 MB around
        if (texCache.size > 6) texCache.remove(texCache.keys.first())
        texCache[size] = pa to ph
        return pa to ph
    }

    /** Unit vector to the Sun in picture space (x right, y up, z towards the viewer), north up. */
    fun sunVector(phaseAngle: Double, brightLimb: Double): DoubleArray {
        val i = Math.toRadians(phaseAngle); val chi = Math.toRadians(brightLimb)
        return doubleArrayOf(sin(i) * -sin(chi), sin(i) * cos(chi), cos(i))
    }

    private const val GAMMA = 1 / 1.12

    /**
     * A size × size picture of the Moon (transparent outside the disc), or null without the
     * textures. rotation: degrees clockwise (the parallactic angle); null: north up, or turned
     * over in the southern hemisphere.
     */
    fun render(sizeIn: Int, phaseAngle: Double, brightLimb: Double, southern: Boolean = false, rotation: Double? = null): Bitmap? {
        val size = sizeIn.coerceIn(8, MAX_SIZE)
        val rot = (((rotation ?: if (southern) 180.0 else 0.0) % 360 + 360) % 360).let { Math.round(it).toInt() % 360 }
        val key = "$size/${Math.round(phaseAngle * 10)}/${Math.round(brightLimb) % 360}/$rot"
        synchronized(imgCache) { imgCache[key]?.let { return it } }
        val (alb, hgt) = textures(size) ?: return null
        val s = sunVector(phaseAngle, brightLimb)
        val illum = (1 + cos(Math.toRadians(phaseAngle))) / 2
        val earth = 0.055 * (1 - illum).pow(1.5)
        val exposure = 1.12 + 0.3 * (1 - illum)
        val out = IntArray(size * size)
        val kk = bump * size / 2
        val half = size / 2.0
        for (j in 0 until size) {
            val y = 1 - (j + 0.5) / size * 2
            val yy = y * y
            val lim = 1 + 2.0 / size
            if (yy > lim * lim) continue
            val up = maxOf(j - 1, 0) * size; val dn = minOf(j + 1, size - 1) * size; val row = j * size
            for (i in 0 until size) {
                val x = (i + 0.5) / size * 2 - 1
                val r2 = x * x + yy
                var a = (1 - sqrt(r2)) * half + 0.5
                if (a <= 0) continue
                if (a > 1) a = 1.0
                val z = if (r2 < 1) sqrt(1 - r2) else 0.0
                val gx = (hgt[row + minOf(i + 1, size - 1)] - hgt[row + maxOf(i - 1, 0)]) / 510.0
                val gy = (hgt[up + i] - hgt[dn + i]) / 510.0
                val k = kk * z
                val nx = x - k * gx; val ny = y - k * gy; val nz = z
                val inv = 1 / sqrt(nx * nx + ny * ny + nz * nz + 1e-12)
                val mu0 = (nx * s[0] + ny * s[1] + nz * s[2]) * inv
                val px = alb[row + i]
                val ar = ((px shr 16) and 0xFF) / 255.0; val ag = ((px shr 8) and 0xFF) / 255.0; val ab = (px and 0xFF) / 255.0
                val shade = if (mu0 > 0) {
                    val mu = maxOf(nz * inv, 0.05)
                    minOf((0.88 * (2 * mu0 / (mu0 + mu)) + 0.12 * mu0) * exposure, 1.6)
                } else 0.0
                val rr = minOf(1.0, ar * (shade * tint[0] + earth * 0.85)).pow(GAMMA)
                val gg = minOf(1.0, ag * (shade * tint[1] + earth * 0.92)).pow(GAMMA)
                val bb = minOf(1.0, ab * (shade * tint[2] + earth * 1.05)).pow(GAMMA)
                // premultiplied → ARGB for Bitmap (not premultiplied in IntArray terms)
                out[row + i] = ((a * 255).toInt() shl 24) or ((rr * 255).toInt() shl 16) or ((gg * 255).toInt() shl 8) or (bb * 255).toInt()
            }
        }
        var bmp = Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888)
        if (rot != 0) {
            val turned = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(turned)
            val m = Matrix().apply { postRotate(rot.toFloat(), size / 2f, size / 2f) }
            c.drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            bmp.recycle()
            bmp = turned
        }
        if (size <= CACHE_MAX_SIZE) synchronized(imgCache) { imgCache[key] = bmp }   // big exports aren't kept (16 MB each at 2048)
        return bmp
    }
}
