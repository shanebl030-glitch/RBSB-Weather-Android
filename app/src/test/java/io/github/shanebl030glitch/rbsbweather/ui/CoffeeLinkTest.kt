// The "buy me a coffee" link in About, as the desktop's test_support: PayPal over https, Shane's
// public Merchant ID only (no email address), NZ$1.47. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI
import java.net.URLDecoder

class CoffeeLinkTest {
    @Test fun linkIsTheAgreedPayPalLink() {
        val u = URI(COFFEE_URL)
        assertEquals("https", u.scheme); assertEquals("www.paypal.com", u.host)
        val q = u.rawQuery.split("&").associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        assertEquals("M4EDN77T6EEPJ", q["business"]); assertEquals("1.47", q["amount"]); assertEquals("NZD", q["currency_code"])
        assertEquals("Coffee for RBSB-Weather", q["item_name"])
        assertFalse("no email address in a public link", "@" in COFFEE_URL)
        val src = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val about = File(src, "java/io/github/shanebl030glitch/rbsbweather/ui/Screens.kt").readText()
        assertTrue(about.contains("consider buying me a coffee for NZ\$1.47"))
    }
}
