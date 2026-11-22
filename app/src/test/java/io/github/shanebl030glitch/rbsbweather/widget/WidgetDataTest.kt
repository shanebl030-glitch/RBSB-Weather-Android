// What the home-screen widgets show (the desktop widget's text).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.widget

import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.TempUnit
import io.github.shanebl030glitch.rbsbweather.data.UnitPrefs
import io.github.shanebl030glitch.rbsbweather.data.parseForecast
import io.github.shanebl030glitch.rbsbweather.ui.resource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataTest {
    private val fc = parseForecast(resource("openmeteo_forecast.json"), AUCKLAND)

    @Test fun noPlaceAndNoWeatherYet() {
        assertTrue(widgetData(Settings(), null).condition.contains("add a place"))
        assertEquals("–", widgetData(Settings(places = listOf(AUCKLAND)), null).temp)
    }

    @Test fun showsTheSelectedPlaceInTheChosenUnits() {
        val now = System.currentTimeMillis()
        val s = Settings(places = listOf(AUCKLAND), units = UnitPrefs(temp = TempUnit.F))
        val d = widgetData(s, fc.copy(fetchedAt = now - 5 * 60_000), now)
        assertEquals("Auckland", d.place)
        assertTrue(d.temp, d.temp.endsWith("°F"))
        assertTrue(d.updated, d.updated.startsWith("↻ ") && !d.updated.contains("ago"))
        assertFalse(d.updatedWarn)
        assertTrue(d.hiLo, d.hiLo.startsWith("H ") && d.hiLo.contains("  ·  L "))
        assertEquals("Open-Meteo", d.source)
    }

    @Test fun oldWeatherTurnsAmberWithItsAge() {
        val now = System.currentTimeMillis()
        val d = widgetData(Settings(places = listOf(AUCKLAND)), fc.copy(fetchedAt = now - 5 * 3_600_000L), now)
        assertTrue(d.updatedWarn)
        assertTrue(d.updated, d.updated.endsWith("(5 h ago)"))
        // "only when I pull down": amber after 3 hours
        val s0 = Settings(places = listOf(AUCKLAND), refreshMinutes = 0)
        assertFalse(widgetData(s0, fc.copy(fetchedAt = now - 2 * 3_600_000L), now).updatedWarn)
        assertTrue(widgetData(s0, fc.copy(fetchedAt = now - 4 * 3_600_000L), now).updatedWarn)
    }

    @Test fun backupProviderIsNamed() {
        val d = widgetData(Settings(places = listOf(AUCKLAND)), fc.copy(source = "MET Norway", fetchedAt = System.currentTimeMillis()))
        assertEquals("MET Norway (backup)", d.source)
    }

    @Test fun ages() {
        assertEquals("just now", ageText(30)); assertEquals("5 min ago", ageText(300))
        assertEquals("2 h ago", ageText(7300)); assertEquals("3 days ago", ageText(3 * 86400 + 5))
    }

    @Test fun framesFitTheBudget() {
        val sc = WidgetFrames.scale(320, 150, 3f)
        assertTrue(320 * sc * 150 * sc * 4 * WidgetFrames.FRAMES <= WidgetFrames.BUDGET_BYTES * 1.01)
        assertEquals(1f, WidgetFrames.scale(100, 100, 1f), 0f)          // never sharper than the screen
    }
}
