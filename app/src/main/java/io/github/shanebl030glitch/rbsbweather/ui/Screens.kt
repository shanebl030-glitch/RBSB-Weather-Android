// Search, Places, Settings, Help and About.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.ClockFormat
import io.github.shanebl030glitch.rbsbweather.data.MetNorway
import io.github.shanebl030glitch.rbsbweather.data.OpenMeteo
import io.github.shanebl030glitch.rbsbweather.data.Place
import io.github.shanebl030glitch.rbsbweather.data.PrecipUnit
import io.github.shanebl030glitch.rbsbweather.data.REFRESH_CHOICES
import io.github.shanebl030glitch.rbsbweather.data.providerInfo
import io.github.shanebl030glitch.rbsbweather.data.TempUnit
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.Version
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.data.WindUnit
import java.util.Locale

// ----------------------------------------------------------------- search
@Composable
fun SearchScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) { } }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; model.searchFor(it) },
            label = { Text("City or town") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("searchField"),
        )
        Spacer(Modifier.height(12.dp))
        when (val s = state.search) {
            SearchState.Idle -> Text("Type at least 2 letters, for example \"Auckland\" or \"Hamilton\".",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            SearchState.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text("Searching…")
            }
            is SearchState.Failed -> Text("⚠ ${s.message}", color = MaterialTheme.colorScheme.error)
            is SearchState.Results -> if (s.places.isEmpty()) {
                Text("No places found. Check the spelling, or try a nearby town.")
            } else {
                LazyColumn(Modifier.testTag("results")) {
                    items(s.places, key = { it.key }) { p ->
                        Column(Modifier.fillMaxWidth().clickable { model.add(p) }.padding(vertical = 12.dp)) {
                            Text(p.displayName, fontWeight = FontWeight.Medium)
                            Text(String.format(Locale.US, "%.2f, %.2f", p.latitude, p.longitude),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- places
@Composable
fun PlacesScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    val places = state.settings.places
    var confirm by remember { mutableStateOf<Place?>(null) }
    if (places.isEmpty()) {
        Welcome(onAdd = { model.go(Screen.SEARCH) }, modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize().testTag("places"), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        items(places, key = { it.key }) { p ->
            val fc = state.forecasts[p.key]
            Row(Modifier.fillMaxWidth().clickable { model.select(p.key); model.go(Screen.HOME) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(fc?.current?.let { WeatherCodes.symbol(it.weatherCode, it.isDay) } ?: "📍", fontSize = 22.sp,
                    modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, fontWeight = if (p.key == state.settings.selected) FontWeight.Bold else FontWeight.Normal)
                        if (state.settings.alerts.banner) state.alerts[p.key]?.firstOrNull()?.let {
                            Badge(it.level, it.badge, Modifier.padding(start = 6.dp).clickable { model.openWarnings(p.key) }.testTag("badge-${p.name}"))
                        }
                    }
                    Text(listOf(p.admin1, p.country).filter { it.isNotBlank() && it != p.name }.joinToString(", "),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Text(Units.fmtTemp(fc?.current?.temperature, state.settings.units.temp), fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { model.move(p.key, -1) }, enabled = p != places.first()) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${p.name} up")
                }
                IconButton(onClick = { model.move(p.key, 1) }, enabled = p != places.last()) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${p.name} down")
                }
                IconButton(onClick = { confirm = p }, modifier = Modifier.testTag("remove-${p.name}")) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${p.name}")
                }
            }
            HorizontalDivider()
        }
        item {
            TextButton(onClick = { model.go(Screen.SEARCH) }, modifier = Modifier.padding(top = 8.dp)) { Text("+ Add a place") }
        }
    }
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Remove ${p.name}?") },
            text = { Text("Its saved forecast is deleted too. You can add it again any time.") },
            confirmButton = { TextButton(onClick = { model.remove(p.key); confirm = null }, modifier = Modifier.testTag("confirmRemove")) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

// --------------------------------------------------------------- settings
@Composable
internal fun <T> Choice(title: String, options: List<Pair<T, String>>, current: T, onPick: (T) -> Unit) {
    if (title.isNotBlank()) Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    options.forEach { (value, label) ->
        Row(Modifier.fillMaxWidth().selectable(selected = value == current, role = Role.RadioButton,
            onClick = { onPick(value) }).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = value == current, onClick = null)
            Text(label, Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun SettingsScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    val s = state.settings
    val u = s.units
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("settings")) {
        SettingsLink("Weather Alerts", "Official warnings, your own limits, notifications", "openAlertSettings") { model.go(Screen.ALERT_SETTINGS) }
        SettingsLink("Earthquakes", "What to show, and when to notify", "openQuakeSettings") { model.go(Screen.QUAKE_SETTINGS) }
        Card(Modifier.fillMaxWidth().clickable { model.go(Screen.PROVIDERS) }.testTag("openProviders")) {
            Column(Modifier.padding(14.dp)) {
                Text("Weather Providers", fontWeight = FontWeight.Bold)
                val names = s.providers.mapNotNull { providerInfo(it)?.name }
                Text("Primary: ${names.firstOrNull() ?: "none"}" +
                    (if (names.size > 1) "  ·  Backups: ${names.drop(1).joinToString(", ")}" else ""), fontSize = 13.sp)
            }
        }
        Choice("Temperature", listOf(TempUnit.C to "Celsius (°C)", TempUnit.F to "Fahrenheit (°F)"), u.temp) {
            model.setUnits(u.copy(temp = it)) }
        Choice("Wind speed", listOf(WindUnit.KMH to "km/h", WindUnit.MS to "m/s", WindUnit.MPH to "mph",
            WindUnit.KN to "knots"), u.wind) { model.setUnits(u.copy(wind = it)) }
        Choice("Rain", listOf(PrecipUnit.MM to "millimetres", PrecipUnit.IN to "inches"), u.precip) {
            model.setUnits(u.copy(precip = it)) }
        Choice("Time", listOf(ClockFormat.H24 to "24-hour (19:30)", ClockFormat.H12 to "12-hour (7:30 pm)"), u.clock) {
            model.setUnits(u.copy(clock = it)) }
        Choice("Refresh the weather", REFRESH_CHOICES.map { it to (if (it == 0) "Only when I pull down" else "When it's over $it minutes old") },
            s.refreshMinutes) { model.setRefreshMinutes(it) }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Animated sky", fontWeight = FontWeight.Bold)
                Text("Moving clouds, rain and snow, in the app and the home-screen widgets. Turn off to save battery.", fontSize = 13.sp)
            }
            Switch(checked = s.animations, onCheckedChange = { model.setAnimations(it) }, modifier = Modifier.testTag("animSwitch"))
        }
        SwitchRow("Temperature in the status bar",
            "Keeps the temperature of the place on screen at the top of your phone (the desktop's tray icon).", s.tempNotification, "tempSwitch") {
            model.setTempNotification(it) }
        if (s.tempNotification && !notificationsAllowed()) Text("Notifications are turned off for RBSB-Weather. Allow them in Android's settings to see it.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        SwitchRow("Check for updates", "Once a day, ask GitHub whether there's a newer RBSB-Weather for Android. Nothing installs without you.",
            s.updateCheck, "updateSwitch") { model.setUpdateCheck(it) }
    }
}

// ------------------------------------------------------------------- help
@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lines = remember {
        try { ctx.assets.open("USER_GUIDE.md").bufferedReader().readLines() } catch (e: Exception) { listOf("The guide couldn't be opened.") }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("help")) {
        lines.forEach { line ->
            when {
                line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp))
                line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                line.startsWith("* ") || line.startsWith("- ") -> Text("•  " + line.drop(2).replace("**", ""),
                    modifier = Modifier.padding(start = 8.dp, bottom = 3.dp))
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                else -> Text(line.replace("**", ""))
            }
        }
    }
}

// ------------------------------------------------------------------ about
const val COFFEE_URL = "https://www.paypal.com/cgi-bin/webscr?cmd=_xclick&business=M4EDN77T6EEPJ" +
    "&item_name=Coffee+for+RBSB-Weather&amount=0.99&currency_code=NZD"
const val REPO_URL = "https://github.com/shanebl030-glitch/RBSB-Weather-Android"

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    fun open(u: String) { try { uri.openUri(u) } catch (e: Exception) { } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("about"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(io.github.shanebl030glitch.rbsbweather.R.drawable.app_logo),
            contentDescription = "RBSB-Weather icon", modifier = Modifier.size(120.dp).testTag("aboutLogo"))
        Text("RBSB-Weather", style = MaterialTheme.typography.headlineSmall)
        Text("for Android · version ${Version.NAME}")
        Spacer(Modifier.height(8.dp))
        Text(Version.AUTHOR_CREDIT, fontWeight = FontWeight.Bold)
        Text("Designed and Created By Shane BlackbuRn")
        Text("Coded By Claude AI")
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Credits", fontWeight = FontWeight.Bold)
                Text(OpenMeteo.CREDIT, Modifier.clickable { open("https://open-meteo.com/") })
                Text(MetNorway.CREDIT, Modifier.clickable { open("https://api.met.no/") })
                Text(io.github.shanebl030glitch.rbsbweather.ui.scene.MoonRender.CREDIT_DEFAULT + ".", fontSize = 13.sp)
                Text(io.github.shanebl030glitch.rbsbweather.data.Alerts.CREDIT + ".", fontSize = 13.sp)
                Text(io.github.shanebl030glitch.rbsbweather.data.Quakes.CREDIT + ".", fontSize = 13.sp)
                Text(io.github.shanebl030glitch.rbsbweather.data.Earth.CREDIT + ".", fontSize = 13.sp)
                Text(io.github.shanebl030glitch.rbsbweather.data.Clouds.CREDIT + ".", fontSize = 13.sp)
                Text("With your own keys: OpenWeather, WeatherAPI.com, Visual Crossing, Tomorrow.io and Weather Underground " +
                    "(each credited under the forecast when it supplied it).", fontSize = 13.sp)
                Text("The Sun, Moon, sunrise and sunset, and day and night on the Earth are worked out on your phone.")
            }
        }
        TextButton(onClick = { open(COFFEE_URL) }) { Text("☕ If you enjoy this app, consider buying me a coffee for NZ$0.99") }
        TextButton(onClick = { open(REPO_URL) }) { Text("RBSB-Weather for Android on GitHub") }
        Text("A separate app from RBSB-Weather for the desktop.", fontSize = 12.sp)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    // the whole row (title and words too) turns it on and off, not just the small switch
    Row(Modifier.fillMaxWidth().padding(top = 16.dp)
        .toggleable(checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange).testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun notificationsAllowed(): Boolean =
    io.github.shanebl030glitch.rbsbweather.background.Notifier.allowed(androidx.compose.ui.platform.LocalContext.current)

@Composable
private fun SettingsLink(title: String, sub: String, tag: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick).testTag(tag)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 13.sp)
        }
    }
}
