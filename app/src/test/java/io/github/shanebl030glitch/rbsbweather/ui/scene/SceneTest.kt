// The animated sky's conditions and moving parts, mirroring the desktop app's test_scene.py.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.scene

import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.data.parseForecast
import io.github.shanebl030glitch.rbsbweather.ui.resource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** A forecast at a chosen local time with sunrise 06:00 and sunset 20:10 (as the desktop's _fc). */
fun sceneFc(code: Int = 2, now: String = "2026-11-15T10:15", lat: Double = -36.8, windDir: Double? = 225.0,
            isDay: Boolean = true, fetchedAt: Long = 0L): Forecast {
    val base = parseForecast(resource("openmeteo_forecast.json"), AUCKLAND.copy(latitude = lat))
    val d0 = base.daily[0].copy(date = "2026-11-15", sunrise = "2026-11-15T06:00", sunset = "2026-11-15T20:10")
    return base.copy(current = base.current.copy(weatherCode = code, time = now, windDirection = windDir, isDay = isDay),
        daily = listOf(d0) + base.daily.drop(1), fetchedAt = fetchedAt)
}

private fun nzMs(local: String) = LocalDateTime.parse(local).atZone(ZoneId.of("Pacific/Auckland")).toInstant().toEpochMilli()

class SceneTest {
    @Test fun everyKnownCodeClassified() {
        for (code in WeatherCodes.known) {
            val (k, i) = classify(code)
            assertNotNull(k); assertTrue(i in 0.0..1.0)
        }
        assertEquals(Kind.PARTLY, classify(null).first)
        assertEquals(Kind.PARTLY, classify(42).first)
        assertEquals(Kind.RAIN to 1.0, classify(65))
        assertEquals(Kind.THUNDER, classify(95).first)
    }

    @Test fun moonAgeKnownDates() {
        assertEquals(0.0, io.github.shanebl030glitch.rbsbweather.data.Moon.ageFraction(java.time.Instant.parse("2000-01-06T18:14:00Z")), 0.01)
        assertEquals(0.5, io.github.shanebl030glitch.rbsbweather.data.Moon.ageFraction(java.time.Instant.parse("2024-01-25T17:54:00Z")), 0.03)
    }

    @Test fun dayNightTwilight() {
        val nowMs = nzMs("2026-11-15T13:00")
        assertEquals(1.0, conditionsFromForecast(sceneFc(now = "2026-11-15T13:00"), nowMs).daylight, 0.0)
        val night = conditionsFromForecast(sceneFc(now = "2026-11-15T23:30"), nowMs)
        assertEquals(0.0, night.daylight, 0.0); assertEquals(0.0, night.twilight, 0.0)
        val dusk = conditionsFromForecast(sceneFc(now = "2026-11-15T20:10"), nowMs)
        assertEquals(1.0, dusk.twilight, 0.01); assertEquals(0.5, dusk.daylight, 0.01)
        val dawn = conditionsFromForecast(sceneFc(now = "2026-11-15T05:50"), nowMs)
        assertTrue(dawn.twilight > 0.6); assertTrue(dawn.daylight < 0.5)
    }

    @Test fun sunProgress() {
        assertEquals(0.0, conditionsFromForecast(sceneFc(now = "2026-11-15T06:00")).sunProgress, 1e-9)
        assertEquals(0.5, conditionsFromForecast(sceneFc(now = "2026-11-15T13:05")).sunProgress, 0.01)
    }

    @Test fun clockAdvancesSinceDownload() {
        val fc = sceneFc(now = "2026-11-15T19:00", fetchedAt = 1_000_000L)
        assertEquals(0.0, conditionsFromForecast(fc, 1_000_000L + 3 * 3_600_000L).daylight, 0.0)    // 22:00 → night
        assertEquals(1.0, conditionsFromForecast(fc, 1_000_000L).daylight, 0.0)
    }

    @Test fun fallbackWithoutSunTimesUsesIsDay() {
        assertEquals(0.0, conditionsFromForecast(sceneFc(isDay = false).copy(daily = emptyList())).daylight, 0.0)
        assertEquals(1.0, conditionsFromForecast(sceneFc(isDay = true, now = "garbage")).daylight, 0.0)
    }

    @Test fun windDirectionAndHemisphere() {
        assertTrue(conditionsFromForecast(sceneFc(windDir = 270.0)).windEast > 0)        // from the west → right
        assertTrue(conditionsFromForecast(sceneFc(windDir = 90.0)).windEast < 0)         // from the east → left
        assertTrue(conditionsFromForecast(sceneFc(lat = -36.8)).southern)
        assertFalse(conditionsFromForecast(sceneFc(lat = 51.5)).southern)
        assertEquals(1.0, windDrift(270.0), 1e-9)
        assertEquals(-1.0, windDrift(90.0), 1e-9)
        assertEquals(0.3 + 0.7 * 0.7071, windDrift(315.0), 0.01)
        assertEquals(MIN_EAST_WEST, windDrift(0.0), 1e-9)                               // north wind still drifts
        assertEquals(1.0, windDrift(null), 0.0)
    }

    @Test fun cloudSpeedsFollowTheDesktopTable() {
        assertEquals(1 / 150.0, cloudSpeed(0.0), 1e-9)
        assertEquals(1 / 150.0, cloudSpeed(null), 1e-9)
        assertEquals(1 / 70.0, cloudSpeed(10.0), 1e-9)
        assertEquals(1 / 32.0, cloudSpeed(40.0), 1e-9)
        assertEquals(1 / 19.0, cloudSpeed(250.0), 1e-9)
        assertTrue(cloudSpeed(15.0) in (1 / 70.0)..(1 / 50.0))
    }

    @Test fun cloudAmountOrdering() {
        fun amt(code: Int) = classify(code).let { Conditions(it.first, it.second).cloudAmount }
        assertEquals(0.0, amt(0), 0.0)
        assertTrue(amt(1) < amt(2)); assertTrue(amt(2) < amt(3))
        assertTrue(classify(99).let { Conditions(it.first, it.second).darkness } > classify(61).let { Conditions(it.first, it.second).darkness })
    }

    @Test fun particlesBoundedOverALongRun() {
        for (code in listOf(65, 75, 99, 55, 45)) {
            val s = SceneEngine(seed = 9)
            s.setSize(500, 200)
            val (k, i) = classify(code)
            s.setConditions(Conditions(k, i, windKmh = 60.0))
            val counts = listOf(s.weather.drops.size, s.weather.flakes.size, s.clouds.size, s.weather.fog.size)
            repeat(6000) { s.advance(0.1) }                       // 10 simulated minutes
            assertEquals(counts, listOf(s.weather.drops.size, s.weather.flakes.size, s.clouds.size, s.weather.fog.size))
            assertTrue(s.weather.splashes.size <= 60)
            s.weather.drops.forEach { assertTrue(it.x in -60.0..560.0 && it.y in -40.0..220.0) }
            s.weather.flakes.forEach { assertTrue(it.x in -10.0..510.0 && it.y in -10.0..210.0) }
            s.clouds.forEach { assertTrue(it.x >= -it.w - 40 && it.x <= 540) }
        }
    }

    @Test fun lightningOnlyInStorms() {
        val s = SceneEngine(seed = 4)
        s.setSize(400, 200)
        s.setConditions(Conditions(Kind.THUNDER, 1.0))
        var flashes = 0
        repeat(600) { val before = s.flash; s.advance(1 / 30.0); if (s.flash > before) flashes++ }
        assertTrue(flashes >= 2)
        assertNotNull(s.bolt)
        s.setConditions(Conditions(Kind.RAIN, 1.0))
        repeat(600) { s.advance(1 / 30.0) }
        assertEquals(0.0, s.flash, 0.0)
    }

    @Test fun rainSlantsWithWind() {
        for (sign in listOf(1.0, -1.0)) {
            val s = SceneEngine(seed = 2)
            s.setSize(400, 200)
            s.setConditions(Conditions(Kind.RAIN, 0.5, windKmh = 40.0, windEast = sign))
            val xs = s.weather.drops.map { it.x }
            s.advance(0.02)
            val moved = s.weather.drops.zip(xs).map { (d, x) -> d.x - x }.filter { kotlin.math.abs(it) < 50 }.average()
            assertEquals(sign > 0, moved > 0)
        }
    }

    @Test fun windEasesInsteadOfJumping() {
        val s = SceneEngine(seed = 1)
        s.setSize(400, 200)
        s.setConditions(Conditions(Kind.PARTLY, 0.5, windKmh = 10.0, windEast = 1.0, fromForecast = true))
        s.setConditions(Conditions(Kind.PARTLY, 0.5, windKmh = 60.0, windEast = -1.0, fromForecast = true))
        assertEquals(1.0, s.driftEast!!, 1e-9)                    // not jumped
        s.advance(WIND_EASE_S)
        assertTrue(s.driftEast!! < 1.0 && s.driftEast!! > -1.0)
        repeat(100) { s.advance(0.5) }
        assertEquals(-1.0, s.driftEast!!, 0.01)
        assertEquals(cloudSpeed(60.0), s.driftSpeed!!, 1e-4)
    }

    @Test fun weatherChangeFadesAndEnds() {
        val s = SceneEngine(seed = 3)
        s.setSize(400, 200)
        s.setConditions(Conditions(Kind.RAIN, 0.6))
        s.setConditions(Conditions(Kind.CLEAR, 0.0))
        assertEquals(0.0, s.cloudFade, 0.0)
        assertTrue(s.fading.isNotEmpty() && s.oldWeather.isNotEmpty())
        repeat(20) { s.advance(0.1) }
        assertEquals(1.0, s.cloudFade, 0.0)
        assertTrue(s.fading.isEmpty() && s.oldWeather.isEmpty())
        // without animation the change is instant
        val still = SceneEngine(seed = 3).apply { animations = false; setSize(400, 200); setConditions(Conditions(Kind.RAIN, 0.6)) }
        still.setConditions(Conditions(Kind.CLEAR, 0.0))
        assertEquals(1.0, still.cloudFade, 0.0)
    }

    @Test fun nightAndDaySkiesDiffer() {
        val day = SceneEngine().apply { setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 1.0)) }.skyColors()
        val night = SceneEngine().apply { setConditions(Conditions(Kind.CLEAR, 0.0, daylight = 0.0)) }.skyColors()
        assertTrue(night.first.blue + night.first.green < day.first.blue + day.first.green)
    }

    @Test fun arcGoesFromBottomLeftThroughTheTopToBottomRight() {
        val rise = arcPoint(0.0, 0f, 0f, 400f, 200f, 20f)!!
        val top = arcPoint(0.5, 0f, 0f, 400f, 200f, 20f)!!
        val set = arcPoint(1.0, 0f, 0f, 400f, 200f, 20f)!!
        assertTrue(rise.second > 200f && set.second > 200f)              // hidden below the horizon
        assertTrue(top.second < 60f && rise.first < top.first && top.first < set.first)
        assertNull(arcPoint(1.2, 0f, 0f, 400f, 200f, 20f)); assertNull(arcPoint(null, 0f, 0f, 400f, 200f, 20f))
    }

    @Test fun moonIsPlacedForThePlaceAndTime() {
        // 21:00 NZDT on 15 Nov 2026 the Moon is up in Auckland; at 08:00 the next morning it isn't
        val evening = conditionsFromForecast(sceneFc(now = "2026-11-15T21:00"), nzMs("2026-11-15T21:00"))
        assertTrue(evening.moonUp)
        val p = evening.moonProgressAt(nzMs("2026-11-15T21:00"))!!
        assertTrue("$p", p in 0.0..1.0)
        assertNotNull(evening.moonPhaseAngle)
        val morning = conditionsFromForecast(sceneFc(now = "2026-11-16T08:00"), nzMs("2026-11-16T08:00"))
        assertFalse(morning.moonUp)
    }

    @Test fun playbackUsesThatHoursWeather() {
        val base = sceneFc(code = 0, now = "2026-11-15T10:00")
        val hours = (0 until 30).map { i ->
            io.github.shanebl030glitch.rbsbweather.data.Hour(LocalDateTime.parse("2026-11-15T10:00").plusHours(i.toLong()).toString(), 15.0 + i, 10.0, 0.0,
                if (i == 3) 63 else 0)
        }
        val fc = base.copy(hourly = hours)
        val now = nzMs("2026-11-15T10:00")
        assertEquals(Kind.CLEAR, conditionsFromForecast(fc, now).kind)
        assertEquals(Kind.RAIN, conditionsFromForecast(fc, now, 3 * 3600.0 + 600).kind)
        assertEquals(PLAYBACK_MAX_S, playbackSeconds(fc, now), 0.0)
        assertEquals(0.0, playbackSeconds(fc.copy(hourly = emptyList()), now), 0.0)
        assertEquals(hours[3], hourlyAt(fc, LocalDateTime.parse("2026-11-15T13:40")))
    }
}
