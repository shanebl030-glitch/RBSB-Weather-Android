// The home-screen widgets: a small one (place, temperature, sky) and a large one that adds the
// next five days. They show the place you last looked at in the app, and update themselves
// about every 30 minutes when the saved weather is older than your refresh setting.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import io.github.shanebl030glitch.rbsbweather.AppGraph
import io.github.shanebl030glitch.rbsbweather.MainActivity
import io.github.shanebl030glitch.rbsbweather.R

abstract class BaseWidget(private val large: Boolean) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.render(context, manager, ids, large)
        // fetch in the background if the saved weather is stale (a receiver has about 10 seconds)
        val pending = goAsync()
        Thread {
            try {
                if (Widgets.refreshIfDue(context)) Widgets.updateAll(context)
            } catch (e: Exception) {
                Log.w("RBSB-Weather", "Widget update failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }.apply { name = "rbsb-widget"; isDaemon = true }.start()
    }
}

class SmallWeatherWidget : BaseWidget(false)
class LargeWeatherWidget : BaseWidget(true)

object Widgets {
    private val dayIds = listOf(
        Triple(R.id.day0_name, R.id.day0_sym, R.id.day0_temp), Triple(R.id.day1_name, R.id.day1_sym, R.id.day1_temp),
        Triple(R.id.day2_name, R.id.day2_sym, R.id.day2_temp), Triple(R.id.day3_name, R.id.day3_sym, R.id.day3_temp),
        Triple(R.id.day4_name, R.id.day4_sym, R.id.day4_temp))

    /** Redraw every widget from the saved forecast (no network). Safe to call from any thread. */
    fun updateAll(context: Context) {
        try {
            val m = AppWidgetManager.getInstance(context) ?: return
            render(context, m, m.getAppWidgetIds(ComponentName(context, SmallWeatherWidget::class.java)), false)
            render(context, m, m.getAppWidgetIds(ComponentName(context, LargeWeatherWidget::class.java)), true)
        } catch (e: Exception) {
            Log.w("RBSB-Weather", "Couldn't update widgets: ${e.javaClass.simpleName}")
        }
    }

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

    fun render(context: Context, manager: AppWidgetManager, ids: IntArray, large: Boolean) {
        if (ids.isEmpty()) return
        val g = AppGraph.get(context)
        val s = g.settings()
        val data = widgetData(s, s.selectedPlace()?.let { g.repo.cached(it) })
        val views = RemoteViews(context.packageName, if (large) R.layout.widget_large else R.layout.widget_small)
        views.setInt(R.id.widget_root, "setBackgroundResource", if (data.isDay) R.drawable.widget_bg_day else R.drawable.widget_bg_night)
        views.setTextViewText(R.id.w_place, data.place)
        views.setTextViewText(R.id.w_temp, data.temp)
        views.setTextViewText(R.id.w_symbol, data.symbol)
        views.setTextViewText(R.id.w_condition, data.condition)
        views.setTextViewText(R.id.w_hilo, data.hiLo)
        views.setTextViewText(R.id.w_updated, data.updated)
        if (large) {
            dayIds.forEachIndexed { i, (n, sym, t) ->
                val d = data.days.getOrNull(i)
                views.setTextViewText(n, d?.name ?: "")
                views.setTextViewText(sym, d?.symbol ?: "")
                views.setTextViewText(t, d?.temps ?: "")
            }
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.widget_root, open)
        manager.updateAppWidget(ids, views)
    }
}
