// The animated sky: where the Sun is, the colours, and drawing every kind of weather with
// the animation running (manual clock) without crashing.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.OpenMeteo
import io.github.shanebl030glitch.rbsbweather.data.UnitPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkyTest {
    @get:Rule val compose = createComposeRule()

    private val fc = OpenMeteo({ _, _ -> resource("openmeteo_forecast.json") }).forecast(AUCKLAND)

    @Test fun sunArcFollowsTheDay() {
        // the sample forecast is for 15 Nov 2026 in Auckland (NZDT, UTC+13)
        val rise = fc.daily[0].sunrise!!; val set = fc.daily[0].sunset!!
        assertTrue(rise.startsWith("2026-11-15") && set.startsWith("2026-11-15"))
        val noon = skyState(fc, AUCKLAND, Instant.parse("2026-11-15T00:10:00Z"))       // ~13:10 NZDT
        assertNotNull(noon.arc)
        assertTrue("arc ${noon.arc}", noon.arc!! in 0.4f..0.65f)
        assertTrue(noon.sunElevation > 50)
        val night = skyState(fc, AUCKLAND, Instant.parse("2026-11-15T12:00:00Z"))      // 01:00 NZDT
        assertTrue(night.sunElevation < -12)
        assertTrue(night.arc!! in 0f..1f)
        // night colours are dark, day colours bright
        val (nTop, _) = skyColours(night.copy(cloudiness = 0f))
        val (dTop, _) = skyColours(noon.copy(cloudiness = 0f))
        assertTrue(nTop.red + nTop.green + nTop.blue < dTop.red + dTop.green + dTop.blue)
    }

    @Test fun windDirectionTurnsTheClouds() {
        val fromWest = skyState(fc.copy(current = fc.current.copy(windDirection = 270.0)), AUCKLAND)
        val fromEast = skyState(fc.copy(current = fc.current.copy(windDirection = 90.0)), AUCKLAND)
        assertEquals(1f, fromWest.windEast, 0.01f)
        assertEquals(-1f, fromEast.windEast, 0.01f)
    }

    @Test fun everyKindOfWeatherDrawsWhileAnimating() {
        compose.mainClock.autoAdvance = false
        val codes = listOf(0, 2, 3, 45, 63, 75, 95, null)
        var code: Int? = 0
        val state = androidx.compose.runtime.mutableStateOf(fc)
        compose.setContent {
            SkyCard(state.value, AUCKLAND, UnitPrefs(), animate = true, status = "Local time at location: 10:15",
                statusIsWarning = false)
        }
        for (c in codes) {
            code = c
            state.value = fc.copy(current = fc.current.copy(weatherCode = code))
            compose.mainClock.advanceTimeBy(2_000)                    // ~120 animation frames
        }
        compose.onNodeWithTag("sky").assertIsDisplayed()
    }
}
