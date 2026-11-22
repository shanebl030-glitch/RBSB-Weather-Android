// The Moon screen, as the desktop's Moon tab: a large, realistic Moon turned exactly as it
// appears in the place's sky (or at its highest point when it's below the horizon), the phase,
// % illuminated, age and waxing/waning, moonrise and moonset today, the distance, the next new,
// first quarter, full and last quarter moons, the next 7 days, and saving a 2048 × 2048 picture.
// Worked out on the phone, so it works offline. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.ClockFormat
import io.github.shanebl030glitch.rbsbweather.data.Moon
import io.github.shanebl030glitch.rbsbweather.data.Place
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.ui.scene.MoonRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

const val BIG_PX = 640
const val SMALL_PX = 112
const val EXPORT_PX = 2048

class MoonReport(
    val place: Place, val zone: ZoneId, val now: ZonedDateTime, val phase: Moon.Phase, val ageDays: Double,
    val days: List<Moon.MoonDay>, val upcoming: List<Pair<String, ZonedDateTime>>, val view: Moon.SkyView,
    val dayViews: List<Moon.SkyView>, val big: Bitmap?, val small: List<Bitmap?>, val error: String = "",
)

/** Everything for the Moon screen (run it off the main thread). */
fun buildMoonReport(place: Place, zone: ZoneId, nowT: Instant = Instant.now(), bigPx: Int = BIG_PX, smallPx: Int = SMALL_PX): MoonReport {
    val now = nowT.atZone(zone)
    val phase = Moon.phase(nowT)
    val days = Moon.moonDays(place.latitude, place.longitude, zone, now.toLocalDate(), 7)
    val view = Moon.skyView(nowT, place.latitude, place.longitude, zone)
    val dayViews = days.map { Moon.dayView(it.date, place.latitude, place.longitude, zone) }
    var big: Bitmap? = null; val small = mutableListOf<Bitmap?>(); var err = ""
    if (MoonRender.available) {
        val bp = if (view.isNow) phase else Moon.phase(view.whenT.toInstant())
        big = MoonRender.render(bigPx, bp.phaseAngle, bp.brightLimb, rotation = view.rotation)
        dayViews.forEach { v -> val ph = Moon.phase(v.whenT.toInstant()); small += MoonRender.render(smallPx, ph.phaseAngle, ph.brightLimb, rotation = v.rotation) }
    } else err = "The Moon pictures couldn't be loaded."
    return MoonReport(place, zone, now, phase, Moon.ageDays(nowT), days, Moon.upcomingPhases(nowT, zone), view, dayViews, big, small, err)
}

fun moonClock(t: ZonedDateTime?, c: ClockFormat) = t?.let { Units.clock(it.hour, it.minute, c) } ?: "–"
fun moonRiseText(md: Moon.MoonDay?, c: ClockFormat) = when { md == null -> "–"; md.rise != null -> moonClock(md.rise, c); md.alwaysUp -> "up all day"; else -> "no moonrise" }
fun moonSetText(md: Moon.MoonDay?, c: ClockFormat) = when { md == null -> "–"; md.set != null -> moonClock(md.set, c); md.alwaysDown -> "down all day"; else -> "no moonset" }

fun moonSkyText(v: Moon.SkyView, c: ClockFormat): String = when {
    v.isNow -> String.format(Locale.US, "Up now · %.0f° high in the %s", v.altitude, Units.compass(v.azimuth))
    v.up -> "Below horizon · shown at its highest, " + v.whenT.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + moonClock(v.whenT, c)
    else -> "Below the horizon all day · shown at its highest"
}

fun moonDayLabel(d: LocalDate, today: LocalDate) = when (d) {
    today -> "Today"; today.plusDays(1) -> "Tomorrow"
    else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.dayOfMonth
}

/** Saves a picture to Pictures/RBSB-Weather; returns where, or throws. */
fun savePicture(ctx: Context, bmp: Bitmap, name: String): String {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RBSB-Weather")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("no picture folder")
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: throw IllegalStateException("couldn't write")
    return "Pictures/RBSB-Weather/$name"
}

@Composable
fun MoonScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    val place = state.settings.selectedPlace()
    val ctx = LocalContext.current
    val clock = state.settings.units.clock
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { MoonRender.init(ctx.applicationContext.assets) }
    if (place == null) { Text("Add a place to see the Moon.", modifier.padding(16.dp)); return }
    val fc = state.forecasts[place.key]
    val zone = Moon.zoneFor(place.timezone.takeIf { it != "auto" } ?: fc?.timezone, fc?.utcOffsetSeconds)
    val minute = (System.currentTimeMillis() / 600_000)            // recalculated every 10 minutes
    val report by produceState<MoonReport?>(null, place.key, zone, minute) {
        value = null
        value = withContext(Dispatchers.Default) { try { buildMoonReport(place, zone) } catch (e: Exception) { null } }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("moon"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val r = report
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
            .background(Brush.radialGradient(listOf(Color(0xFF1B2440), Color(0xFF05070D)))), contentAlignment = Alignment.Center) {
            if (r?.big != null) Image(r.big.asImageBitmap(), "The Moon as it looks in ${place.name}'s sky", Modifier.fillMaxSize(0.82f).testTag("bigMoon"))
            else if (r == null) CircularProgressIndicator() else Text(r.error, color = Color.White)
        }
        if (r == null) return@Column
        Text(r.phase.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("moonPhase"))
        Text(String.format(Locale.US, "%.0f%% illuminated  ·  %.1f days old  ·  %s", r.phase.illumination * 100, r.ageDays, if (r.phase.waxing) "waxing" else "waning"), fontSize = 16.sp)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Fact("Moonrise today", moonRiseText(r.days.firstOrNull(), clock), true)
                Fact("Moonset today", moonSetText(r.days.firstOrNull(), clock), true)
                Fact("In the sky", moonSkyText(r.view, clock))
                Fact("Distance", String.format(Locale.US, "%,.0f km", r.phase.distanceKm))
                Fact("Times shown in", "${place.name} local time (${r.now.zone.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)})")
            }
        }
        Text("The picture is turned exactly as the Moon appears in this place's sky, so the tilt of the crescent matches what you'd see looking up.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        Text("Upcoming phases", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        r.upcoming.forEach { (name, w) ->
            Text("$name:  " + w.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + w.dayOfMonth + " " +
                w.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + ", " + moonClock(w, clock), fontSize = 14.sp)
        }
        Text("Next 7 days", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).testTag("moonWeek"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            r.days.forEachIndexed { i, md ->
                Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(moonDayLabel(md.date, r.now.toLocalDate()), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xFF05070D)), contentAlignment = Alignment.Center) {
                        r.small.getOrNull(i)?.let { Image(it.asImageBitmap(), md.name, Modifier.size(60.dp)) }
                    }
                    Text(md.name, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Text(String.format(Locale.US, "%.0f%%", md.phase.illumination * 100), fontSize = 11.sp)
                    Text("↑ " + moonRiseText(md, clock), fontSize = 11.sp)
                    Text("↓ " + moonSetText(md, clock), fontSize = 11.sp)
                }
            }
        }
        Text("\"no moonrise\" or \"no moonset\" on a day is normal: the Moon rises about 50 minutes later each day, so some days miss one.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        OutlinedButton(onClick = {
            if (saving) return@OutlinedButton
            saving = true
            scope.launch {
                val msg = withContext(Dispatchers.Default) {
                    try {
                        val ph = if (r.view.isNow) r.phase else Moon.phase(r.view.whenT.toInstant())
                        val bmp = MoonRender.render(EXPORT_PX, ph.phaseAngle, ph.brightLimb, rotation = r.view.rotation) ?: throw IllegalStateException("no picture")
                        "Saved to " + savePicture(ctx, bmp, "moon-${r.now.toLocalDate()}-${place.name.replace(Regex("[^A-Za-z0-9]+"), "-")}.png")
                    } catch (e: Exception) { "Couldn't save the picture (${e.javaClass.simpleName})." }
                }
                saving = false
                model.say(msg)
            }
        }, enabled = !saving && r.big != null, modifier = Modifier.testTag("saveMoon")) { Text(if (saving) "Saving…" else "Save high-resolution image") }
        Text("Moon imagery: NASA Scientific Visualization Studio (CGI Moon Kit)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun Fact(title: String, value: String, big: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(title, Modifier.width(130.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        Text(value, fontSize = if (big) 17.sp else 14.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal)
    }
}
