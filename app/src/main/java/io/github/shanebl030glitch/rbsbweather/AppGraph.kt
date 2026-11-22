// The objects the app and the home-screen widgets share: settings, API keys, providers and the
// saved forecasts. One copy per process, so a widget update and the open app use the same chain.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

import android.content.Context
import io.github.shanebl030glitch.rbsbweather.data.AlertClient
import io.github.shanebl030glitch.rbsbweather.data.ForecastCache
import io.github.shanebl030glitch.rbsbweather.data.QuakeClient
import io.github.shanebl030glitch.rbsbweather.data.Watch
import io.github.shanebl030glitch.rbsbweather.data.Http
import io.github.shanebl030glitch.rbsbweather.data.KeyStore
import io.github.shanebl030glitch.rbsbweather.data.ProviderManager
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.WeatherRepository
import io.github.shanebl030glitch.rbsbweather.ui.SettingsStore
import java.io.File

/** Settings in the app's private SharedPreferences. */
class PrefsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("rbsb-weather", Context.MODE_PRIVATE)
    override fun load(): String? = prefs.getString("settings", null)
    override fun save(json: String) {
        prefs.edit().putString("settings", json).apply()
    }
}

/** API keys in their own private SharedPreferences file, never in the settings JSON. */
class PrefsKeys(context: Context) : KeyStore {
    private val prefs = context.getSharedPreferences("rbsb-weather-keys", Context.MODE_PRIVATE)
    override fun get(providerId: String): String? = prefs.getString(providerId, null)
    override fun set(providerId: String, key: String?) {
        prefs.edit().apply { if (key.isNullOrBlank()) remove(providerId) else putString(providerId, key) }.apply()
    }
}

class AppGraph private constructor(context: Context) {
    val store = PrefsStore(context)
    /** Set by the open app so the chain follows changes at once; otherwise read from storage. */
    @Volatile var liveSettings: (() -> Settings)? = null
    fun settings(): Settings = liveSettings?.invoke() ?: Settings.fromJson(store.load())
    val providers = ProviderManager(Http.default, PrefsKeys(context), ::settings)
    val repo = WeatherRepository({ providers.chain() }, ForecastCache(File(context.filesDir, "forecasts")),
        { settings().recovery })
    /** Warnings and earthquakes, shared by the app and the background check. */
    val watch = Watch(File(context.filesDir, "watch"), AlertClient(Http.bytes), QuakeClient(Http.bytes))
    /** Update checks (0.6): GitHub's newest release, and when it was last asked. */
    var updateClient = io.github.shanebl030glitch.rbsbweather.data.UpdateClient()
    val updateStore = io.github.shanebl030glitch.rbsbweather.data.UpdateStore(File(context.filesDir, "updates"))

    companion object {
        @Volatile private var instance: AppGraph? = null
        fun get(context: Context): AppGraph = instance ?: synchronized(this) {
            instance ?: AppGraph(context.applicationContext).also { instance = it }
        }
    }
}
