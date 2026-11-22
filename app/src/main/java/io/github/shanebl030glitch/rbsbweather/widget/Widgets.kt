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
        Widgets.executor.execute {
            try {
                Widgets.renderIds(context, manager, ids)
                if (Widgets.refreshIfDue(context)) Widgets.renderIds(context, manager, ids)
            } catch (e: Throwable) {
                Log.w("RBSB-Weather", "Widget update failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle?) {
        Widgets.executor.execute { try { Widgets.renderIds(context, manager, intArrayOf(id)) } catch (e: Throwable) { } }
    }
}

class SmallWeatherWidget : BaseWidget()
class LargeWeatherWidget : BaseWidget()

object Widgets {
    /** One thread draws widgets, so frames never pile up. */
    val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "rbsb-widgets").apply { isDaemon = true } }

    /** Redraw every widget from the saved forecast (no network). Safe to call from any thread. */
    fun updateAll(context: Context) {
        val app = context.applicationContext
        executor.execute {
            try {
                val m = AppWidgetManager.getInstance(app) ?: return@execute
                renderIds(app, m, allIds(app, m))
            } catch (e: Throwable) {
                Log.w("RBSB-Weather", "Couldn't update widgets: ${e.javaClass.simpleName}")
            }
        }
    }

    fun allIds(context: Context, m: AppWidgetManager): IntArray =
        m.getAppWidgetIds(ComponentName(context, SmallWeatherWidget::class.java)) +
            m.getAppWidgetIds(ComponentName(context, LargeWeatherWidget::class.java))

    /** Get fresh weather for the widget's place when the saved copy is older than the refresh setting. */
    fun refreshIfDue(context: Context): Boolean {
        val g = AppGraph.get(context)
        val s = g.settings()
        val place = s.selectedPlace() ?: return false
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

    fun renderIds(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val g = AppGraph.get(context)
        val s = g.settings()
        val fc = s.selectedPlace()?.let { g.repo.cached(it) }
        val data = widgetData(s, fc)
        val density = context.resources.displayMetrics.density
        for (id in ids) {
            val (wDp, hDp) = sizeDp(manager, id)
            val frames = try { WidgetFrames.render(context, s, fc, wDp, hDp, density) } catch (e: Throwable) {
                Log.w("RBSB-Weather", "Widget sky couldn't be drawn: ${e.javaClass.simpleName}"); emptyList()
            }
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
            views.setTextViewText(R.id.w_temp, data.temp)
            views.setTextViewText(R.id.w_condition, data.condition)
            views.setTextViewText(R.id.w_hilo, data.hiLo)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, open)
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
