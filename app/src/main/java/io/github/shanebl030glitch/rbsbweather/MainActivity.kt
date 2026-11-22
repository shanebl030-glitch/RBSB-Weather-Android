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
import kotlinx.coroutines.Dispatchers

/** Keeps the app's state across screen rotation and while the app is in the background. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = AppGraph.get(app)
    val model = AppModel(graph.store, graph.repo, OpenMeteo(), viewModelScope, Dispatchers.IO,
        providers = graph.providers, onForecast = { Widgets.updateAll(app) })

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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        io.github.shanebl030glitch.rbsbweather.ui.scene.MoonRender.init(applicationContext.assets)
        setContent { RbsbApp(vm.model) }
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
