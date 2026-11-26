// The home-screen widgets, as close to the desktop widget as Android allows: the animated sky
// with the mini forecast (looping frames, see WidgetFrames), the place with its provider, "↻ time"
// top right (amber when old), the temperature, conditions and high/low. Resize it like the
// desktop's Size menu (small shows 1–2 days, medium 3–5, large up to 7). Tap to open the app.
// They update when the app does, and by themselves about every 30 minutes when the weather is
// older than the refresh setting. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import io.github.shanebl030glitch.rbsbweather.AppGraph
import io.github.shanebl030glitch.rbsbweather.MainActivity
import io.github.shanebl030glitch.rbsbweather.R
import java.util.concurrent.Executors

abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Widgets.executor.execute { Widgets.updateThenRefresh(context, manager, ids, finish = { pending.finish() }) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle?) {
        Widgets.executor.execute { try { Widgets.renderIds(context, manager, intArrayOf(id)) } catch (e: Throwable) { } }
    }
}

class SmallWeatherWidget : BaseWidget()
class LargeWeatherWidget : BaseWidget()

object Widgets {
    /**
     * One thread draws widgets, so frames never pile up. Everything the app draws as text into a
     * picture (the widget frames, the status-bar temperature icon) is drawn on this thread only:
     * Android's text drawing crashed (SIGSEGV in SkGlyph::mask) when two threads did it at once,
     * which closed the app when its widget was tapped (Shane's phone, 26 Nov 2026).
     */
    val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "rbsb-widgets").apply { isDaemon = true } }

    /**
     * A widget update from Android (on the widget thread). Android gives it about 10 seconds (60 in
     * the background) before calling the app "not responding", so the picture is drawn and the
     * update reported done ([finish]) first; the weather download, which on a slow connection can
     * take a minute per provider, comes after (0.7.2; before, it came first).
     */
    fun updateThenRefresh(context: Context, manager: AppWidgetManager, ids: IntArray, finish: () -> Unit,
                          refresh: (Context) -> Boolean = ::refreshIfDue) {
        try {
            renderIds(context, manager, ids)
        } catch (e: Throwable) {
            Log.w("RBSB-Weather", "Widget update failed: ${e.javaClass.simpleName}")
        } finally {
            finish()
        }
        try {
            if (refresh(context)) renderIds(context, manager, ids)
        } catch (e: Throwable) {
            Log.w("RBSB-Weather", "Widget weather refresh failed: ${e.javaClass.simpleName}")
        }
    }

    /** Requests that arrive while one is waiting are merged into it (see [updateAll]). */
    private val queued = java.util.concurrent.atomic.AtomicBoolean(false)
    /** How long a request waits for others to join it. */
    const val MERGE_MS = 1_200L
    /** What each widget last showed; an update that would show the same again isn't sent. */
    private val shown = java.util.concurrent.ConcurrentHashMap<Int, String>()
    /** Widget updates actually sent to the launcher, and skipped as unchanged (for tests and the log). */
    val sent = java.util.concurrent.atomic.AtomicInteger(0)
    val skipped = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * Redraw every widget from the saved forecast (no network), if what it shows has changed. Safe
     * to call from any thread. Requests within [MERGE_MS] of each other become one redraw.
     *
     * Every redraw hands the launcher a new set of animation frames, which restarts the widget's
     * loop: a visible jump. 0.7.0 asked for redraws far more often (each place's forecast, My
     * location's checks and name, the app going to the background), several within seconds, and
     * Shane saw the widget flicker (26 Nov 2026). Now only real changes reach the widget.
     */
    /** Redraw the status-bar temperature on the widget thread (never on the caller's thread). */
    fun updateTempNotification(context: Context) {
        val app = context.applicationContext
        executor.execute { try { io.github.shanebl030glitch.rbsbweather.background.TempNotification.update(app) } catch (e: Throwable) { } }
    }

    fun updateAll(context: Context) {
        val app = context.applicationContext
        if (!queued.compareAndSet(false, true)) return               // one is already waiting: it will include this
        executor.execute {
            try {
                Thread.sleep(MERGE_MS)
                queued.set(false)
                io.github.shanebl030glitch.rbsbweather.background.TempNotification.update(app)   // the status-bar temperature too
                val m = AppWidgetManager.getInstance(app) ?: return@execute
                renderIds(app, m, allIds(app, m), onlyIfChanged = true)
            } catch (e: Throwable) {
                queued.set(false)
                Log.w("RBSB-Weather", "Couldn't update widgets: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * What a widget will show, as text: the place, its weather and when it was fetched, warnings, the
     * widget's size and settings, and the 10-minute slot of the day (the Sun and Moon move on).
     */
    fun signature(data: WidgetData, fc: io.github.shanebl030glitch.rbsbweather.data.Forecast?, s: io.github.shanebl030glitch.rbsbweather.data.Settings,
                  wDp: Int, hDp: Int, nowMs: Long = System.currentTimeMillis()): String =
        listOf(data, fc?.fetchedAt, fc?.place?.latitude, fc?.place?.longitude, s.animations, s.units, wDp, hDp, nowMs / 600_000L).joinToString("|")

    fun allIds(context: Context, m: AppWidgetManager): IntArray =
        m.getAppWidgetIds(ComponentName(context, SmallWeatherWidget::class.java)) +
            m.getAppWidgetIds(ComponentName(context, LargeWeatherWidget::class.java))

    /** Get fresh weather for the widget's place when the saved copy is older than the refresh setting. */
    fun refreshIfDue(context: Context): Boolean {
        val g = AppGraph.get(context)
        val s = g.settings()
        val place = s.widgetPlace() ?: return false
        val fc = g.repo.cached(place)
        val maxAge = (if (s.refreshMinutes == 0) 60 else s.refreshMinutes) * 60_000L
        if (fc != null && System.currentTimeMillis() - fc.fetchedAt < maxAge) return false
        g.repo.fetch(place)
        return true
    }

    /** The widget's size in dp (portrait: the minimum width and maximum height Android reports). */
    fun sizeDp(m: AppWidgetManager, id: Int): Pair<Int, Int> {
        val o = try { m.getAppWidgetOptions(id) } catch (e: Exception) { null }
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        return (if (w > 40) w else 250) to (if (h > 40) h else 120)
    }

    /** The widget's views: the frames in the flipper and the desktop widget's text over them. */
    fun buildViews(context: Context, data: WidgetData, frames: List<android.graphics.Bitmap>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_scene)
        views.removeAllViews(R.id.w_frames)
        for (bmp in frames) {
            val f = RemoteViews(context.packageName, R.layout.widget_frame)
            f.setImageViewBitmap(R.id.frame_img, bmp)
            views.addView(R.id.w_frames, f)
        }
        // the flip interval and auto start are set in widget_scene.xml (not every launcher allows changing them)
        views.setTextViewText(R.id.w_place, data.place)
        views.setTextViewText(R.id.w_source, data.source)
        views.setViewVisibility(R.id.w_source, if (data.source.isBlank()) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.w_updated, data.updated)
        views.setViewVisibility(R.id.w_updated, if (data.updated.isBlank()) View.GONE else View.VISIBLE)
        views.setTextColor(R.id.w_updated, if (data.updatedWarn) 0xFFFFD27A.toInt() else 0xEBFFFFFF.toInt())
        views.setTextViewText(R.id.w_alert, data.alert)
        views.setViewVisibility(R.id.w_alert, if (data.alert.isBlank()) View.GONE else View.VISIBLE)
        io.github.shanebl030glitch.rbsbweather.data.Alerts.COLOURS[data.alertLevel]?.let { (bg, fg) ->
            views.setInt(R.id.w_alert, "setBackgroundColor", bg.toInt())
            views.setTextColor(R.id.w_alert, fg.toInt())
        }
        views.setTextViewText(R.id.w_temp, data.temp)
        views.setTextViewText(R.id.w_condition, data.condition)
        views.setTextViewText(R.id.w_hilo, data.hiLo)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }

    fun renderIds(context: Context, manager: AppWidgetManager, ids: IntArray, onlyIfChanged: Boolean = false) {
        if (ids.isEmpty()) return
        val g = AppGraph.get(context)
        val s = g.settings()
        val fc = s.widgetPlace()?.let { g.repo.cached(it) }
        val alerts = s.widgetPlace()?.let { p -> try { g.watch.alertsFor(p, s, fc) } catch (e: Exception) { emptyList() } } ?: emptyList()
        val data = widgetData(s, fc, alerts = alerts)
        val density = context.resources.displayMetrics.density
        for (id in ids) {
            val (wDp, hDp) = sizeDp(manager, id)
            val sig = signature(data, fc, s, wDp, hDp)
            if (onlyIfChanged && shown[id] == sig) { skipped.incrementAndGet(); continue }
            val frames = try { WidgetFrames.render(context, s, fc, wDp, hDp, density) } catch (e: TextNotDrawnInTime) {
                // the main thread was too busy to draw the words: keep the picture already on the
                // home screen and try again at the next update
                Log.w("RBSB-Weather", "Widget text not drawn in time; keeping the current picture"); continue
            } catch (e: Throwable) {
                Log.w("RBSB-Weather", "Widget sky couldn't be drawn: ${e.javaClass.simpleName}"); emptyList()
            }
            shown[id] = sig
            sent.incrementAndGet()
            val views = buildViews(context, data, frames)
            try {
                manager.updateAppWidget(id, views)
            } catch (e: Exception) {
                // too much picture for this launcher: fall back to a single still frame
                Log.w("RBSB-Weather", "Widget frames refused (${e.javaClass.simpleName}); using a still picture")
                views.removeAllViews(R.id.w_frames)
                frames.firstOrNull()?.let { b ->
                    val f = RemoteViews(context.packageName, R.layout.widget_frame)
                    f.setImageViewBitmap(R.id.frame_img, b)
                    views.addView(R.id.w_frames, f)
                }
                try { manager.updateAppWidget(id, views) } catch (e2: Exception) { }
            }
        }
    }
}
