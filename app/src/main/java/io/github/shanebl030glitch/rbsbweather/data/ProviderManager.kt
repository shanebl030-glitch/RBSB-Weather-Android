// Builds the provider chain from the settings (primary, then backups) and keeps API keys apart
// from the settings, in their own private store. Keys never appear in messages or the settings file.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

/** Where API keys are kept (a separate private SharedPreferences file on the phone, memory in tests). */
interface KeyStore {
    fun get(providerId: String): String?
    fun set(providerId: String, key: String?)
}

class MemoryKeys(private val map: MutableMap<String, String> = mutableMapOf()) : KeyStore {
    override fun get(providerId: String) = map[providerId]
    override fun set(providerId: String, key: String?) {
        if (key.isNullOrBlank()) map.remove(providerId) else map[providerId] = key.trim()
    }
}

/** A key is plain letters, digits and a few separators; anything else is a paste mistake. */
fun cleanKey(raw: String?): String? {
    val k = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
    if (k.isEmpty()) return null
    require(k.length in 8..128 && k.all { it.isLetterOrDigit() || it in "-_." }) {
        "That doesn't look like an API key (letters and digits only, 8–128 characters)."
    }
    return k
}

class ProviderManager(
    private val http: HttpClient,
    val keys: KeyStore,
    private val settings: () -> Settings,
) {
    private val openMeteo = OpenMeteoProvider(OpenMeteo(http))
    private val metNo = MetNorwayProvider(MetNorway(http))
    private val keyed = mutableMapOf<String, Provider>()

    /** One provider object per service, so state such as One Call fallback lasts between updates. */
    fun provider(id: String): Provider? = when (id) {
        "open-meteo" -> openMeteo
        "met.no" -> metNo
        else -> synchronized(keyed) {
            keyed.getOrPut(id) {
                val key = { keys.get(id) }
                when (id) {
                    "openweather" -> OpenWeatherProvider(http, key)
                    "weatherapi" -> WeatherApiProvider(http, key)
                    "visualcrossing" -> VisualCrossingProvider(http, key)
                    "tomorrow" -> TomorrowProvider(http, key)
                    "wunderground" -> WundergroundProvider(http, key, { settings().option("wunderground", "station") })
                    else -> return null
                }
            }
        }
    }

    fun hasKey(id: String) = !keys.get(id).isNullOrBlank()

    /**
     * The chain to ask: the primary always (so a missing key is reported), backups only when
     * they can work (a backup without its key is skipped quietly).
     */
    fun chain(): List<Provider> {
        val ids = settings().providers
        return ids.mapIndexedNotNull { i, id ->
            val info = providerInfo(id) ?: return@mapIndexedNotNull null
            if (i > 0 && info.needsKey && !hasKey(id)) null else provider(id)
        }
    }
}
