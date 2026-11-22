// Forecast playback and the mini forecast inside the sky.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.Hour
import io.github.shanebl030glitch.rbsbweather.data.parseForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PlaybackTest {
    private val fc = parseForecast(resource("openmeteo_forecast.json"), AUCKLAND)
    private val zone = ZoneId.of("Pacific/Auckland")
    private fun at(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant()

    @Test fun labels() {
        assertEquals("15 min", playbackLabel(1))
        assertEquals("1 h", playbackLabel(4))
        assertEquals("2 h 45 min", playbackLabel(11))
        assertEquals("24 h", playbackLabel(PLAYBACK_STEPS))
    }

    @Test fun momentEasesTemperatureBetweenHours() {
        val h = fc.hourly
        val i = 5
        val t0 = h[i]; val t1 = h[i + 1]
        val m = momentAt(fc.copy(hourly = h.mapIndexed { k, x -> if (k == i) x.copy(temperature = 10.0) else if (k == i + 1) x.copy(temperature = 14.0) else x }),
            AUCKLAND, at(t0.time).plusSeconds(30 * 60))!!
        assertEquals(12.0, m.temperature!!, 0.001)
        assertEquals(t0.weatherCode, m.weatherCode)
        assertTrue(m.label, m.label.endsWith(t0.time.substring(11, 14) + "30"))
        assertNotNull(t1)
    }

    @Test fun momentOutsideTheForecastIsNull() {
        assertNull(momentAt(fc, AUCKLAND, at(fc.hourly.first().time).minusSeconds(3600)))
        assertNull(momentAt(fc, AUCKLAND, at(fc.hourly.last().time).plusSeconds(4 * 3600)))
        assertNull(momentAt(fc.copy(hourly = emptyList<Hour>()), AUCKLAND, at(fc.hourly.first().time)))
    }

    @Test fun playbackChangesTheSky() {
        // a clear hour at night and a rainy hour by day give different skies
        val h0 = fc.hourly.first()
        val night = fc.copy(hourly = listOf(h0.copy(time = fc.daily[0].date + "T02:00", weatherCode = 0),
            h0.copy(time = fc.daily[0].date + "T03:00", weatherCode = 0)))
        val m = momentAt(night, AUCKLAND, at(fc.daily[0].date + "T02:30"))!!
        val s = skyState(night, AUCKLAND, m.at, m)
        assertTrue(s.sunElevation < 0)
        assertEquals(false, s.rain)
        val wet = fc.copy(hourly = listOf(h0.copy(time = fc.daily[0].date + "T13:00", weatherCode = 63),
            h0.copy(time = fc.daily[0].date + "T14:00", weatherCode = 63)))
        val m2 = momentAt(wet, AUCKLAND, at(fc.daily[0].date + "T13:15"))!!
        val s2 = skyState(wet, AUCKLAND, m2.at, m2)
        assertTrue(s2.sunElevation > 0)
        assertTrue(s2.rain)
    }

    @Test fun miniForecastFitsTheWidth() {
        assertEquals(3, miniDayCount(150f, 7))       // never fewer than 3
        assertEquals(5, miniDayCount(320f, 7))       // a phone held upright
        assertEquals(7, miniDayCount(900f, 7))       // tablets / landscape: up to 7
        assertEquals(3, miniDayCount(900f, 3))       // never more days than the forecast has
        assertEquals(3, miniDayName("2026-11-25").length.coerceAtMost(3))
    }
}
