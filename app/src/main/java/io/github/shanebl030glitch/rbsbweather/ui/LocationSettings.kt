// Settings → 📍 My location (0.7): use the phone's location as a place that follows you, and
// (optionally) have the home-screen widgets and the status-bar temperature show it.
// Android-only; the desktop app doesn't have it. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.shanebl030glitch.rbsbweather.background.LocationFinder

@Composable
fun MyLocationSection(state: UiState, model: AppModel,
                      source: io.github.shanebl030glitch.rbsbweather.background.LocationSource? = null,
                      namer: io.github.shanebl030glitch.rbsbweather.data.PlaceNamer? = null) {
    val ctx = LocalContext.current
    val s = state.settings
    // permissions can change in Android's settings while we're away: look again on return
    var tick by remember { mutableIntStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val allowed = remember(tick, s.followLocation) { LocationFinder.hasPermission(ctx) }
    val precise = remember(tick, s.followLocation) { LocationFinder.hasPrecise(ctx) }
    val background = remember(tick, s.followLocation) { LocationFinder.hasBackground(ctx) }

    fun locateNow() {
        val src = source ?: LocationFinder.androidSource(ctx)
        model.locate({ src.current() }, namer ?: LocationFinder.androidNamer(ctx))
    }
    val askForeground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tick++
        if (r.values.any { it }) { model.setFollowLocation(true); locateNow() }
        else model.say("Location access wasn't allowed, so My location stays off.")
    }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("myLocation")) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            SwitchRow("📍 Use my location",
                "Adds My location to your places and moves it when you travel to another suburb, town or country.",
                s.followLocation, "followSwitch") { on ->
                when {
                    !on -> model.setFollowLocation(false)
                    allowed -> { model.setFollowLocation(true); locateNow() }
                    else -> askForeground.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }
            }
            if (s.followLocation) {
                val here = s.here()
                val small = Modifier.padding(top = 6.dp)
                Text(when {
                    state.locating -> "Finding where you are…"
                    here != null && here.name == io.github.shanebl030glitch.rbsbweather.data.Here.UNNAMED ->       // no geocoder: show where
                        "Now: ${here.name} (" + String.format(java.util.Locale.US, "%.3f, %.3f", here.latitude, here.longitude) + ")"
                    here != null -> "Now: ${here.displayName}"
                    else -> "Waiting for your phone's location…"
                }, fontSize = 13.sp, modifier = small.testTag("hereStatus"))
                state.hereNote?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("hereNote")) }
                if (allowed && !precise) Text("Using your approximate location (good to a few kilometres), as you chose.", fontSize = 12.sp)
                TextButton(onClick = { locateNow() }, enabled = allowed && !state.locating, modifier = Modifier.testTag("locateNow")) { Text("Update my location now") }

                SwitchRow("Widgets show my location",
                    "The home-screen widgets and the status-bar temperature show 📍 My location instead of the place you last looked at.",
                    s.widgetHere, "widgetHereSwitch") { model.setWidgetHere(it) }

                Text("While the app is closed", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                if (background) {
                    Text("On: about every 15 minutes (when Android allows) My location and the widget follow you.", fontSize = 13.sp,
                        modifier = Modifier.testTag("backgroundOn"))
                } else {
                    Text("Off: My location updates when you open the app. To have the widget follow you while it's closed, " +
                        "choose \"Allow all the time\" for Location.", fontSize = 13.sp, modifier = Modifier.testTag("backgroundOff"))
                    Row {
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= 30 || !allowed) openAppSettings(ctx)          // Android 11+ only offers it there
                            else askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }, enabled = allowed, modifier = Modifier.testTag("allowBackground")) { Text("Allow all the time…") }
                    }
                }
                Text("Your location stays on your phone; only the rounded position (about 100 m) is sent to the weather service, " +
                    "the same as for any saved place. If your phone can't name the spot, its name is looked up from OpenStreetMap with that rounded position.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

private fun openAppSettings(ctx: android.content.Context) {
    try {
        ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { }
}
