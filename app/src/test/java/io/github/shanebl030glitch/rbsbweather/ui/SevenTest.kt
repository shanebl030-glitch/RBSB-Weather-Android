// 0.7 📍 My location end to end without a phone: the app model following you from Auckland to
// Wellington, the background check, the widget's place, and the Settings section (with Android's
// location permission granted and refused). Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import android.Manifest
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.shanebl030glitch.rbsbweather.AppGraph
import io.github.shanebl030glitch.rbsbweather.background.ChecksWorker
import io.github.shanebl030glitch.rbsbweather.background.LocationSource
import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.Fix
import io.github.shanebl030glitch.rbsbweather.data.ForecastCache
import io.github.shanebl030glitch.rbsbweather.data.Naming
import io.github.shanebl030glitch.rbsbweather.data.Place
import io.github.shanebl030glitch.rbsbweather.data.PlaceNamer
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.WeatherRepository
import io.github.shanebl030glitch.rbsbweather.widget.widgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val AKL_FIX = Fix(-36.8485, 174.7635)
private val WLG_FIX = Fix(-41.2866, 174.7756)
private val NAMER = PlaceNamer { lat, _ -> if (lat < -40) Naming("Wellington Central", "Wellington", "New Zealand") else Naming("Auckland Central", "Auckland", "New Zealand") }

/** Where the pretend phone is. */
class FakeGps(var fix: Fix? = AKL_FIX) : LocationSource { var asked = 0; override suspend fun current(): Fix? { asked++; return fix } }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SevenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val provider = FakeProvider()
    private var widgetChanges = 0
    private fun model(s: Settings = Settings(animations = false), store: MemoryStore = MemoryStore(s.toJson().toString())) =
        AppModel(store, WeatherRepository(listOf(provider), ForecastCache(tmp.root)), SEARCH, CoroutineScope(Dispatchers.Unconfined),
            Dispatchers.Unconfined, onWidgetPlace = { widgetChanges++ })

    private fun AppModel.go(gps: FakeGps) = locate({ gps.current() }, NAMER)

    @Test fun followsYouFromAucklandToWellington() {
        val store = MemoryStore(Settings(animations = false, places = listOf(AUCKLAND)).toJson().toString())
        val m = model(store = store)
        val gps = FakeGps()
        m.go(gps)
        assertEquals(0, gps.asked)                                   // off by default: never looks
        m.setFollowLocation(true)
        m.go(gps)
        var s = m.state.value
        val here = s.settings.here()!!
        assertEquals("Auckland Central", here.name)
        assertEquals(listOf(Place.HERE_KEY, AUCKLAND.key), s.settings.places.map { it.key })
        assertEquals(here.latitude, s.forecasts[Place.HERE_KEY]!!.place.latitude, 0.0)
        assertEquals(here.key, s.settings.widgetPlace()!!.key)
        assertTrue(Settings.fromJson(store.text).here() != null)    // saved
        val calls = provider.calls

        gps.fix = AKL_FIX.copy(lat = AKL_FIX.lat + 0.003)            // a few hundred metres: same place
        m.go(gps)
        assertEquals("Auckland Central", m.state.value.settings.here()!!.name)

        gps.fix = WLG_FIX                                             // drove to Wellington
        m.go(gps)
        s = m.state.value
        assertEquals("Wellington Central", s.settings.here()!!.name)
        assertEquals(-41.287, s.forecasts[Place.HERE_KEY]!!.place.latitude, 0.0)   // the new spot's weather
        assertTrue(provider.calls > calls)
        assertEquals("📍 Now at Wellington Central", s.message)
        assertTrue(widgetChanges > 0)
        assertEquals(2, s.settings.places.size)                       // still one My location
        assertNull(s.hereNote)
    }

    @Test fun noFixSaysWhyAndKeepsTheLastPlace() {
        val m = model(Settings(animations = false, followLocation = true))
        val gps = FakeGps()
        m.go(gps)
        gps.fix = null
        m.go(gps)
        assertNotNull(m.state.value.hereNote)
        assertEquals("Auckland Central", m.state.value.settings.here()!!.name)
        assertFalse(m.state.value.locating)
        gps.fix = AKL_FIX
        m.go(gps)
        assertNull(m.state.value.hereNote)
    }

    @Test fun turningOffOrRemovingItTakesMyLocationAway() {
        val m = model(Settings(animations = false, places = listOf(AUCKLAND), followLocation = true))
        m.go(FakeGps())
        assertNotNull(m.state.value.settings.here())
        m.setFollowLocation(false)
        assertNull(m.state.value.settings.here())
        assertNull(m.state.value.forecasts[Place.HERE_KEY])
        assertEquals(listOf(AUCKLAND.key), m.state.value.settings.places.map { it.key })
        assertFalse(tmp.root.listFiles()!!.any { it.name == "forecast-here.json" })
        m.setFollowLocation(true); m.go(FakeGps())
        m.remove(Place.HERE_KEY)                                       // the bin on My places
        assertFalse(m.state.value.settings.followLocation)
        assertNull(m.state.value.settings.here())
    }

    @Test fun widgetShowsMyLocationOrThePlaceOnScreen() {
        val m = model(Settings(animations = false, places = listOf(AUCKLAND), followLocation = true))
        m.go(FakeGps(WLG_FIX))
        val s = m.state.value.settings
        assertEquals("📍 Wellington Central", widgetData(s, m.state.value.forecasts[Place.HERE_KEY]).place)
        m.setWidgetHere(false)
        assertEquals("Auckland", widgetData(m.state.value.settings, null).place)
    }

    @Test fun backgroundCheckMovesItWhileTheAppIsClosedOrHandsItToTheOpenApp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val g = AppGraph.get(ctx)
        g.liveSettings = null; g.onHere = null
        g.store.save(Settings(places = listOf(AUCKLAND), followLocation = true).toJson().toString())
        val fetched = mutableListOf<Place>()
        val gps = FakeGps(WLG_FIX)
        assertNull(ChecksWorker.followHere(ctx, g, gps, NAMER, allowed = false, fetch = { fetched += it }))   // no "all the time": nothing
        assertEquals(0, gps.asked)
        val p = ChecksWorker.followHere(ctx, g, gps, NAMER, allowed = true, fetch = { fetched += it })!!
        assertEquals("Wellington Central", g.settings().here()!!.name)
        assertEquals(listOf(p), fetched)
        assertNull(ChecksWorker.followHere(ctx, g, gps, NAMER, allowed = true, fetch = { fetched += it }))     // hasn't moved
        // the app is open: it gets the new place instead
        val handed = mutableListOf<Place>()
        g.onHere = { handed += it }
        gps.fix = AKL_FIX
        ChecksWorker.followHere(ctx, g, gps, NAMER, allowed = true, fetch = { fetched += it })
        assertEquals("Auckland Central", handed.single().name)
        assertEquals(1, fetched.size)
        g.onHere = null
        // turned off: never looks
        g.store.save(Settings(places = listOf(AUCKLAND)).toJson().toString())
        val asked = gps.asked
        assertNull(ChecksWorker.followHere(ctx, g, gps, NAMER, allowed = true, fetch = { fetched += it }))
        assertEquals(asked, gps.asked)
    }

    @Test fun theNewestFixWinsThenTheMostAccurate() {
        val old = Fix(-36.8, 174.7, 20f, 1_000_000L, "fused")            // from before you moved
        val newer = Fix(-41.2, 174.7, 1500f, 1_000_000L + 10 * 60_000L, "gps")
        val sameMinuteBetter = Fix(-41.2, 174.7, 15f, 1_000_000L + 10 * 60_000L + 5_000L, "network")
        val pick = io.github.shanebl030glitch.rbsbweather.background.LocationFinder::best
        assertEquals("gps", pick(listOf(old, newer))!!.provider)
        assertEquals("network", pick(listOf(old, newer, sameMinuteBetter))!!.provider)
        assertNull(pick(emptyList()))
    }

    private fun waitFor(tag: String) = compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun settingsSectionWithPermissionGranted() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val m = model(Settings(animations = false, places = listOf(AUCKLAND)))
        val gps = FakeGps(WLG_FIX)
        compose.setContent { val st = m.state.collectAsStateValue(); MyLocationSection(st, m, gps, NAMER) }
        compose.onNodeWithTag("followSwitch").assertIsOff()
        compose.onNodeWithTag("followSwitch").performClick()
        waitFor("hereStatus")
        compose.waitUntil(10_000) { m.state.value.settings.here() != null }
        compose.onNodeWithTag("followSwitch").assertIsOn()
        compose.onNodeWithText("Now: Wellington Central, Wellington, New Zealand").assertExists()
        compose.onNodeWithText("Using your approximate location (good to a few kilometres), as you chose.").assertExists()
        compose.onNodeWithTag("widgetHereSwitch").assertIsOn()
        compose.onNodeWithTag("backgroundOff").assertExists()
        gps.fix = AKL_FIX
        compose.onNodeWithTag("locateNow").performClick()
        compose.waitUntil(10_000) { m.state.value.settings.here()?.name == "Auckland Central" }
        compose.onNodeWithTag("widgetHereSwitch").performClick()
        compose.waitUntil(5_000) { !m.state.value.settings.widgetHere }
        compose.onNodeWithTag("followSwitch").performClick()
        compose.waitUntil(5_000) { !m.state.value.settings.followLocation }
        assertNull(m.state.value.settings.here())
    }

    @Test fun settingsScreenHasTheSection() {
        val m = model()
        compose.setContent { SettingsScreen(m.state.collectAsStateValue(), m) }
        compose.onNodeWithTag("followSwitch").assertIsOff()
        compose.onNodeWithText("📍 Use my location").assertExists()
    }

    @Test fun backgroundAllowedShowsOn() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val m = model(Settings(animations = false, followLocation = true))
        compose.setContent { MyLocationSection(m.state.collectAsStateValue(), m, FakeGps(), NAMER) }
        compose.onNodeWithTag("backgroundOn").assertExists()
        compose.onNodeWithText("Using your approximate location (good to a few kilometres), as you chose.").assertDoesNotExist()
    }

    @Test fun myPlacesAndHomeShowThePin() {
        val m = model(Settings(animations = false, places = listOf(AUCKLAND), followLocation = true))
        m.go(FakeGps(WLG_FIX))
        compose.setContent { PlacesScreen(m.state.collectAsStateValue(), m) }
        compose.onNodeWithText("📍 Wellington Central").assertExists()
        compose.onNodeWithTag("remove-Wellington Central").performClick()
        compose.onNodeWithText("Stop using your location?").assertExists()
        compose.onNodeWithTag("confirmRemove").performClick()
        compose.waitUntil(5_000) { !m.state.value.settings.followLocation }
    }
}

@androidx.compose.runtime.Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateValue(): T {
    val v by collectAsState()
    return v
}
