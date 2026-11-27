// 📍 My location's name worldwide, from real OpenStreetMap (Nominatim) replies for 29 places in 21
// countries and one at sea, fetched with the app's own request (zoom 17) on 25 Nov 2026
// (scripts/osm/fetch_world.py). Shane: "suburb identification must work worldwide... Do not
// incorrectly label a city or town as a suburb." Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldNamesTest {
    private val replies: Map<String, String> =
        javaClass.classLoader!!.getResourceAsStream("osm_world.txt")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() }.associate { line -> val f = line.split(" ", limit = 4); f[0] to f[3] }

    private fun name(id: String) = OsmNamer.parse(replies.getValue(id))

    /** id → expected name, the area after it, and whether it's a local area (true) or a town/city fallback (false). */
    private val expected = listOf(
        Triple("nz_east_tamaki", "East Tāmaki|Howick, Auckland|New Zealand", true),
        Triple("nz_waiuku", "Waiuku|Franklin, Auckland|New Zealand", false),
        Triple("nz_clarks_beach", "Clarks Beach|Franklin, Auckland|New Zealand", false),
        Triple("nz_ponsonby", "Freemans Bay|Auckland|New Zealand", true),
        Triple("nz_wellington_cbd", "Wellington Central|Wellington|New Zealand", true),
        Triple("uk_shoreditch", "Shoreditch|Greater London, England|United Kingdom", true),
        Triple("uk_cotswolds_village", "Northleach with Eastington|Northleach, England|United Kingdom", true),
        Triple("fr_paris_11", "11th Arrondissement|Paris, Ile-de-France|France", true),
        Triple("fr_gordes", "Gordes|Vaucluse, Provence-Alpes-Côte d'Azur|France", false),     // the village, not the larger municipality (Apt)
        Triple("de_kreuzberg", "Kreuzberg|Berlin|Germany", true),
        Triple("nl_jordaan", "Centrum|Amsterdam, North Holland|Netherlands", true),
        Triple("us_williamsburg", "Brooklyn|New York|United States", true),
        Triple("us_marfa", "Marfa|Presidio County, Texas|United States", false),
        Triple("us_rural_kansas", "Farhman|Barton County, Kansas|United States", false),
        Triple("ca_annex_toronto", "Annex|Toronto, Ontario|Canada", true),
        Triple("mx_coyoacan", "Coyoacán|Mexico City|Mexico", true),
        Triple("br_copacabana", "Copacabana|Rio de Janeiro|Brazil", true),
        Triple("jp_shibuya", "Udagawachō|Shibuya|Japan", true),                     // a neighbourhood: Japan has no "suburb" here
        Triple("cn_chaoyang", "Chaowai Subdistrict|Chaoyang District|China", true),
        Triple("in_bandra", "Khar|Mumbai, Maharashtra|India", true),
        Triple("tr_kadikoy", "Osmanağa Mahallesi|Kadıköy, Istanbul|Turkey", true),
        Triple("ae_dubai_marina", "Dubai Marina|Dubai Emirate|United Arab Emirates", true),
        Triple("ke_westlands", "Highridge division|Nairobi, Nairobi County|Kenya", true),
        Triple("za_sea_point", "Cape Town|Western Cape|South Africa", false),         // "Cape Town Ward 54" isn't a place: the city
        Triple("au_bondi", "Bondi Beach|Sydney, New South Wales|Australia", true),
        Triple("is_reykjavik", "Miðborg|Reykjavik, Capital Region|Iceland", true),
        Triple("es_gracia_barcelona", "Gràcia|Barcelona, Catalonia|Spain", true),
        Triple("it_trastevere", "Trastevere|Rome, Lazio|Italy", true),               // "Municipio Roma I" skipped: the quarter
        Triple("kr_gangnam", "Yeoksam 1(il)-dong|Seoul|South Korea", true),
    )

    @Test fun everyPlaceGetsItsMostSpecificMeaningfulName() {
        val wrong = mutableListOf<String>()
        for ((id, want, local) in expected) {
            val n = name(id)
            val got = n?.let { "${it.name}|${it.admin1}|${it.country}" }
            if (got != want) wrong += "$id: wanted $want, got $got"
            if (n != null && (n.level == Naming.SUBURB) != local) wrong += "$id: level ${n.level}, wanted ${if (local) "local area" else "town or city"}"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
        assertEquals(29, expected.size); assertEquals(30, replies.size)
    }

    @Test fun aCityOrTownIsNeverPassedOffAsALocalArea() {
        for ((id, _, _) in expected) {
            val n = name(id)!!
            if (n.level == Naming.SUBURB) {
                val city = org.json.JSONObject(replies.getValue(id)).getJSONObject("address").optString("city")
                assertFalse("$id: ${n.name} is the city", n.name.equals(city, ignoreCase = true))
            }
        }
        // Paris files the whole city as its own district: never used as the local area
        assertEquals("11th Arrondissement", name("fr_paris_11")!!.name)
        // a reply with only the city: the city, marked as a town or city
        val only = OsmNamer.parse("""{"address":{"city":"Paris","city_district":"Paris","country":"France"}}""")!!
        assertEquals("Paris", only.name); assertEquals(Naming.TOWN, only.level)
    }

    @Test fun theSeaAndEmptyRepliesHaveNoName() {
        assertNull(name("sea_tasman"))
        assertNull(OsmNamer.parse("""{"address":{"country":"New Zealand"}}"""))
        assertEquals(Naming.REGION, OsmNamer.parse("""{"address":{"county":"Barton County","state":"Kansas"}}""")!!.level)
    }

    @Test fun administrativeUnitsArentPlaces() {
        for (s in listOf("Cape Town Ward 54", "Municipio Roma I", "Mumbai Zone 3", "Ward 7")) assertTrue(s, OsmNamer.administrative(s))
        for (s in listOf("Edwardstown", "Howard", "Wellington Central", "11th Arrondissement", "Yeoksam 1(il)-dong", "Woodward Park")) assertFalse(s, OsmNamer.administrative(s))
    }
}
