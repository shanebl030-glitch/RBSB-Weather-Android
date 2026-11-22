// The widget loads the way a launcher loads it: every view in its layouts must be one Android
// allows in widgets (RemoteViews), or the launcher says "Couldn't add widget" / "Problem loading
// widget". Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.widget

import android.content.res.XmlResourceParser
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.ViewFlipper
import io.github.shanebl030glitch.rbsbweather.R
import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.ui.scene.sceneFc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetLayoutTest {
    private val ctx = RuntimeEnvironment.getApplication()

    /** The views Android lets widgets use (RemoteViews), for Android 10 and newer. */
    private val allowed = setOf("FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout", "AnalogClock", "Button",
        "Chronometer", "ImageButton", "ImageView", "ProgressBar", "TextView", "ViewFlipper", "ListView", "GridView",
        "StackView", "AdapterViewFlipper", "ViewStub", "TextClock", "merge")

    private fun tags(layout: Int): List<String> {
        val p: XmlResourceParser = ctx.resources.getLayout(layout)
        val out = mutableListOf<String>()
        while (p.next() != XmlResourceParser.END_DOCUMENT) if (p.eventType == XmlResourceParser.START_TAG) out += p.name
        return out
    }

    @Test fun onlyWidgetViewsAreUsed() {
        for (layout in listOf(R.layout.widget_scene, R.layout.widget_frame)) {
            val bad = tags(layout).filter { it !in allowed }
            assertTrue("not allowed in a widget: $bad", bad.isEmpty())
        }
    }

    @Test fun theLaunchersFirstLayoutLoads() {
        // what the launcher shows while adding the widget (initialLayout / previewLayout)
        for (info in listOf(R.xml.widget_small_info, R.xml.widget_large_info)) {
            val p = ctx.resources.getXml(info)
            while (p.next() != XmlResourceParser.START_TAG) { }
            for (attr in listOf("initialLayout", "previewLayout")) {
                val id = p.getAttributeResourceValue("http://schemas.android.com/apk/res/android", attr, 0)
                assertTrue("$attr missing", id != 0)
                RemoteViews(ctx.packageName, id).apply(ctx, FrameLayout(ctx))
            }
        }
    }

    @Test fun theUpdatedWidgetLoads() {
        val s = Settings(places = listOf(AUCKLAND))
        val fc = sceneFc(code = 63, now = "2026-11-15T13:00")
        val frames = WidgetFrames.render(ctx, s, fc, 300, 140, 2f)
        val v = Widgets.buildViews(ctx, widgetData(s, fc), frames).apply(ctx, FrameLayout(ctx))
        assertEquals(WidgetFrames.FRAMES, v.findViewById<ViewFlipper>(R.id.w_frames).childCount)
        assertEquals("Auckland", v.findViewById<TextView>(R.id.w_place).text.toString())
        assertTrue(v.findViewById<TextView>(R.id.w_temp).text.endsWith("°C"))
        // no place yet, no pictures
        Widgets.buildViews(ctx, widgetData(Settings(), null), emptyList()).apply(ctx, FrameLayout(ctx))
    }
}
