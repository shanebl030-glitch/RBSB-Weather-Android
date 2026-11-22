// The app's behaviour without the screens: adding, selecting, refreshing, backup, errors, search.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.ForecastCache
import io.github.shanebl030glitch.rbsbweather.data.HttpClient
import io.github.shanebl030glitch.rbsbweather.data.OpenMeteo
import io.github.shanebl030glitch.rbsbweather.data.OpenMeteoProvider
import io.github.shanebl030glitch.rbsbweather.data.Place
import io.github.shanebl030glitch.rbsbweather.data.Provider
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.TempUnit
import io.github.shanebl030glitch.rbsbweather.data.WeatherException
import io.github.shanebl030glitch.rbsbweather.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

fun resource(name: String): String =
    AppModelTest::class.java.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

/** A provider that can be switched off, and counts its calls. */
class FakeProvider(override val name: String = "Open-Meteo") : Provider {
    var down = false
    var calls = 0
    private val real = OpenMeteoProvider(OpenMeteo({ _, _ -> resource("openmeteo_forecast.json") }))
    override fun forecast(place: Place): Forecast {
        calls++
        if (down) throw WeatherException("No internet connection.")
        return real.forecast(place)
    }
}

val SEARCH = OpenMeteo(HttpClient { url, _ ->
    if ("name=zz" in url) throw WeatherException("No internet connection.")
    resource("geocode.json")
})

@OptIn(ExperimentalCoroutinesApi::class)
class AppModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun model(store: MemoryStore = MemoryStore(), p: FakeProvider = FakeProvider(), now: () -> Long = System::currentTimeMillis,
                      scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)): AppModel =
        AppModel(store, WeatherRepository(listOf(p), ForecastCache(tmp.root)), SEARCH, scope, Dispatchers.Unconfined, now)

    @Test fun addingAPlaceSavesItAndFetchesItsWeather() {
        val store = MemoryStore()
        val p = FakeProvider()
        val m = model(store, p)
        m.add(AUCKLAND)
        val s = m.state.value
        assertEquals(listOf("Auckland"), s.settings.places.map { it.name })
        assertEquals(Screen.HOME, s.screen)
        assertEquals(16.6, s.forecasts[AUCKLAND.key]!!.current.temperature!!, 1e-9)
        assertTrue(s.loading.isEmpty())
        assertEquals(1, p.calls)
        assertEquals(listOf("Auckland"), Settings.fromJson(store.text).places.map { it.name })   // saved
    }

    @Test fun refreshOnlyWhenDue() {
        var now = System.currentTimeMillis()
        val p = FakeProvider()
        val m = model(p = p, now = { now })
        m.add(AUCKLAND)
        val fetched = m.state.value.forecasts[AUCKLAND.key]!!.fetchedAt
        now = fetched + 10 * 60_000                       // 10 minutes later: not due (default 30)
        m.refresh()
        assertEquals(1, p.calls)
        now = fetched + 31 * 60_000
        m.refresh()
        assertEquals(2, p.calls)
        m.refresh(force = true)
        assertEquals(3, p.calls)
        m.setRefreshMinutes(0)                            // "only when I pull down"
        now += 10 * 3_600_000
        m.refresh()
        assertEquals(3, p.calls)
    }

    @Test fun failureKeepsTheSavedForecastAndSaysWhy() {
        val p = FakeProvider()
        val m = model(p = p)
        m.add(AUCKLAND)
        p.down = true
        m.refresh(force = true)
        val s = m.state.value
        assertEquals("No internet connection.", s.errors[AUCKLAND.key])
        assertEquals(16.6, s.forecasts[AUCKLAND.key]!!.current.temperature!!, 1e-9)
        val (text, warn) = statusText(s.forecasts[AUCKLAND.key], false, s.errors[AUCKLAND.key], s.settings.units, System.currentTimeMillis())
        assertTrue(warn)
        assertTrue(text.startsWith("⚠ Couldn't update: No internet connection."))
        p.down = false
        m.refresh(force = true)
        assertNull(m.state.value.errors[AUCKLAND.key])
    }

    @Test fun savedForecastsShowAtStartEvenOffline() {
        val store = MemoryStore()
        val p = FakeProvider()
        model(store, p).add(AUCKLAND)
        p.down = true
        val m2 = model(store, p)
        val fc = m2.state.value.forecasts[AUCKLAND.key]!!
        assertTrue(fc.fromCache)
        m2.refresh()                                      // a saved copy is always refreshed when possible
        assertEquals("No internet connection.", m2.state.value.errors[AUCKLAND.key])
    }

    @Test fun removeAndMoveAndUnits() {
        val m = model()
        val b = Place("Hamilton", -37.78, 175.28, "New Zealand")
        m.add(AUCKLAND); m.add(b)
        assertEquals(b.key, m.state.value.settings.selected)
        m.move(b.key, -1)
        assertEquals(listOf("Hamilton", "Auckland"), m.state.value.settings.places.map { it.name })
        m.remove(b.key)
        assertEquals("Removed Hamilton", m.state.value.message)
        assertEquals(AUCKLAND.key, m.state.value.settings.selected)
        assertFalse(b.key in m.state.value.forecasts)
        m.setUnits(m.state.value.settings.units.copy(temp = TempUnit.F))
        assertEquals(TempUnit.F, m.state.value.settings.units.temp)
    }

    @Test fun aResultArrivingForARemovedPlaceIsIgnored() {
        val scope = TestScope(StandardTestDispatcher())
        val m = AppModel(MemoryStore(), WeatherRepository(listOf(FakeProvider()), null), SEARCH, scope,
            StandardTestDispatcher(scope.testScheduler))
        m.add(AUCKLAND)
        m.remove(AUCKLAND.key)                            // before the download finishes
        scope.advanceUntilIdle()
        assertTrue(m.state.value.forecasts.isEmpty())
        assertTrue(m.state.value.loading.isEmpty())
    }

    @Test fun search() {
        val scope = TestScope(StandardTestDispatcher())
        val m = AppModel(MemoryStore(), WeatherRepository(listOf(FakeProvider()), null), SEARCH, scope,
            StandardTestDispatcher(scope.testScheduler))
        m.go(Screen.SEARCH)
        m.searchFor("A")
        assertEquals(SearchState.Idle, m.state.value.search)
        m.searchFor("Au")
        m.searchFor("Auc")                                 // typing on: only the last search runs
        scope.advanceUntilIdle()
        val r = m.state.value.search as SearchState.Results
        assertEquals(4, r.places.size)
        m.searchFor("zz")
        scope.advanceUntilIdle()
        assertEquals(SearchState.Failed("No internet connection."), m.state.value.search)
        assertTrue(m.back())
        assertFalse(m.back())
    }

    // ------------------------------------------------------- weather providers
    private fun providerModel(keys: io.github.shanebl030glitch.rbsbweather.data.MemoryKeys = io.github.shanebl030glitch.rbsbweather.data.MemoryKeys(),
                              store: MemoryStore = MemoryStore()): AppModel {
        lateinit var m: AppModel
        val pm = io.github.shanebl030glitch.rbsbweather.data.ProviderManager(
            HttpClient { url, _ -> if ("weatherapi" in url) resource("wapi_forecast.json") else resource("openmeteo_forecast.json") },
            keys, { m.settingsNow })
        m = AppModel(store, WeatherRepository({ pm.chain() }, null), SEARCH, CoroutineScope(Dispatchers.Unconfined),
            Dispatchers.Unconfined, providers = pm)
        return m
    }

    @Test fun choosingPrimaryAndBackups() {
        val store = MemoryStore()
        val m = providerModel(store = store)
        assertEquals(listOf("open-meteo", "met.no"), m.state.value.settings.providers)
        m.makePrimary("met.no")
        assertEquals(listOf("met.no", "open-meteo"), m.state.value.settings.providers)
        m.setBackup("weatherapi", true)
        m.setBackup("tomorrow", true)
        m.moveProvider("tomorrow", -1)
        assertEquals(listOf("met.no", "open-meteo", "tomorrow", "weatherapi"), m.state.value.settings.providers)
        m.moveProvider("open-meteo", -5)                                   // can't pass the primary
        assertEquals("met.no", m.state.value.settings.providers[0])
        m.setBackup("met.no", false)                                       // the primary can't be taken out
        assertEquals("met.no", m.state.value.settings.providers[0])
        m.setBackup("tomorrow", false)
        assertEquals(listOf("met.no", "open-meteo", "weatherapi"), m.state.value.settings.providers)
        m.setRecovery(60); m.setRecovery(7)
        assertEquals(60, m.state.value.settings.recovery)
        assertEquals(listOf("met.no", "open-meteo", "weatherapi"), Settings.fromJson(store.text).providers)  // saved
    }

    @Test fun keysAreSavedApartAndUsed() {
        val keys = io.github.shanebl030glitch.rbsbweather.data.MemoryKeys()
        val store = MemoryStore()
        val m = providerModel(keys, store)
        m.makePrimary("weatherapi")
        m.add(AUCKLAND)
        assertTrue(m.state.value.errors[AUCKLAND.key]!!.contains("needs an API key") ||
            m.state.value.forecasts[AUCKLAND.key]?.source == "Open-Meteo")       // primary without a key → backup
        assertEquals("Open-Meteo", m.state.value.forecasts[AUCKLAND.key]?.source)
        m.setKey("weatherapi", "bad key!")
        assertTrue(m.state.value.message!!.contains("doesn't look like an API key"))
        assertNull(keys.get("weatherapi"))
        m.setKey("weatherapi", " abcdef123456 ")
        assertEquals("abcdef123456", keys.get("weatherapi"))
        assertTrue("weatherapi" in m.state.value.keysSaved)
        assertFalse("the key never goes into the settings", store.text!!.contains("abcdef123456"))
        m.refresh(force = true)
        assertEquals("WeatherAPI.com", m.state.value.forecasts[AUCKLAND.key]?.source)
        m.testProvider("weatherapi")
        assertTrue(m.state.value.providerTests["weatherapi"]!!.startsWith("Working"))
        m.setKey("weatherapi", "")
        assertNull(keys.get("weatherapi"))
        assertFalse("weatherapi" in m.state.value.keysSaved)
        m.testProvider("weatherapi")
        assertTrue(m.state.value.providerTests["weatherapi"]!!.startsWith("Not working"))
    }

    @Test fun stationOptionIsChecked() {
        val m = providerModel()
        m.setProviderOption("wunderground", "station", "IAUCKL123")
        assertEquals("IAUCKL123", m.state.value.settings.option("wunderground", "station"))
        m.setProviderOption("wunderground", "station", "no spaces!")
        assertEquals("IAUCKL123", m.state.value.settings.option("wunderground", "station"))
        assertTrue(m.state.value.message!!.contains("isn't a valid"))
        m.setProviderOption("wunderground", "station", "")
        assertNull(m.state.value.settings.option("wunderground", "station"))
    }

    @Test fun backFromProvidersGoesToSettings() {
        val m = providerModel()
        m.go(Screen.PROVIDERS)
        assertTrue(m.back())
        assertEquals(Screen.SETTINGS, m.state.value.screen)
        assertTrue(m.back())
        assertEquals(Screen.HOME, m.state.value.screen)
    }
}
