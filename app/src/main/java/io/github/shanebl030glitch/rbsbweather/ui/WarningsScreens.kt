// Weather warnings and earthquakes on screen, as on the desktop: the coloured banner across the
// sky, badges, the Warnings screen (each warning in full, Open on…, Mute), Settings → Weather
// Alerts (with each place's coverage), the Earthquakes list and Settings → Earthquakes.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.Alert
import io.github.shanebl030glitch.rbsbweather.data.Alerts
import io.github.shanebl030glitch.rbsbweather.data.ClockFormat
import io.github.shanebl030glitch.rbsbweather.data.DEFAULT_LIMITS
import io.github.shanebl030glitch.rbsbweather.data.LIMIT_RANGES
import io.github.shanebl030glitch.rbsbweather.data.Limit
import io.github.shanebl030glitch.rbsbweather.data.PrecipUnit
import io.github.shanebl030glitch.rbsbweather.data.QuakeSettings
import io.github.shanebl030glitch.rbsbweather.data.Quakes
import io.github.shanebl030glitch.rbsbweather.data.SOURCE_NAMES
import io.github.shanebl030glitch.rbsbweather.data.SOURCE_SITES
import io.github.shanebl030glitch.rbsbweather.data.TempUnit
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WindUnit
import io.github.shanebl030glitch.rbsbweather.data.alertSourceFor
import io.github.shanebl030glitch.rbsbweather.data.coverageText
import io.github.shanebl030glitch.rbsbweather.data.describeQuake
import io.github.shanebl030glitch.rbsbweather.data.nearestPlace
import io.github.shanebl030glitch.rbsbweather.data.nowS
import io.github.shanebl030glitch.rbsbweather.data.quakeColour
import io.github.shanebl030glitch.rbsbweather.data.rainBetween
import io.github.shanebl030glitch.rbsbweather.widget.ageText
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

fun levelColours(level: String): Pair<Color, Color> = (Alerts.COLOURS[level] ?: (0xFFDBE4F0 to 0xFF1C2C44)).let { Color(it.first) to Color(it.second) }

/** "Mon 18:00" in the place's time zone. */
fun alertTime(t: Double?, zone: ZoneId, clock: ClockFormat): String {
    t ?: return "–"
    val z = Instant.ofEpochMilli((t * 1000).toLong()).atZone(zone)
    return z.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + z.dayOfMonth + " " +
        z.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + Units.clock(z.hour, z.minute, clock)
}

/** The banner's line: "Orange Heavy Rain Warning · until Mon 18:00 · MetService". */
fun bannerText(a: Alert, zone: ZoneId, clock: ClockFormat, now: Double = nowS()): String {
    val whenTxt = when {
        a.onset != null && a.onset > now -> "from " + alertTime(a.onset, zone, clock)
        a.expires != null -> "until " + alertTime(a.expires, zone, clock)
        else -> ""
    }
    return listOf("⚠ " + a.title(), whenTxt, if (a.official) a.source else "not official").filter { it.isNotEmpty() }.joinToString("  ·  ")
}

@Composable
fun Badge(level: String, text: String, modifier: Modifier = Modifier) {
    val (bg, fg) = levelColours(level)
    Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(5.dp)).background(bg).padding(horizontal = 5.dp, vertical = 1.dp))
}

@Composable
fun WarningBanner(a: Alert, more: Int, zone: ZoneId, clock: ClockFormat, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (bg, fg) = levelColours(a.level)
    Text(bannerText(a, zone, clock) + if (more > 0) "  (+$more more)" else "", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg.copy(alpha = 0.93f)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp).testTag("warningBanner"))
}

// ----------------------------------------------------------------- Warnings screen
@Composable
fun WarningsScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    val s = state.settings
    val place = s.selectedPlace()
    val uri = LocalUriHandler.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("warnings"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (place == null) { Text("Add a place to see its warnings."); return@Column }
        Text(place.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        val sid = alertSourceFor(place)
        Text(if (sid != null) "Official warnings from ${SOURCE_NAMES[sid]}." + (state.alertErrors[sid]?.let { " The last check failed ($it); the warnings from the check before stay until they expire." } ?: "")
            else "No official warning service covers this place in RBSB-Weather yet. Your own limits still work.", fontSize = 13.sp)
        if (state.alertChecked > 0) Text("Last checked ${ageText((nowS() - state.alertChecked).toLong())}.", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        val fc = state.forecasts[place.key]
        val zone = fc?.let { io.github.shanebl030glitch.rbsbweather.data.Sun.zoneFor(place, it.utcOffsetSeconds) } ?: io.github.shanebl030glitch.rbsbweather.data.Sun.zoneFor(place)
        val list = state.allAlerts[place.key] ?: emptyList()
        if (list.isEmpty()) Text("No warnings for ${place.name} right now.", modifier = Modifier.testTag("noWarnings"))
        list.forEach { a ->
            val muted = a.id in s.alerts.muted
            Card(Modifier.fillMaxWidth().testTag("warning")) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(a.level, a.badge)
                        Spacer(Modifier.width(8.dp))
                        Text(a.title(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    if (muted) Text("Muted: not on the banner, badges or notifications.", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    if (a.area.isNotEmpty()) Text("Area: ${a.area}", fontSize = 13.sp)
                    Text("Valid: " + listOfNotNull(a.onset?.let { "from " + alertTime(it, zone, s.units.clock) }, a.expires?.let { "until " + alertTime(it, zone, s.units.clock) })
                        .joinToString(" ").ifEmpty { "until further notice" }, fontSize = 13.sp)
                    if (a.headline.isNotEmpty() && a.headline != a.event) Text(a.headline, fontSize = 14.sp)
                    if (a.description.isNotEmpty()) { Text("What's expected", fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(a.description, fontSize = 14.sp) }
                    if (a.instruction.isNotEmpty()) { Text("What to do", fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(a.instruction, fontSize = 14.sp) }
                    if (a.hazard == "rain") rainBetween(fc, a.onset ?: nowS(), a.expires)?.let {
                        Text("RBSB-Weather's forecast for the same hours: ${Units.fmtPrecip(it, s.units.precip)} of rain.", fontSize = 13.sp)
                    }
                    Text(if (a.official) "Issued by ${a.source}" + (a.issued?.let { " at " + alertTime(it, zone, s.units.clock) } ?: "") else "From your own limit; not an official warning.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val link = a.url.ifEmpty { sid?.let { SOURCE_SITES[it] } ?: "" }
                        if (a.official && link.isNotEmpty()) OutlinedButton(onClick = { try { uri.openUri(link) } catch (e: Exception) { } }) { Text("Open on ${a.source}") }
                        TextButton(onClick = { model.mute(a.id, !muted) }, modifier = Modifier.testTag("mute")) { Text(if (muted) "Unmute" else "Mute this warning") }
                    }
                }
            }
        }
        Text("Always follow official advice. RBSB-Weather passes warnings on; it doesn't replace your weather service, Civil Defence or emergency services.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        TextButton(onClick = { model.checkWatch(force = true) }, enabled = !state.checking) { Text(if (state.checking) "Checking…" else "Check now") }
        TextButton(onClick = { model.go(Screen.ALERT_SETTINGS) }) { Text("Settings → Weather Alerts") }
        Text(Alerts.CREDIT, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

// --------------------------------------------------------- Settings → Weather Alerts
private fun limitLabel(key: String) = mapOf("gust" to "Wind gusts over", "rain" to "Rain in a day over", "heat" to "Temperature above",
    "frost" to "Temperature below", "snow" to "Any snow", "thunder" to "Any thunderstorms")[key] ?: key

/** A limit's value in the user's units, and back to °C / km/h / mm. */
fun limitShown(key: String, v: Double, t: TempUnit, w: WindUnit, p: PrecipUnit): Double = when (key) {
    "gust" -> Units.wind(v, w) ?: v
    "rain" -> if (p == PrecipUnit.IN) v / 25.4 else v
    "heat", "frost" -> Units.temp(v, t) ?: v
    else -> v
}
fun limitBase(key: String, v: Double, t: TempUnit, w: WindUnit, p: PrecipUnit): Double = when (key) {
    "gust" -> when (w) { WindUnit.KMH -> v; WindUnit.MS -> v * 3.6; WindUnit.MPH -> v * 1.609344; WindUnit.KN -> v * 1.852 }
    "rain" -> if (p == PrecipUnit.IN) v * 25.4 else v
    "heat", "frost" -> if (t == TempUnit.F) (v - 32) * 5 / 9 else v
    else -> v
}

@Composable
fun AlertSettingsScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    val s = state.settings; val a = s.alerts; val u = s.units
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("alertSettings"),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Your places", fontWeight = FontWeight.Bold)
        s.places.forEach { p ->
            val sid = alertSourceFor(p)
            Text("${p.name}: ${coverageText(p)}" + (sid?.let { state.alertErrors[it]?.let { e -> " — last check failed ($e)" } ?: if (state.alertChecked > 0) " — checked ${ageText((nowS() - state.alertChecked).toLong())}" else "" } ?: ""),
                fontSize = 13.sp)
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        SwitchRow("Official warnings", "From MetService, the US National Weather Service, MET Norway and MeteoAlarm", a.official, "alertsOfficial") { model.setAlertSettings(a.copy(official = it)) }
        Choice("Show from", listOf("all" to "All, including advisories", "watch" to "Watches and Yellow and above", "orange" to "Orange and Red", "red" to "Red only"), a.minLevel) {
            model.setAlertSettings(a.copy(minLevel = it)) }
        Choice("Check for warnings", Alerts.INTERVAL_CHOICES.map { it to "Every $it minutes" }, a.interval) { model.setAlertSettings(a.copy(interval = it)) }
        Text("While the app is closed, Android checks about every 15 minutes at most, and sometimes later to save battery.", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        SwitchRow("Banner and badges", "The banner across the sky, badges in My places and on the 7-day forecast", a.banner, "alertsBanner") { model.setAlertSettings(a.copy(banner = it)) }
        SwitchRow("Notifications", "When a warning is issued, upgraded or reissued (each change once)", a.notify, "alertsNotify") { model.setAlertSettings(a.copy(notify = it)) }
        SwitchRow("Alarm sound for Red warnings", null, a.redAlarm, "alertsRedAlarm") { model.setAlertSettings(a.copy(redAlarm = it)) }
        SwitchRow("Only for the place on screen", "Notify only for the place you're looking at", a.selectedOnly, "alertsSelectedOnly") { model.setAlertSettings(a.copy(selectedOnly = it)) }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        SwitchRow("My own limits", "Alerts from RBSB-Weather's forecast when it passes limits you set. Marked MY LIMIT, not official.", a.own, "alertsOwn") { model.setAlertSettings(a.copy(own = it)) }
        DEFAULT_LIMITS.keys.forEach { key ->
            val lim = a.limits[key] ?: DEFAULT_LIMITS[key]!!
            val hasValue = key in setOf("gust", "rain", "heat", "frost")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(limitLabel(key), modifier = Modifier.weight(1f))
                if (hasValue) {
                    var text by remember(key, lim.value, u) {
                        mutableStateOf(limitShown(key, lim.value, u.temp, u.wind, u.precip).let { if (key == "rain" && u.precip == PrecipUnit.IN) "%.1f".format(Locale.US, it) else it.roundToInt().toString() })
                    }
                    OutlinedTextField(text, { v ->
                        text = v.take(6)
                        v.toDoubleOrNull()?.let { x ->
                            val (lo, hi) = LIMIT_RANGES[key]!!
                            val base = limitBase(key, x, u.temp, u.wind, u.precip).coerceIn(lo, hi)
                            model.setAlertSettings(a.copy(limits = a.limits + (key to Limit(lim.on, base))))
                        }
                    }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text(when (key) { "gust" -> u.wind.label; "rain" -> if (u.precip == PrecipUnit.IN) "in" else "mm"; else -> u.temp.label }) },
                        modifier = Modifier.width(110.dp).testTag("limit-$key"))
                }
                Switch(lim.on, { on -> model.setAlertSettings(a.copy(limits = a.limits + (key to lim.copy(on = on)))) }, enabled = a.own,
                    modifier = Modifier.padding(start = 8.dp).testTag("limitOn-$key"))
            }
        }
        Text("Gust limits need gust forecasts: Open-Meteo gives them for every day, other providers only for today.", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        if (a.muted.isNotEmpty()) TextButton(onClick = { model.setAlertSettings(a.copy(muted = emptySet())) }) { Text("Unmute all (${a.muted.size})") }
    }
}

@Composable
internal fun SwitchRow(title: String, sub: String?, on: Boolean, tag: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, fontSize = 12.sp)
        }
        Switch(on, onChange, enabled = enabled, modifier = Modifier.testTag(tag))
    }
}

// ---------------------------------------------------------------- Earthquakes
@Composable
fun QuakesScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    val s = state.settings
    val uri = LocalUriHandler.current
    val now = nowS()
    LazyColumn(modifier.fillMaxSize().testTag("quakes"), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("${state.quakes.size} earthquake${if (state.quakes.size == 1) "" else "s"} of magnitude ${s.quakes.minMag} and up in the past ${s.quakes.period}.",
                fontWeight = FontWeight.Bold)
            Text("Red: in the last hour · orange: 6 hours · yellow: a day · grey: older. The world map and globe arrive with the Earth tabs.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            state.quakeErrors.forEach { (src, e) -> Text("$src couldn't be reached ($e); its last quakes are kept.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            if (state.quakeChecked > 0) Text("Checked ${ageText((now - state.quakeChecked).toLong())}.", fontSize = 12.sp)
        }
        items(state.quakes, key = { it.id }) { q ->
            Card(Modifier.fillMaxWidth().clickable(enabled = q.url.isNotEmpty()) { try { uri.openUri(q.url) } catch (e: Exception) { } }.testTag("quake")) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val r = io.github.shanebl030glitch.rbsbweather.data.quakeRadius(q.mag)
                    Box(Modifier.size((r * 2.4).dp).clip(CircleShape).background(Color(quakeColour(q, now))))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(describeQuake(q, now), fontSize = 14.sp)
                        nearestPlace(q, s.places)?.let { (p, km) -> Text("${km.roundToInt()} km from ${p.name}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)) }
                        if (q.tsunami && q.mag >= 6.5) Text("A large quake under the sea: if you're near the coast, check your official tsunami advice.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            Text("RBSB-Weather is not an earthquake or tsunami warning service. In New Zealand, follow Civil Defence (civildefence.govt.nz) and emergency mobile alerts. A long or strong quake near the coast is itself the warning: move to high ground straight away.",
                fontSize = 12.sp)
            Row { TextButton(onClick = { model.checkWatch(force = true) }, enabled = !state.checking) { Text(if (state.checking) "Checking…" else "Check now") }
                TextButton(onClick = { model.go(Screen.QUAKE_SETTINGS) }) { Text("Settings → Earthquakes") } }
            Text(Quakes.CREDIT, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun QuakeSettingsScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    val q = state.settings.quakes
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("quakeSettings")) {
        Choice("Show quakes from", listOf("day" to "The past day", "week" to "The past week"), q.period) { model.setQuakeSettings(q.copy(period = it)) }
        Choice("Show", listOf(2.5 to "Magnitude 2.5 and up (many small quakes)", 4.5 to "Magnitude 4.5 and up"), q.minMag) { model.setQuakeSettings(q.copy(minMag = it)) }
        Choice("Check", Quakes.INTERVALS.map { it to "Every $it minutes while the app is open" }, q.interval) { model.setQuakeSettings(q.copy(interval = it)) }
        Text("While the app is closed, Android checks about every 15 minutes at most.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow("Notify: quakes near my places", null, q.notifyNear, "quakesNear") { model.setQuakeSettings(q.copy(notifyNear = it)) }
        Choice("Magnitude at least", QuakeSettings.NEAR_MAGS.map { it to "M$it" }, q.nearMag) { model.setQuakeSettings(q.copy(nearMag = it)) }
        Choice("Within", QuakeSettings.NEAR_KMS.map { it to "$it km" }, q.nearKm) { model.setQuakeSettings(q.copy(nearKm = it)) }
        SwitchRow("Notify: major quakes anywhere", null, q.notifyMajor, "quakesMajor") { model.setQuakeSettings(q.copy(notifyMajor = it)) }
        Choice("Major means at least", QuakeSettings.MAJOR_MAGS.map { it to "M$it" }, q.majorMag) { model.setQuakeSettings(q.copy(majorMag = it)) }
        Text("Each quake is notified once, and only if it happened in the last 2 hours.", fontSize = 12.sp)
    }
}
