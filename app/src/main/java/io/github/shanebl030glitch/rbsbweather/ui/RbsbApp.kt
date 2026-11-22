// The app's frame: top bar, menu, messages and which screen is showing.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay

/** The screens with the bottom tabs (the desktop's main tabs). */
val TOP_LEVEL = setOf(Screen.HOME, Screen.MOON, Screen.EARTH, Screen.WIND, Screen.WARNINGS)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RbsbApp(model: AppModel) {
    val state by model.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }

    BackHandler(enabled = state.screen != Screen.HOME) { model.back() }
    LaunchedEffect(state.message) {
        state.message?.let { snack.showSnackbar(it); model.messageShown() }
    }
    // keep the weather fresh while the app is open (checks each minute; refreshes when it's due)
    LaunchedEffect(Unit) {
        model.refreshAll()
        model.checkWatch()
        while (true) {
            delay(60_000)
            model.refresh()
            model.checkWatch()                   // warnings and quakes, when their check is due
        }
    }

    RbsbTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(when (state.screen) {
                            Screen.HOME -> "RBSB-Weather"
                            Screen.MOON -> "Moon"
                            Screen.EARTH -> "Earth"
                            Screen.WIND -> "Wind"
                            Screen.SEARCH -> "Add a place"
                            Screen.PLACES -> "My places"
                            Screen.SETTINGS -> "Settings"
                            Screen.PROVIDERS -> "Weather Providers"
                            Screen.ALERT_SETTINGS -> "Weather Alerts"
                            Screen.QUAKE_SETTINGS -> "Earthquakes"
                            Screen.WARNINGS -> "⚠ Warnings"
                            Screen.QUAKES -> "Earthquakes"
                            Screen.HELP -> "User guide"
                            Screen.ABOUT -> "About"
                            Screen.UPDATES -> "Check for updates"
                            Screen.CONNECTION -> "Test connection"
                        })
                    },
                    navigationIcon = {
                        if (state.screen !in TOP_LEVEL) {
                            IconButton(onClick = { model.back() }, modifier = Modifier.testTag("back")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (state.screen in TOP_LEVEL) {
                            if (state.screen == Screen.HOME && state.settings.places.isNotEmpty()) {
                                IconButton(onClick = { model.refresh(force = true) }, modifier = Modifier.testTag("refresh")) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                                }
                            }
                            IconButton(onClick = { model.go(Screen.SEARCH) }, modifier = Modifier.testTag("add")) {
                                Icon(Icons.Filled.Add, contentDescription = "Add a place")
                            }
                            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("menu")) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                listOf(Screen.PLACES to "My places", Screen.WARNINGS to "Warnings", Screen.QUAKES to "Earthquakes", Screen.SETTINGS to "Settings",
                                    Screen.HELP to "User guide", Screen.CONNECTION to "Test connection", Screen.UPDATES to "Check for updates",
                                    Screen.ABOUT to "About").forEach { (sc, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; model.go(sc) },
                                        modifier = Modifier.testTag("menu-$label"))
                                }
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                // the desktop's tabs: Forecast, Moon, Earth (flat and globe), Wind and Warnings
                if (state.screen in TOP_LEVEL) NavigationBar {
                    listOf(Triple(Screen.HOME, "🌤", "Forecast"), Triple(Screen.MOON, "🌙", "Moon"), Triple(Screen.EARTH, "🌍", "Earth"),
                        Triple(Screen.WIND, "🌬", "Wind"), Triple(Screen.WARNINGS, "⚠", "Warnings")).forEach { (sc, icon, label) ->
                        NavigationBarItem(selected = state.screen == sc, onClick = { model.go(sc) }, icon = { Text(icon, fontSize = 20.sp) },
                            label = { Text(label) }, modifier = Modifier.testTag("tab-$label"))
                    }
                }
            },
        ) { pad ->
            val m = Modifier.padding(pad)
            when (state.screen) {
                Screen.HOME -> HomeScreen(state, model, m)
                Screen.MOON -> MoonScreen(state, model, m)
                Screen.EARTH -> io.github.shanebl030glitch.rbsbweather.ui.earth.EarthScreen(state, model, m)
                Screen.WIND -> io.github.shanebl030glitch.rbsbweather.ui.earth.WindScreen(state, model, m)
                Screen.SEARCH -> SearchScreen(state, model, m)
                Screen.PLACES -> PlacesScreen(state, model, m)
                Screen.SETTINGS -> SettingsScreen(state, model, m)
                Screen.PROVIDERS -> ProvidersScreen(state, model, m)
                Screen.ALERT_SETTINGS -> AlertSettingsScreen(state, model, m)
                Screen.QUAKE_SETTINGS -> QuakeSettingsScreen(state, model, m)
                Screen.WARNINGS -> WarningsScreen(state, model, m)
                Screen.QUAKES -> QuakesScreen(state, model, m)
                Screen.HELP -> HelpScreen(m)
                Screen.ABOUT -> AboutScreen(m)
                Screen.UPDATES -> UpdatesScreen(state, model, m)
                Screen.CONNECTION -> ConnectionScreen(state, model, m)
            }
        }
    }
}
