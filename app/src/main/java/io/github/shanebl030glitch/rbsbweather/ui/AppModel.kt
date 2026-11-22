// The app's state and actions: saved places, settings, forecasts, refreshing and search.
// Plain Kotlin (no Android types), so the tests can drive it directly.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import io.github.shanebl030glitch.rbsbweather.data.Forecast
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

class MemoryStore(var text: String? = null) : SettingsStore {
    override fun load() = text
    override fun save(json: String) { text = json }
}

enum class Screen { HOME, SEARCH, PLACES, SETTINGS, HELP, ABOUT }

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
)

class AppModel(
    private val store: SettingsStore,
    private val repo: WeatherRepository,
    private val search: OpenMeteo,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(UiState(settings = Settings.fromJson(store.load())))
    val state: StateFlow<UiState> = _state

    private var searchJob: Job? = null

    init {
        // saved forecasts first, so something shows straight away (even offline)
        scope.launch {
            val cached = withContext(io) {
                _state.value.settings.places.mapNotNull { p -> repo.cached(p)?.let { p.key to it } }.toMap()
            }
            _state.update { it.copy(forecasts = cached + it.forecasts) }
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
        val due = when {
            force || fc == null || fc.fromCache -> true
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
                        if (!stillSaved) st.copy(loading = st.loading - place.key)
                        else st.copy(loading = st.loading - place.key, forecasts = st.forecasts + (place.key to f),
                            errors = st.errors - place.key)
                    },
                    onFailure = { e ->
                        st.copy(loading = st.loading - place.key,
                            errors = if (stillSaved) st.errors + (place.key to (e.message ?: "Couldn't update.")) else st.errors)
                    })
            }
        }
    }

    /** Every saved place, one after the other (at start-up). */
    fun refreshAll() {
        _state.value.settings.places.forEach { refresh(it) }
    }

    // --------------------------------------------------------------- places
    fun select(key: String) {
        if (_state.value.settings.places.none { it.key == key }) return
        saveSettings(_state.value.settings.copy(selected = key))
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
        if (_state.value.screen == Screen.HOME) return false
        _state.update { it.copy(screen = Screen.HOME) }
        return true
    }
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
