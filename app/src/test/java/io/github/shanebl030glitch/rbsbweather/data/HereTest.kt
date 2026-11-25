// 📍 My location (0.7): when a new position counts as a new place, naming, and the settings it lives in.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val NOW = 1_790_000_000_000L
private val NAMES = PlaceNamer { lat, _ -> if (lat < -40) Naming("Te Aro", "Wellington", "New Zealand") else Naming("Ponsonby", "Auckland", "New Zealand") }

class HereTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun firstFixMakesMyLocationRoundedAndNamed() {
        val p = Here.next(null, Fix(-36.848531, 174.763491, 30f, NOW), NOW, NAMES)!!
        assertTrue(p.current)
        assertEquals("Ponsonby", p.name); assertEquals("Auckland", p.admin1); assertEquals("New Zealand", p.country)
        assertEquals(-36.849, p.latitude, 0.0); assertEquals(174.763, p.longitude, 0.0)    // ~100 m, nothing finer kept
        assertEquals(Place.HERE_KEY, p.key)
        assertEquals("📍 Ponsonby", p.label)
    }

    @Test fun smallMovesChangeNothingAndDontAskForAName() {
        var asked = 0
        val namer = PlaceNamer { _, _ -> asked++; Naming("X") }
        val old = Here.next(null, Fix(-36.8485, 174.7635), NOW, namer)!!
        assertEquals(1, asked)
        assertNull(Here.next(old, Fix(-36.8530, 174.7635), NOW, namer))      // ~500 m
        assertEquals(1, asked)
        assertNotNull(Here.next(old, Fix(-36.8620, 174.7635), NOW, namer))   // ~1.5 km: a new suburb
        assertEquals(2, asked)
    }

    @Test fun travellingToAnotherCityOrCountryMovesIt() {
        val akl = Here.next(null, Fix(-36.8485, 174.7635), NOW, NAMES)!!
        val wlg = Here.next(akl, Fix(-41.2866, 174.7756), NOW, NAMES)!!
        assertEquals("Te Aro", wlg.name)
        val syd = Here.next(wlg, Fix(-33.8688, 151.2093), NOW) { _, _ -> Naming("Sydney", "New South Wales", "Australia") }!!
        assertEquals("Australia", syd.country)
    }

    @Test fun unusableFixesAreIgnored() {
        assertNull(Here.next(null, Fix(Double.NaN, 1.0), NOW, NAMES))
        assertNull(Here.next(null, Fix(95.0, 1.0), NOW, NAMES))
        assertNull(Here.next(null, Fix(-36.8, 174.7, timeMs = NOW - 31 * 60_000L), NOW, NAMES))   // too old
        assertNull(Here.next(null, Fix(-36.8, 174.7, accuracyM = 9_000f), NOW, NAMES))           // too vague
        assertNotNull(Here.next(null, Fix(-36.8, 174.7, accuracyM = 2_000f, timeMs = NOW - 60_000L), NOW, NAMES))
    }

    @Test fun withoutANameItKeepsTheOldNameNearbyElseMyLocation() {
        val none = PlaceNamer { _, _ -> null }
        val boom = PlaceNamer { _, _ -> throw java.io.IOException("offline") }
        val old = Here.next(null, Fix(-36.8485, 174.7635), NOW, NAMES)!!
        assertEquals("Ponsonby", Here.next(old, Fix(-36.8700, 174.7635), NOW, none)!!.name)       // 2.4 km
        assertEquals("Ponsonby", Here.next(old, Fix(-36.8700, 174.7635), NOW, boom)!!.name)
        assertEquals(Here.UNNAMED, Here.next(old, Fix(-41.2866, 174.7756), NOW, none)!!.name)     // far away
        assertEquals(Here.UNNAMED, Here.next(null, Fix(-41.2866, 174.7756), NOW, PlaceNamer { _, _ -> Naming("  ") })!!.name)
    }

    @Test fun anUnnamedMyLocationIsNamedLaterWithoutMoving() {
        val old = Here.next(null, Fix(-36.8485, 174.7635), NOW) { _, _ -> null }!!
        assertEquals(Here.UNNAMED, old.name)
        assertNull(Here.next(old, Fix(-36.8490, 174.7635), NOW) { _, _ -> null })            // still no name: nothing changes
        val named = Here.next(old, Fix(-36.8490, 174.7635), NOW, NAMES)!!
        assertEquals("Ponsonby", named.name); assertEquals("New Zealand", named.country)
        assertEquals(old.latitude, named.latitude, 0.0)                                          // same spot, no new download needed
        assertNull(Here.next(named, Fix(-36.8490, 174.7635), NOW, NAMES))                      // named: small moves change nothing again
    }

    @Test fun openStreetMapNamesTheSuburbCityAndCountry() {
        var asked = ""
        val body = """{"place_id":1,"lat":"-36.858","lon":"174.746","display_name":"Ponsonby, Auckland, New Zealand",
            "address":{"road":"Ponsonby Road","suburb":"Ponsonby","city":"Auckland","state":"Auckland","country":"New Zealand","country_code":"nz"}}"""
        val n = OsmNamer(HttpClient { url, h -> asked = url + " " + h["Accept-Language"]; body }, language = { "en-NZ" }).name(-36.85812, 174.74633)!!
        assertEquals(Naming("Ponsonby", "Auckland", "New Zealand"), n)
        assertTrue(asked.startsWith("https://nominatim.openstreetmap.org/reverse?"))
        assertTrue("lat=-36.858" in asked && "lon=174.746" in asked && "zoom=14" in asked && asked.endsWith("en-NZ"))   // only the rounded spot
        // a country town: no suburb
        assertEquals(Naming("Taupō", "Waikato", "New Zealand"), OsmNamer.parse("""{"address":{"town":"Taupō","state":"Waikato","country":"New Zealand"}}"""))
        // a city with no suburb: the city, and its region
        assertEquals(Naming("Wellington", "Wellington", "New Zealand").copy(admin1 = ""), OsmNamer.parse("""{"address":{"city":"Wellington","country":"New Zealand"}}"""))
        // the sea, errors and junk: no name
        assertNull(OsmNamer.parse("""{"error":"Unable to geocode"}"""))
        assertNull(OsmNamer.parse("""{"address":{"country":""}}"""))
        assertNull(OsmNamer(HttpClient { _, _ -> throw WeatherException("No internet connection.") }).name(-36.8, 174.7))
        assertNull(OsmNamer(HttpClient { _, _ -> "<html>" }).name(-36.8, 174.7))
    }

    @Test fun settingsKeepMyLocationAndTheWidgetChoice() {
        val here = Here.next(null, Fix(-41.2866, 174.7756), NOW, NAMES)!!
        val s = Settings(places = listOf(AUCKLAND), selected = AUCKLAND.key, followLocation = true).withHere(here)
        assertEquals(listOf(Place.HERE_KEY, AUCKLAND.key), s.places.map { it.key })      // first when new
        assertEquals(AUCKLAND.key, s.selected)                                             // what you were looking at stays
        assertEquals(here.key, s.widgetPlace()!!.key)                                       // widgets follow you
        assertEquals(AUCKLAND.key, s.copy(widgetHere = false).widgetPlace()!!.key)
        assertEquals(AUCKLAND.key, s.copy(followLocation = false).widgetPlace()!!.key)
        val back = Settings.fromJson(s.toJson().toString())
        assertTrue(back.followLocation); assertTrue(back.widgetHere)
        assertEquals(s.places, back.places)
        assertTrue(back.places[0].current)
        // moving keeps its spot in the list
        val moved = s.moved(AUCKLAND.key, -1).withHere(here.copy(name = "Kelburn", latitude = -41.29))
        assertEquals(listOf(AUCKLAND.key, Place.HERE_KEY), moved.places.map { it.key })
        assertEquals("Kelburn", moved.here()!!.name)
        assertEquals(listOf(AUCKLAND.key), moved.withoutHere().places.map { it.key })
        // old settings files (before 0.7) still read
        val old = Settings.fromJson(Settings(places = listOf(AUCKLAND)).toJson().apply { remove("follow_location"); remove("widget_here") }.toString())
        assertFalse(old.followLocation); assertTrue(old.widgetHere); assertNull(old.here())
    }

    @Test fun savedForecastForSomewhereElseIsntShownForHere() {
        val cache = ForecastCache(tmp.root)
        val akl = Here.next(null, Fix(-36.8485, 174.7635), NOW, NAMES)!!
        val fc = parseForecast(javaClass.classLoader!!.getResourceAsStream("openmeteo_forecast.json")!!.bufferedReader().readText(), akl)
        cache.save(fc)
        assertNotNull(cache.load(akl))
        val wlg = Here.next(akl, Fix(-41.2866, 174.7756), NOW, NAMES)!!
        assertNull(cache.load(wlg))                                        // Auckland's weather isn't Wellington's
        assertTrue(tmp.root.listFiles()!!.any { it.name == "forecast-here.json" })
        cache.cleanOrphans(listOf(wlg))
        assertTrue(tmp.root.listFiles()!!.any { it.name == "forecast-here.json" })    // the here file stays while it's saved
    }
}
