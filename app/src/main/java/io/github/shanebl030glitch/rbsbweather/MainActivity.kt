// RBSB-Weather for Android. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shanebl030glitch.rbsbweather.data.OpenMeteo
import io.github.shanebl030glitch.rbsbweather.ui.AppModel
import io.github.shanebl030glitch.rbsbweather.ui.RbsbApp
import io.github.shanebl030glitch.rbsbweather.widget.Widgets
import io.github.shanebl030glitch.rbsbweather.background.ChecksWorker
import io.github.shanebl030glitch.rbsbweather.background.Notifier
import io.github.shanebl030glitch.rbsbweather.ui.Screen
import android.Manifest
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers

/** Keeps the app's state across screen rotation and while the app is in the background. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = AppGraph.get(app)
    val model = AppModel(graph.store, graph.repo, OpenMeteo(), viewModelScope, Dispatchers.IO,
        providers = graph.providers, onForecast = { Widgets.updateAll(app) }, watch = graph.watch,
        notify = { notices, s -> Notifier.post(app, notices, s.alerts.redAlarm); Widgets.updateAll(app) })

    init {
        graph.liveSettings = { model.settingsNow }
        graph.repo.cleanUp(model.state.value.settings.places)
    }

    override fun onCleared() {
        graph.liveSettings = null
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLog.install(this)                    // first, so any later crash is recorded
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        io.github.shanebl030glitch.rbsbweather.ui.scene.MoonRender.init(applicationContext.assets)
        io.github.shanebl030glitch.rbsbweather.ui.earth.EarthRender.init(applicationContext.assets)
        Notifier.channels(this)
        ChecksWorker.schedule(this)
        askForNotifications()
        handle(intent)
        val crash = CrashLog.pending(this)
        setContent { RbsbApp(vm.model); CrashDialog(this, crash) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A tapped notification opens the Warnings or Earthquakes screen. */
    private fun handle(intent: Intent?) {
        when (intent?.getStringExtra(Notifier.EXTRA_OPEN)) {
            "warnings" -> vm.model.openWarnings(intent.getStringExtra(Notifier.EXTRA_PLACE))
            "quakes" -> vm.model.openEarth(globe = true, focusQuake = intent.getStringExtra(Notifier.EXTRA_PLACE))   // turn the globe to the quake
        }
        intent?.removeExtra(Notifier.EXTRA_OPEN)
    }

    /** Android 13 and newer ask before an app may notify. Asked once, when it's needed. */
    private fun askForNotifications() {
        val s = vm.model.settingsNow
        if (Build.VERSION.SDK_INT >= 33 && (s.alerts.notify || s.quakes.notifyNear || s.quakes.notifyMajor) && !Notifier.allowed(this)) {
            val prefs = getSharedPreferences("rbsb-weather", MODE_PRIVATE)
            if (!prefs.getBoolean("askedNotifications", false)) {
                prefs.edit().putBoolean("askedNotifications", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.model.refresh()               // coming back to the app: update if it's due
    }

    override fun onStop() {
        super.onStop()
        Widgets.updateAll(this)          // the widgets follow the place you were looking at
    }
}
