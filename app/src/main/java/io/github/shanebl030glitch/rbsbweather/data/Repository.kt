// Getting the weather: the primary provider first, then the backups in order; the last forecast
// for each place is saved so the app shows something useful offline.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import org.json.JSONObject
import java.io.File

/** A weather service the repository can ask. */
interface Provider {
    val name: String
    val id: String get() = name
    fun forecast(place: Place): Forecast
}

class OpenMeteoProvider(private val api: OpenMeteo) : Provider {
    override val name = OpenMeteo.NAME
    override val id = "open-meteo"
    override fun forecast(place: Place) = api.forecast(place)
}

class MetNorwayProvider(private val api: MetNorway) : Provider {
    override val name = MetNorway.NAME
    override val id = "met.no"
    override fun forecast(place: Place) = api.forecast(place)
}

/** How a provider got on last time (shown in Settings → Weather Providers). */
data class ProviderStatus(val lastOk: Long = 0L, val lastError: String? = null, val lastErrorAt: Long = 0L)

/** After the primary fails: try it again at the next update (0), after 15/30/60 minutes, or stay on the backup (-1). */
val RECOVERY_CHOICES = listOf(0, 15, 30, 60, -1)

class WeatherRepository(
    private val providers: () -> List<Provider>,
    private val cache: ForecastCache?,
    private val recoveryMinutes: () -> Int = { 0 },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(list: List<Provider>, cache: ForecastCache?) : this({ list }, cache)

    private val statuses = java.util.concurrent.ConcurrentHashMap<String, ProviderStatus>()
    @Volatile private var primaryFailedAt: Pair<String, Long>? = null

    fun status(id: String): ProviderStatus = statuses[id] ?: ProviderStatus()

    /** Forget that the primary failed (the user changed providers or asked to go back). */
    fun resetRecovery() { primaryFailedAt = null }

    /** The order providers will be asked in right now: the primary waits out its recovery time. */
    fun order(): List<Provider> {
        val list = providers()
        val first = list.firstOrNull() ?: return list
        val failed = primaryFailedAt?.takeIf { it.first == first.id } ?: return list
        val rec = recoveryMinutes()
        val resting = when {
            rec < 0 -> true
            rec == 0 -> false
            else -> clock() - failed.second < rec * 60_000L
        }
        return if (resting && list.size > 1) list.drop(1) + first else list
    }

    /**
     * The forecast for a place from the first provider that answers. If all fail, the
     * error says what each one said (the caller can still show the saved copy).
     */
    fun fetch(place: Place): Forecast {
        val problems = mutableListOf<String>()
        val primary = providers().firstOrNull()?.id
        if (primary == null) throw WeatherException("No weather provider is chosen (Settings → Weather Providers).")
        for (p in order()) {
            try {
                val fc = p.forecast(place).copy(place = place)
                statuses[p.id] = status(p.id).copy(lastOk = clock())
                if (p.id == primary) primaryFailedAt = null
                cache?.save(fc)
                return fc
            } catch (e: Exception) {
                val msg = if (e is WeatherException) e.message ?: "failed" else "unexpected problem (${e.javaClass.simpleName})"
                problems += "${p.name}: $msg"
                statuses[p.id] = status(p.id).copy(lastError = msg, lastErrorAt = clock())
                if (p.id == primary && primaryFailedAt?.first != primary) primaryFailedAt = primary to clock()
            }
        }
        throw WeatherException(
            if (problems.size == 1) problems[0].substringAfter(": ")
            else "Couldn't get the weather. " + problems.joinToString("  ·  "), retryable = true)
    }

    fun cached(place: Place): Forecast? = cache?.load(place)

    fun forget(place: Place) {
        cache?.remove(place)
    }

    fun cleanUp(keep: List<Place>) {
        cache?.cleanOrphans(keep)
    }
}

/** One small JSON file per place in the app's private folder. */
class ForecastCache(private val dir: File) {
    private fun file(place: Place) = File(dir, "forecast-" + place.key.replace(Regex("[^0-9.,-]"), "_") + ".json")

    fun save(fc: Forecast) {
        dir.mkdirs()
        val target = file(fc.place)
        val tmp = File(dir, target.name + "." + Thread.currentThread().id + ".tmp")   // the app and a widget may save at once
        tmp.writeText(fc.toJson().toString())
        if (!tmp.renameTo(target)) {               // rename is atomic on the same folder
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun load(place: Place): Forecast? {
        val f = file(place)
        if (!f.isFile || f.length() > 4 * 1024 * 1024) return null
        return try {
            Forecast.fromJson(JSONObject(f.readText()))?.copy(place = place)
        } catch (e: Exception) {
            null
        }
    }

    fun remove(place: Place) {
        file(place).delete()
    }

    /** Delete saved forecasts of places that are no longer saved. */
    fun cleanOrphans(keep: List<Place>) {
        val names = keep.map { file(it).name }.toSet()
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("forecast-") && f.name.endsWith(".json") && f.name !in names) f.delete()
            if (f.name.startsWith("forecast-") && f.name.endsWith(".tmp") && System.currentTimeMillis() - f.lastModified() > 60_000) f.delete()
        }
    }
}
