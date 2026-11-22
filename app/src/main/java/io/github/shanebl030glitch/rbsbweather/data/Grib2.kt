// A small GRIB2 reader, ported from the desktop's grib2.py: just enough for NOAA GFS wind,
// temperature and rain on a latitude/longitude grid. GRIB2 is the World Meteorological
// Organization's binary format for gridded weather data. Supported:
//  * grids: template 3.0 (regular latitude/longitude), any scanning direction except
//    "boustrophedon" rows;
//  * products: templates 4.0/4.1/4.8 (level and forecast time);
//  * packing: 5.0 (simple), 5.2 (complex) and 5.3 (complex with 1st or 2nd order spatial
//    differencing, which GFS uses);
//  * bitmaps (section 6): missing points come back as NaN.
// Checked against the desktop's reader (itself checked against ECMWF's ecCodes) on the same files.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

class Grib2Exception(message: String) : Exception(message)

object Grib2 {
    const val MAX_POINTS = 4_000_000          // a damaged file can't make us allocate gigabytes

    data class Grid(val ni: Int, val nj: Int, val la1: Double, val lo1: Double, val la2: Double, val lo2: Double,
                    val di: Double, val dj: Double, val scan: Int)

    class Message(val discipline: Int, val category: Int, val number: Int, val levelType: Int, val level: Double,
                  val forecastHours: Double?, val referenceTime: IntArray, val grid: Grid, val values: DoubleArray) {
        /** Values as nj rows from north to south, each west to east (row-major). */
        fun rowsNorthToSouth(): DoubleArray {
            val g = grid
            if (g.scan and 0x30 != 0) throw Grib2Exception("Unsupported scanning mode (columns first or alternating rows).")
            val flipRows = g.scan and 0x40 != 0; val flipCols = g.scan and 0x80 != 0
            if (!flipRows && !flipCols) return values
            val out = DoubleArray(values.size)
            for (j in 0 until g.nj) for (i in 0 until g.ni) {
                val sj = if (flipRows) g.nj - 1 - j else j; val si = if (flipCols) g.ni - 1 - i else i
                out[j * g.ni + i] = values[sj * g.ni + si]
            }
            return out
        }
    }

    fun isGrib2(b: ByteArray) = b.size >= 16 && b[0] == 'G'.code.toByte() && b[1] == 'R'.code.toByte() &&
        b[2] == 'I'.code.toByte() && b[3] == 'B'.code.toByte() && b[7].toInt() == 2

    private fun u(b: ByteArray, off: Int, n: Int): Long {
        if (off < 0 || off + n > b.size) throw Grib2Exception("A GRIB2 section is shorter than it should be (damaged file).")
        var v = 0L; for (k in 0 until n) v = (v shl 8) or (b[off + k].toLong() and 0xFF); return v
    }
    private fun ub(b: ByteArray, off: Int) = u(b, off, 1).toInt()
    /** GRIB2 signed integers are sign-magnitude (top bit = sign), not two's complement. */
    fun s(b: ByteArray, off: Int, n: Int): Long {
        val v = u(b, off, n); val top = 1L shl (8 * n - 1)
        return if (v and top != 0L) -(v and (top - 1)) else v
    }
    private fun f32(b: ByteArray, off: Int) = java.lang.Float.intBitsToFloat(u(b, off, 4).toInt()).toDouble()

    /** `count` unsigned integers of `width` bits from bit `bitpos`. */
    fun unpack(data: ByteArray, bitpos: Long, count: Int, width: Int, from: Int = 0): LongArray {
        if (count <= 0) return LongArray(0)
        if (width == 0) return LongArray(count)
        if (width > 63) throw Grib2Exception("Packed values wider than 63 bits.")
        val endBit = bitpos + count.toLong() * width
        if (from + (endBit + 7) / 8 > data.size) throw Grib2Exception("The data section is shorter than its description says.")
        val out = LongArray(count)
        var pos = bitpos
        for (k in 0 until count) {
            var v = 0L; var need = width
            while (need > 0) {
                val byte = data[from + (pos ushr 3).toInt()].toInt() and 0xFF
                val bitInByte = (pos and 7).toInt()
                val take = minOf(need, 8 - bitInByte)
                val bits = (byte ushr (8 - bitInByte - take)) and ((1 shl take) - 1)
                v = (v shl take) or bits.toLong()
                need -= take; pos += take
            }
            out[k] = v
        }
        return out
    }

    private fun padded(bits: Long) = (bits + 7) / 8 * 8

    /** Every GRIB2 message in `buf`. */
    fun messages(buf: ByteArray): List<Message> {
        val out = ArrayList<Message>()
        var pos = find(buf, 0)
        while (pos != -1 && pos + 16 <= buf.size) {
            val edition = buf[pos + 7].toInt() and 0xFF
            if (edition != 2) throw Grib2Exception("Not a GRIB2 message (edition $edition).")
            val total = u(buf, pos + 8, 8)
            if (total < 20 || pos + total > buf.size) throw Grib2Exception("The GRIB2 file is cut short.")
            val end = (pos + total).toInt()
            if (buf[end - 4] != '7'.code.toByte() || buf[end - 3] != '7'.code.toByte() || buf[end - 2] != '7'.code.toByte() || buf[end - 1] != '7'.code.toByte())
                throw Grib2Exception("A GRIB2 message doesn't end with 7777 (damaged file).")
            out += readMessage(buf.copyOfRange(pos, end))
            pos = find(buf, end)
        }
        if (out.isEmpty()) throw Grib2Exception("No GRIB2 data in the file.")
        return out
    }

    private fun find(b: ByteArray, from: Int): Int {
        var i = from
        while (i + 4 <= b.size) {
            if (b[i] == 'G'.code.toByte() && b[i + 1] == 'R'.code.toByte() && b[i + 2] == 'I'.code.toByte() && b[i + 3] == 'B'.code.toByte()) return i
            i++
        }
        return -1
    }

    private class Rep(val tmpl: Int, val r: Double, val e: Int, val d: Int, val nbits: Int,
                      val ng: Int = 0, val refW: Int = 0, val bitsW: Int = 0, val refL: Long = 0, val incL: Int = 0,
                      val lastL: Long = 0, val bitsL: Int = 0, val order: Int = 0, val ie: Int = 0)

    private fun readMessage(msg: ByteArray): List<Message> {
        val discipline = msg[6].toInt() and 0xFF
        var off = 16
        var ref = IntArray(6)
        var grid: Grid? = null
        var prod: DoubleArray? = null     // cat, num, levelType, level, hours (NaN = none)
        var rep: Rep? = null
        var bitmap: ByteArray? = null; var bitmapOff = 0
        val results = ArrayList<Message>()
        while (off < msg.size - 4) {
            val length = u(msg, off, 4).toInt()
            val num = ub(msg, off + 4)
            if (length < 5 || off + length > msg.size) throw Grib2Exception("A GRIB2 section has an impossible length (damaged file).")
            val sec = msg.copyOfRange(off, off + length)
            when (num) {
                1 -> ref = intArrayOf(u(sec, 12, 2).toInt(), ub(sec, 14), ub(sec, 15), ub(sec, 16), ub(sec, 17), ub(sec, 18))
                3 -> grid = grid(sec)
                4 -> prod = product(sec)
                5 -> rep = representation(sec)
                6 -> when (val ind = ub(sec, 5)) {
                    0 -> { bitmap = sec; bitmapOff = 6 }
                    255 -> bitmap = null
                    254 -> { }                                    // reuse the previous bitmap
                    else -> throw Grib2Exception("Predefined bitmaps aren't supported ($ind).")
                }
                7 -> {
                    val g = grid; val p = prod; val r = rep
                    if (g == null || p == null || r == null) throw Grib2Exception("A GRIB2 data section came before its descriptions.")
                    val vals = decode(sec, 5, r, g.ni * g.nj, bitmap, bitmapOff)
                    results += Message(discipline, p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3], p[4].takeIf { !it.isNaN() }, ref, g, vals)
                }
            }
            off += length
        }
        return results
    }

    private fun grid(sec: ByteArray): Grid {
        val tmpl = u(sec, 12, 2).toInt()
        if (tmpl != 0) throw Grib2Exception("Only latitude/longitude grids are supported (this is template 3.$tmpl).")
        val ni = u(sec, 30, 4); val nj = u(sec, 34, 4)
        if (ni <= 0 || nj <= 0 || ni * nj > MAX_POINTS) throw Grib2Exception("The grid size is impossible (damaged file).")
        val basic = u(sec, 38, 4); val sub = u(sec, 42, 4)
        val unit = if (basic == 0L || basic == 0xFFFFFFFFL || sub == 0L || sub == 0xFFFFFFFFL) 1e-6 else basic.toDouble() / sub
        return Grid(ni.toInt(), nj.toInt(), s(sec, 46, 4) * unit, s(sec, 50, 4) * unit, s(sec, 55, 4) * unit, s(sec, 59, 4) * unit,
            u(sec, 63, 4) * unit, u(sec, 67, 4) * unit, ub(sec, 71))
    }

    private val HOURS = mapOf(0 to 1.0 / 60, 1 to 1.0, 2 to 24.0, 10 to 3.0, 11 to 6.0, 12 to 12.0, 13 to 1.0 / 3600)

    private fun product(sec: ByteArray): DoubleArray {
        val tmpl = u(sec, 7, 2).toInt()
        val cat = ub(sec, 9).toDouble(); val num = ub(sec, 10).toDouble()
        if (tmpl !in listOf(0, 1, 8)) return doubleArrayOf(cat, num, 255.0, Double.NaN, Double.NaN)
        val unit = ub(sec, 17); val ftime = s(sec, 18, 4)
        val levelType = ub(sec, 22).toDouble()
        val scale = s(sec, 23, 1).toInt(); val value = s(sec, 24, 4)
        val level = if (scale != 0) value / Math.pow(10.0, scale.toDouble()) else value.toDouble()
        val hours = HOURS[unit]?.let { ftime * it } ?: Double.NaN
        return doubleArrayOf(cat, num, levelType, level, hours)
    }

    private fun representation(sec: ByteArray): Rep {
        val tmpl = u(sec, 9, 2).toInt()
        if (tmpl !in listOf(0, 2, 3)) throw Grib2Exception("Unsupported GRIB2 packing (template 5.$tmpl).")
        val r = f32(sec, 11); val e = s(sec, 15, 2).toInt(); val d = s(sec, 17, 2).toInt(); val nbits = ub(sec, 19)
        if (tmpl == 0) return Rep(0, r, e, d, nbits)
        if (ub(sec, 22) != 0) throw Grib2Exception("Missing-value management inside the data isn't supported.")
        val order = if (tmpl == 3) ub(sec, 47) else 0; val ie = if (tmpl == 3) ub(sec, 48) else 0
        if (tmpl == 3 && (order !in listOf(1, 2) || ie == 0)) throw Grib2Exception("Unsupported spatial differencing.")
        return Rep(tmpl, r, e, d, nbits, u(sec, 31, 4).toInt(), ub(sec, 35), ub(sec, 36), u(sec, 37, 4), ub(sec, 41),
            u(sec, 42, 4), ub(sec, 46), order, ie)
    }

    private fun decode(sec: ByteArray, from: Int, r: Rep, npts: Int, bitmap: ByteArray?, bmOff: Int): DoubleArray {
        var present = npts
        var mask: BooleanArray? = null
        if (bitmap != null) {
            val bits = unpack(bitmap, 0, npts, 1, bmOff)
            mask = BooleanArray(npts) { bits[it] != 0L }
            present = mask.count { it }
        }
        val raw = if (r.tmpl == 0) unpack(sec, 0, present, r.nbits, from) else complex(sec, from, r, present)
        val ebin = Math.pow(2.0, r.e.toDouble()); val dec = Math.pow(10.0, -r.d.toDouble())
        if (mask == null) return DoubleArray(npts) { (r.r + raw[it] * ebin) * dec }
        val out = DoubleArray(npts) { Double.NaN }
        var k = 0
        for (i in 0 until npts) if (mask[i]) { out[i] = (r.r + raw[k++] * ebin) * dec }
        return out
    }

    private fun complex(sec: ByteArray, from: Int, r: Rep, n: Int): LongArray {
        var pos = from
        val h = LongArray(maxOf(r.order, 1)); var hmin = 0L
        if (r.tmpl == 3) {
            for (k in 0 until r.order) { h[k] = s(sec, pos, r.ie); pos += r.ie }
            hmin = s(sec, pos, r.ie); pos += r.ie
        }
        val ng = r.ng
        if (ng <= 0 || ng > n) throw Grib2Exception("Impossible number of groups (damaged file).")
        val base = pos
        var bit = 0L
        val refs = unpack(sec, bit, ng, r.nbits, base); bit = padded(bit + ng.toLong() * r.nbits)
        val widths = unpack(sec, bit, ng, r.bitsW, base); bit = padded(bit + ng.toLong() * r.bitsW)
        val lens = unpack(sec, bit, ng, r.bitsL, base); bit = padded(bit + ng.toLong() * r.bitsL)
        for (g in 0 until ng) { widths[g] += r.refW.toLong(); lens[g] = r.refL + lens[g] * r.incL }
        lens[ng - 1] = r.lastL
        if (lens.sum() != n.toLong()) throw Grib2Exception("The group lengths don't add up (damaged file).")
        val out = LongArray(n)
        var k = 0
        for (g in 0 until ng) {
            val len = lens[g].toInt(); val w = widths[g].toInt()
            if (w > 0) { val v = unpack(sec, bit, len, w, base); for (x in 0 until len) out[k + x] = v[x] + refs[g] }
            else for (x in 0 until len) out[k + x] = refs[g]
            bit += len.toLong() * w; k += len
        }
        if (r.tmpl != 3) return out
        // undo the spatial differencing
        val f = LongArray(n)
        if (n == 0) return f
        if (r.order == 1) {
            f[0] = h[0]
            for (i in 1 until n) f[i] = out[i] + hmin + f[i - 1]
        } else {
            f[0] = h[0]; if (n > 1) f[1] = h[1]
            for (i in 2 until n) f[i] = out[i] + hmin + 2 * f[i - 1] - f[i - 2]
        }
        return f
    }
}
