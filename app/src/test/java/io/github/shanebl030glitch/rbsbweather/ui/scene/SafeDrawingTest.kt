// The 0.5.0 globe crash ("ending radius must be > 0") can't come back: real Android refuses a radial
// gradient with radius 0, which the test environment didn't check. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SafeDrawingTest {
    @Test fun radiusIsNeverZero() {
        for (bad in listOf(0f, -3f, Float.NaN, 0.1f)) assertEquals(MIN_GRADIENT_RADIUS, safeRadius(bad))
        assertEquals(12f, safeRadius(12f))
        assertTrue(MIN_GRADIENT_RADIUS > 0f)
    }

    /** No drawing code calls Brush.radialGradient with a radius of its own: it goes through safeRadial. */
    @Test fun allRadialGradientsAreSafe() {
        val src = File("src/main/java").takeIf { it.isDirectory } ?: File("app/src/main/java")
        val offenders = src.walkTopDown().filter { it.name.endsWith(".kt") && it.name != "SafeBrush.kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                // a background brush with no radius (it fills its own size) is fine
                if ("Brush.radialGradient(" in line && "radialGradient(listOf(Color(0xFF1B2440)" !in line) "${f.name}:${i + 1}" else null
            }
        }.toList()
        assertTrue("call safeRadial instead: $offenders", offenders.isEmpty())
        assertTrue(src.walkTopDown().any { it.name == "EarthScreens.kt" && "safeRadial(" in it.readText() })
    }
}
