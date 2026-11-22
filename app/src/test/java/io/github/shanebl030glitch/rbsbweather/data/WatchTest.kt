// The shared warnings-and-quakes check: notifies each thing once (across restarts), follows the
// settings, and keeps going when a source fails. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WatchTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun watch(a: FakeAlertServer = FakeAlertServer(), q: FakeQuakeServer = FakeQuakeServer()) = Watch(tmp.root, a.client(), q.client())

    @Test fun warningsNotifiedOnceEvenAfterARestart() {
        val s = Settings(places = listOf(AUCKLAND, WELLINGTON), alerts = AlertSettings(own = false))
        val first = watch().checkAlerts(s, emptyMap(), force = true)
        assertEquals(setOf("Orange Heavy Rain Warning", "Strong Wind Watch"), first.map { it.alert.title() }.toSet())
        assertEquals(AUCKLAND, first.first { it.alert.level == "orange" }.place)
        assertEquals(emptyList<AlertNotice>(), watch().checkAlerts(s, emptyMap(), force = true))       // a new Watch = a restart
        // shown on screen for the place, with the min level
        val w = watch()
        assertEquals(listOf("orange"), w.alertsFor(AUCKLAND, s, null).map { it.level })
        assertEquals(emptyList<Alert>(), w.alertsFor(WELLINGTON, s.copy(alerts = s.alerts.copy(minLevel = "orange")), null))
        // muted: off the banner list but still on the Warnings screen
        val id = w.alertsFor(AUCKLAND, s, null)[0].id
        val muted = s.copy(alerts = s.alerts.copy(muted = setOf(id)))
        assertEquals(emptyList<Alert>(), w.alertsFor(AUCKLAND, muted, null))
        assertEquals(listOf(id), w.allAlertsFor(AUCKLAND, muted, null).map { it.id })
    }

    @Test fun settingsDecideWhatIsNotified() {
        val base = Settings(places = listOf(AUCKLAND, WELLINGTON), selected = WELLINGTON.key, alerts = AlertSettings(own = false))
        assertEquals(emptyList<AlertNotice>(), watch().checkAlerts(base.copy(alerts = base.alerts.copy(notify = false)), emptyMap(), true))
        tmp.root.listFiles()?.forEach { it.delete() }
        assertEquals(listOf(WELLINGTON), watch().checkAlerts(base.copy(alerts = base.alerts.copy(selectedOnly = true)), emptyMap(), true).map { it.place })
    }

    @Test fun checksAreSpacedOutAndFailuresKeepOldWarnings() {
        val srv = FakeAlertServer()
        val w = watch(srv)
        val s = Settings(places = listOf(AUCKLAND), alerts = AlertSettings(own = false, interval = 15))
        w.checkAlerts(s, emptyMap(), now = 1_000_000.0)
        w.checkAlerts(s, emptyMap(), now = 1_000_000.0 + 600)                       // 10 minutes: not due
        assertEquals(1, srv.hits["/alerts-metservice/cap/rss"])
        srv.mode["metservice"] = "http500"
        w.checkAlerts(s, emptyMap(), now = 1_000_000.0 + 900)
        assertEquals(2, srv.hits["/alerts-metservice/cap/rss"])
        assertTrue("metservice" in w.alertStore.errors)
        assertEquals(1, w.alertStore.alerts[AUCKLAND.key]!!.size)                   // kept
    }

    @Test fun quakesNotifiedOnce() {
        val s = Settings(places = listOf(WELLINGTON))
        val w = watch()
        // the fixtures are dated QNOW; check "at" that time
        val first = w.checkQuakes(s, force = true, now = QNOW)
        assertEquals(listOf(7.3, 5.3), first.map { it.quake.mag })
        assertEquals(emptyList<QuakeNotice>(), watch().checkQuakes(s, force = true, now = QNOW))
        assertEquals(5, watch().shownQuakes(s, QNOW).size)
        assertEquals(7, watch().shownQuakes(s.copy(quakes = QuakeSettings(period = "week")), QNOW).size)
    }
}
