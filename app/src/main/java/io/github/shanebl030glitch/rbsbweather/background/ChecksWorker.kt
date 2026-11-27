// The background check for weather warnings and earthquakes, so notifications arrive while the
// app is closed (the desktop app does this while it runs in the tray). Android runs it about
// every 15 minutes at most often, when there's a network, and may delay it to save battery.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.shanebl030glitch.rbsbweather.AppGraph
import io.github.shanebl030glitch.rbsbweather.data.Notice
import io.github.shanebl030glitch.rbsbweather.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ChecksWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val g = AppGraph.get(applicationContext)
            val s0 = g.settings()
            if (s0.updateCheck) checkForUpdate(applicationContext, g)              // once a day (0.6)
            if (s0.followLocation) followHere(applicationContext, g)                // 📍 My location (0.7)
            val s = g.settings()
            if (s.places.isEmpty()) return@withContext Result.success()
            val forecasts = s.places.mapNotNull { p -> g.repo.cached(p)?.let { p.key to it } }.toMap()
            val notices = mutableListOf<Notice>()
            if (s.alerts.official || s.alerts.own) notices += g.watch.checkAlerts(s, forecasts)
            if (s.quakes.notifyNear || s.quakes.notifyMajor) notices += g.watch.checkQuakes(s)
            Notifier.post(applicationContext, notices, s.alerts.redAlarm)
            Widgets.updateAll(applicationContext)                     // the warning line on the widget
            Result.success()
        } catch (e: Exception) {
            Log.w("RBSB-Weather", "Background check failed: ${e.javaClass.simpleName}")
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "rbsb-checks"

        /** If a check is due, ask GitHub; tell the user once about each newer version they haven't skipped. */
        fun checkForUpdate(ctx: Context, g: AppGraph): String? {
            if (!g.updateStore.due()) return null
            g.updateStore.markAttempt()
            return try {
                val info = g.updateClient.latest()
                g.updateStore.markOk(info.version)
                if (io.github.shanebl030glitch.rbsbweather.data.Updates.isNewer(info) && info.version != g.updateStore.skipped
                    && info.version != g.updateStore.notified) {
                    if (Notifier.postUpdate(ctx, info.version)) g.updateStore.notified = info.version
                }
                info.version
            } catch (e: Exception) { null }
        }

        /**
         * Move 📍 My location if the phone has moved more than a kilometre since last time. Needs
         * "Allow all the time" location access while the app is closed; without it this does nothing
         * (My location then updates whenever you open the app). Returns the new place, if it moved.
         */
        suspend fun followHere(ctx: Context, g: AppGraph,
                               source: LocationSource = LocationFinder.androidSource(ctx, LocationFinder.STALE_BACKGROUND_MS),
                               namer: io.github.shanebl030glitch.rbsbweather.data.PlaceNamer = LocationFinder.androidNamer(ctx),
                               allowed: Boolean = LocationFinder.hasPermission(ctx) && LocationFinder.hasBackground(ctx),
                               fetch: (io.github.shanebl030glitch.rbsbweather.data.Place) -> Unit = { g.repo.fetch(it) }): io.github.shanebl030glitch.rbsbweather.data.Place? {
            if (!allowed) return null
            val s = g.settings()
            if (!s.followLocation) return null
            val fix = try { source.current() } catch (e: Exception) { null } ?: return null
            val next = io.github.shanebl030glitch.rbsbweather.data.Here.next(s.here(), fix, System.currentTimeMillis(), namer) ?: return null
            val live = g.onHere
            if (live != null) {
                // the app is open: it moves the place and gets the weather itself (on its own thread)
                live(next)
                return next
            }
            g.store.save(g.settings().withHere(next).toJson().toString())
            try { fetch(next) } catch (e: Exception) { }     // saved to the cache for the widget
            Widgets.updateAll(ctx)                                  // and the status-bar temperature
            return next
        }

        /** Start (or keep) the background check. Safe to call often. */
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ChecksWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            try {
                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
            } catch (e: Exception) {
                Log.w("RBSB-Weather", "Couldn't schedule background checks: ${e.javaClass.simpleName}")
            }
        }
    }
}
