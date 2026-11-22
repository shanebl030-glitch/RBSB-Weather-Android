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
        while (true) {
            delay(60_000)
            model.refresh()
        }
    }

    RbsbTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(when (state.screen) {
                            Screen.HOME -> "RBSB-Weather"
                            Screen.SEARCH -> "Add a place"
                            Screen.PLACES -> "My places"
                            Screen.SETTINGS -> "Settings"
                            Screen.PROVIDERS -> "Weather Providers"
                            Screen.HELP -> "User guide"
                            Screen.ABOUT -> "About"
                        })
                    },
                    navigationIcon = {
                        if (state.screen != Screen.HOME) {
                            IconButton(onClick = { model.back() }, modifier = Modifier.testTag("back")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (state.screen == Screen.HOME) {
                            if (state.settings.places.isNotEmpty()) {
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
                                listOf(Screen.PLACES to "My places", Screen.SETTINGS to "Settings",
                                    Screen.HELP to "User guide", Screen.ABOUT to "About").forEach { (sc, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; model.go(sc) },
                                        modifier = Modifier.testTag("menu-$label"))
                                }
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snack) },
        ) { pad ->
            val m = Modifier.padding(pad)
            when (state.screen) {
                Screen.HOME -> HomeScreen(state, model, m)
                Screen.SEARCH -> SearchScreen(state, model, m)
                Screen.PLACES -> PlacesScreen(state, model, m)
                Screen.SETTINGS -> SettingsScreen(state, model, m)
                Screen.PROVIDERS -> ProvidersScreen(state, model, m)
                Screen.HELP -> HelpScreen(m)
                Screen.ABOUT -> AboutScreen(m)
            }
        }
    }
}
