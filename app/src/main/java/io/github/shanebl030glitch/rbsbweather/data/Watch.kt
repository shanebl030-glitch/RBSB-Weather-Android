// Checking for weather warnings and earthquakes, shared by the open app and the background check
// (so a warning is never notified twice, whichever of them sees it first). Plain Kotlin.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import java.io.File

/** Something to tell the user about: a warning for a place, or an earthquake. */
sealed interface Notice
data class AlertNotice(val place: Place, val alert: Alert) : Notice
data class QuakeNotice(val quake: Quake, val near: Pair<Place, Double>?) : Notice

/**
 * Locking (0.7.2): the stores are guarded by this object's lock, which is only ever held for quick
 * work in memory and the small store files, never during a download. The screen reads the stores
 * on the main thread (alertsFor, allAlertsFor, shownQuakes), so before 0.7.2, when a check held
 * the lock through a slow or failing download, the screen waited too and Android reported the app
 * as not responding (caught by the stability audit). Downloads now run outside it, one at a time
 * per feed (alertFetch, quakeFetch), so the open app and the background check never download the
 * same feed twice at once, and a warning is still notified only once.
 */
class Watch(dir: File, private val alertClient: AlertClient, private val quakeClient: QuakeClient) {
    val alertStore = AlertStore(dir).load()
    val quakeStore = QuakeStore(dir).load()
    private val alertFetch = Any()
    private val quakeFetch = Any()

    /** Every alert for a place (official from the last check, plus own limits from its forecast), as shown. */
    @Synchronized
    fun alertsFor(place: Place, s: Settings, fc: Forecast?, now: Double = nowS()): List<Alert> {
        val all = mutableListOf<Alert>()
        if (s.alerts.official) all += alertStore.alerts[place.alertKey] ?: emptyList()
        if (s.alerts.own && fc != null) all += ownAlerts(fc, s.alerts.limits, s.units, now)
        return shownAlerts(all, s.alerts.minLevel, s.alerts.muted, now)
    }

    /** Every alert for a place including muted ones (the Warnings screen lists muted ones too). */
    @Synchronized
    fun allAlertsFor(place: Place, s: Settings, fc: Forecast?, now: Double = nowS()): List<Alert> {
        val all = mutableListOf<Alert>()
        if (s.alerts.official) all += alertStore.alerts[place.alertKey] ?: emptyList()
        if (s.alerts.own && fc != null) all += ownAlerts(fc, s.alerts.limits, s.units, now)
        return shownAlerts(all, s.alerts.minLevel, emptySet(), now)
    }

    fun alertsDue(s: Settings, now: Double = nowS()) = now - synchronized(this) { alertStore.checked } >= s.alerts.interval * 60 - 30

    /**
     * Checks official warnings (when due or forced) and own limits, and returns what should be
     * notified. Each warning is notified once per level and issue, even across restarts.
     */
    fun checkAlerts(s: Settings, forecasts: Map<String, Forecast>, force: Boolean = false, now: Double = nowS()): List<AlertNotice> {
        if (s.places.isEmpty()) return emptyList()
        if (s.alerts.official && (force || alertsDue(s, now))) {
            synchronized(alertFetch) {                             // one download at a time, without the stores' lock
                if (force || alertsDue(s, now)) {                  // the other caller may have just done it
                    val (fresh, errors) = alertClient.fetchAll(s.places)
                    synchronized(this) {
                        alertStore.update(fresh, s.places.map { it.alertKey }, now)
                        alertStore.errors = errors
                        alertStore.checked = now
                    }
                }
            }
        }
        return synchronized(this) { alertNotices(s, forecasts, now) }
    }

    private fun alertNotices(s: Settings, forecasts: Map<String, Forecast>, now: Double): List<AlertNotice> {
        val out = mutableListOf<AlertNotice>()
        if (s.alerts.notify) {
            val sel = s.selectedPlace()
            for (p in s.places) {
                if (s.alerts.selectedOnly && p.key != sel?.key) continue
                val all = mutableListOf<Alert>()
                if (s.alerts.official) all += alertStore.alerts[p.alertKey] ?: emptyList()
                if (s.alerts.own) forecasts[p.key]?.let { all += ownAlerts(it, s.alerts.limits, s.units, now) }
                for (a in alertsToNotify(all, alertStore.notified, s.alerts.minLevel, s.alerts.muted)) {
                    alertStore.notified[a.id] = a.signature()
                    out += AlertNotice(p, a)
                }
            }
        }
        alertStore.save()
        return out
    }

    fun quakesDue(s: Settings, now: Double = nowS(), minimumMinutes: Int = 0) =
        now - synchronized(this) { quakeStore.checked } >= maxOf(s.quakes.interval, minimumMinutes) * 60 - 30

    /** Checks the quake feeds (when due or forced) and returns new quakes to notify. */
    fun checkQuakes(s: Settings, force: Boolean = false, now: Double = nowS()): List<QuakeNotice> {
        if (!force && !quakesDue(s, now)) return emptyList()
        // the notification rules need the big quakes even when only 4.5+ is shown, so fetch 2.5+ when notifying near
        val minMag = if (s.quakes.notifyNear && s.quakes.nearMag < 4.5) 2.5 else s.quakes.minMag
        synchronized(quakeFetch) {                                 // one download at a time, without the stores' lock
            if (!force && !quakesDue(s, now)) return emptyList()   // the other caller has just done it
            val (fresh, errors) = quakeClient.fetch(s.quakes.period, minMag)
            synchronized(this) {
                quakeStore.update(fresh, errors, now)
                val out = quakesToNotify(quakeStore.quakes, s.places, s.quakes.rules, quakeStore.notified, now).map { (q, near) -> QuakeNotice(q, near) }
                quakeStore.notified.addAll(out.map { it.quake.id })
                quakeStore.save()
                return out
            }
        }
    }

    /** The quakes to show with the current settings. */
    @Synchronized
    fun shownQuakes(s: Settings, now: Double = nowS()): List<Quake> =
        quakesWithin(quakeStore.quakes, Quakes.PERIODS[s.quakes.period] ?: 86400.0, s.quakes.minMag, now)
}
