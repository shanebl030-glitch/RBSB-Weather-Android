// The weather data layer: parsing, the backup provider, the cache, settings, units, the Sun.
// Fixtures in src/test/resources come from the desktop RBSB-Weather's test server (same formats).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private fun res(name: String): String =
    DataTest::class.java.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

val AUCKLAND = Place("Auckland", -36.84853, 174.76349, "New Zealand", "Auckland", "Pacific/Auckland")

class DataTest {
    @get:Rule val tmp = TemporaryFolder()

    // ------------------------------------------------------------ Open-Meteo
    @Test fun openMeteoForecastIsRead() {
        val fc = parseForecast(res("openmeteo_forecast.json"), AUCKLAND)
        assertEquals("Open-Meteo", fc.source)
        assertEquals("Pacific/Auckland", fc.timezone)
        assertEquals(16.6, fc.current.temperature!!, 1e-9)
        assertEquals(168, fc.hourly.size)
        assertEquals(7, fc.daily.size)
        assertEquals(19.3, fc.daily[0].tempMax!!, 1e-9)
        assertTrue(fc.daily.all { it.sunrise != null && it.sunset != null })
        assertTrue(fc.hourly.all { it.time.length == 16 })
    }

    @Test fun openMeteoDamagedReplies() {
        for (bad in listOf("", "{not json", "[]", "{}", """{"current":{}}""",
            """{"error":true,"reason":"Latitude must be in range"}""")) {
            try {
                parseForecast(bad, AUCKLAND)
                fail("should have refused: $bad")
            } catch (e: WeatherException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
        // NaN / infinity / strings / missing columns are just gaps
        val o = JSONObject(res("openmeteo_forecast.json"))
        o.getJSONObject("current").put("relative_humidity_2m", "NaN")
        o.getJSONObject("hourly").remove("precipitation")
        o.getJSONObject("daily").put("uv_index_max", JSONObject.NULL)
        val fc = parseForecast(o.toString(), AUCKLAND)
        assertNull(fc.current.humidity)
        assertTrue(fc.hourly.all { it.precipitation == null })
        assertTrue(fc.daily.all { it.uvIndexMax == null })
    }

    @Test fun placeSearch() {
        val places = parseGeocode(res("geocode.json"))
        assertEquals(4, places.size)                              // duplicate, blank and bad ones dropped
        assertEquals("Auckland", places[0].name)
        assertEquals(listOf("Hamilton", "Hamilton"), places.filter { it.name == "Hamilton" }.map { it.name })
        assertEquals(emptyList<Place>(), parseGeocode("{}"))
    }

    // ----------------------------------------------------------- MET Norway
    private val metNow: Instant by lazy {
        val t = JSONObject(res("metno_forecast.json")).getJSONObject("properties")
            .getJSONArray("timeseries").getJSONObject(0).getString("time")
        Instant.parse(t).plusSeconds(30 * 60)
    }

    @Test fun metNorwayForecastIsRead() {
        val fc = parseMetNorway(res("metno_forecast.json"), AUCKLAND, 7, metNow)
        assertEquals("MET Norway", fc.source)
        assertNotNull(fc.current.temperature)
        assertNotNull(fc.current.apparentTemperature)             // worked out (MET doesn't send it)
        assertTrue(fc.hourly.size in 40..80)
        assertTrue(fc.daily.size in 3..7)
        assertTrue(fc.daily.all { it.tempMax!! >= it.tempMin!! })
        assertTrue(fc.daily[0].sunrise!!.startsWith(fc.daily[0].date))          // worked out from the Sun
        // wind converted from m/s to km/h
        val raw = JSONObject(res("metno_forecast.json")).getJSONObject("properties").getJSONArray("timeseries")
            .getJSONObject(0).getJSONObject("data").getJSONObject("instant").getJSONObject("details")
            .getDouble("wind_speed")
        assertEquals(raw * 3.6, fc.current.windSpeed!!, 0.06)
    }

    @Test fun metNorwaySymbols() {
        assertEquals(0, WeatherCodes.fromMetSymbol("clearsky_day"))
        assertEquals(80, WeatherCodes.fromMetSymbol("lightrainshowers_night"))
        assertEquals(65, WeatherCodes.fromMetSymbol("heavyrain"))
        assertEquals(73, WeatherCodes.fromMetSymbol("sleet"))
        assertEquals(95, WeatherCodes.fromMetSymbol("rainandthunder"))
        assertNull(WeatherCodes.fromMetSymbol("nonsense"))
        assertNull(WeatherCodes.fromMetSymbol(null))
    }

    @Test fun metNorwayDamagedReplies() {
        for (bad in listOf("", "{}", """{"properties":{"timeseries":[]}}""",
            """{"properties":{"timeseries":[{"time":"x","data":{}}]}}""")) {
            try {
                parseMetNorway(bad, AUCKLAND, 7, metNow)
                fail("should have refused: $bad")
            } catch (e: WeatherException) { /* expected */ }
        }
    }

    // -------------------------------------------------------- backup + cache
    private val om = OpenMeteoProvider(OpenMeteo({ _, _ -> res("openmeteo_forecast.json") }))
    private val met = MetNorwayProvider(MetNorway({ _, _ -> res("metno_forecast.json") }))
    private val down = object : Provider {
        override val name = "Open-Meteo"
        override fun forecast(place: Place): Forecast = throw WeatherException("No internet connection.")
    }

    @Test fun backupIsUsedWhenTheMainProviderFails() {
        val repo = WeatherRepository(listOf(down, met), ForecastCache(tmp.root))
        val fc = repo.fetch(AUCKLAND)
        assertEquals("MET Norway", fc.source)
        assertEquals("MET Norway", repo.cached(AUCKLAND)!!.source)
    }

    @Test fun allProvidersFailing() {
        val down2 = object : Provider {
            override val name = "MET Norway"
            override fun forecast(place: Place): Forecast = throw RuntimeException("boom")
        }
        try {
            WeatherRepository(listOf(down, down2), null).fetch(AUCKLAND)
            fail()
        } catch (e: WeatherException) {
            assertTrue(e.message!!.contains("No internet connection"))
            assertTrue(e.message!!.contains("unexpected problem"))
        }
    }

    @Test fun cacheRoundTripAndDamage() {
        val cache = ForecastCache(tmp.root)
        val fc = WeatherRepository(listOf(om), cache).fetch(AUCKLAND)
        val back = cache.load(AUCKLAND)!!
        assertTrue(back.fromCache)
        assertEquals(fc.copy(fromCache = true), back)
        tmp.root.listFiles()!!.first { it.name.startsWith("forecast-") }.writeText("{damaged")
        assertNull(cache.load(AUCKLAND))
        cache.save(fc)
        val other = Place("Elsewhere", 10.0, 10.0)
        cache.save(fc.copy(place = other))
        cache.cleanOrphans(listOf(AUCKLAND))
        assertNotNull(cache.load(AUCKLAND))
        assertNull(cache.load(other))
    }

    // -------------------------------------------------------------- settings
    @Test fun settingsRoundTripAndBadValues() {
        val s = Settings().withPlace(AUCKLAND).withPlace(Place("London", 51.5, -0.12, "United Kingdom"))
            .copy(units = UnitPrefs(TempUnit.F, WindUnit.MPH, PrecipUnit.IN, ClockFormat.H12), refreshMinutes = 60)
        assertEquals(s, Settings.fromJson(s.toJson().toString()))
        assertEquals("51.5000,-0.1200", s.selected)
        val bad = """{"places":[{"name":"X","latitude":200,"longitude":0},5,{"name":"Ok","latitude":1,"longitude":2}],
            "temp":"K","refreshMinutes":7,"animations":"yes","selected":"nope"}"""
        val b = Settings.fromJson(bad)
        assertEquals(listOf("Ok"), b.places.map { it.name })
        assertEquals("1.0000,2.0000", b.selected)
        assertEquals(TempUnit.C, b.units.temp)
        assertEquals(30, b.refreshMinutes)
        assertTrue(b.animations)
        assertEquals(Settings(), Settings.fromJson("{oops"))
        assertEquals(Settings(), Settings.fromJson(null))
    }

    @Test fun placesAddRemoveMove() {
        var s = Settings()
        val a = Place("A", 1.0, 1.0); val b = Place("B", 2.0, 2.0); val c = Place("C", 3.0, 3.0)
        s = s.withPlace(a).withPlace(b).withPlace(c).withPlace(a)        // adding again just selects it
        assertEquals(listOf("A", "B", "C"), s.places.map { it.name })
        assertEquals(a.key, s.selected)
        s = s.moved(c.key, -2)
        assertEquals(listOf("C", "A", "B"), s.places.map { it.name })
        s = s.withoutPlace(a.key)
        assertEquals(c.key, s.selected)                                 // the first left
        var many = Settings()
        for (i in 0 until MAX_PLACES) many = many.withPlace(Place("P$i", i * 0.1, 0.0))
        try { many.withPlace(Place("one too many", 89.0, 0.0)); fail() } catch (e: IllegalArgumentException) { }
    }

    // ----------------------------------------------------------------- units
    @Test fun units() {
        assertEquals("17°C", Units.fmtTemp(16.6, TempUnit.C))
        assertEquals("62°F", Units.fmtTemp(16.6, TempUnit.F))
        assertEquals("0°C", Units.fmtTemp(-0.4, TempUnit.C))                    // never "-0"
        assertEquals("10 mph", Units.fmtWind(16.09, WindUnit.MPH))
        assertEquals("5 kn", Units.fmtWind(9.26, WindUnit.KN))
        assertEquals("1.00 in", Units.fmtPrecip(25.4, PrecipUnit.IN))
        assertEquals("2.5 mm", Units.fmtPrecip(2.5, PrecipUnit.MM))
        assertEquals("–", Units.fmtTemp(null, TempUnit.C))
        assertEquals("SW", Units.compass(225.0))
        assertEquals("N", Units.compass(359.0))
        assertEquals("19:30", Units.clock("2026-11-22T19:30", ClockFormat.H24))
        assertEquals("7:30 pm", Units.clock("2026-11-22T19:30", ClockFormat.H12))
        assertEquals("12:05 am", Units.clock("2026-11-22T00:05", ClockFormat.H12))
        assertEquals("7 pm", Units.hourLabel("2026-11-22T19:00", ClockFormat.H12))
        assertEquals("–", Units.clock("bad", ClockFormat.H24))
    }

    @Test fun weatherCodes() {
        assertEquals("Thunderstorm", WeatherCodes.describe(95))
        assertEquals("Unknown", WeatherCodes.describe(12345))
        assertEquals("🌙", WeatherCodes.symbol(0, isDay = false))
        assertTrue(WeatherCodes.isRain(61) && WeatherCodes.isSnow(73) && WeatherCodes.isFog(45))
        assertFalse(WeatherCodes.isRain(3))
    }

    // ------------------------------------------------------------------- Sun
    @Test fun sunriseAndSunsetInAuckland() {
        // Auckland, 22 Nov 2026: sunrise ~05:58, sunset ~20:15 NZDT (as Open-Meteo gives)
        val (rise, set) = Sun.riseSet(LocalDate.of(2026, 11, 22), AUCKLAND.latitude, AUCKLAND.longitude,
            ZoneId.of("Pacific/Auckland"))
        val r = rise!!.hour * 60 + rise.minute
        val s = set!!.hour * 60 + set.minute
        assertTrue("sunrise $rise", abs(r - (5 * 60 + 58)) <= 3)
        assertTrue("sunset $set", abs(s - (20 * 60 + 15)) <= 3)
    }

    @Test fun polarNightHasNoSunrise() {
        val (rise, set) = Sun.riseSet(LocalDate.of(2026, 12, 21), 78.2, 15.6, ZoneId.of("Arctic/Longyearbyen"))
        assertNull(rise)
        assertNull(set)
    }

    @Test fun dayAndNight() {
        // noon and midnight in Auckland (NZDT = UTC+13)
        assertTrue(Sun.isDay(Instant.parse("2026-11-21T23:00:00Z"), AUCKLAND.latitude, AUCKLAND.longitude))
        assertFalse(Sun.isDay(Instant.parse("2026-11-22T11:00:00Z"), AUCKLAND.latitude, AUCKLAND.longitude))
        assertEquals(ZoneId.of("Pacific/Auckland"), Sun.zoneFor(AUCKLAND))
        assertEquals("+12:00", Sun.zoneFor(Place("X", -40.0, 175.0)).id)
    }
}
