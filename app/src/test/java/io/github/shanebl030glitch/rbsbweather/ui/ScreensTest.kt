// The screens, run headless with Robolectric: first start, adding a place, the weather on
// screen, settings, removing, help and about.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.shanebl030glitch.rbsbweather.data.AUCKLAND
import io.github.shanebl030glitch.rbsbweather.data.ForecastCache
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreensTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    // animations off: an endless animation would keep the test waiting
    private fun model(store: MemoryStore = MemoryStore(Settings(animations = false).toJson().toString()),
                      p: FakeProvider = FakeProvider()) =
        AppModel(store, WeatherRepository(listOf(p), ForecastCache(tmp.root)), SEARCH,
            CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    @Test fun firstStartAddAPlaceAndSeeItsWeather() {
        val m = model()
        compose.setContent { RbsbApp(m) }
        compose.onNodeWithText("Welcome to RBSB-Weather").assertIsDisplayed()
        compose.onNodeWithTag("addFirstPlace").performClick()
        compose.onNodeWithTag("searchField").performTextInput("Auckland")
        compose.waitUntil(5_000) { m.state.value.search is SearchState.Results }
        compose.onAllNodesWithText("Auckland, New Zealand")[0].performClick()
        compose.waitUntil(5_000) { AUCKLAND.key in m.state.value.forecasts || m.state.value.forecasts.isNotEmpty() }
        compose.onNodeWithTag("temp").assertIsDisplayed()
        compose.onNodeWithText("17°C").assertIsDisplayed()
        compose.onNodeWithTag("details").assertIsDisplayed()
        // the credit is at the bottom of the page: scroll down to it
        compose.onNodeWithTag("page-${AUCKLAND.key}").performScrollToNode(hasTestTag("credit"))
        compose.onNodeWithTag("credit").assertIsDisplayed()
        compose.onNodeWithText("Created By Shane BlackbuRn").assertIsDisplayed()
    }

    @Test fun unitsChangeEverywhere() {
        val m = model()
        m.add(AUCKLAND)
        compose.setContent { RbsbApp(m) }
        compose.onNodeWithText("17°C").assertIsDisplayed()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("menu-Settings").performClick()
        compose.onNodeWithText("Fahrenheit (°F)").performClick()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithText("62°F").assertIsDisplayed()
    }

    @Test fun removeAPlaceAsksFirst() {
        val m = model()
        m.add(AUCKLAND)
        compose.setContent { RbsbApp(m) }
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("menu-My places").performClick()
        compose.onNodeWithTag("remove-Auckland").performClick()
        compose.onNodeWithText("Remove Auckland?").assertIsDisplayed()
        compose.onNodeWithTag("confirmRemove").performClick()
        compose.waitForIdle()
        assertTrue(m.state.value.settings.places.isEmpty())
    }

    @Test fun offlineShowsTheSavedCopyAndWhy() {
        val p = FakeProvider()
        val store = MemoryStore(Settings(animations = false).toJson().toString())
        model(store, p).add(AUCKLAND)
        p.down = true
        val m = model(store, p)
        compose.setContent { RbsbApp(m) }
        compose.waitForIdle()
        compose.onNodeWithTag("skyStatus", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("17°C").assertIsDisplayed()
        assertEquals("No internet connection.", m.state.value.errors[AUCKLAND.key])
    }

    @Test fun helpAndAbout() {
        val m = model()
        compose.setContent { RbsbApp(m) }
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("menu-User guide").performClick()
        compose.onNodeWithText("Getting started").assertExists()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("menu-About").performClick()
        compose.onNodeWithText("Designed and Created By Shane BlackbuRn").assertExists()
        compose.onNodeWithText("Coded By Claude AI").assertExists()
    }

    @Test fun weatherProvidersScreen() {
        val m = model()
        compose.setContent { RbsbApp(m) }
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("menu-Settings").performClick()
        compose.onNodeWithTag("openProviders").performClick()
        compose.onNodeWithText("Weather Providers").assertIsDisplayed()
        compose.onNodeWithTag("providerRole-open-meteo").assertIsDisplayed()
        compose.onNodeWithTag("providers").performScrollToNode(hasTestTag("primary-met.no"))
        compose.onNodeWithTag("primary-met.no").performClick()
        assertEquals("met.no", m.state.value.settings.providers[0])
        compose.onNodeWithTag("providers").performScrollToNode(hasTestTag("details-tomorrow"))
        compose.onNodeWithTag("details-tomorrow").performClick()
        compose.onNodeWithTag("providers").performScrollToNode(hasTestTag("key-tomorrow"))
        compose.onNodeWithTag("key-tomorrow").assertExists()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("settings").assertIsDisplayed()
    }

    @Test fun playbackShowsALaterTime() {
        val m = model()
        m.add(AUCKLAND)
        compose.setContent { RbsbApp(m) }
        compose.onNodeWithTag("miniForecast").assertIsDisplayed()
        compose.onNodeWithTag("playback").assertIsDisplayed()
        compose.onNodeWithTag("playbackNow").assertIsDisplayed()
    }
}
