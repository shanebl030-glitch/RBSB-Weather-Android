// The app's state and actions: saved places, settings, forecasts, refreshing and search.
// Plain Kotlin (no Android types), so the tests can drive it directly.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import io.github.shanebl030glitch.rbsbweather.data.ALL_PROVIDERS
import io.github.shanebl030glitch.rbsbweather.data.Alert
import io.github.shanebl030glitch.rbsbweather.data.AlertSettings
import io.github.shanebl030glitch.rbsbweather.data.Notice
import io.github.shanebl030glitch.rbsbweather.data.Quake
import io.github.shanebl030glitch.rbsbweather.data.QuakeSettings
import io.github.shanebl030glitch.rbsbweather.data.Watch
import io.github.shanebl030glitch.rbsbweather.data.Forecast
import io.github.shanebl030glitch.rbsbweather.data.ProviderManager
import io.github.shanebl030glitch.rbsbweather.data.ProviderStatus
import io.github.shanebl030glitch.rbsbweather.data.Diagnostics
import io.github.shanebl030glitch.rbsbweather.data.RECOVERY_CHOICES
import io.github.shanebl030glitch.rbsbweather.data.Units
import io.github.shanebl030glitch.rbsbweather.data.WeatherCodes
import io.github.shanebl030glitch.rbsbweather.data.cleanKey
import io.github.shanebl030glitch.rbsbweather.data.providerInfo
import io.github.shanebl030glitch.rbsbweather.data.OpenMeteo
import io.github.shanebl030glitch.rbsbweather.data.Place
import io.github.shanebl030glitch.rbsbweather.data.Settings
import io.github.shanebl030glitch.rbsbweather.data.UnitPrefs
import io.github.shanebl030glitch.rbsbweather.data.WeatherException
import io.github.shanebl030glitch.rbsbweather.data.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where settings are kept (SharedPreferences on the phone, memory in tests). */
interface SettingsStore {
    fun load(): String?
    fun save(json: String)
}

val RETRY_STEPS_S = listOf(30L, 60L, 120L, 300L)
const val STARTUP_STAGGER_MS = 1500L
const val STARTUP_FRESH_MS = 2 * 60_000L

val TEST_PLACE = Place("Auckland", -36.8485, 174.7633, "New Zealand", "Auckland", "Pacific/Auckland")

class MemoryStore(var text: String? = null) : SettingsStore {
    override fun load() = text
    override fun save(json: String) { text = json }
}

/** The address each provider's forecasts come from (for Test connection). */
val API_HOSTS = mapOf("open-meteo" to "https://api.open-meteo.com/", "met.no" to "https://api.met.no/",
    "openweather" to "https://api.openweathermap.org/", "weatherapi" to "https://api.weatherapi.com/",
    "visualcrossing" to "https://weather.visualcrossing.com/", "tomorrow" to "https://api.tomorrow.io/",
    "wunderground" to "https://api.weather.com/")

enum class Screen { HOME, MOON, EARTH, SEARCH, PLACES, SETTINGS, PROVIDERS, ALERT_SETTINGS, QUAKE_SETTINGS, WARNINGS, QUAKES, HELP, ABOUT, UPDATES, CONNECTION }

sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Results(val places: List<Place>) : SearchState
    data class Failed(val message: String) : SearchState
}

data class UiState(
    val settings: Settings = Settings(),
    val forecasts: Map<String, Forecast> = emptyMap(),        // by Place.key
    val loading: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap(),
    val screen: Screen = Screen.HOME,
    val search: SearchState = SearchState.Idle,
    val message: String? = null,                              // one-off note ("Removed Auckland")
    val keysSaved: Set<String> = emptySet(),                  // provider ids with an API key (never the key itself)
    val providerTests: Map<String, String> = emptyMap(),      // provider id → result of "Test connection"
    val connection: List<String> = emptyList(),              // Test connection (0.6): the report so far
    val connectionRunning: Boolean = false,
    val testing: Set<String> = emptySet(),
    val alerts: Map<String, List<Alert>> = emptyMap(),        // shown alerts by place (not muted)
    val allAlerts: Map<String, List<Alert>> = emptyMap(),     // including muted ones (Warnings screen)
    val alertErrors: Map<String, String> = emptyMap(),
    val alertChecked: Double = 0.0,
    val quakes: List<Quake> = emptyList(),
    val quakeErrors: Map<String, String> = emptyMap(),
    val quakeChecked: Double = 0.0,
    val checking: Boolean = false,
    val earthGlobe: Boolean = false,                          // Earth screen: globe (true) or flat map
    val focusQuake: String? = null,
)

class AppModel(
    private val store: SettingsStore,
    private val repo: WeatherRepository,
    private val search: OpenMeteo,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val now: () -> Long = System::currentTimeMillis,
    private val providers: ProviderManager? = null,
    private val onForecast: (Forecast) -> Unit = {},          // e.g. update the home-screen widgets
    private val watch: Watch? = null,                         // warnings and earthquakes
    private val notify: (List<Notice>, Settings) -> Unit = { _, _ -> },
    private val onTempNotification: (Boolean) -> Unit = {},     // show or remove the temperature notification
) {
    private val _state = MutableStateFlow(UiState(settings = Settings.fromJson(store.load()),
        keysSaved = ALL_PROVIDERS.filter { it.needsKey && providers?.hasKey(it.id) == true }.map { it.id }.toSet()))
    /** The latest settings (the provider chain reads them on the network thread). */
    val settingsNow: Settings get() = _state.value.settings
    val state: StateFlow<UiState> = _state

    private var searchJob: Job? = null
    /** Failed updates in a row and when to try again, by place (as the desktop: 30 s, 1, 2, then every 5 min). */
    private val failures = mutableMapOf<String, Pair<Int, Long>>()

    init {
        // saved forecasts first, so something shows straight away (even offline)
        scope.launch {
            val cached = withContext(io) {
                _state.value.settings.places.mapNotNull { p -> repo.cached(p)?.let { p.key to it } }.toMap()
            }
            _state.update { it.copy(forecasts = cached + it.forecasts) }
            recomputeAlerts()
        }
    }

    // ----------------------------------------------------------- refreshing
    /** Refresh a place's weather (the selected one by default) if it's due, or always when forced. */
    fun refresh(place: Place? = _state.value.settings.selectedPlace(), force: Boolean = false) {
        place ?: return
        val s = _state.value
        if (place.key in s.loading) return
        val fc = s.forecasts[place.key]
        val ageMin = if (fc == null || fc.fromCache) Long.MAX_VALUE else (now() - fc.fetchedAt) / 60_000
        val retryAt = failures[place.key]?.second
        val due = when {
            force -> true
            retryAt != null && s.settings.refreshMinutes != 0 -> now() >= retryAt
            fc == null || fc.fromCache -> true
            s.settings.refreshMinutes == 0 -> false
            else -> ageMin >= s.settings.refreshMinutes
        }
        if (!due) return
        _state.update { it.copy(loading = it.loading + place.key) }
        scope.launch {
            val result = withContext(io) {
                try {
                    Result.success(repo.fetch(place))
                } catch (e: WeatherException) {
                    Result.failure(e)
                } catch (e: Exception) {
                    Result.failure(WeatherException("Unexpected problem: ${e.javaClass.simpleName}"))
                }
            }
            _state.update { st ->
                val stillSaved = st.settings.places.any { it.key == place.key }
                result.fold(
                    onSuccess = { f ->
                        failures.remove(place.key)
                        if (stillSaved) try { onForecast(f) } catch (e: Exception) { }
                        if (stillSaved) scope.launch { recomputeAlerts() }
                        if (!stillSaved) st.copy(loading = st.loading - place.key)
                        else st.copy(loading = st.loading - place.key, forecasts = st.forecasts + (place.key to f),
                            errors = st.errors - place.key)
                    },
                    onFailure = { e ->
                        val n = (failures[place.key]?.first ?: 0) + 1
                        failures[place.key] = n to (now() + RETRY_STEPS_S[minOf(n, RETRY_STEPS_S.size) - 1] * 1000L)
                        st.copy(loading = st.loading - place.key,
                            errors = if (stillSaved) st.errors + (place.key to (e.message ?: "Couldn't update.")) else st.errors)
                    })
            }
        }
    }

    /**
     * At start-up every saved place updates (as the desktop): the one on screen first, then the
     * others one at a time 1½ seconds apart, so a small free allowance isn't used up in a burst.
     * A place updated in the last 2 minutes is skipped.
     */
    fun refreshAll(staggerMs: Long = STARTUP_STAGGER_MS) {
        val s = _state.value.settings
        val first = s.selectedPlace() ?: return
        refresh(first)
        val others = s.places.filter { it.key != first.key }
        scope.launch {
            for (p in others) {
                delay(staggerMs)
                if (_state.value.settings.places.none { it.key == p.key }) continue
                val fc = _state.value.forecasts[p.key]
                if (fc != null && !fc.fromCache && now() - fc.fetchedAt < STARTUP_FRESH_MS) continue
                refresh(p)
            }
        }
    }

    // --------------------------------------------------------------- places
    fun select(key: String) {
        if (_state.value.settings.places.none { it.key == key }) return
        saveSettings(_state.value.settings.copy(selected = key))
        if (_state.value.settings.tempNotification) onTempNotification(true)
        refresh()
    }

    fun add(place: Place) {
        val s = _state.value.settings
        val next = try { s.withPlace(place) } catch (e: IllegalArgumentException) {
            _state.update { it.copy(message = e.message) }
            return
        }
        saveSettings(next)
        _state.update { it.copy(screen = Screen.HOME, search = SearchState.Idle) }
        scope.launch {
            val cached = withContext(io) { repo.cached(place) }
            if (cached != null) _state.update { it.copy(forecasts = it.forecasts + (place.key to cached)) }
            refresh(place, force = true)
            checkWatch(force = true)
        }
    }

    fun remove(key: String) {
        val s = _state.value.settings
        val place = s.places.firstOrNull { it.key == key } ?: return
        saveSettings(s.withoutPlace(key))
        _state.update { it.copy(forecasts = it.forecasts - key, errors = it.errors - key,
            loading = it.loading - key, message = "Removed ${place.name}") }
        scope.launch { withContext(io) { repo.forget(place) } }
    }

    fun move(key: String, delta: Int) = saveSettings(_state.value.settings.moved(key, delta))

    // ------------------------------------------------------------- settings
    fun setUnits(u: UnitPrefs) = saveSettings(_state.value.settings.copy(units = u))
    fun setRefreshMinutes(m: Int) = saveSettings(_state.value.settings.copy(refreshMinutes = m))
    fun setAnimations(on: Boolean) = saveSettings(_state.value.settings.copy(animations = on))

    // ------------------------------------------------- warnings and earthquakes
    /** Work out every place's alerts from the last check and the forecasts (no network). */
    fun recomputeAlerts() {
        val w = watch ?: return
        val st = _state.value
        val shown = st.settings.places.associate { p -> p.key to w.alertsFor(p, st.settings, st.forecasts[p.key]) }
        val all = st.settings.places.associate { p -> p.key to w.allAlertsFor(p, st.settings, st.forecasts[p.key]) }
        _state.update { it.copy(alerts = shown, allAlerts = all, alertErrors = w.alertStore.errors, alertChecked = w.alertStore.checked,
            quakes = w.shownQuakes(it.settings), quakeErrors = w.quakeStore.errors, quakeChecked = w.quakeStore.checked) }
    }

    /** Check warnings and quakes when they're due (or now, when forced), then notify. */
    fun checkWatch(force: Boolean = false) {
        val w = watch ?: return
        if (_state.value.checking) return
        val s = _state.value.settings
        if (s.places.isEmpty() && !force) { recomputeAlerts(); return }
        _state.update { it.copy(checking = true) }
        scope.launch {
            val notices = withContext(io) {
                val out = mutableListOf<Notice>()
                try { if (s.alerts.official || s.alerts.own) out += w.checkAlerts(s, _state.value.forecasts, force) } catch (e: Exception) { }
                try { out += w.checkQuakes(s, force) } catch (e: Exception) { }
                out
            }
            _state.update { it.copy(checking = false) }
            recomputeAlerts()
            if (notices.isNotEmpty()) try { notify(notices, s) } catch (e: Exception) { }
        }
    }

    fun setAlertSettings(a: AlertSettings) {
        val old = _state.value.settings.alerts
        saveSettings(_state.value.settings.copy(alerts = a))
        recomputeAlerts()
        if (a.official && !old.official) checkWatch(force = true)
    }

    fun setEarthClouds(on: Boolean) = saveSettings(_state.value.settings.copy(earthClouds = on))
    /** Menu → Test connection (0.6): DNS, IPv4/IPv6, handshakes, then timed real requests. */
    fun testConnection(header: List<String>, resolve: (String) -> Array<java.net.InetAddress> = { java.net.InetAddress.getAllByName(it) },
                       prober: Diagnostics.Prober = Diagnostics.realProber) {
        if (_state.value.connectionRunning) return
        _state.update { it.copy(connection = emptyList(), connectionRunning = true) }
        val place = _state.value.settings.selectedPlace() ?: TEST_PLACE
        val chain = try { providers?.chain() } catch (e: Exception) { null } ?: emptyList()
        val urls = listOf("https://geocoding-api.open-meteo.com/v1/search", "https://api.open-meteo.com/v1/forecast") +
            chain.mapNotNull { API_HOSTS[it.id] }
        val checks = listOf<Pair<String, () -> Unit>>("Place search" to { search.search(place.name) }) +
            chain.map { p -> "${p.name} forecast for ${place.name}" to { p.forecast(place); Unit } }
        scope.launch {
            withContext(io) {
                try {
                    Diagnostics.run(header, Diagnostics.hostsOf(urls), checks, resolve, prober,
                        out = { line -> _state.update { it.copy(connection = it.connection + line) } })
                } catch (e: Exception) {
                    _state.update { it.copy(connection = it.connection + "The test stopped: ${e.javaClass.simpleName}") }
                }
            }
            _state.update { it.copy(connectionRunning = false) }
        }
    }

    fun setUpdateCheck(on: Boolean) = saveSettings(_state.value.settings.copy(updateCheck = on))
    fun setTempNotification(on: Boolean) { saveSettings(_state.value.settings.copy(tempNotification = on)); onTempNotification(on) }
    fun setEarthQuakes(on: Boolean) = setQuakeSettings(_state.value.settings.quakes.copy(show = on))

    /** Earth screens: show the flat map or the globe; a quake to focus (from a notification). */
    fun openEarth(globe: Boolean? = null, focusQuake: String? = null) {
        _state.update { it.copy(screen = Screen.EARTH, earthGlobe = globe ?: it.earthGlobe, focusQuake = focusQuake) }
    }
    fun setEarthGlobe(on: Boolean) = _state.update { it.copy(earthGlobe = on) }

    fun setQuakeSettings(q: QuakeSettings) {
        val old = _state.value.settings.quakes
        saveSettings(_state.value.settings.copy(quakes = q))
        recomputeAlerts()
        if (q.period != old.period || q.minMag != old.minMag) checkWatch(force = true)
    }

    /** Take a warning off the banner, badges and notifications (it stays listed on the Warnings screen). */
    fun mute(id: String, on: Boolean = true) {
        val a = _state.value.settings.alerts
        setAlertSettings(a.copy(muted = if (on) a.muted + id else a.muted - id))
    }

    fun openWarnings(placeKey: String? = null) {
        if (placeKey != null && _state.value.settings.places.any { it.key == placeKey }) saveSettings(_state.value.settings.copy(selected = placeKey))
        go(Screen.WARNINGS)
    }

    // ----------------------------------------------------- weather providers
    fun providerStatus(id: String): ProviderStatus = repo.status(id)

    private fun setChain(ids: List<String>) {
        val clean = ids.filter { providerInfo(it) != null }.distinct()
        if (clean.isEmpty()) return
        saveSettings(_state.value.settings.copy(providers = clean))
        repo.resetRecovery()
    }

    fun makePrimary(id: String) {
        val l = _state.value.settings.providers
        setChain(listOf(id) + l.filterNot { it == id })
    }

    /** Add a provider as the last backup, or take it out (the primary can't be taken out). */
    fun setBackup(id: String, on: Boolean) {
        val l = _state.value.settings.providers
        if (on) { if (id !in l) setChain(l + id) }
        else if (l.firstOrNull() != id) setChain(l - id)
    }

    fun moveProvider(id: String, delta: Int) {
        val l = _state.value.settings.providers.toMutableList()
        val i = l.indexOf(id)
        if (i <= 0) return                                        // the primary stays first (use makePrimary)
        val j = (i + delta).coerceIn(1, l.size - 1)
        if (i == j) return
        l.add(j, l.removeAt(i))
        setChain(l)
    }

    /** "Use the primary now": ask the primary first at the next update (and update now). */
    fun usePrimaryNow() {
        repo.resetRecovery()
        refresh(force = true)
        _state.update { it.copy(message = "The primary provider will be asked first") }
    }

    fun setRecovery(minutes: Int) {
        if (minutes !in RECOVERY_CHOICES) return
        saveSettings(_state.value.settings.copy(recovery = minutes))
        repo.resetRecovery()
    }

    fun setProviderOption(provider: String, name: String, value: String) {
        val info = providerInfo(provider)?.option
        val v = value.trim()
        if (v.isNotEmpty() && (info == null || info.first != name || !info.third.matches(v))) {
            _state.update { it.copy(message = "That isn't a valid ${info?.second?.substringBefore(" (") ?: name}.") }
            return
        }
        saveSettings(_state.value.settings.withOption(provider, name, v))
    }

    /** Save (or clear, when blank) a provider's API key. The key goes to its own private store. */
    fun setKey(id: String, raw: String) {
        val pm = providers ?: return
        val key = try { cleanKey(raw) } catch (e: IllegalArgumentException) {
            _state.update { it.copy(message = e.message) }
            return
        }
        try { pm.keys.set(id, key) } catch (e: Exception) {
            _state.update { it.copy(message = "Couldn't save the key: ${e.javaClass.simpleName}") }
            return
        }
        val name = providerInfo(id)?.name ?: id
        _state.update { it.copy(keysSaved = if (key == null) it.keysSaved - id else it.keysSaved + id,
            providerTests = it.providerTests - id,
            message = if (key == null) "Removed the $name key" else "Saved the $name key") }
        repo.resetRecovery()
    }

    /** Ask one provider for the selected place (or Auckland) and report what happened. */
    fun testProvider(id: String) {
        val pm = providers ?: return
        val p = pm.provider(id) ?: return
        if (id in _state.value.testing) return
        val place = _state.value.settings.selectedPlace() ?: TEST_PLACE
        _state.update { it.copy(testing = it.testing + id, providerTests = it.providerTests - id) }
        scope.launch {
            val msg = withContext(io) {
                try {
                    val f = p.forecast(place)
                    "Working: ${Units.fmtTemp(f.current.temperature, _state.value.settings.units.temp)} " +
                        "${WeatherCodes.describe(f.current.weatherCode).lowercase()} at ${place.name}, " +
                        "${f.daily.size} days, ${f.hourly.size} hours." +
                        (try { p.usage() } catch (e: Exception) { emptyMap() }).values.joinToString("") { " " + it.replaceFirstChar { c -> c.uppercase() } + "." }
                } catch (e: WeatherException) {
                    "Not working: ${e.message}"
                } catch (e: Exception) {
                    "Not working: unexpected problem (${e.javaClass.simpleName})."
                }
            }
            _state.update { it.copy(testing = it.testing - id, providerTests = it.providerTests + (id to msg)) }
        }
    }

    private fun saveSettings(s: Settings) {
        _state.update { it.copy(settings = s) }
        try {
            store.save(s.toJson().toString())
        } catch (e: Exception) {
            _state.update { it.copy(message = "Couldn't save your settings: ${e.message}") }
        }
    }

    // ------------------------------------------------------------ navigation
    fun go(screen: Screen) = _state.update { it.copy(screen = screen, search = if (screen == Screen.SEARCH) SearchState.Idle else it.search) }
    fun back(): Boolean {
        val sc = _state.value.screen
        if (sc == Screen.HOME) return false
        _state.update { it.copy(screen = if (sc in setOf(Screen.PROVIDERS, Screen.ALERT_SETTINGS, Screen.QUAKE_SETTINGS)) Screen.SETTINGS else Screen.HOME, focusQuake = null) }
        return true
    }
    fun say(msg: String) = _state.update { it.copy(message = msg) }
    fun messageShown() = _state.update { it.copy(message = null) }

    // ---------------------------------------------------------------- search
    /** Search as the user types (waits for a short pause first). */
    fun searchFor(text: String, pauseMs: Long = 400) {
        searchJob?.cancel()
        if (text.trim().length < 2) {
            _state.update { it.copy(search = SearchState.Idle) }
            return
        }
        searchJob = scope.launch {
            delay(pauseMs)
            _state.update { it.copy(search = SearchState.Searching) }
            val r = withContext(io) {
                try {
                    SearchState.Results(search.search(text))
                } catch (e: WeatherException) {
                    SearchState.Failed(e.message ?: "Search failed.")
                } catch (e: Exception) {
                    SearchState.Failed("Search failed (${e.javaClass.simpleName}).")
                }
            }
            _state.update { it.copy(search = r) }
        }
    }
}
