// RBSB-Weather for Android. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shanebl030glitch.rbsbweather.data.ForecastCache
import io.github.shanebl030glitch.rbsbweather.data.MetNorway
import io.github.shanebl030glitch.rbsbweather.data.MetNorwayProvider
import io.github.shanebl030glitch.rbsbweather.data.OpenMeteo
import io.github.shanebl030glitch.rbsbweather.data.OpenMeteoProvider
import io.github.shanebl030glitch.rbsbweather.data.WeatherRepository
import io.github.shanebl030glitch.rbsbweather.ui.AppModel
import io.github.shanebl030glitch.rbsbweather.ui.RbsbApp
import io.github.shanebl030glitch.rbsbweather.ui.SettingsStore
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Settings in the app's private SharedPreferences. */
class PrefsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("rbsb-weather", Context.MODE_PRIVATE)
    override fun load(): String? = prefs.getString("settings", null)
    override fun save(json: String) {
        prefs.edit().putString("settings", json).apply()
    }
}

/** Keeps the app's state across screen rotation and while the app is in the background. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val openMeteo = OpenMeteo()
    private val repo = WeatherRepository(
        listOf(OpenMeteoProvider(openMeteo), MetNorwayProvider(MetNorway())),
        ForecastCache(File(app.filesDir, "forecasts")))
    val model = AppModel(PrefsStore(app), repo, openMeteo, viewModelScope, Dispatchers.IO)

    init {
        repo.cleanUp(model.state.value.settings.places)
    }
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RbsbApp(vm.model) }
    }

    override fun onResume() {
        super.onResume()
        vm.model.refresh()               // coming back to the app: update if it's due
    }
}
