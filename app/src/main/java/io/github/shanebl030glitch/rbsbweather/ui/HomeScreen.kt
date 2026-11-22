// The main screen: swipe between your places; each shows the animated sky, the details,
// the next 24 hours and the 7-day forecast. Pull down to refresh.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.Day
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.ALL_PROVIDERS
import io.github.shanebl030glitch.rbsbweather.data.providerInfo
import io.github.shanebl030glitch.rbsbweather.data.OpenMeteo
import io.github.shanebl030glitch.rbsbweather.data.Place
import io.github.shanebl030glitch.rbsbweather.data.UnitPrefs
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.Version
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun HomeScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    val places = state.settings.places
    if (places.isEmpty()) {
        Welcome(onAdd = { model.go(Screen.SEARCH) }, modifier)
        return
    }
    val selIndex = places.indexOfFirst { it.key == state.settings.selected }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = selIndex) { places.size }
    // swiping selects; choosing a place elsewhere turns the pager to it
    LaunchedEffect(pager, places) {
        snapshotFlow { pager.settledPage }.collect { page ->
            places.getOrNull(page)?.let { if (it.key != model.state.value.settings.selected) model.select(it.key) }
        }
    }
    LaunchedEffect(selIndex) {
        if (pager.currentPage != selIndex && selIndex < pager.pageCount) pager.scrollToPage(selIndex)
    }
    Column(modifier.fillMaxSize()) {
        if (places.size > 1) PageDots(places.size, pager.currentPage)
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { places[it].key }) { page ->
            PlacePage(places[page], state, model)
        }
    }
}

@Composable
private fun PageDots(n: Int, current: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center) {
        repeat(n.coerceAtMost(15)) { i ->
            Box(Modifier.padding(3.dp).size(if (i == current) 8.dp else 6.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (i == current) 0.8f else 0.3f)))
        }
    }
}

@Composable
fun Welcome(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🌤️", fontSize = 64.sp)
        Text("Welcome to RBSB-Weather", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Add a city or town to see its weather.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd, modifier = Modifier.testTag("addFirstPlace")) { Text("Add a place") }
        Spacer(Modifier.height(32.dp))
        Text(Version.AUTHOR_CREDIT, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePage(place: Place, state: UiState, model: AppModel) {
    val fc = state.forecasts[place.key]
    val units = state.settings.units
    val loading = place.key in state.loading
    val err = state.errors[place.key]
    val status = statusText(fc, loading, err, units, System.currentTimeMillis())
    PullToRefreshBox(isRefreshing = loading, onRefresh = { model.refresh(place, force = true) },
        modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("page-${place.key}"), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SkyCard(fc, place, units, state.settings.animations, status.first, status.second) }
            if (fc != null) {
                item { DetailsCard(fc, units) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Next 24 hours", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            HourlyChart(next24(fc.hourly, fc.current.time), units)
                        }
                    }
                }
                item { DailyCard(fc, units, state.settings.animations) }
            }
            item { Footer(fc, state.settings.providers.firstOrNull()?.let { providerInfo(it)?.name }) }
        }
    }
}

/** The line at the bottom of the sky: local time, or what's wrong. (text, is it a warning) */
fun statusText(fc: Forecast?, loading: Boolean, err: String?, units: UnitPrefs, nowMs: Long): Pair<String, Boolean> {
    val local = fc?.current?.time?.let { "Local time at location: " + Units.clock(it, units.clock) } ?: ""
    return when {
        err != null && fc != null -> "⚠ Couldn't update: ${err.trimEnd('.')}. Showing the last forecast (${age(fc, nowMs)})." to true
        err != null -> "⚠ $err" to true
        loading && fc == null -> "Loading the weather…" to false
        loading -> "Updating…  ·  $local" to false
        fc != null && fc.fromCache -> "$local  ·  saved copy (${age(fc, nowMs)})" to false
        else -> local to false
    }
}

fun age(fc: Forecast, nowMs: Long): String {
    val min = ((nowMs - fc.fetchedAt) / 60_000).coerceAtLeast(0)
    return when {
        fc.fetchedAt <= 0 -> "earlier"
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 48 * 60 -> "${min / 60} h ago"
        else -> "${min / (24 * 60)} days ago"
    }
}

@Composable
private fun DetailsCard(fc: Forecast, u: UnitPrefs) {
    val c = fc.current
    val today = fc.daily.firstOrNull()
    val rows = listOf(
        "Feels like" to Units.fmtTemp(c.apparentTemperature, u.temp),
        "Humidity" to (c.humidity?.let { "${it.toInt()}%" } ?: "–"),
        "Wind" to "${Units.fmtWind(c.windSpeed, u.wind)} ${Units.compass(c.windDirection)}".trim(),
        "Gusts" to Units.fmtWind(c.windGusts, u.wind),
        "Pressure" to (c.pressure?.let { "${it.toInt()} hPa" } ?: "–"),
        "Cloud cover" to (c.cloudCover?.let { "${it.toInt()}%" } ?: "–"),
        "Precipitation" to Units.fmtPrecip(c.precipitation, u.precip),
        "UV index (max)" to (today?.uvIndexMax?.let { String.format(Locale.US, "%.1f", it) } ?: "–"),
        "Sunrise" to Units.clock(today?.sunrise, u.clock),
        "Sunset" to Units.clock(today?.sunset, u.clock),
    )
    Card(Modifier.fillMaxWidth().testTag("details")) {
        Column(Modifier.padding(12.dp)) {
            rows.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    pair.forEach { (k, v) ->
                        Row(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                fontSize = 14.sp)
                            Text(v, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

fun dayLabel(date: String, today: LocalDate?): String {
    val d = try { LocalDate.parse(date) } catch (e: Exception) { return date }
    return when (today) {
        d -> "Today"
        d.minusDays(1) -> "Tomorrow"
        else -> d.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH) + " " + d.dayOfMonth + " " +
            d.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH)
    }
}

@Composable
private fun DailyCard(fc: Forecast, u: UnitPrefs, animate: Boolean) {
    val today = try { LocalDate.parse(fc.current.time.take(10)) } catch (e: Exception) { null }
    Card(Modifier.fillMaxWidth().testTag("daily")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("${fc.daily.size}-day forecast", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
            fc.daily.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                DayRow(d, dayLabel(d.date, today), u, animate, i * 0.37)
            }
        }
    }
}

@Composable
private fun DayRow(d: Day, label: String, u: UnitPrefs, animate: Boolean, phase: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(92.dp)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            val pp = d.precipitationProbabilityMax
            Text("💧 ${pp?.toInt()?.let { "$it%" } ?: "–"}  ${Units.fmtPrecip(d.precipitationSum, u.precip)}",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
            if (d.windSpeedMax != null) Text("💨 " + Units.fmtWind(d.windSpeedMax, u.wind),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
        }
        io.github.shanebl030glitch.rbsbweather.ui.scene.AnimatedWeatherIcon(d.weatherCode, true, 34.dp, phase, animate,
            windy = (d.windSpeedMax ?: 0.0) >= io.github.shanebl030glitch.rbsbweather.ui.scene.WINDY_KMH &&
                io.github.shanebl030glitch.rbsbweather.ui.scene.classify(d.weatherCode).first.ordinal <= 2,
            modifier = Modifier.padding(horizontal = 3.dp).testTag("dayIcon"))
        Text(WeatherCodes.describe(d.weatherCode), Modifier.weight(1f), fontSize = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Text(Units.fmtTemp(d.tempMax, u.temp, false), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(" / " + Units.fmtTemp(d.tempMin, u.temp, false), fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
    }
}

@Composable
private fun Footer(fc: Forecast?, primary: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val credit = ALL_PROVIDERS.firstOrNull { it.name == fc?.source }?.credit ?: OpenMeteo.CREDIT
        Text(credit, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        if (fc != null && primary != null && fc.source != primary) {
            Text("(from a backup: $primary couldn't be reached)", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("backupNote"))
        }
        Text(Version.AUTHOR_CREDIT, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("credit"))
    }
}
