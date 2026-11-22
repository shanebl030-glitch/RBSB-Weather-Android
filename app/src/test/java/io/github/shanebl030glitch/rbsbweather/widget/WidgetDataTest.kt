// What the home-screen widgets show.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.widget

import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.TempUnit
import io.github.shanebl030glitch.rbsbweather.data.UnitPrefs
import io.github.shanebl030glitch.rbsbweather.data.parseForecast
import io.github.shanebl030glitch.rbsbweather.ui.resource
import org.junit.Assert.assertEquals
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
        assertEquals(WIDGET_DAYS, d.days.size)
        assertEquals("Today", d.days[0].name)
        assertTrue(d.updated.startsWith("Updated "))
        assertTrue(d.hiLo.startsWith("H "))
    }

    @Test fun oldWeatherIsMarkedAsASavedCopy() {
        val now = System.currentTimeMillis()
        val d = widgetData(Settings(places = listOf(AUCKLAND)), fc.copy(fetchedAt = now - 5 * 3_600_000L), now)
        assertTrue(d.updated, d.updated.startsWith("Saved copy from "))
    }
}
