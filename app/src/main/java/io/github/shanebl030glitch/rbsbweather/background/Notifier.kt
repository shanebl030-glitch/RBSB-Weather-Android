// Phone notifications for weather warnings and earthquakes (the desktop's desktop notifications).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.shanebl030glitch.rbsbweather.MainActivity
import io.github.shanebl030glitch.rbsbweather.R
import io.github.shanebl030glitch.rbsbweather.data.AlertNotice
import io.github.shanebl030glitch.rbsbweather.data.Notice
import io.github.shanebl030glitch.rbsbweather.data.QuakeNotice
import io.github.shanebl030glitch.rbsbweather.data.quakeNotice

object Notifier {
    const val CH_WARNINGS = "warnings"
    const val CH_RED = "warnings_red"
    const val CH_QUAKES = "quakes"
    const val CH_UPDATES = "updates"
    const val EXTRA_OPEN = "open"            // "warnings" | "quakes"
    const val EXTRA_PLACE = "place"

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_WARNINGS, "Weather warnings", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Official warnings for your places, and your own limits"
        })
        nm.createNotificationChannel(NotificationChannel(CH_RED, "Red warnings (alarm sound)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Red warnings when \"Alarm sound for Red warnings\" is on"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
        })
        nm.createNotificationChannel(NotificationChannel(CH_QUAKES, "Earthquakes", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Quakes near your places, and major quakes anywhere"
        })
        nm.createNotificationChannel(NotificationChannel(CH_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a newer RBSB-Weather for Android is on GitHub"
        })
    }

    fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(ctx: Context, what: String, place: String?, code: Int): PendingIntent =
        PendingIntent.getActivity(ctx, code, Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN, what).putExtra(EXTRA_PLACE, place),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** A newer version is on GitHub: tap to see what's new and install it. */
    fun postUpdate(ctx: Context, version: String): Boolean {
        if (!allowed(ctx)) return false
        channels(ctx)
        return try {
            NotificationManagerCompat.from(ctx).notify(4343, NotificationCompat.Builder(ctx, CH_UPDATES)
                .setSmallIcon(R.drawable.ic_stat_weather)
                .setContentTitle("RBSB-Weather for Android $version is available")
                .setContentText("Tap to see what's new and get it.")
                .setContentIntent(open(ctx, "updates", null, 4343)).setAutoCancel(true).build())
            true
        } catch (e: SecurityException) { false }
    }

    /** Post notices. Returns how many were shown (0 when notifications aren't allowed). */
    fun post(ctx: Context, notices: List<Notice>, redAlarm: Boolean): Int {
        if (notices.isEmpty() || !allowed(ctx)) return 0
        channels(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        var n = 0
        for (x in notices) {
            val (id, b) = when (x) {
                is AlertNotice -> {
                    val a = x.alert
                    val title = "${a.badge}: ${a.title()} · ${x.place.name}"
                    val text = (a.headline.ifEmpty { a.description }).take(400) +
                        if (a.official) "\n${a.source}" else "\nNot official: your own limit."
                    val ch = if (redAlarm && a.level == "red") CH_RED else CH_WARNINGS
                    (x.place.key + a.id).hashCode() to NotificationCompat.Builder(ctx, ch).setContentTitle(title).setContentText(text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setContentIntent(open(ctx, "warnings", x.place.key, (x.place.key + a.id).hashCode()))
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                }
                is QuakeNotice -> {
                    val (title, text) = quakeNotice(x.quake, x.near)
                    x.quake.id.hashCode() to NotificationCompat.Builder(ctx, CH_QUAKES).setContentTitle(title).setContentText(text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setContentIntent(open(ctx, "quakes", x.quake.id, x.quake.id.hashCode()))
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                }
            }
            try {
                @Suppress("MissingPermission")
                nm.notify(id, b.setSmallIcon(R.drawable.ic_stat_weather).setAutoCancel(true).build())
                n++
            } catch (e: SecurityException) { }
        }
        return n
    }
}
