// Finds the phone's position for 📍 My location (0.7), using Android's own location service (no
// Google Play services needed), and names it with Android's geocoder (suburb or town, region,
// country). Nothing leaves the phone except the rounded coordinates, which go to the weather
// service the same way a saved place's do. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import io.github.shanebl030glitch.rbsbweather.data.Fix
import io.github.shanebl030glitch.rbsbweather.data.Naming
import io.github.shanebl030glitch.rbsbweather.data.PlaceNamer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Where the phone is: a fix, or null if it can't tell right now. */
fun interface LocationSource { suspend fun current(): Fix? }

object LocationFinder {
    const val TIMEOUT_MS = 20_000L

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasPrecise(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** "Allow all the time": needed for the widget to follow you while the app is closed. */
    fun hasBackground(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Is the phone's Location switch on? */
    fun locationOn(ctx: Context): Boolean = try {
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else true
    } catch (e: Exception) { false }

    private fun Location.toFix() = Fix(latitude, longitude, if (hasAccuracy()) accuracy else 0f, time, provider ?: "")

    /**
     * With the app open, a newest fix older than this is refreshed from GPS too (Android's fused or
     * network position can be a cached one from before you moved). In the background check (every
     * 15 minutes or so), only after an hour, so GPS isn't woken four times an hour (battery audit).
     */
    const val STALE_MS = 60_000L
    const val STALE_BACKGROUND_MS = 60 * 60_000L

    // Every caller has checked the permission first (androidSource), and a SecurityException (it
    // was taken away a moment ago) is caught and treated as "no position".
    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun fresh(lm: LocationManager, provider: String): Fix? {
        if (Build.VERSION.SDK_INT < 30) return freshAndroid10(lm, provider)
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<Fix?> { cont ->
                val cancel = CancellationSignal()
                val ex = Executors.newSingleThreadExecutor()
                cont.invokeOnCancellation { cancel.cancel(); ex.shutdown() }
                try {
                    lm.getCurrentLocation(provider, cancel, ex) { loc -> ex.shutdown(); if (cont.isActive) cont.resume(loc?.toFix()) }
                } catch (e: Exception) { ex.shutdown(); if (cont.isActive) cont.resume(null) }       // SecurityException, provider gone
            }
        }
    }

    /**
     * One update by listening: used for GPS, and for everything on Android 10, which has no
     * "current location" call. Ask for one update and stop listening as soon as
     * it comes, or after 20 s. (Found by the stability audit: without this, Android 10 only had a
     * position if another app had asked recently.)
     */
    @android.annotation.SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun freshAndroid10(lm: LocationManager, provider: String): Fix? = withTimeoutOrNull(TIMEOUT_MS) {
        suspendCancellableCoroutine<Fix?> { cont ->
            // all four methods written out: on Android 10 they have no defaults
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: Location) {
                    try { lm.removeUpdates(this) } catch (e: Exception) { }
                    if (cont.isActive) cont.resume(loc.toFix())
                }
                @Deprecated("Android 10") override fun onStatusChanged(p: String?, status: Int, extras: android.os.Bundle?) { }
                override fun onProviderEnabled(p: String) { }
                override fun onProviderDisabled(p: String) {
                    try { lm.removeUpdates(this) } catch (e: Exception) { }
                    if (cont.isActive) cont.resume(null)
                }
            }
            cont.invokeOnCancellation { try { lm.removeUpdates(listener) } catch (e: Exception) { } }
            try {
                lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
            } catch (e: Exception) { if (cont.isActive) cont.resume(null) }     // SecurityException, provider gone
        }
    }

    /** The newest of some fixes; within a minute of each other, the more accurate one. */
    fun best(fixes: List<Fix>): Fix? = fixes.maxWithOrNull(compareBy<Fix> { it.timeMs / 60_000L }.thenBy { -(if (it.accuracyM > 0f) it.accuracyM else 1e6f) })

    /**
     * Android's location service: a fresh position (fused or network, up to 20 s), compared with the
     * last position each provider already has; if the newest is older than `staleMs`, GPS is
     * asked as well. Logs which provider answered (no coordinates).
     */
    @android.annotation.SuppressLint("MissingPermission")     // checked just below; SecurityException caught
    fun androidSource(ctx: Context, staleMs: Long = STALE_MS) = LocationSource {
        val app = ctx.applicationContext
        if (!hasPermission(app)) return@LocationSource null
        val lm = app.getSystemService(LocationManager::class.java) ?: return@LocationSource null
        val enabled = try { lm.getProviders(true) } catch (e: Exception) { emptyList() }
        val quick = listOfNotNull(if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { it in enabled }
        val got = mutableListOf<Fix>()
        quick?.let { fresh(lm, it) }?.let { got += it }
        got += enabled.mapNotNull { p -> try { lm.getLastKnownLocation(p)?.toFix() } catch (e: Exception) { null } }
        var pick = best(got)
        val now = System.currentTimeMillis()
        if ((pick == null || now - pick.timeMs > staleMs) && LocationManager.GPS_PROVIDER in enabled && hasPrecise(app)) {
            // GPS: one update by listening (getCurrentLocation for GPS gave nothing after the first fix on Android 12 in the audit)
            freshAndroid10(lm, LocationManager.GPS_PROVIDER)?.let { got += it; pick = best(got) }
        }
        android.util.Log.i("RBSB-Weather", "My location: ${got.size} fixes from ${enabled.joinToString(",")}; using " +
            (pick?.let { "${it.provider}, ${(now - it.timeMs) / 1000} s old, ±${it.accuracyM.toInt()} m" } ?: "none"))
        pick
    }

    /** OpenStreetMap, remembered by spot for the whole app, so it's asked rarely. */
    private val osm by lazy { io.github.shanebl030glitch.rbsbweather.data.CachedNamer(io.github.shanebl030glitch.rbsbweather.data.OsmNamer()) }

    /** Android's geocoder; OpenStreetMap when the phone can't name the suburb. */
    fun androidNamer(ctx: Context): PlaceNamer {
        val phone = phoneNamer(ctx)
        return PlaceNamer { lat, lon ->
            val p = phone.name(lat, lon)
            val used = io.github.shanebl030glitch.rbsbweather.data.bestNaming(p) { osm.name(lat, lon) }
            android.util.Log.i("RBSB-Weather", "My location name: " + when {
                used == null -> "none found"
                used === p -> "from the phone (level ${p.level})"
                else -> "from OpenStreetMap (level ${used.level}; phone gave ${p?.level ?: "none"})"
            })
            used
        }
    }

    /** The phone's own geocoder (suburb or town, region, country), waiting at most 8 s. Null when it's missing or can't name the spot. */
    fun phoneNamer(ctx: Context) = PlaceNamer { lat, lon ->
        if (!Geocoder.isPresent()) return@PlaceNamer null
        try {
            val g = Geocoder(ctx.applicationContext, Locale.getDefault())
            val a: android.location.Address? = if (Build.VERSION.SDK_INT >= 33) {
                val latch = java.util.concurrent.CountDownLatch(1)
                val got = java.util.concurrent.atomic.AtomicReference<android.location.Address?>(null)
                g.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(list: MutableList<android.location.Address>) { got.set(list.firstOrNull()); latch.countDown() }
                    override fun onError(msg: String?) { latch.countDown() }
                })
                latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
                got.get()
            } else {
                @Suppress("DEPRECATION")
                g.getFromLocation(lat, lon, 1)?.firstOrNull()
            }
            a ?: return@PlaceNamer null
            // the suburb only when it's a real local area, not an administrative unit or the city's name again
            val sub = a.subLocality?.trim()?.takeIf { it.isNotEmpty() && !io.github.shanebl030glitch.rbsbweather.data.OsmNamer.administrative(it) && !it.equals(a.locality?.trim(), ignoreCase = true) }
            val levels = listOf(sub to Naming.SUBURB, a.locality to Naming.TOWN, a.subAdminArea to Naming.REGION, a.adminArea to Naming.REGION)
            val (name, level) = levels.firstOrNull { !it.first.isNullOrBlank() }?.let { it.first!!.trim() to it.second } ?: return@PlaceNamer null
            val region = listOf(a.locality, a.subAdminArea, a.adminArea).firstOrNull { !it.isNullOrBlank() && it.trim() != name }?.trim() ?: ""
            Naming(name, region, a.countryName?.trim() ?: "", level)
        } catch (e: Exception) { null }
    }
}
