// Regression tests for Shane's 0.7.0 phone test (26 Nov 2026): "When I play the 24-hour weather
// animation, the Moon flickers." The forecast sky (drawn on the main thread) must keep showing a
// real Moon picture while the next one is made in the background; it must never fall back to the
// plain drawn Moon once a real one has been shown. The same for the clouds. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui.scene

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaybackFlickerTest {
    private fun cond(hour: Int) = Conditions(moonPhaseAngle = 60.0 + hour * 0.5, moonLimb = 250.0 + hour * 0.6, moonRotation = 20.0 + hour * 7.0, moonUp = true)

    @Test fun theMoonNeverFlashesBackToThePlainDrawingDuringPlayback() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        MoonRender.init(app.assets)
        assertTrue("runs on the main thread, like the sky", Looper.myLooper() == Looper.getMainLooper())
        // the sky's first frames: wait (as the screen would) until the real Moon is there
        val end = System.currentTimeMillis() + 60_000
        var first = defaultMoonImage(104, cond(0))
        while (first == null && System.currentTimeMillis() < end) { Thread.sleep(20); first = defaultMoonImage(104, cond(0)) }
        assertNotNull("the real Moon appears", first)
        // playback: an hour a second, about 30 frames a second, so each frame asks for a new Moon
        var missing = 0
        for (frame in 0 until 24 * 30) {
            val hour = frame / 30
            val img = defaultMoonImage(104, cond(hour).copy(moonRotation = 20.0 + frame * 0.25))
            if (img == null) missing++
        }
        assertEquals("frames drawn with the plain Moon instead of the real one (each is a visible flash)", 0, missing)
    }

    @Test fun cloudsNeverDisappearWhileTheirNewPictureIsMade() {
        // a cloud already shown, then darker as evening comes during playback: never skipped
        val first = cloudSpriteOrLater(220, 120, 0.2, 5)
            ?: run { Thread.sleep(3000); cloudSpriteOrLater(220, 120, 0.2, 5) }
        assertNotNull(first)
        var missing = 0
        for (i in 0 until 40) if (cloudSpriteOrLater(220, 120, 0.2 + i * 0.02, 5) == null) missing++
        assertEquals("frames with the cloud missing", 0, missing)
        // a cloud never seen before is drawn at once, not left out (no clouds popping in)
        assertNotNull(cloudSpriteOrLater(300, 140, 0.4, 77))
    }
}
