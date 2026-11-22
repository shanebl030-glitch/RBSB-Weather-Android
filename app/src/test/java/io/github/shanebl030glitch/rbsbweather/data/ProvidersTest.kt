// The keyed weather providers, the normaliser and the primary/backup chain, using saved replies
// made by the desktop app's provider fixtures (so both apps read the services the same way).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.ui.resource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

val FIXTURE_NOW: Instant = Instant.ofEpochSecond(1795308960)          // 2026-11-22 13:56 in Auckland

/** Answers each service's address with its saved reply and records what was asked. */
class FakeHttp(private val routes: (String) -> String) : HttpClient {
    val asked = mutableListOf<String>()
    override fun get(url: String, headers: Map<String, String>): String {
        asked += url
        return routes(url)
    }
}

class ProvidersTest {
    private val key = { "abcdef123456" }

    private fun <P : KeyedProvider> P.at(now: Instant = FIXTURE_NOW): P = apply { clock = { now } }

    /** What every provider must give the app (as in the desktop app's check_common). */
    private fun checkCommon(fc: Forecast, name: String, minDays: Int, minHours: Int) {
        assertEquals(name, fc.source)
        val c = fc.current
        assertNotNull(c.temperature)
        assertTrue(c.weatherCode in WeatherCodes.known)
        listOf(c.humidity, c.cloudCover).forEach { assertTrue(it == null || it in 0.0..100.0) }
        assertTrue(c.windDirection == null || c.windDirection!! in 0.0..359.99)
        assertTrue(c.time, c.time.startsWith("2026-11-22T13:"))
        assertTrue(fc.daily.size in minDays..7)
        assertEquals("2026-11-22", fc.daily[0].date)
        fc.daily.forEach { d ->
            if (d.tempMin != null && d.tempMax != null) assertTrue(d.tempMin!! <= d.tempMax!!)
            assertTrue(d.toString(), d.sunrise!!.startsWith(d.date) && d.sunset!!.startsWith(d.date))
            assertTrue(d.precipitationProbabilityMax == null || d.precipitationProbabilityMax!! in 0.0..100.0)
        }
        assertTrue(fc.hourly.size >= minHours)
        assertEquals(fc.hourly.map { it.time }.sorted(), fc.hourly.map { it.time })
        assertEquals(13 * 3600, fc.utcOffsetSeconds)
        assertTrue(fc.fetchedAt > System.currentTimeMillis() - 60_000)
    }

    @Test fun openWeatherOneCall() {
        val http = FakeHttp { resource("owm_onecall.json") }
        val fc = OpenWeatherProvider(http, key).at().forecast(AUCKLAND)
        checkCommon(fc, "OpenWeather", 7, 48)
        val c = fc.current
        assertEquals(listOf(16.2, 15.9, 72.0), listOf(c.temperature, c.apparentTemperature, c.humidity))
        assertEquals(listOf(16.6, 29.5, 230.0), listOf(c.windSpeed, c.windGusts, c.windDirection))     // m/s → km/h
        assertEquals(3, c.weatherCode)                                                                 // 804 overcast
        val d0 = fc.daily[0]
        assertEquals(listOf(20.0, 11.0, 40.0, 9.1), listOf(d0.tempMax, d0.tempMin, d0.precipitationProbabilityMax, d0.uvIndexMax))
        assertEquals("06:01" to "20:09", d0.sunrise!!.substring(11) to d0.sunset!!.substring(11))
        assertEquals(0.0, fc.daily[1].precipitationSum!!, 0.0)
        assertEquals(35.0, fc.hourly[0].precipitationProbability!!, 0.0)                               // 0..1 → %
        assertEquals(1, http.asked.size)
        assertFalse("the key must not leak into the forecast", fc.toJson().toString().contains("abcdef123456"))
    }

    @Test fun openWeatherFreeKeyFallsBackAndRemembers() {
        val http = FakeHttp { url ->
            when {
                "/3.0/onecall" in url -> throw WeatherException("HTTP 401", status = 401)
                "/2.5/weather" in url -> resource("owm_weather.json")
                else -> resource("owm_forecast.json")
            }
        }
        val p = OpenWeatherProvider(http, key).at()
        val fc = p.forecast(AUCKLAND)
        checkCommon(fc, "OpenWeather", 5, 100)
        assertEquals(3, http.asked.size)                                   // onecall, weather, forecast
        assertNull(fc.daily[1].uvIndexMax)                                 // not on the free plan
        val hrs = fc.hourly.take(6).map { java.time.LocalDateTime.parse(it.time) }
        assertTrue(hrs.zipWithNext().all { (a, b) -> java.time.Duration.between(a, b).toHours() == 1L })
        p.forecast(AUCKLAND)
        assertEquals(5, http.asked.size)                                   // One Call not asked again
    }

    @Test fun weatherApi() {
        val fc = WeatherApiProvider(FakeHttp { resource("wapi_forecast.json") }, key).at().forecast(AUCKLAND)
        checkCommon(fc, "WeatherAPI.com", 3, 24)
        assertEquals(3, fc.daily.size)
        val c = fc.current
        assertEquals(listOf<Any?>(16.0, 15.1, 3, 1015.0), listOf<Any?>(c.temperature, c.windSpeed, c.weatherCode, c.pressure))
        assertEquals(63, fc.daily[0].weatherCode)                          // 1189 moderate rain
        assertEquals(95, fc.daily[2].weatherCode)                          // 1276 rain with thunder
        assertEquals("${fc.daily[1].date}T20:09", fc.daily[1].sunset)      // "08:09 PM"
        assertNotNull(fc.daily[0].sunset)                                  // "No sunset" → worked out
    }

    @Test fun visualCrossing() {
        val fc = VisualCrossingProvider(FakeHttp { resource("vc_timeline.json") }, key).at().forecast(AUCKLAND)
        checkCommon(fc, "Visual Crossing", 7, 24 * 7)
        assertEquals(7, fc.daily.size)                                     // 15 offered, 7 used
        val c = fc.current
        assertEquals(listOf<Any?>(16.8, 14.8, 2), listOf<Any?>(c.temperature, c.windSpeed, c.weatherCode))
        assertEquals(listOf(63, 0, 95), fc.daily.take(3).map { it.weatherCode })
        assertEquals(9.0, fc.daily[0].uvIndexMax!!, 0.0)
    }

    @Test fun tomorrowIo() {
        val fc = TomorrowProvider(FakeHttp { resource("tio_forecast.json") }, key).at().forecast(AUCKLAND)
        checkCommon(fc, "Tomorrow.io", 5, 100)
        assertEquals(5, fc.daily.size)
        assertEquals(listOf(16.2, 1016.2), listOf(fc.current.windSpeed, fc.current.pressure))
        assertEquals(listOf(63, 0, 95, 71, 2), fc.daily.map { it.weatherCode })
    }

    private fun wuHttp(stations: String = """{"location":{"stationId":["IOFF1","IAUCKLAND1"],"qcStatus":[-1,1]}}""",
                       obs: (String) -> String = { resource("wu_obs.json") }) = FakeHttp { url ->
        when {
            "/v3/wx/forecast" in url -> resource("wu_5day.json")
            "/v3/location/near" in url -> stations
            else -> obs(url)
        }
    }

    @Test fun wundergroundNearestStation() {
        val http = wuHttp()
        val fc = WundergroundProvider(http, key, { null }).at().forecast(AUCKLAND)
        checkCommon(fc, "Weather Underground", 6, 0)
        assertEquals(6, fc.daily.size)
        assertTrue(fc.hourly.isEmpty())
        val c = fc.current
        assertEquals(listOf(17.4, 13.0, 22.3, 235.0, 68.0), listOf(c.temperature, c.windSpeed, c.windGusts, c.windDirection, c.humidity))
        assertEquals(1015.92, c.pressure!!, 0.0)
        assertEquals(3, c.weatherCode)               // the day part says rain, the station measures none: cloud instead
        assertNotNull(c.apparentTemperature)
        assertEquals(listOf(63, 0, 95, 3, 2, 85), fc.daily.map { it.weatherCode })
        val d0 = fc.daily[0]
        assertEquals(listOf(21.0, 11.0, 60.0, 5.1, 9.0, 20.0),
            listOf(d0.tempMax, d0.tempMin, d0.precipitationProbabilityMax, d0.precipitationSum, d0.uvIndexMax, d0.windSpeedMax))
        assertEquals("06:01" to "20:09", d0.sunrise!!.substring(11) to d0.sunset!!.substring(11))
        assertFalse(http.asked.any { "IOFF1" in it })                    // qcStatus -1 skipped
    }

    @Test fun wundergroundOwnStationNotReporting() {
        val http = wuHttp(obs = { throw WeatherException("No content", status = 204) })
        try {
            WundergroundProvider(http, key, { "IMYSTN9" }).at().forecast(AUCKLAND)
            fail("expected an error")
        } catch (e: WeatherException) {
            assertTrue(e.message, e.message!!.contains("IMYSTN9") && e.message!!.contains("isn't reporting"))
        }
        // a stale report (5 hours old) is skipped too
        val late = WundergroundProvider(wuHttp(), key, { "IAUCKLAND1" }).at(FIXTURE_NOW.plusSeconds(5 * 3600))
        try { late.forecast(AUCKLAND); fail("expected an error") } catch (e: WeatherException) {
            assertTrue(e.message, e.message!!.contains("5 h ago"))
        }
    }

    @Test fun missingKeyBadKeyAndQuotaGiveClearMessages() {
        try { WeatherApiProvider(FakeHttp { "" }, { null }).forecast(AUCKLAND); fail() } catch (e: WeatherException) {
            assertTrue(e is KeyMissing)
            assertTrue(e.message!!.contains("needs an API key"))
        }
        val bad = FakeHttp { throw WeatherException("HTTP 401 for https://x/?key=abcdef123456", status = 401) }
        try { WeatherApiProvider(bad, key).forecast(AUCKLAND); fail() } catch (e: WeatherException) {
            assertTrue(e.message!!.contains("didn't accept the API key"))
            assertFalse(e.message!!.contains("abcdef123456"))
        }
        val quota = FakeHttp { throw WeatherException("HTTP 429", status = 429) }
        try { TomorrowProvider(quota, key).forecast(AUCKLAND); fail() } catch (e: WeatherException) {
            assertTrue(e.retryable && e.message!!.contains("allowance"))
        }
        try { VisualCrossingProvider(FakeHttp { "<html>" }, key).forecast(AUCKLAND); fail() } catch (e: WeatherException) {
            assertTrue(e.message!!.contains("couldn't be read"))
        }
    }

    @Test fun everyMappedCodeIsKnown() {
        val codes = (200..804).map { owmCode(it) } + (1000..1282).map { wapiCode(org.json.JSONObject().put("code", it)) } +
            (0..47).map { wuCode(it.toDouble()) } + listOf(1000, 4001, 8000, 10000, 40011, 51000).map { tioCode(it.toDouble()) } +
            listOf("rain", "snow", "fog", "cloudy", "clear-day", "partly-cloudy-night", "thunder-rain", "showers-day", "snow-showers-day")
                .map { vcCode(it, 3.0, 2.0) }
        codes.forEach { c -> assertTrue("$c", c == null || c in WeatherCodes.known) }
        assertEquals(63, tioCode(40011.0))                 // day/night variant of 4001
    }

    @Test fun normaliseDropsImpossibleValuesAndFillsGaps() {
        val base = parseForecast(resource("openmeteo_forecast.json"), AUCKLAND)
        val bad = base.copy(current = base.current.copy(humidity = 140.0, windSpeed = 9999.0, weatherCode = 7, apparentTemperature = null,
            windDirection = -90.0), daily = base.daily.mapIndexed { i, d -> if (i == 0) d.copy(tempMax = 5.0, tempMin = 20.0, sunrise = null, sunset = null) else d })
        val n = normalise(bad, AUCKLAND, 7, FIXTURE_NOW)
        assertEquals(100.0, n.current.humidity!!, 0.0)
        assertNull(n.current.windSpeed)
        assertEquals(270.0, n.current.windDirection!!, 0.0)
        assertTrue(n.current.weatherCode in WeatherCodes.known)
        assertEquals(5.0 to 20.0, n.daily[0].tempMin to n.daily[0].tempMax)            // swapped back
        assertTrue(n.daily[0].sunrise!!.startsWith(n.daily[0].date))                     // worked out
        try { normalise(base.copy(current = base.current.copy(temperature = 300.0)), AUCKLAND); fail() } catch (e: WeatherException) { }
        assertEquals(3, codeFrom(90.0, 0.0)); assertEquals(63, codeFrom(null, 4.0)); assertNull(codeFrom(null, null))
    }

    @Test fun keysAreCheckedBeforeSaving() {
        assertEquals("abcDEF12345", cleanKey("  \"abcDEF12345\" "))
        assertNull(cleanKey("   "))
        listOf("short", "has spaces in it", "x".repeat(200), "key<script>").forEach {
            try { cleanKey(it); fail(it) } catch (e: IllegalArgumentException) { }
        }
    }

    // ------------------------------------------------------------- the chain
    class Named(override val id: String, var down: Boolean = false) : Provider {
        override val name = id
        var calls = 0
        override fun forecast(place: Place): Forecast {
            calls++
            if (down) throw WeatherException("$id is down")
            return parseForecast(resource("openmeteo_forecast.json"), place).copy(source = id)
        }
    }

    @Test fun backupIsUsedAndPrimaryRestsForTheRecoveryTime() {
        val a = Named("a", down = true); val b = Named("b")
        var now = 1_000_000L
        var rec = 30
        val repo = WeatherRepository({ listOf(a, b) }, null, { rec }, { now })
        assertEquals("b", repo.fetch(AUCKLAND).source)
        assertEquals(1, a.calls)
        assertEquals("a is down", repo.status("a").lastError)
        assertTrue(repo.status("b").lastOk > 0)
        a.down = false
        now += 10 * 60_000
        assertEquals("b", repo.fetch(AUCKLAND).source)                 // still resting (30 min)
        assertEquals(1, a.calls)
        now += 25 * 60_000
        assertEquals("a", repo.fetch(AUCKLAND).source)                 // back to the primary
        // "stay on the backup" until the user changes something
        a.down = true; rec = -1
        repo.fetch(AUCKLAND)
        a.down = false
        now += 24 * 3_600_000L
        assertEquals("b", repo.fetch(AUCKLAND).source)
        repo.resetRecovery()
        assertEquals("a", repo.fetch(AUCKLAND).source)
        // "next update": the primary is always tried first
        rec = 0; a.down = true; repo.fetch(AUCKLAND); a.down = false
        assertEquals("a", repo.fetch(AUCKLAND).source)
    }

    @Test fun allFailingSaysWhatEachSaid() {
        val repo = WeatherRepository({ listOf(Named("a", true), Named("b", true)) }, null)
        try { repo.fetch(AUCKLAND); fail() } catch (e: WeatherException) {
            assertTrue(e.message!!.contains("a: a is down") && e.message!!.contains("b: b is down"))
        }
        try { WeatherRepository({ emptyList() }, null).fetch(AUCKLAND); fail() } catch (e: WeatherException) {
            assertTrue(e.message!!.contains("No weather provider"))
        }
    }

    @Test fun managerBuildsTheChainFromSettings() {
        val keys = MemoryKeys()
        var s = Settings(providers = listOf("tomorrow", "visualcrossing", "open-meteo"))
        val pm = ProviderManager(FakeHttp { "" }, keys, { s })
        assertEquals(listOf("tomorrow", "open-meteo"), pm.chain().map { it.id })   // primary kept; keyless backup skipped
        keys.set("visualcrossing", "abcdef123456")
        assertEquals(listOf("tomorrow", "visualcrossing", "open-meteo"), pm.chain().map { it.id })
        assertTrue(pm.provider("tomorrow") === pm.provider("tomorrow"))          // one object per service
        assertNull(pm.provider("nope"))
        s = s.copy(providers = listOf("met.no"))
        assertEquals(listOf("met.no"), pm.chain().map { it.id })
    }

    @Test fun providerSettingsRoundTripAndRejectNonsense() {
        val s = Settings(providers = listOf("weatherapi", "open-meteo"), recovery = 60)
            .withOption("wunderground", "station", "IAUCKL123")
        val back = Settings.fromJson(s.toJson().toString())
        assertEquals(s.providers, back.providers)
        assertEquals(60, back.recovery)
        assertEquals("IAUCKL123", back.option("wunderground", "station"))
        assertFalse("keys never go in the settings", s.toJson().toString().contains("key\""))
        val junk = Settings.fromJson("""{"providers":["x","open-meteo","open-meteo",5],"recovery":7}""")
        assertEquals(listOf("open-meteo"), junk.providers)
        assertEquals(0, junk.recovery)
        assertEquals(DEFAULT_PROVIDERS, Settings.fromJson("""{"providers":[]}""").providers)
        assertEquals(DEFAULT_PROVIDERS, Settings.fromJson("{}").providers)
    }

    @get:org.junit.Rule val tmp = org.junit.rules.TemporaryFolder()

    @Test fun smallAllowancesAreAskedLessOften() {
        val tio = Named("tomorrow")
        var now = 5_000_000L
        val repo = WeatherRepository({ listOf(tio) }, ForecastCache(tmp.root), { 0 }, { now })
        repo.fetch(AUCKLAND); assertEquals(1, tio.calls)
        now += 5 * 60_000
        assertEquals("tomorrow", repo.fetch(AUCKLAND).source); assertEquals(1, tio.calls)     // the saved forecast, no request
        now += 5 * 60_000 + 1
        repo.fetch(AUCKLAND); assertEquals(2, tio.calls)                                       // 10 minutes on: asked again
        val om = Named("open-meteo")
        val repo2 = WeatherRepository({ listOf(om) }, ForecastCache(tmp.newFolder()), { 0 }, { now })
        repo2.fetch(AUCKLAND); repo2.fetch(AUCKLAND); assertEquals(2, om.calls)               // no limit for Open-Meteo
    }
}
