// The phone version of the desktop's tray temperature: an optional, silent notification that keeps
// the place's temperature in the status bar (drawn as the small icon, like the tray icon) with the
// conditions beside it. Updated whenever the weather or the widgets update. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.shanebl030glitch.rbsbweather.AppGraph
import io.github.shanebl030glitch.rbsbweather.MainActivity
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.TempUnit
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import kotlin.math.roundToInt

object TempNotification {
    const val CHANNEL = "temperature"
    const val ID = 4242

    /** The number shown in the status bar: "17", "-3" (no unit: there's no room, as in the tray). */
    fun iconText(tempC: Double?, unit: TempUnit): String {
        val t = tempC ?: return "–"
        return (Units.temp(t, unit) ?: t).roundToInt().toString()
    }

    /** "17°C · Partly cloudy" and "Auckland · H 19° L 11° · updated 14:05". */
    fun texts(s: Settings, fc: Forecast?, placeName: String): Pair<String, String> {
        if (fc == null) return "$placeName: no weather yet" to "Open RBSB-Weather to get the weather"
        val c = fc.current
        val title = Units.fmtTemp(c.temperature, s.units.temp) + " · " + WeatherCodes.describe(c.weatherCode)
        val d = fc.daily.firstOrNull()
        val hl = if (d != null) " · H ${Units.fmtTemp(d.tempMax, s.units.temp, withUnit = false)} L ${Units.fmtTemp(d.tempMin, s.units.temp, withUnit = false)}" else ""
        return title to placeName + hl
    }

    /** White digits on a transparent square, as Android wants for a status-bar icon. */
    fun icon(text: String, px: Int = 96): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            textSize = px * (if (text.length <= 2) 0.78f else 0.58f)
        }
        val y = px / 2f - (p.descent() + p.ascent()) / 2f
        Canvas(b).drawText(text, px / 2f, y, p)
        return b
    }

    fun update(ctx: Context) {
        val app = ctx.applicationContext
        val nm = NotificationManagerCompat.from(app)
        try {
            val g = AppGraph.get(app)
            val s = g.settings()
            val place = s.selectedPlace()
            if (!s.tempNotification || place == null || !Notifier.allowed(app)) { nm.cancel(ID); return }
            if (Build.VERSION.SDK_INT >= 26) app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Temperature in the status bar", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Keeps the temperature of the place on screen in the status bar"; setShowBadge(false)
                })
            val fc = g.repo.cached(place)
            val (title, text) = texts(s, fc, place.name)
            val open = PendingIntent.getActivity(app, ID, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(IconCompat.createWithBitmap(icon(iconText(fc?.current?.temperature, s.units.temp))))
                .setContentTitle(title).setContentText(text)
                .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS).setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(open).build()
            nm.notify(ID, n)
        } catch (e: SecurityException) { } catch (e: Exception) {
            android.util.Log.w("RBSB-Weather", "Temperature notification: ${e.javaClass.simpleName}")
        }
    }
}
